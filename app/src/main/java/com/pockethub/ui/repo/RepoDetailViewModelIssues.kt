package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
internal fun RepoDetailViewModel.loadIssues(owner: String, repo: String, state: String? = null, force: Boolean = false): Job? {
    val effectiveState = state ?: _issueStateFilter.value.apiValue
    if (!force && loadedIssueState == effectiveState && (_issues.value.isNotEmpty() || _pulls.value.isNotEmpty())) return null
    loadedIssueState = effectiveState
    issuePage = 1
    issuesCanLoadMore = true
    return fetchIssuesPage(owner, repo, effectiveState, append = false, forceFresh = force)
}

internal fun RepoDetailViewModel.loadPulls(owner: String, repo: String, state: String? = null, force: Boolean = false): Job? {
    // Shares the issues fetch (PRs come from the same endpoint); just ensure loaded.
    return loadIssues(owner, repo, state, force)
}

/** Fetch the next page of issues/PRs for the current filter. */
internal fun RepoDetailViewModel.loadMoreIssues(owner: String, repo: String) {
    if (!issuesCanLoadMore || _isLoadingMoreIssues.value) return
    val state = _issueStateFilter.value.apiValue
    issuePage++
    fetchIssuesPage(owner, repo, state, append = true)
}

internal fun RepoDetailViewModel.fetchIssuesPage(owner: String, repo: String, state: String, append: Boolean, forceFresh: Boolean = false): Job {
    return viewModelScope.launch {
        if (append) _isLoadingMoreIssues.update { true } else _isLoadingIssues.update { true }
        try {
            // forceFresh goes straight to the network (bypassing the 5-min TTL)
            // so pull-to-refresh always re-fetches instead of serving the same
            // cached blob — the "spinner spins but nothing changes" bug.
            val all = cache.getIssues(owner, repo, state = state, page = issuePage, forceFresh = forceFresh)
            val issuesOnly = all.filter { it.pullRequest == null }
            val pullsOnly = all.filter { it.pullRequest != null }
            if (append) {
                val existingIssueIds = _issues.value.map { it.id }.toSet()
                val existingPrIds = _pulls.value.map { it.id }.toSet()
                _issues.update { it + issuesOnly.filter { n -> n.id !in existingIssueIds } }
                _pulls.update { it + pullsOnly.filter { n -> n.id !in existingPrIds } }
            } else {
                _issues.update { issuesOnly }
                _pulls.update { pullsOnly }
            }
            issuesCanLoadMore = all.size >= 30
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "fetchIssuesPage", e)
            if (!append) {
                _issues.update { emptyList() }
                _pulls.update { emptyList() }
            }
            _error.update { e.userMessage("Failed to load issues") }
        } finally {
            if (append) _isLoadingMoreIssues.update { false } else _isLoadingIssues.update { false }
        }
    }
}
