package com.pockethub.data.remote

// Notification endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import com.pockethub.data.model.GitHubNotification
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationEndpoints {

    /** Unread notifications (all). */
    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("all") all: Boolean = false,
        @Query("participating") participating: Boolean = false,
    ): List<GitHubNotification>

    /** Mark a thread as read. */
    @PATCH("notifications/threads/{thread_id}")
    suspend fun markNotificationRead(
        @Path("thread_id") threadId: String,
    ): Response<Unit>

    /** Unsubscribe from a thread (no future notifications for this thread). */
    @DELETE("notifications/threads/{thread_id}/subscription")
    suspend fun unsubscribeThread(
        @Path("thread_id") threadId: String,
    ): Response<Unit>

    /** Mark all notifications as read. */
    @PUT("notifications")
    suspend fun markAllNotificationsRead(): Response<Unit>

    // ──────────────────────────────────────────────
    //  Activity feed (received_events — for the "Following" feed section)
    // ──────────────────────────────────────────────
}
