package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.components.CommentUiState
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun PullRequestDetailViewModel.postComment(body: String, onSuccess: () -> Unit = {}) {
    val owner = loadedOwner ?: return
    val repo = loadedRepo ?: return
    val number = loadedNumber ?: return
    if (body.isBlank()) return
    viewModelScope.launch {
        _isSendingComment.update { true }
        _commentError.update { null }
        try {
            val newComment = api.createIssueComment(owner, repo, number, GitHubApi.CommentRequest(body))
            _comments.update { it + newComment }
            _pr.update { pr -> pr?.copy(comments = pr.comments + 1) }
            onSuccess()
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "postComment", e)
            _commentError.update { e.localizedMessage ?: "Failed to post comment" }
        } finally {
            _isSendingComment.update { false }
        }
    }
}

internal fun PullRequestDetailViewModel.editComment(commentId: Long, newBody: String) {
    val owner = loadedOwner ?: return
    val repo = loadedRepo ?: return
    if (newBody.isBlank() || commentId in _busyComments.value) return
    viewModelScope.launch {
        _busyComments.update { it + commentId }
        _commentError.update { null }
        try {
            val updated = api.editIssueComment(owner, repo, commentId, GitHubApi.CommentRequest(newBody))
            _comments.update { list -> list.map { if (it.id == commentId) updated else it } }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "editComment", e)
            _commentError.update { e.localizedMessage ?: "Failed to update comment" }
        } finally {
            _busyComments.update { it - commentId }
        }
    }
}

internal fun PullRequestDetailViewModel.deleteComment(commentId: Long) {
    val owner = loadedOwner ?: return
    val repo = loadedRepo ?: return
    if (commentId in _busyComments.value) return
    viewModelScope.launch {
        _busyComments.update { it + commentId }
        _commentError.update { null }
        try {
            api.deleteIssueComment(owner, repo, commentId)
            _comments.update { list -> list.filter { it.id != commentId } }
            _pr.update { pr -> pr?.copy(comments = (pr.comments - 1).coerceAtLeast(0)) }
            _viewerReactions.update { it - commentId }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "deleteComment", e)
            _commentError.update { e.localizedMessage ?: "Failed to delete comment" }
        } finally {
            _busyComments.update { it - commentId }
        }
    }
}

internal fun PullRequestDetailViewModel.toggleReaction(commentId: Long, content: GitHubApi.ReactionContent) {
    val owner = loadedOwner ?: return
    val repo = loadedRepo ?: return
    if (commentId in _busyComments.value) return
    val mine = _viewerReactions.value[commentId]?.get(content.apiValue)
    viewModelScope.launch {
        _busyComments.update { it + commentId }
        try {
            if (mine != null) {
                api.deleteIssueCommentReaction(owner, repo, commentId, mine)
                _viewerReactions.update { all -> all[commentId]?.let { m -> all + (commentId to (m - content.apiValue)) } ?: all }
                _comments.update { list -> list.map { if (it.id == commentId) decrement(it, content) else it } }
            } else {
                val created = api.createIssueCommentReaction(owner, repo, commentId, GitHubApi.ReactionRequest(content.apiValue))
                _viewerReactions.update { all -> all[commentId]?.let { m -> all + (commentId to (m + (content.apiValue to created.id))) } ?: all + (commentId to mapOf(content.apiValue to created.id)) }
                _comments.update { list -> list.map { if (it.id == commentId) increment(it, content) else it } }
            }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "toggleReaction", e)
            _commentError.update { e.localizedMessage ?: "Failed to toggle reaction" }
        } finally {
            _busyComments.update { it - commentId }
        }
    }
}

internal fun PullRequestDetailViewModel.increment(c: GitHubApi.IssueComment, content: GitHubApi.ReactionContent): GitHubApi.IssueComment {
    val r = (c.reactions ?: com.pockethub.data.model.Reactions())
    val rr = when (content) {
        GitHubApi.ReactionContent.PLUS_ONE -> r.copy(plusOne = r.plusOne + 1)
        GitHubApi.ReactionContent.MINUS_ONE -> r.copy(minusOne = r.minusOne + 1)
        GitHubApi.ReactionContent.LAUGH -> r.copy(laugh = r.laugh + 1)
        GitHubApi.ReactionContent.CONFUSED -> r.copy(confused = r.confused + 1)
        GitHubApi.ReactionContent.HEART -> r.copy(heart = r.heart + 1)
        GitHubApi.ReactionContent.HOORAY -> r.copy(hooray = r.hooray + 1)
        GitHubApi.ReactionContent.ROCKET -> r.copy(rocket = r.rocket + 1)
        GitHubApi.ReactionContent.EYES -> r.copy(eyes = r.eyes + 1)
    }
    return c.copy(reactions = rr)
}

internal fun PullRequestDetailViewModel.decrement(c: GitHubApi.IssueComment, content: GitHubApi.ReactionContent): GitHubApi.IssueComment {
    val r = (c.reactions ?: com.pockethub.data.model.Reactions())
    val rr = when (content) {
        GitHubApi.ReactionContent.PLUS_ONE -> r.copy(plusOne = (r.plusOne - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.MINUS_ONE -> r.copy(minusOne = (r.minusOne - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.LAUGH -> r.copy(laugh = (r.laugh - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.CONFUSED -> r.copy(confused = (r.confused - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.HEART -> r.copy(heart = (r.heart - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.HOORAY -> r.copy(hooray = (r.hooray - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.ROCKET -> r.copy(rocket = (r.rocket - 1).coerceAtLeast(0))
        GitHubApi.ReactionContent.EYES -> r.copy(eyes = (r.eyes - 1).coerceAtLeast(0))
    }
    return c.copy(reactions = rr)
}

internal fun PullRequestDetailViewModel.commentStates(): List<CommentUiState> {
    val login = _currentLogin.value ?: return emptyList()
    val busy = _busyComments.value
    val vrs = _viewerReactions.value
    return _comments.value.map { c ->
        CommentUiState(
            comment = c,
            repoContext = "${loadedOwner.orEmpty()}/${loadedRepo.orEmpty()}",
            isMine = c.user?.login == login,
            viewerReactions = vrs[c.id].orEmpty(),
            isReacting = c.id in busy,
        )
    }
}
