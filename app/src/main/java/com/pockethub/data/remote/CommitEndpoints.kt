package com.pockethub.data.remote

// Commit history, comments and ref update endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommitEndpoints {

    /** List commits for a repo (paginated). */
    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sha") sha: String? = null, // branch or commit SHA
        @Query("path") path: String? = null, // filter by file/dir path
    ): List<GitHubApi.Commit>

    /** Single commit detail (includes files diff). */
    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): GitHubApi.CommitDetail

    /** Comments on a commit (section / line-level via positional fields). */
    @GET("repos/{owner}/{repo}/commits/{ref}/comments")
    suspend fun getCommitComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Query("per_page") perPage: Int = 100,
    ): List<GitHubApi.CommitComment>

    /** Add a comment to a commit. Body-only (no path/line) posts a top-level
     *  commit comment on GitHub web's commit page. */
    @POST("repos/{owner}/{repo}/commits/{ref}/comments")
    suspend fun createCommitComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Body body: GitHubApi.CommitCommentCreate,
    ): GitHubApi.CommitComment

    /**
     * Force-update a Git ref (branch/tag) to point at a given SHA.
     *
     * Used by the commit-detail "revert to parent" action: we move the default
     * branch ref back to the commit's parent, effectively discarding the commit
     * on the user's own repository. `force = true` is required because rewinding
     * a branch ref is a non-fast-forward update.
     *
     * GitHub REST: `PATCH /repos/{owner}/{repo}/git/refs/{ref}` where `{ref}` is
     * e.g. `heads/main` (no leading `refs/`).
     */
    @PATCH("repos/{owner}/{repo}/git/refs/{ref}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Body body: GitHubApi.UpdateRefRequest,
    ): Response<Unit>
}
