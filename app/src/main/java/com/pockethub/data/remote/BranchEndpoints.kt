package com.pockethub.data.remote

// Branch listing endpoints.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BranchEndpoints {

    /** List branches for a repo. */
    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<Branch>

    @kotlinx.serialization.Serializable
    data class Branch(
        val name: String = "",
        val commit: BranchCommit? = null,
        val `protected`: Boolean = false,
    ) {
        @kotlinx.serialization.Serializable
        data class BranchCommit(
            val sha: String = "",
            @kotlinx.serialization.SerialName("url") val url: String? = null,
        )
    }
}
