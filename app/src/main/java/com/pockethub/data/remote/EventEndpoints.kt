package com.pockethub.data.remote

// User event feed endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import com.pockethub.data.model.User
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface EventEndpoints {

    /** Public activity of a user (works for any public user; private events need the authed user). */
    @GET("users/{login}/received_events")
    suspend fun getReceivedEvents(
        @Path("login") login: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<com.pockethub.data.model.FeedEvent>

    /** Public activity of a single user. */
    @GET("users/{login}/events")
    suspend fun getUserEvents(
        @Path("login") login: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<com.pockethub.data.model.FeedEvent>
}
