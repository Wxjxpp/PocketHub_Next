package com.pockethub.data.remote


/**
 * GitHub REST API v3 interface.
 *
 * All endpoints require an authenticated token (set via [AuthInterceptor]).
 * See https://docs.github.com/en/rest for the full reference.
 */
interface GitHubApi :
    UserEndpoints,
    FollowEndpoints,
    RepoEndpoints,
    ContentEndpoints,
    IssueEndpoints,
    ReactionEndpoints,
    PullRequestEndpoints,
    CommitEndpoints,
    BranchEndpoints,
    ReleaseEndpoints,
    ActionEndpoints,
    NotificationEndpoints,
    EventEndpoints,
    SearchEndpoints,
    OAuthEndpoints,
    GraphQLEndpoints {

    @kotlinx.serialization.Serializable
    data class GitHubErrorBody(
        val message: String? = null,
        val documentation_url: String? = null,
    )

}
