package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun PullRequestDetailViewModel.merge(owner: String, repo: String, number: Int, method: String = "merge") {
    if (_isMerging.value) return
    viewModelScope.launch {
        _isMerging.update { true }
        _mergeResult.update { null }
        try {
            val response = api.mergePullRequest(owner, repo, number, GitHubApi.MergeRequest(merge_method = method))
            val result = response.body()
            if (response.isSuccessful && result?.merged == true) {
                _mergeResult.update { "Merged" }
                // Refresh PR to show merged state
                _pr.update { null }
                loadedNumber = null
                loadPullRequest(owner, repo, number)
            } else {
                _mergeResult.update { result?.message ?: "Merge failed (${response.code()})" }
            }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "merge", e)
            _mergeResult.update { e.localizedMessage ?: "Merge failed" }
        } finally {
            _isMerging.update { false }
        }
    }
}

/**
 * Close / reopen the PR. Mirror of GitHub web's "Close pull request" / "Reopen"
 * controls: PATCH the pulls endpoint with the toggled `state`. Refreshes the PR
 * on success so header / merge banner update. Merged PRs cannot be closed or
 * reopened; the UI should hide this affordance when `pr.merged == true`.
 */
internal fun PullRequestDetailViewModel.togglePrState(owner: String, repo: String, number: Int) {
    val current = _pr.value
    // Guard against re-entry and incompatible states (already-merged / loading).
    if (_isTogglingState.value) return
    if (current?.merged == true) return
    val newState = if (current?.state == "open") "closed" else "open"
    viewModelScope.launch {
        _isTogglingState.update { true }
        _actionMessage.update { null }
        try {
            val updated = api.updatePullRequest(owner, repo, number, GitHubApi.PullUpdateRequest(state = newState))
            _pr.update { updated }
            _actionMessage.update {
                if (newState == "closed") "Pull request closed" else "Pull request reopened"
            }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "togglePrState", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            _actionMessage.update { e.localizedMessage ?: "Failed to update PR state" }
        } finally {
            _isTogglingState.update { false }
        }
    }
}

internal fun PullRequestDetailViewModel.submitReview(owner: String, repo: String, number: Int, event: String, body: String) {
    if (_isSendingReview.value) return
    viewModelScope.launch {
        _isSendingReview.update { true }
        _reviewResult.update { null }
        try {
            val review = api.createPullRequestReview(
                owner, repo, number,
                GitHubApi.ReviewRequest(body = body.ifBlank { null }, event = event),
            )
            _reviews.update { it + review }
            _reviewResult.update {
                when (event) {
                    "APPROVE" -> "Approved"
                    "REQUEST_CHANGES" -> "Changes requested"
                    else -> "Reviewed"
                }
            }
            // Refresh the PR so mergeable / merge_state / requested reviewers
            // reflect the newly submitted review — same pattern as merge().
            _pr.update { null }
            loadedNumber = null
            loadPullRequest(owner, repo, number)
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "submitReview", e)
            _reviewResult.update { e.localizedMessage ?: "Review 提交失败" }
        } finally {
            _isSendingReview.update { false }
        }
    }
}

internal fun PullRequestDetailViewModel.requestReviewers(owner: String, repo: String, number: Int, reviewers: List<String>) {
    if (_reviewerWorking.value || reviewers.isEmpty()) return
    viewModelScope.launch {
        _reviewerWorking.update { true }
        _reviewerError.update { null }
        try {
            val updated = api.requestReviewers(owner, repo, number, GitHubApi.RequestedReviewersBody(reviewers = reviewers))
            _pr.update { updated }
            _actionMessage.update { "Requested ${reviewers.size} reviewer(s)" }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "requestReviewers", e)
            _reviewerError.update { e.localizedMessage ?: "Failed to request reviewer" }
        } finally {
            _reviewerWorking.update { false }
        }
    }
}

internal fun PullRequestDetailViewModel.removeReviewer(owner: String, repo: String, number: Int, reviewer: String) {
    if (_reviewerWorking.value) return
    viewModelScope.launch {
        _reviewerWorking.update { true }
        _reviewerError.update { null }
        try {
            api.removeReviewers(owner, repo, number, GitHubApi.RequestedReviewersBody(reviewers = listOf(reviewer)))
            _pr.update {
                it?.copy(requestedReviewers = it.requestedReviewers.filterNot { r -> r.login == reviewer })
            }
            _actionMessage.update { "Removed reviewer @${reviewer}" }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "removeReviewer", e)
            _reviewerError.update { e.localizedMessage ?: "Failed to remove reviewer" }
        } finally {
            _reviewerWorking.update { false }
        }
    }
}
