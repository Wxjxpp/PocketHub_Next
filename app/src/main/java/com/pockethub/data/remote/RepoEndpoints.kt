package com.pockethub.data.remote

// Repository metadata, star / watch / fork endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

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

        /** README — returns base64 content + download_url. Parsed into [GitHubApi.ReadmeResponse].
     *  [ref] selects the branch (defaults to the repo's default branch). */
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null,
    ): GitHubApi.ReadmeResponse

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
        @Body payload: GitHubApi.WatchSubscriptionRequest = GitHubApi.WatchSubscriptionRequest(),
    ): Response<GitHubApi.WatchSubscription>

    /** Check if currently watched — 200 + subscription JSON or 404 when not subscribed. */
    @GET("repos/{owner}/{repo}/subscription")
    suspend fun getSubscription(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<GitHubApi.WatchSubscription>

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
        @Body body: GitHubApi.ForkRequest = GitHubApi.ForkRequest(),
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
        @Body body: GitHubApi.RepoUpdateRequest,
    ): Response<Repository>
}
