package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.GitHubApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
internal fun RepoDetailViewModel.fork(owner: String, repo: String, newName: String? = null) {
    viewModelScope.launch {
        if (_isForking.value) return@launch
        _isForking.update { true }
        try {
            val trimmed = newName?.trim().orEmpty()
            // Empty input means "keep the source name" — send no name field.
            val body = if (trimmed.isEmpty() || trimmed == repo) GitHubApi.ForkRequest()
            else GitHubApi.ForkRequest(name = trimmed)
            val resp = api.forkRepository(owner, repo, body)
            if (resp.isSuccessful) {
                _forkMessage.update {
                    if (trimmed.isEmpty() || trimmed == repo) "Forked to current account"
                    else "Forked to $trimmed"
                }
            } else {
                _forkMessage.update { "Fork failed: ${resp.code()}" }
            }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "fork", e)
            _forkMessage.update { e.localizedMessage ?: "Fork 失败" }
        } finally {
            _isForking.update { false }
        }
    }
}

internal fun RepoDetailViewModel.clearForkMessage() {
    _forkMessage.update { null }
}

/**
 * Delete the repository. Requires owner/admin rights and a token carrying the
 * `delete_repo` scope; the API returns 204 on success.
 */
internal fun RepoDetailViewModel.deleteRepository(owner: String, repo: String) {
    viewModelScope.launch {
        if (_isDeleting.value) return@launch
        _isDeleting.update { true }
        _deleteMessage.update { null }
        try {
            val resp = api.deleteRepository(owner, repo)
            if (resp.isSuccessful) {
                cache.invalidateRepo(owner, repo)
                // The list endpoint is cached independently from the repo detail.
                // Without this eviction, navigating back can show the deleted row
                // until the five-minute repository-list TTL expires.
                cache.invalidateMyRepositories()
                _deleteSuccess.update { true }
            } else {
                val err = resp.errorBody()?.string()
                val reason = when (resp.code()) {
                    403 -> "Forbidden: only the repo owner or admin can delete, and the token needs the delete_repo scope"
                    404 -> "Repo not found or no access"
                    else -> "Delete failed (${resp.code()}): ${err?.take(200)}"
                }
                _deleteMessage.update { reason }
            }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "deleteRepository", e)
            _deleteMessage.update { e.localizedMessage ?: "Delete failed" }
        } finally {
            _isDeleting.update { false }
        }
    }
}

internal fun RepoDetailViewModel.consumeDeleteSuccess() {
    _deleteSuccess.update { false }
}

internal fun RepoDetailViewModel.clearDeleteMessage() {
    _deleteMessage.update { null }
}

internal fun RepoDetailViewModel.clearReleaseDeleteMessage() {
    _releaseDeleteMessage.update { null }
}

/**
 * Toggle this repository between private and public. Only callable when the
 * current user has admin rights on the repo (see [canDelete]). Uses the
 * PATCH /repos/{owner}/{repo} endpoint with the `private` field — GitHub treats
 * this as the authoritative toggle for visibility. Refreshes the in-memory
 * repo state on success so the UI locks/unlocks immediately.
 */
internal fun RepoDetailViewModel.toggleVisibility(owner: String, repo: String) {
    val current = _repo.value ?: return
    if (_isTogglingVisibility.value) return
    viewModelScope.launch {
        _isTogglingVisibility.update { true }
        _visibilityMessage.update { null }
        try {
            val target = !current.private
            val targetVisibility = if (target) "private" else "public"
            val resp = api.updateRepository(
                owner,
                repo,
                // `visibility` is GitHub's authoritative field (the legacy
                // boolean `private` still works but is deprecated and some
                // accounts/endpoints reject it without the `visibility`
                // counterpart). Send only `visibility` to stay unambiguous.
                GitHubApi.RepoUpdateRequest(visibility = targetVisibility),
            )
            if (resp.isSuccessful) {
                resp.body()?.let { updated ->
                    _repo.update { updated }
                    cache.invalidateRepo(owner, repo)
                    // Visibility changes can move the repo in or out of a filtered list.
                    cache.invalidateMyRepositories()
                }
                _visibilityMessage.update {
                    if (target) "Repository set to private" else "Repository set to public"
                }
            } else {
                // Surface GitHub's actual error message rather than just the
                // status code. On 422 ("A previous visibility change is still
                // in progress") the user gets a meaningful "wait a moment"
                // nudge instead of an opaque "(422)".
                val ghMsg = runCatching {
                    resp.errorBody()?.charStream()?.use { reader ->
                        kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        }.decodeFromString(
                            GitHubApi.GitHubErrorBody.serializer(),
                            reader.readText(),
                        ).message
                    }
                }.getOrNull()
                _visibilityMessage.update {
                    when {
                        // GitHub returns this when a previous visibility
                        // change is still being processed server-side.
                        resp.code() == 422 && ghMsg != null ->
                            "$ghMsg Try again in a few seconds."
                        ghMsg != null -> "$ghMsg (${resp.code()})"
                        else -> "Failed to update visibility (${resp.code()})"
                    }
                }
            }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "toggleVisibility", e)
            _visibilityMessage.update { e.localizedMessage ?: "Failed to update visibility" }
        } finally {
            _isTogglingVisibility.update { false }
        }
    }
}

internal fun RepoDetailViewModel.clearVisibilityMessage() {
    _visibilityMessage.update { null }
}
