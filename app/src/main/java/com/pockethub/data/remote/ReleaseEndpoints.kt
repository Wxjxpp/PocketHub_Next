package com.pockethub.data.remote

// Release endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ReleaseEndpoints {

    /** Releases for a repo. */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): List<Release>

    /**
     * Delete a release. Requires the authenticated user to be the repo owner or
     * have admin permission (the `delete_repo` scope is NOT required for releases).
     * Returns 204 on success; 403 / 404 for permission / not-found cases.
     */
    @DELETE("repos/{owner}/{repo}/releases/{release_id}")
    suspend fun deleteRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("release_id") releaseId: Long,
    ): Response<Unit>

    @kotlinx.serialization.Serializable
    data class Release(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("tag_name") val tagName: String = "",
        @kotlinx.serialization.SerialName("name") val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("published_at") val publishedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val author: User? = null,
        @kotlinx.serialization.SerialName("assets") val assets: List<ReleaseAsset> = emptyList(),
    ) {
        @kotlinx.serialization.Serializable
        data class ReleaseAsset(
            val id: Long = 0,
            val name: String = "",
            @kotlinx.serialization.SerialName("download_count") val downloadCount: Int = 0,
            val size: Long = 0,
            @kotlinx.serialization.SerialName("browser_download_url") val browserDownloadUrl: String = "",
        )
    }

    //  Notifications
}
