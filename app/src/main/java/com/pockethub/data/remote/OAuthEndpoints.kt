package com.pockethub.data.remote

// OAuth token exchange endpoint.
// Split out of GitHubApi.kt; inherited by GitHubApi so Retrofit and
// call sites keep resolving everything through GitHubApi.X.

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
    ): OAuthTokenResponse

    //  Search result wrappers

    @kotlinx.serialization.Serializable
    data class OAuthTokenResponse(
        val access_token: String = "",
        val token_type: String = "",
        val scope: String = "",
        @kotlinx.serialization.SerialName("error") val error: String? = null,
        @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
    )

    //  PR inline review comment edit / delete
    //  (https://docs.github.com/en/rest/pulls/comments)
}
