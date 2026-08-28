package com.pockethub.data.remote

// Repository metadata, star / watch / fork endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.Repository
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RepoEndpoints {

    /** Your repositories (paginated). */
    @GET("user/repos")
    suspend fun getMyRepositories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "pushed",       // pushed | updated | created
        @Query("direction") direction: String = "desc",
        @Query("type") type: String? = null,           // owner | collaborator | member
        @Query("visibility") visibility: String? = null, // public | private
    ): List<Repository>

    /** Starred repositories. Returns a Response so the caller can read the
     *  `link` header to count total pages (the endpoint has no total_count). */
    @GET("user/starred")
    suspend fun getStarredRepositories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc",
    ): Response<List<Repository>>

    /** Repository by full name. */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Repository

        /** README — returns base64 content + download_url. Parsed into [ReadmeResponse].
     *  [ref] selects the branch (defaults to the repo's default branch). */
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null,
    ): ReadmeResponse

    /** Toggle star — PUT with no body stars the repo. */
    @PUT("user/starred/{owner}/{repo}")
    suspend fun star(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** Check if the current user has starred the repo — 204 starred, 404 not. */
    @GET("user/starred/{owner}/{repo}")
    suspend fun checkStarred(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstar(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /**
     * Watch the repo — sets the current user as subscribed (will receive notifications
     * for releases / discussions / issue/PR activity depending on the `subscribed` flag).
     * `ignored=true` mutes the repo entirely. Default [payload] leaves both flags
     * untouched, which on GitHub means "watch all repo activity".
     */
    @PUT("repos/{owner}/{repo}/subscription")
    suspend fun watch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body payload: WatchSubscriptionRequest = WatchSubscriptionRequest(),
    ): Response<WatchSubscription>

    /** Check if currently watched — 200 + subscription JSON or 404 when not subscribed. */
    @GET("repos/{owner}/{repo}/subscription")
    suspend fun getSubscription(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<WatchSubscription>

    /** Unwatch — DELETE the subscription. */
    @DELETE("repos/{owner}/{repo}/subscription")
    suspend fun unwatch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** Fork a repository — 202 Accepted, repo object returned when complete.
     *  [name] lets the user rename the fork; omit for same-name-as-source. */
    @POST("repos/{owner}/{repo}/forks")
    suspend fun forkRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: ForkRequest = ForkRequest(),
    ): Response<Repository>

    /**
     * Delete a repository. Requires the authenticated user to be the owner (or an
     * org admin) AND the token to carry the `delete_repo` scope.
     * Returns 204 on success; 403 when missing rights/scope; 404 if not found.
     */
    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /**
     * Update repository settings — used here for toggling visibility
     * (private/public). Requires admin permission on the repo.
     * PUT-style update of the repo; returns the updated [Repository].
     * Docs: https://docs.github.com/en/rest/repos/repos#update-a-repository
     */
    @PATCH("repos/{owner}/{repo}")
    suspend fun updateRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: RepoUpdateRequest,
    ): Response<Repository>

    @kotlinx.serialization.Serializable
    data class ReadmeResponse(
        val name: String = "",
        val path: String = "",
        val content: String = "",          // base64 encoded markdown body
        val encoding: String = "base64",
        @kotlinx.serialization.SerialName("download_url") val downloadUrl: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val size: Long = 0,
    )

    @kotlinx.serialization.Serializable
    data class WatchSubscription(
        val subscribed: Boolean = false,
        val ignored: Boolean = false,
        val reason: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        val url: String? = null,
        @kotlinx.serialization.SerialName("repository_url") val repositoryUrl: String? = null,
        @kotlinx.serialization.SerialName("thread_url") val threadUrl: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WatchSubscriptionRequest(
        val subscribed: Boolean = true,
        val ignored: Boolean = false,
    )

    /** GitHub accepts `name` (and optional `default_branch_only`) on fork creation. */
    @kotlinx.serialization.Serializable
    data class ForkRequest(
        val name: String? = null,
        @kotlinx.serialization.SerialName("default_branch_only") val defaultBranchOnly: Boolean = false,
    )

    @kotlinx.serialization.Serializable
    data class RepoUpdateRequest(
        /**
         * `visibility: "public" | "private"` — GitHub's authoritative visibility
         * field. The legacy boolean `private` field still works but is deprecated
         * by GitHub; reaching for `visibility` avoids ambiguity (see
         * https://docs.github.com/en/rest/repos/repos#update-a-repository).
         */
        val visibility: String? = null,
        /** `private: true` makes the repo private; GitHub treats this field as authoritative for pub/priv toggle. */
        val `private`: Boolean? = null,
        /** Optional name update — pass-through only, left null for visibility changes. */
        val name: String? = null,
        /** Optional description update — left null for visibility changes. */
        val description: String? = null,
    )
}
