package com.pockethub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Issue or Pull Request body. Issues and PRs share many fields in the API;
 * PR-specific fields (mergeable, reviewers, etc.) can be fetched separately.
 */
@Serializable
data class Issue(
    val id: Long,
    @SerialName("node_id") val nodeId: String? = null,
    val number: Int,
    @SerialName("html_url") val htmlUrl: String? = null,
    val state: String = "open", // "open" | "closed"
    @SerialName("state_reason") val stateReason: String? = null, // "completed" | "not_planned" | "reopened"
    val title: String,
    val body: String? = null,
    val user: User? = null,
    val labels: List<Label> = emptyList(),
    val assignee: User? = null,
    @SerialName("assignees") val assignees: List<User> = emptyList(),
    @SerialName("milestone") val milestone: Milestone? = null,

    // Counts
    @SerialName("comments") val comments: Int = 0,
    @SerialName("reactions") val reactions: Reactions? = null,

    /** True when the conversation is locked (no further comments allowed). */
    val locked: Boolean = false,

    // Timestamps
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,

    // PR-specific (present when ?pulls endpoint is used)
    @SerialName("pull_request") val pullRequest: PullRequestRef? = null,

    /** True when the PR was merged. Only the /pulls DETAIL endpoint sets this
     *  flag — the LIST endpoints don't include it, so prefer [mergedAt]. */
    val merged: Boolean = false,

    /** Merge timestamp. The ONLY reliable merged signal on list endpoints:
     *  /pulls returns merged_at but has no `merged` boolean at all. */
    @SerialName("merged_at") val mergedAt: String? = null,

    // Only present on /search/issues responses — lets a work-list surface the
    // owning repo without re-fetching. Null on the per-repo issues endpoint.
    // NOTE: GitHub stopped returning `repository` in search results; owner/repo
    // must fall back to [repositoryUrl] or [htmlUrl] (see ui.search parsing).
    @SerialName("repository") val repository: com.pockethub.data.model.Repository? = null,

    /** e.g. "https://api.github.com/repos/owner/repo" — present in search results. */
    @SerialName("repository_url") val repositoryUrl: String? = null,
) {
    /** Merged = explicit flag OR merged_at present (list endpoints only give the timestamp). */
    val isMerged: Boolean get() = merged || mergedAt != null

    @Serializable
    data class Label(
        val id: Long? = null,
        val name: String,
        val description: String? = null,
        val color: String? = null,
    )

    @Serializable
    data class Milestone(
        val id: Long? = null,
        val number: Int,
        val title: String,
        val state: String = "open",
    )

    /** Lightweight reference when a list endpoint includes PR metadata. */
    @Serializable
    data class PullRequestRef(
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("diff_url") val diffUrl: String? = null,
    )
}
