package com.pockethub.data.remote

// Follow / followers endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FollowEndpoints {

    /** Check whether the authenticated user follows [login]. 204 = yes, 404 = no. */
    @GET("user/following/{login}")
    suspend fun checkFollowing(@Path("login") login: String): Response<Unit>

    /** Follow a user. */
    @PUT("user/following/{login}")
    suspend fun followUser(@Path("login") login: String): Response<Unit>

    /** Unfollow a user. */
    @DELETE("user/following/{login}")
    suspend fun unfollowUser(@Path("login") login: String): Response<Unit>

    /** Followers of a user. */
    @GET("users/{login}/followers")
    suspend fun getFollowers(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<User>

    /** Users the given user follows. */
    @GET("users/{login}/following")
    suspend fun getFollowing(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<User>

    // ──────────────────────────────────────────────
    //  Repositories
    // ──────────────────────────────────────────────
}
