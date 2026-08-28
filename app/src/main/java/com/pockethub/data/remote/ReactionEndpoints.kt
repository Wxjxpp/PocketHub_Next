package com.pockethub.data.remote

// Reaction endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ReactionEndpoints {

    /** Add a reaction to an issue / PR comment (issue-PR comments share the same endpoint). */
    @POST("repos/{owner}/{repo}/issues/comments/{comment_id}/reactions")
    suspend fun createIssueCommentReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body content: GitHubApi.ReactionRequest,
    ): GitHubApi.ReactionResponse

    /**
     * List reactions on an issue / PR comment. We use the IDs returned here to
     * delete reactions the current viewer has previously added (the GitHub API
     * needs a specific reaction_id, not just a content type).
     */
    @GET("repos/{owner}/{repo}/issues/comments/{comment_id}/reactions")
    suspend fun listIssueCommentReactions(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Query("per_page") perPage: Int = 100,
    ): List<GitHubApi.ReactionResponse>

    /** Delete a reaction on an issue / PR comment. */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}/reactions/{reaction_id}")
    suspend fun deleteIssueCommentReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Path("reaction_id") reactionId: Long,
    ): Response<Unit>
}
