package com.pockethub.data.remote

import com.pockethub.data.local.CacheDao
import com.pockethub.data.local.CachedItemEntity
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first wrapper around [GitHubApi]. Reads go through [CacheDao] with configurable
 * TTLs; writes bypass the cache and invalidate relevant entries.
 *
 * Cache TTLs:
 *  - Repositories / Issues / Releases / Feed: 5 minutes
 *  - Trending / Featured: 10 minutes (less time-sensitive)
 *  - Single repo / README: 3 minutes
 */
@Singleton
class CachedRepository @Inject constructor(
    private val api: GitHubApi,
    private val cacheDao: CacheDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val FIVE_MIN = 5 * 60 * 1000L
        private const val TEN_MIN = 10 * 60 * 1000L
        private const val THREE_MIN = 3 * 60 * 1000L
    }

    // ── Repositories ────────────────────────────────────

    suspend fun getMyRepositories(page: Int = 1, sort: String = "pushed", type: String? = null, visibility: String? = null, forceFresh: Boolean = false): List<Repository> {
        val key = "repos:mine:$page:$sort:$type:$visibility"
        return cacheFirst(key, FIVE_MIN, forceFresh) {
            api.getMyRepositories(page = page, sort = sort, type = type, visibility = visibility)
        }
    }

    suspend fun getStarredRepositories(page: Int = 1, forceFresh: Boolean = false): List<Repository> {
        val key = "repos:starred:$page"
        return cacheFirst(key, FIVE_MIN, forceFresh) {
            api.getStarredRepositories(page = page).body().orEmpty()
        }
    }

    /**
     * Total starred-repo count for the signed-in user. GitHub has no REST field
     * for it, so we go through three layers:
     *  1. GraphQL `viewer.starredRepositories.totalCount` — exact, one round
     *     trip, immune to proxies that strip response headers (the bug that
     *     made this read "1").
     *  2. REST `link` header last-page number (per_page=1).
     *  3. Link header missing/unparseable → page through per_page=100.
     */
    suspend fun getStarredTotalCount(): Int {
        try {
            val resp = api.graphQL(
                GitHubApi.GraphQLRequest(
                    query = "query { viewer { starredRepositories(first: 1) { totalCount } } }",
                ),
            )
            val total = resp.data?.get("viewer")
                ?.let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("starredRepositories")
                ?.let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("totalCount")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content?.toIntOrNull()
            if (total != null) return total
        } catch (_: Exception) {
            // fall through to REST
        }
        return try {
            val resp = api.getStarredRepositories(page = 1, perPage = 1)
            val link = resp.headers()["link"]
            // Link: <...&page=2>; rel="next", <...&page=42>; rel="last"
            val last = link?.let {
                Regex("""page=(\d+)>;\s*rel="last"""").find(it)?.groupValues?.get(1)?.toIntOrNull()
            }
            if (last != null) {
                last
            } else {
                // Header stripped by a proxy — count by paging.
                var page = 1
                var total = 0
                while (page <= 10) {
                    val items = api.getStarredRepositories(page = page, perPage = 100).body().orEmpty()
                    total += items.size
                    if (items.size < 100) break
                    page++
                }
                total
            }
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getRepository(owner: String, repo: String): Repository {
        val key = "repo:$owner/$repo"
        return cacheFirst(key, THREE_MIN) {
            api.getRepository(owner, repo)
        }
    }

    suspend fun getUserRepositories(login: String, page: Int = 1, sort: String = "updated"): List<Repository> {
        val key = "repos:user:$login:$page:$sort"
        return cacheFirst(key, FIVE_MIN) {
            api.getUserRepositories(login, page = page, sort = sort)
        }
    }

    // ── Issues ──────────────────────────────────────────

    suspend fun getIssues(owner: String, repo: String, state: String = "open", page: Int = 1, forceFresh: Boolean = false): List<Issue> {
        val key = "issues:$owner/$repo:$state:$page"
        return cacheFirst(key, FIVE_MIN, forceFresh) {
            api.getIssues(owner, repo, state = state, page = page)
        }
    }

    // ── Releases ────────────────────────────────────────

    suspend fun getReleases(owner: String, repo: String, page: Int = 1, forceFresh: Boolean = false): List<GitHubApi.Release> {
        val key = "releases:$owner/$repo:$page"
        return cacheFirst(key, FIVE_MIN, forceFresh) {
            api.getReleases(owner, repo, page = page)
        }
    }

    /** Invalidate cached release pages so the next fetch hits the network. */
    suspend fun invalidateReleases(owner: String, repo: String) {
        cacheDao.evictContaining("releases:$owner/$repo")
    }

    // ── Feed / Trending ─────────────────────────────────

    suspend fun searchTrending(query: String, sort: String = "stars", perPage: Int = 20): GitHubApi.SearchRepoResult {
        val key = "trending:$query:$sort:$perPage"
        return cacheFirst(key, TEN_MIN) {
            api.searchTrending(query = query, sort = sort, perPage = perPage)
        }
    }

    /**
     * Same as [searchTrending] but bypasses the in-room cache entirely. The
     * Explore tab's double-tap refresh uses this path so the request reaches
     * the network even while the cached entry is within its TTL.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun searchTrendingFresh(query: String, sort: String = "stars", perPage: Int = 20): GitHubApi.SearchRepoResult {
        val result = api.searchTrending(query = query, sort = sort, perPage = perPage)
        try {
            val serialized = json.encodeToString(serializer<GitHubApi.SearchRepoResult>(), result)
            val key = "trending:$query:$sort:$perPage"
            cacheDao.put(CachedItemEntity(key = key, json = serialized))
        } catch (_: Exception) { /* non-fatal */ }
        return result
    }

    suspend fun getReceivedEvents(login: String, perPage: Int = 30, forceFresh: Boolean = false): List<FeedEvent> {
        val key = "feed:$login:$perPage"
        return cacheFirst(key, FIVE_MIN, forceFresh) {
            api.getReceivedEvents(login, perPage = perPage)
        }
    }

    // ── Notifications (always fresh — don't cache these) ─

    suspend fun getNotifications(
        page: Int = 1,
        perPage: Int = 50,
        all: Boolean = false,
        participating: Boolean = false,
    ): List<com.pockethub.data.model.GitHubNotification> =
        api.getNotifications(page = page, perPage = perPage, all = all, participating = participating)

    // ── README ──────────────────────────────────────────

    suspend fun getReadme(owner: String, repo: String, ref: String? = null): GitHubApi.ReadmeResponse {
        val key = "readme:$owner/$repo@$ref"
        return cacheFirst(key, THREE_MIN) {
            api.getReadme(owner, repo, ref = ref)
        }
    }

    // ── Cache invalidation ──────────────────────────────

    /** Clear cached entries for a specific repo (e.g. after starring/unstarring). */
    suspend fun invalidateRepo(owner: String, repo: String) {
        cacheDao.evictContaining("$owner/$repo")
        // Starred list changes when (un)starring — drop those pages too.
        cacheDao.evictContaining("repos:starred:")
    }

    /** Drop every page of the signed-in user's repository list after a repo mutation. */
    suspend fun invalidateMyRepositories() {
        cacheDao.evictContaining("repos:mine:")
    }

    /** Drop cached pages for a public user's repositories before pull-to-refresh. */
    suspend fun invalidateUserRepositories(login: String) {
        cacheDao.evictContaining("repos:user:$login:")
    }

    /** Evict items older than [maxAge]. Called periodically or on manual refresh. */
    suspend fun evictOlderThan(maxAge: Long) {
        cacheDao.evictOlderThan(maxAge)
    }

    suspend fun clearCache(): Int = cacheDao.clearAll()

    // ── Generic cache-first helper ──────────────────────

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> cacheFirst(
        key: String,
        ttlMs: Long,
        forceFresh: Boolean = false,
        fetch: suspend () -> T,
    ): T {
        // 1. Check cache (skipped when the caller forces a network refresh —
        //    pull-to-refresh must never serve a TTL-fresh blob as "new" data).
        if (!forceFresh) {
            val cached = cacheDao.getIfFresh(key, System.currentTimeMillis() - ttlMs)
            if (cached != null) {
                return json.decodeFromString(serializer(), cached)
            }
        }
        // 2. Fetch from network
        val result = fetch()
        // 3. Cache the result
        try {
            val serialized = json.encodeToString(serializer(), result)
            cacheDao.put(CachedItemEntity(key = key, json = serialized))
        } catch (_: Exception) {
            // Serialization failure is non-fatal — we just won't cache
        }
        return result
    }
}
