package com.pockethub.data.remote

// OAuth token exchange endpoint.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OAuthEndpoints {

    /** Exchange OAuth code for access token (POST to GitHub, not api.github.com). */
    @FormUrlEncoded
    @POST("https://github.com/login/oauth/access_token")
    suspend fun exchangeOAuthCode(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
    ): GitHubApi.OAuthTokenResponse

    // ──────────────────────────────────────────────
    //  Search result wrappers
    // ──────────────────────────────────────────────
}
