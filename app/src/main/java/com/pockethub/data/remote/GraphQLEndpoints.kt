package com.pockethub.data.remote

// GraphQL v4 endpoint.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import retrofit2.http.Body
import retrofit2.http.POST

interface GraphQLEndpoints {

    /** GraphQL endpoint (currently maps to https://api.github.com/graphql). */
    @POST("graphql")
    suspend fun graphQL(@Body body: GraphQLRequest): GraphQLResponse

    //  GitHub Actions — workflow run jobs & per-job logs
    //  (https://docs.github.com/en/rest/actions/workflow-jobs)

    /**
     * Body for a GraphQL query / mutation request.
     *
     * GitHub GraphQL v4 accepts POST with `{query, variables, operationName}`; the
     * `operationName` and `variables` fields can be omitted for single-operation
     * queries like the resolve / unresolve mutations used by this feature.
     */
    @kotlinx.serialization.Serializable
    data class GraphQLRequest(
        val query: String,
        @kotlinx.serialization.SerialName("variables") val variables: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    )

    /**
     * GitHub GraphQL response. `data` holds the per-field results object and
     * `errors` is non-empty on failure; both are optional per the GraphQL spec.
     */
    @kotlinx.serialization.Serializable
    data class GraphQLResponse(
        val data: kotlinx.serialization.json.JsonObject? = null,
        val errors: List<GraphQLError>? = null,
    )

    @kotlinx.serialization.Serializable
    data class GraphQLError(
        val message: String = "",
        @kotlinx.serialization.SerialName("type") val type: String? = null,
    )
}
