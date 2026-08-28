package com.pockethub.data.remote

// Commit history, comments and ref update endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.User
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
    ): List<Commit>

    /** Single commit detail (includes files diff). */
    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): CommitDetail

    /** Comments on a commit (section / line-level via positional fields). */
    @GET("repos/{owner}/{repo}/commits/{ref}/comments")
    suspend fun getCommitComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Query("per_page") perPage: Int = 100,
    ): List<CommitComment>

    /** Add a comment to a commit. Body-only (no path/line) posts a top-level
     *  commit comment on GitHub web's commit page. */
    @POST("repos/{owner}/{repo}/commits/{ref}/comments")
    suspend fun createCommitComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Body body: CommitCommentCreate,
    ): CommitComment

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
        @Body body: UpdateRefRequest,
    ): Response<Unit>

    @kotlinx.serialization.Serializable
    data class CommitCommentCreate(
        val body: String,
        // Optional positional fields — omitted for top-level comments.
        val path: String? = null,
        val position: Int? = null,
        val line: Int? = null,
    )

    @kotlinx.serialization.Serializable
    data class UpdateRefRequest(
        val sha: String,
        val force: Boolean = false,
    )

    @kotlinx.serialization.Serializable
    data class CommitComment(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val body: String = "",
        val path: String? = null,
        val position: Int? = null,
        val line: Int? = null,
        val user: User? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class Commit(
        val sha: String = "",
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val commit: CommitInfo? = null,
        val author: User? = null,
        val committer: User? = null,
        @kotlinx.serialization.SerialName("parents") val parents: List<Parent> = emptyList(),
    ) {
        @kotlinx.serialization.Serializable
        data class CommitInfo(
            val message: String = "",
            val author: CommitAuthor? = null,
            val committer: CommitAuthor? = null,
        ) {
            @kotlinx.serialization.Serializable
            data class CommitAuthor(
                val name: String = "",
                val email: String = "",
                val date: String? = null,
            )
        }
        @kotlinx.serialization.Serializable
        data class Parent(val sha: String = "")
    }

    @kotlinx.serialization.Serializable
    data class CommitDetail(
        val sha: String = "",
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val commit: Commit.CommitInfo? = null,
        val author: User? = null,
        val committer: User? = null,
        val stats: CommitStats? = null,
        val files: List<CommitFile> = emptyList(),
        @kotlinx.serialization.SerialName("parents") val parents: List<Commit.Parent> = emptyList(),
    ) {
        @kotlinx.serialization.Serializable
        data class CommitStats(
            val total: Int = 0,
            val additions: Int = 0,
            val deletions: Int = 0,
        )

        @kotlinx.serialization.Serializable
        data class CommitFile(
            val sha: String = "",
            val filename: String = "",
            val status: String = "", // "added" | "modified" | "removed" | "renamed"
            val additions: Int = 0,
            val deletions: Int = 0,
            val changes: Int = 0,
            val patch: String? = null,
            @kotlinx.serialization.SerialName("previous_filename") val previousFilename: String? = null,
            @kotlinx.serialization.SerialName("raw_url") val rawUrl: String? = null,
            @kotlinx.serialization.SerialName("blob_url") val blobUrl: String? = null,
        )
    }

}
