package com.pockethub.data.remote

// Reaction endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.User
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
        @Body content: ReactionRequest,
    ): ReactionResponse

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
    ): List<ReactionResponse>

    /** Delete a reaction on an issue / PR comment. */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}/reactions/{reaction_id}")
    suspend fun deleteIssueCommentReaction(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Path("reaction_id") reactionId: Long,
    ): Response<Unit>

    /** GitHub reaction content values accepted by the reactions API. */
    enum class ReactionContent(val apiValue: String) {
        PLUS_ONE("+1"),
        MINUS_ONE("-1"),
        LAUGH("laugh"),
        CONFUSED("confused"),
        HEART("heart"),
        HOORAY("hooray"),
        ROCKET("rocket"),
        EYES("eyes");
    }

    @kotlinx.serialization.Serializable
    data class ReactionResponse(
        val id: Long = 0,
        val user: User? = null,
        val content: String = "",
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ReactionRequest(val content: String)

}
