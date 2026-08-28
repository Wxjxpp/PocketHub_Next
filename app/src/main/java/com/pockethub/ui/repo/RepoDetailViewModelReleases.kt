package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun RepoDetailViewModel.loadReleases(owner: String, repo: String): Job {
    return viewModelScope.launch {
        _isLoadingReleases.update { true }
        try {
            _releases.update { cache.getReleases(owner, repo) }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "loadReleases", e)
            _releases.update { emptyList() }
            _error.update { e.userMessage("Failed to load releases") }
        } finally {
            _isLoadingReleases.update { false }
        }
    }
}
/**
 * Delete a release. GitHub's release-delete endpoint requires the same
 * repo owner/admin permission as deleting the repo, but does NOT need the
 * `delete_repo` token scope. Returns 204 on success.
 */
internal fun RepoDetailViewModel.deleteRelease(owner: String, repo: String, releaseId: Long) {
    viewModelScope.launch {
        if (_isDeletingRelease.value) return@launch
        _isDeletingRelease.update { true }
        _releaseDeleteMessage.update { null }
        try {
            val resp = api.deleteRelease(owner, repo, releaseId)
            if (resp.isSuccessful) {
                cache.invalidateReleases(owner, repo)
                // Remove the deleted release from the live list so the UI
                // reflects the change immediately without a refetch.
                _releases.update { list -> list.filterNot { it.id == releaseId } }
                _releaseDeleteMessage.update { "Deleted" }
            } else {
                val err = resp.errorBody()?.string()
                val reason = when (resp.code()) {
                    403 -> "Forbidden: only the repo owner or admin can delete releases"
                    404 -> "Release not found or no access"
                    else -> "Delete failed (${resp.code()}): ${err?.take(200)}"
                }
                _releaseDeleteMessage.update { reason }
            }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "deleteRelease", e)
            _releaseDeleteMessage.update { e.userMessage("Delete failed") }
        } finally {
            _isDeletingRelease.update { false }
        }
    }
}
