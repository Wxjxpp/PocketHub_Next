package com.pockethub.data.remote

// Search endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchEndpoints {

    /**
     * Generic repo search — single endpoint backing both the Explore feed
     * (Trending / Featured / For You sections) and the global Search screen.
     *
     * GitHub has no official Trending API; the search API is the closest
     * equivalent. Callers compose the appropriate `created:>/stars:>…` filter
     * strings and pick `sort`/`order`.
     */
    @GET("search/repositories")
    suspend fun searchTrending(
        @Query("q") query: String = "stars:>1",
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): SearchRepoResult

    /** Global search — repositories. */
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
    ): SearchRepoResult

    /** Global search — users. */
    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
    ): SearchUserResult

    /** Global search — code. */
    @GET("search/code")
    suspend fun searchCode(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): SearchCodeResult

    /**
     * Global search — issues & pull requests (GitHub's /search/issues endpoint
     * returns both; use `is:issue` / `is:pr` to scope). Backs the Profile work-list
     * ("Assigned to me", "Mentions me", "Created by me") via qualifier strings like
     * `assignee:<login> state:open`, `involves:<login>`, `author:<login>`.
     */
    @GET("search/issues")
    suspend fun searchIssues(
        @Query("q") query: String,
        @Query("sort") sort: String = "updated",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): SearchIssueResult

    //  Generic / raw endpoint for OAuth token exchange

    @kotlinx.serialization.Serializable
    data class SearchRepoResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<Repository> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class SearchUserResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<User> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class SearchCodeResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<CodeSearchItem> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class CodeSearchItem(
        val name: String = "",
        val path: String = "",
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String = "",
        val repository: Repository? = null,
    )

    /** Wrapper returned by /search/issues (issues + PRs share this shape). */
    @kotlinx.serialization.Serializable
    data class SearchIssueResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<com.pockethub.data.model.Issue> = emptyList(),
    )
}
