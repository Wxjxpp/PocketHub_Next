package com.pockethub.data.remote

// Repository contents (file tree) endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.Repository
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ContentEndpoints {

    /**
     * List contents of a directory or fetch a single file.
     *
     * The API returns either a [ContentEntry] (when `path` points to a file) or
     * a JSON array of [ContentEntry] (when it points to a directory). We declare the
     * return as [kotlinx.serialization.json.JsonElement] and decode in the caller via
     * [kotlinx.serialization.json.Json], so one method covers both cases.
     */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null,
    ): kotlinx.serialization.json.JsonElement

    /** Contents of the root of the repo's default branch (no path). */
    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null,
    ): kotlinx.serialization.json.JsonElement

    @kotlinx.serialization.Serializable
    data class ContentEntry(
        val name: String = "",
        val path: String = "",
        val sha: String = "",
        @kotlinx.serialization.SerialName("download_url") val downloadUrl: String? = null,
        val type: String = "file", // "file" | "dir" | "symlink" | "submodule"
        val size: Long = 0,
        val content: String = "",   // base64 (only present for single-file fetches)
        val encoding: String = "none",
    )

    //  Issues & Pull Requests
}
