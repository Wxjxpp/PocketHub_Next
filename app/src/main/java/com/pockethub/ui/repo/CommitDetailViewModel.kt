package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommitDetailViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    private val api: GitHubApi,
) : ViewModel() {

    private val _commit = MutableStateFlow<GitHubApi.CommitDetail?>(null)
    val commit: StateFlow<GitHubApi.CommitDetail?> = _commit.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _comments = MutableStateFlow<List<GitHubApi.CommitComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.CommitComment>> = _comments.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    private val _commentError = MutableStateFlow<String?>(null)
    val commentError: StateFlow<String?> = _commentError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    /**
     * Repository record for the loaded commit — fetched once alongside the
     * commit so the screen can decide whether to show the "revert" button
     * (needs the default branch name + the authenticated user's push
     * permission). `null` until [load] completes the parallel repo fetch.
     */
    private val _repoInfo = MutableStateFlow<com.pockethub.data.model.Repository?>(null)
    val repoInfo: StateFlow<com.pockethub.data.model.Repository?> = _repoInfo.asStateFlow()

    /** True while a revert-to-parent request is in flight. */
    private val _isReverting = MutableStateFlow(false)
    val isReverting: StateFlow<Boolean> = _isReverting.asStateFlow()

    private var loadedSha: String? = null

    fun load(owner: String, repo: String, sha: String) {
        if (loadedSha == sha && _commit.value != null) return
        loadedSha = sha
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _commit.update { api.getCommit(owner, repo, sha) }
            } catch (e: Exception) {
                issueReporter.reportError("CommitDetail", "load", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.update { e.localizedMessage ?: "Failed to load commit" }
            } finally {
                _isLoading.update { false }
            }
        }
        // Fetch repository (for default branch + push permission) in parallel —
        // the revert button on the screen gates on repoInfo.permissions.push.
        viewModelScope.launch {
            try {
                _repoInfo.update { api.getRepository(owner, repo) }
            } catch (_: Exception) {
                // Non-fatal: revert button simply stays disabled.
            }
        }
        // Load commit comments in parallel — independent of the commit itself.
        loadComments(owner, repo, sha)
    }

    fun retry(owner: String, repo: String, sha: String) {
        loadedSha = null
        _repoInfo.update { null }
        load(owner, repo, sha)
    }

    fun loadComments(owner: String, repo: String, sha: String) {
        viewModelScope.launch {
            _commentsError.update { null }
            try {
                _comments.update { api.getCommitComments(owner, repo, sha) }
            } catch (e: Exception) {
                issueReporter.reportError("CommitDetail", "loadComments", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                _commentsError.update { e.localizedMessage ?: "Failed to load commit comments" }
            }
        }
    }

    /**
     * Post a top-level commit comment (no positional info). Mirrors GitHub web's
     * "Comment on this commit" footer — used to leave a general remark about the
     * whole commit, not tied to a specific file line.
     */
    fun postComment(owner: String, repo: String, sha: String, body: String) {
        if (body.isBlank() || _isSendingComment.value) return
        viewModelScope.launch {
            _isSendingComment.update { true }
            _commentError.update { null }
            try {
                val created = api.createCommitComment(owner, repo, sha, GitHubApi.CommitCommentCreate(body = body))
                _comments.update { it + created }
                _actionMessage.update { "Comment added" }
            } catch (e: Exception) {
                issueReporter.reportError("CommitDetail", "postComment", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                _commentError.update { e.localizedMessage ?: "Failed to post comment" }
            } finally {
                _isSendingComment.update { false }
            }
        }
    }

    fun postLineComment(owner: String, repo: String, sha: String, path: String, line: Int, body: String) {
        if (body.isBlank() || _isSendingComment.value) return
        viewModelScope.launch {
            _isSendingComment.update { true }
            _commentError.update { null }
            try {
                val created = api.createCommitComment(
                    owner, repo, sha,
                    GitHubApi.CommitCommentCreate(body = body, path = path, line = line),
                )
                _comments.update { it + created }
                _actionMessage.update { "Line comment added" }
            } catch (e: Exception) {
                issueReporter.reportError("CommitDetail", "postLineComment", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                _commentError.update { e.localizedMessage ?: "Failed to post line comment" }
            } finally {
                _isSendingComment.update { false }
            }
        }
    }

    fun clearActionMessage() { _actionMessage.update { null } }
    fun clearCommentError() { _commentError.update { null } }
    fun retryComments(owner: String, repo: String, sha: String) = loadComments(owner, repo, sha)

    /**
     * Roll the repository's default branch back one step: move the branch ref to
     * this commit's first parent, discarding this commit (and anything after it
     * on the default branch). This mirrors `git reset --hard HEAD~1` on the
     * default branch rather than `git revert` — it actually drops the commit,
     * so it only makes sense on the user's own repo where they have push rights.
     *
     * Requires the commit to have at least one parent (initial commits cannot be
     * reverted this way) and the authenticated user to have push permission,
     * which the screen gates on before enabling the button.
     *
     * @return `null` on success, an error message string on failure (the screen
     * shows it via snackbar).
     */
    suspend fun revert(owner: String, repo: String, sha: String): String? {
        if (_isReverting.value) return null
        val parentSha = _commit.value?.parents?.firstOrNull()?.sha
            ?: return "This commit has no parent to revert to."
        val branch = _repoInfo.value?.defaultBranch
            ?: return "Repository info not loaded yet."
        _isReverting.update { true }
        try {
            val resp = api.updateRef(owner, repo, "heads/$branch", GitHubApi.UpdateRefRequest(parentSha, force = true))
            if (resp.isSuccessful) return null
            return "Revert failed: HTTP ${resp.code()}"
        } catch (e: Exception) {
            issueReporter.reportError("CommitDetail", "revert", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            return e.localizedMessage ?: "Revert failed"
        } finally {
            _isReverting.update { false }
        }
    }
}
