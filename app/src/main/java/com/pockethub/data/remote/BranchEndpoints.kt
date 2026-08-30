package com.pockethub.data.remote

// Branch listing endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BranchEndpoints {

    /** List branches for a repo. */
    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<GitHubApi.Branch>
}
