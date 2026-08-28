package com.pockethub.data.remote

// Pull request lifecycle, reviews, review comments endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

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
    ): GitHubApi.PullRequest

    /** Add requested reviewers to a pull request. */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers")
    suspend fun requestReviewers(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GitHubApi.RequestedReviewersBody,
    ): GitHubApi.PullRequest

    /** Remove requested reviewers from a pull request. */
    @DELETE("repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers")
    suspend fun removeReviewers(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GitHubApi.RequestedReviewersBody,
    ): Response<Unit>

    @PATCH("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun updatePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GitHubApi.PullUpdateRequest,
    ): GitHubApi.PullRequest

    /** List files changed in a pull request. */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/files")
    suspend fun getPullRequestFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<GitHubApi.PullRequestFile>

    /** List reviews on a pull request. */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun getPullRequestReviews(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<GitHubApi.PullRequestReview>

    /** Merge a pull request. */
    @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
    suspend fun mergePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GitHubApi.MergeRequest = GitHubApi.MergeRequest(),
    ): Response<GitHubApi.MergeResult>

    /** Submit a pull request review. */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun createPullRequestReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GitHubApi.ReviewRequest,
    ): GitHubApi.PullRequestReview

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
    ): List<GitHubApi.ReviewComment>

    /**
     * Post a line-level review comment on a PR.
     *
     * Use [GitHubApi.ReviewCommentRequest.line] (single-line) or [GitHubApi.ReviewCommentRequest.startLine] + `line`
     * (multi-line range). The full positional parameters are required by GitHub to anchor a
     * comment on the file diff rather than the issue timeline.
     */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/comments")
    suspend fun createPullRequestReviewComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GitHubApi.ReviewCommentRequest,
    ): GitHubApi.ReviewComment

    /** Edit a review comment body. */
    @PATCH("repos/{owner}/{repo}/pulls/comments/{comment_id}")
    suspend fun editPullRequestReviewComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body body: GitHubApi.EditReviewCommentRequest,
    ): GitHubApi.ReviewComment

    /** Delete a review comment. */
    @DELETE("repos/{owner}/{repo}/pulls/comments/{comment_id}")
    suspend fun deletePullRequestReviewComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
    ): retrofit2.Response<Unit>

    // ──────────────────────────────────────────────
    //  GraphQL endpoint for thread resolve / unresolve
    //  (https://docs.github.com/en/graphql)
    // ──────────────────────────────────────────────
}
