package com.pockethub.data.remote

// Pull request lifecycle, reviews, review comments endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PullRequestEndpoints {

    /** Get a single pull request (includes merge info, diff stats, reviewers). */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
    ): PullRequest

    /** Add requested reviewers to a pull request. */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers")
    suspend fun requestReviewers(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: RequestedReviewersBody,
    ): PullRequest

    /** Remove requested reviewers from a pull request. */
    @DELETE("repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers")
    suspend fun removeReviewers(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: RequestedReviewersBody,
    ): Response<Unit>

    @PATCH("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun updatePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: PullUpdateRequest,
    ): PullRequest

    /** List files changed in a pull request. */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/files")
    suspend fun getPullRequestFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<PullRequestFile>

    /** List reviews on a pull request. */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun getPullRequestReviews(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<PullRequestReview>

    /** Merge a pull request. */
    @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
    suspend fun mergePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: MergeRequest = MergeRequest(),
    ): Response<MergeResult>

    /** Submit a pull request review. */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun createPullRequestReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: ReviewRequest,
    ): PullRequestReview

    /**
     * List review comments (line-level comments) on a PR — these are different from issue
     * comments (general PR discussion): they are anchored to a specific file + line range.
     */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/comments")
    suspend fun listPullRequestReviewComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): List<ReviewComment>

    /**
     * Post a line-level review comment on a PR.
     *
     * Use [ReviewCommentRequest.line] (single-line) or [ReviewCommentRequest.startLine] + `line`
     * (multi-line range). The full positional parameters are required by GitHub to anchor a
     * comment on the file diff rather than the issue timeline.
     */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/comments")
    suspend fun createPullRequestReviewComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: ReviewCommentRequest,
    ): ReviewComment

    /** Edit a review comment body. */
    @PATCH("repos/{owner}/{repo}/pulls/comments/{comment_id}")
    suspend fun editPullRequestReviewComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body body: EditReviewCommentRequest,
    ): ReviewComment

    /** Delete a review comment. */
    @DELETE("repos/{owner}/{repo}/pulls/comments/{comment_id}")
    suspend fun deletePullRequestReviewComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
    ): retrofit2.Response<Unit>

    //  GraphQL endpoint for thread resolve / unresolve
    //  (https://docs.github.com/en/graphql)

    @kotlinx.serialization.Serializable
    data class PullUpdateRequest(
        val state: String, // "open" | "closed"
    )

    @kotlinx.serialization.Serializable
    data class ReviewComment(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val path: String = "",
        val line: Int? = null,
        @kotlinx.serialization.SerialName("start_line") val startLine: Int? = null,
        @kotlinx.serialization.SerialName("original_line") val originalLine: Int? = null,
        @kotlinx.serialization.SerialName("original_start_line") val originalStartLine: Int? = null,
        @kotlinx.serialization.SerialName("in_reply_to_id") val inReplyToId: Long? = null,
        val body: String = "",
        val user: User? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("commit_id") val commitId: String? = null,
    )

    /**
     * Body for [createPullRequestReviewComment].
     *
     * Fields follow the GitHub v3 doc:
     *   https://docs.github.com/en/rest/pulls/comments#create-a-review-comment
     *
     * Two modes:
     *   1. New anchored line comment — `path`, `line`, `commit_id`, `side` required.
     *   2. Reply within an existing thread — `in_reply_to_id` of the root comment;
     *      `path`/`line`/`commit_id` are ignored by the server in this mode.
     *
     * `subject_type` is "line" by default; `side` defaults to "RIGHT" (new file).
     */
    @kotlinx.serialization.Serializable
    data class ReviewCommentRequest(
        val body: String,
        @kotlinx.serialization.SerialName("in_reply_to_id") val inReplyToId: Long? = null,
        @kotlinx.serialization.SerialName("commit_id") val commitId: String? = null,
        val path: String? = null,
        val line: Int? = null,
        @kotlinx.serialization.SerialName("start_line") val startLine: Int? = null,
        val side: String = "RIGHT",
        @kotlinx.serialization.SerialName("start_side") val startSide: String? = null,
        @kotlinx.serialization.SerialName("subject_type") val subjectType: String = "line",
    )

    @kotlinx.serialization.Serializable
    data class PullRequest(
        val id: Long = 0,
        val number: Int = 0,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val state: String = "open", // "open" | "closed"
        @kotlinx.serialization.SerialName("state_reason") val stateReason: String? = null,
        val title: String = "",
        val body: String? = null,
        val user: User? = null,
        val labels: List<Issue.Label> = emptyList(),
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("closed_at") val closedAt: String? = null,
        @kotlinx.serialization.SerialName("merged_at") val mergedAt: String? = null,
        @kotlinx.serialization.SerialName("merged") val merged: Boolean = false,
        @kotlinx.serialization.SerialName("mergeable") val mergeable: Boolean? = null,
        @kotlinx.serialization.SerialName("merge_state") val mergeState: String? = null,
        @kotlinx.serialization.SerialName("merge_commit_sha") val mergeCommitSha: String? = null,
        @kotlinx.serialization.SerialName("draft") val draft: Boolean = false,
        val head: RefInfo? = null,
        val base: RefInfo? = null,
        @kotlinx.serialization.SerialName("changed_files") val changedFiles: Int = 0,
        @kotlinx.serialization.SerialName("additions") val additions: Int = 0,
        @kotlinx.serialization.SerialName("deletions") val deletions: Int = 0,
        @kotlinx.serialization.SerialName("commits") val commits: Int = 0,
        @kotlinx.serialization.SerialName("review_comments") val reviewComments: Int = 0,
        val comments: Int = 0,
        @kotlinx.serialization.SerialName("requested_reviewers") val requestedReviewers: List<User> = emptyList(),
        @kotlinx.serialization.SerialName("requested_teams") val requestedTeams: List<Team> = emptyList(),
        @kotlinx.serialization.SerialName("merged_by") val mergedBy: User? = null,
    ) {
        @kotlinx.serialization.Serializable
        data class RefInfo(
            val label: String = "",
            val ref: String = "",
            val sha: String = "",
            val repo: Repository? = null,
        )

        @kotlinx.serialization.Serializable
        data class Team(
            val id: Long = 0,
            val name: String = "",
            val slug: String = "",
        )
    }

    @kotlinx.serialization.Serializable
    data class PullRequestFile(
        val sha: String = "",
        val filename: String = "",
        val status: String = "", // "added" | "modified" | "removed" | "renamed"
        val additions: Int = 0,
        val deletions: Int = 0,
        val changes: Int = 0,
        val patch: String? = null,
        @kotlinx.serialization.SerialName("previous_filename") val previousFilename: String? = null,
        @kotlinx.serialization.SerialName("raw_url") val rawUrl: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class PullRequestReview(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val user: User? = null,
        val state: String = "", // "APPROVED" | "CHANGES_REQUESTED" | "COMMENTED" | "DISMISSED" | "PENDING"
        val body: String? = null,
        @kotlinx.serialization.SerialName("submitted_at") val submittedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("pull_request_url") val pullRequestUrl: String? = null,
        @kotlinx.serialization.SerialName("author_association") val authorAssociation: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class MergeRequest(
        val commit_title: String? = null,
        val commit_message: String? = null,
        val merge_method: String = "merge", // "merge" | "squash" | "rebase"
    )

    @kotlinx.serialization.Serializable
    data class MergeResult(
        val sha: String? = null,
        val merged: Boolean = false,
        val message: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ReviewRequest(
        val body: String? = null,
        val event: String, // "APPROVE" | "REQUEST_CHANGES" | "COMMENT"
        @kotlinx.serialization.SerialName("comments") val comments: List<ReviewInlineComment> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class RequestedReviewersBody(
        val reviewers: List<String> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class ReviewInlineComment(
        val path: String? = null,
        val position: Int? = null,
        val body: String = "",
    )


    /** Body for editing a pull request review comment. */
    @kotlinx.serialization.Serializable
    data class EditReviewCommentRequest(val body: String)
}
