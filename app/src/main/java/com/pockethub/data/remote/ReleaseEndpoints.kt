package com.pockethub.data.remote

// Release endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ReleaseEndpoints {

    /** Releases for a repo. */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): List<GitHubApi.Release>

    /**
     * Delete a release. Requires the authenticated user to be the repo owner or
     * have admin permission (the `delete_repo` scope is NOT required for releases).
     * Returns 204 on success; 403 / 404 for permission / not-found cases.
     */
    @DELETE("repos/{owner}/{repo}/releases/{release_id}")
    suspend fun deleteRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("release_id") releaseId: Long,
    ): Response<Unit>
}
