package com.pockethub.data.remote

// Repository contents (file tree) endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import com.pockethub.data.model.Repository
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ContentEndpoints {

    /**
     * List contents of a directory or fetch a single file.
     *
     * The API returns either a [GitHubApi.ContentEntry] (when `path` points to a file) or
     * a JSON array of [GitHubApi.ContentEntry] (when it points to a directory). We declare the
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

    /**
     * Full recursive file tree of a ref ([treeSha] accepts a branch name or SHA).
     * One request returns the whole tree; large repos come back with
     * [GitHubApi.GitTreeResponse.truncated] = true.
     */
    @GET("repos/{owner}/{repo}/git/trees/{treeSha}")
    suspend fun getGitTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("treeSha", encoded = true) treeSha: String,
        @Query("recursive") recursive: String = "1",
    ): GitHubApi.GitTreeResponse
}
