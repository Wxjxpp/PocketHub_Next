package com.pockethub.data.remote

// Issue CRUD, comments, labels, milestones endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.Issue
import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface IssueEndpoints {

    /** Issues for a repo. (PRs are also returned by this endpoint.) */
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc",
    ): List<Issue>

    /** Create a new issue. */
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: IssueCreateRequest,
    ): Issue

    /** Single issue detail. */
    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Issue

    /** Lock conversation on an issue or PR. Server returns 200 with empty body. */
    @Headers("Accept: application/vnd.github+json")
    @PUT("repos/{owner}/{repo}/issues/{number}/lock")
    suspend fun lockIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Response<Unit>

    /** Unlock conversation on an issue or PR. Server returns 204 with empty body. */
    @Headers("Accept: application/vnd.github+json")
    @DELETE("repos/{owner}/{repo}/issues/{number}/lock")
    suspend fun unlockIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Response<Unit>

    /** Comments on an issue or PR. Returns a Response so callers can read the
     *  `link` header to detect whether more pages exist (the GitHub API doesn't
     *  return total_count for this endpoint). */
    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun getIssueComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
    ): Response<List<IssueComment>>

    /**
     * Timeline events for an issue / PR — labeled, assigned, closed, reopened,
     * referenced, cross-referenced, milestoned, locked, unlocked, etc. Used to
     * render a chronological event stream interleaved with comments.
     */
    @GET("repos/{owner}/{repo}/issues/{number}/events")
    suspend fun getIssueEvents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): Response<List<IssueEvent>>

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun createIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: CommentRequest,
    ): IssueComment

    /** Update an issue's editable fields. Null fields are left unchanged by GitHub. */
    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: IssueUpdateRequest,
    ): Issue

    /** Labels configured for a repository. */
    @GET("repos/{owner}/{repo}/labels")
    suspend fun getRepositoryLabels(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<Issue.Label>

    /** Open milestones configured for a repository. */
    @GET("repos/{owner}/{repo}/milestones")
    suspend fun getRepositoryMilestones(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 100,
    ): List<Issue.Milestone>

    @PATCH("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun editIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body body: CommentRequest,
    ): IssueComment

    /** Delete a comment. */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun deleteIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
    ): Response<Unit>


    @kotlinx.serialization.Serializable
    data class IssueCreateRequest(
        val title: String,
        val body: String? = null,
        val labels: List<String> = emptyList(),
        val assignees: List<String> = emptyList(),
        val milestone: Int? = null,
    )

    @kotlinx.serialization.Serializable
    data class IssueEvent(
        val id: Long = 0,
        val event: String = "",
        @kotlinx.serialization.SerialName("commit_id") val commitId: String? = null,
        @kotlinx.serialization.SerialName("commit_url") val commitUrl: String? = null,
        val actor: User? = null,
        val label: Issue.Label? = null,
        val assignee: User? = null,
        val assigner: User? = null,
        val milestone: Issue.Milestone? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class IssueComment(
        val id: Long = 0,
        val body: String = "",
        val user: User? = null,
        @kotlinx.serialization.SerialName("author_association") val authorAssociation: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val reactions: com.pockethub.data.model.Reactions? = null,
    )

    @kotlinx.serialization.Serializable
    data class CommentRequest(val body: String)

    @kotlinx.serialization.Serializable
    data class IssueUpdateRequest(
        val title: String? = null,
        val body: String? = null,
        val state: String? = null,
        val labels: List<String>? = null,
        val assignees: List<String>? = null,
        val milestone: Int? = null,
    )
}
