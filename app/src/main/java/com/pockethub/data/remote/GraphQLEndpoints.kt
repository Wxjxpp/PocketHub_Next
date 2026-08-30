package com.pockethub.data.remote

// GraphQL v4 endpoint.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.http.Body
import retrofit2.http.POST

interface GraphQLEndpoints {

    /** GraphQL endpoint (currently maps to https://api.github.com/graphql). */
    @POST("graphql")
    suspend fun graphQL(@Body body: GitHubApi.GraphQLRequest): GitHubApi.GraphQLResponse

    // ──────────────────────────────────────────────
    //  GitHub Actions — workflow run jobs & per-job logs
    //  (https://docs.github.com/en/rest/actions/workflow-jobs)
    // ──────────────────────────────────────────────
}
