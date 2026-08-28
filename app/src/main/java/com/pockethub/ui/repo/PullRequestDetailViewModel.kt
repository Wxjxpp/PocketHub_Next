package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aggregated CI status for the PR head SHA. Rendered as a one-line banner. */
sealed interface CheckSummary {
    /** No check runs returned by GitHub — usually means CI is not set up on this repo. */
    data object NONE : CheckSummary
    data class Passed(val passed: Int, val total: Int) : CheckSummary
    data class Failed(val failed: Int, val total: Int) : CheckSummary
    data class Pending(val pending: Int, val total: Int) : CheckSummary
}

@HiltViewModel
class PullRequestDetailViewModel @Inject constructor(
    internal val issueReporter: com.pockethub.data.reporting.IssueReporter,
    internal val api: GitHubApi,
    internal val accounts: AccountRepository,
) : ViewModel() {

    internal val _pr = MutableStateFlow<GitHubApi.PullRequest?>(null)
    val pr: StateFlow<GitHubApi.PullRequest?> = _pr

    internal val _files = MutableStateFlow<List<GitHubApi.PullRequestFile>>(emptyList())
    val files: StateFlow<List<GitHubApi.PullRequestFile>> = _files
    internal val _filesError = MutableStateFlow<String?>(null)
    val filesError: StateFlow<String?> = _filesError.asStateFlow()

    internal val _reviewComments = MutableStateFlow<List<GitHubApi.ReviewComment>>(emptyList())
    val reviewComments: StateFlow<List<GitHubApi.ReviewComment>> = _reviewComments
    internal val _reviewCommentsError = MutableStateFlow<String?>(null)
    val reviewCommentsError: StateFlow<String?> = _reviewCommentsError.asStateFlow()

    internal val _isSendingLineComment = MutableStateFlow(false)
    val isSendingLineComment: StateFlow<Boolean> = _isSendingLineComment.asStateFlow()

    internal val _reviews = MutableStateFlow<List<GitHubApi.PullRequestReview>>(emptyList())
    val reviews: StateFlow<List<GitHubApi.PullRequestReview>> = _reviews
    internal val _reviewsError = MutableStateFlow<String?>(null)
    val reviewsError: StateFlow<String?> = _reviewsError.asStateFlow()

    /**
     * PR review thread state, keyed by the comment id of the first / root comment of each thread.
     *
     * Filled lazily by [fetchThreadState]; holds the GraphQL thread node id and the
     * resolved flag (only available via GraphQL — REST `ReviewComment` has neither).
     * This is an in-memory cache — never persisted. Refreshed on PR refresh or on
     * resolve mutation failure.
     */
    internal val _threadState = MutableStateFlow<Map<Long, ThreadInfo>>(emptyMap())
    val threadState: StateFlow<Map<Long, ThreadInfo>> = _threadState

    /** Hit-map of comment ids currently mutating (edit / delete / resolve). */
    internal val _busyReviewComments = MutableStateFlow<Set<Long>>(emptySet())
    val busyReviewComments: StateFlow<Set<Long>> = _busyReviewComments

    /** Last error encountered while mutating inline review comments (edit / reply / resolve / delete). */
    internal val _inlineCommentError = MutableStateFlow<String?>(null)
    val inlineCommentError: StateFlow<String?> = _inlineCommentError

    internal val _comments = MutableStateFlow<List<GitHubApi.IssueComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.IssueComment>> = _comments
    internal val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

    /** Timeline events for the PR (labeled / assigned / closed / merged / review_requested …). */
    internal val _events = MutableStateFlow<List<GitHubApi.IssueEvent>>(emptyList())
    val events: StateFlow<List<GitHubApi.IssueEvent>> = _events
    internal val _eventsError = MutableStateFlow<String?>(null)
    val eventsError: StateFlow<String?> = _eventsError.asStateFlow()

    internal val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    internal val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    internal val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    internal val _mergeResult = MutableStateFlow<String?>(null)
    val mergeResult: StateFlow<String?> = _mergeResult

    internal val _isSendingReview = MutableStateFlow(false)
    val isSendingReview: StateFlow<Boolean> = _isSendingReview.asStateFlow()

    internal val _reviewResult = MutableStateFlow<String?>(null)
    val reviewResult: StateFlow<String?> = _reviewResult

    internal val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    internal val _commentError = MutableStateFlow<String?>(null)
    val commentError: StateFlow<String?> = _commentError

    /** True while a close / reopen request is in flight. */
    internal val _isTogglingState = MutableStateFlow(false)
    val isTogglingState: StateFlow<Boolean> = _isTogglingState.asStateFlow()

    /** Last close/reopen PR status feedback (success / failure). */
    internal val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    fun clearActionMessage() { _actionMessage.update { null } }

    internal val _reviewerWorking = MutableStateFlow<Boolean>(false)
    val reviewerWorking: StateFlow<Boolean> = _reviewerWorking.asStateFlow()

    internal val _reviewerError = MutableStateFlow<String?>(null)
    val reviewerError: StateFlow<String?> = _reviewerError.asStateFlow()

    fun clearReviewerError() { _reviewerError.update { null } }

    internal val _viewerReactions = MutableStateFlow<Map<Long, Map<String, Long>>>(emptyMap())
    val viewerReactions: StateFlow<Map<Long, Map<String, Long>>> = _viewerReactions

    internal val _busyComments = MutableStateFlow<Set<Long>>(emptySet())
    val busyComments: StateFlow<Set<Long>> = _busyComments

    internal val _currentLogin = MutableStateFlow<String?>(null)
    val currentLogin: StateFlow<String?> = _currentLogin.asStateFlow()

    internal var loadedOwner: String? = null
    internal var loadedRepo: String? = null
    internal var loadedNumber: Int? = null

    init {
        viewModelScope.launch { _currentLogin.value = accounts.getActiveLogin().takeIf { it.isNotBlank() } }
    }

    fun loadPullRequest(owner: String, repo: String, number: Int) {
        if (loadedNumber == number && _pr.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedNumber = number
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _pr.update { api.getPullRequest(owner, repo, number) }
            } catch (e: Exception) {
                issueReporter.reportError("PullRequestDetail", "loadPullRequest", e)
                _error.update { e.localizedMessage ?: "Failed to load PR" }
            } finally {
                _isLoading.update { false }
            }
            // Load files, reviews, and comments in parallel (independent)
            viewModelScope.launch {
                _filesError.update { null }
                try {
                    _files.update { api.getPullRequestFiles(owner, repo, number) }
                } catch (e: Exception) {
                    issueReporter.reportError("PullRequestDetail", "loadFiles", e)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _filesError.update { e.localizedMessage ?: "Failed to load files" }
                }
            }
            viewModelScope.launch {
                _reviewsError.update { null }
                try {
                    _reviews.update { api.getPullRequestReviews(owner, repo, number) }
                } catch (e: Exception) {
                    issueReporter.reportError("PullRequestDetail", "loadReviews", e)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _reviewsError.update { e.localizedMessage ?: "Failed to load reviews" }
                }
            }
            viewModelScope.launch {
                _reviewCommentsError.update { null }
                try {
                    _reviewComments.update { api.listPullRequestReviewComments(owner, repo, number) }
                } catch (e: Exception) {
                    issueReporter.reportError("PullRequestDetail", "loadReviewComments", e)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _reviewCommentsError.update { e.localizedMessage ?: "Failed to load review comments" }
                }
            }
            viewModelScope.launch {
                runCatching { fetchThreadState(owner, repo, number) }
            }
            viewModelScope.launch {
                _commentsError.update { null }
                try {
                    val resp = api.getIssueComments(owner, repo, number)
                    _comments.update { resp.body().orEmpty() }
                    hydrateReactions(owner, repo)
                } catch (e: Exception) {
                    issueReporter.reportError("PullRequestDetail", "loadComments", e)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _commentsError.update { e.localizedMessage ?: "Failed to load comments" }
                }
            }
            viewModelScope.launch {
                _eventsError.update { null }
                try {
                    val resp = api.getIssueEvents(owner, repo, number)
                    _events.update { resp.body().orEmpty() }
                } catch (e: Exception) {
                    issueReporter.reportError("PullRequestDetail", "loadEvents", e)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _eventsError.update { e.localizedMessage ?: "Failed to load events" }
                }
            }
            // Load CI checks for the PR head SHA so users see whether the PR is
            // mergeable from a checks perspective (parallel with files/reviews).
            viewModelScope.launch {
                val sha = _pr.value?.head?.sha
                if (!sha.isNullOrBlank()) {
                    runCatching { api.listCheckRuns(owner, repo, sha) }.onSuccess { resp ->
                        _checkRuns.update { resp.runs }
                        _checkSummary.update { summarize(resp.runs) }
                    }
                }
            }
        }
    }

    /**
     * Retry a single section (files / reviews / reviewComments / comments)
     * after a per-section load failure. Each retry clears the matching error
     * before the attempt, so the UI banner flips back to its loading state.
     */
    fun retryFiles() = reloadSection("files")
    fun retryReviews() = reloadSection("reviews")
    fun retryReviewComments() = reloadSection("reviewComments")
    fun retryComments() = reloadSection("comments")

    internal fun reloadSection(section: String) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        viewModelScope.launch {
            when (section) {
                "files" -> {
                    _filesError.update { null }
                    try { _files.update { api.getPullRequestFiles(owner, repo, number) } }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _filesError.update { e.localizedMessage ?: "Failed to load files" }
                    }
                }
                "reviews" -> {
                    _reviewsError.update { null }
                    try { _reviews.update { api.getPullRequestReviews(owner, repo, number) } }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _reviewsError.update { e.localizedMessage ?: "Failed to load reviews" }
                    }
                }
                "reviewComments" -> {
                    _reviewCommentsError.update { null }
                    try { _reviewComments.update { api.listPullRequestReviewComments(owner, repo, number) } }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _reviewCommentsError.update { e.localizedMessage ?: "Failed to load review comments" }
                    }
                }
                "comments" -> {
                    _commentsError.update { null }
                    try {
                        val resp = api.getIssueComments(owner, repo, number)
                        _comments.update { resp.body().orEmpty() }
                        hydrateReactions(owner, repo)
                    } catch (e: Exception) {
                        issueReporter.reportError("PullRequestDetail", "reloadSection", e)
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _commentsError.update { e.localizedMessage ?: "Failed to load comments" }
                    }
                }
            }
        }
    }

    internal val _checkRuns = MutableStateFlow<List<GitHubApi.CheckRun>>(emptyList())
    val checkRuns: StateFlow<List<GitHubApi.CheckRun>> = _checkRuns

    /** Aggregated PR checks status — a single line shown above the files section. */
    internal val _checkSummary = MutableStateFlow<CheckSummary>(CheckSummary.NONE)
    val checkSummary: StateFlow<CheckSummary> = _checkSummary

    /** Manually re-fetch check runs. Used when the user pulls to refresh CI status.
     *  Exposes a loading flag so the refresh button can show inline feedback. */
    internal val _isLoadingCheckRuns = MutableStateFlow(false)
    val isLoadingCheckRuns: StateFlow<Boolean> = _isLoadingCheckRuns.asStateFlow()

    fun refreshCheckRuns(owner: String, repo: String) {
        val sha = _pr.value?.head?.sha ?: return
        if (_isLoadingCheckRuns.value) return
        viewModelScope.launch {
            _isLoadingCheckRuns.value = true
            try {
                runCatching { api.listCheckRuns(owner, repo, sha) }.onSuccess { resp ->
                    _checkRuns.update { resp.runs }
                    _checkSummary.update { summarize(resp.runs) }
                }
            } finally {
                _isLoadingCheckRuns.value = false
            }
        }
    }

    /** Computes the headline status from a list of check runs. */
    internal fun summarize(runs: List<GitHubApi.CheckRun>): CheckSummary {
        if (runs.isEmpty()) return CheckSummary.NONE
        val total = runs.size
        val passed = runs.count { it.status == "completed" && it.conclusion == "success" }
        val failed = runs.count { it.status == "completed" && it.conclusion in FAILED_CONCLUSIONS }
        val neutral = runs.count { it.status == "completed" && it.conclusion in NEUTRAL_CONCLUSIONS }
        val pending = total - passed - failed - neutral

        return when {
            failed > 0 -> CheckSummary.Failed(failed = failed, total = total)
            pending > 0 -> CheckSummary.Pending(pending = pending, total = total)
            passed + neutral == total -> CheckSummary.Passed(passed = passed, total = total)
            else -> CheckSummary.Pending(pending = total - passed - neutral, total = total)
        }
    }

    /** Conclusions that should appear red on the PR check summary. */
    internal val FAILED_CONCLUSIONS = setOf("failure", "cancelled", "timed_out", "action_required")
    /** Conclusions that are non-passing but also non-failing (skipped/neutral/stale). */
    internal val NEUTRAL_CONCLUSIONS = setOf("neutral", "skipped", "stale")

    fun clearMergeResult() { _mergeResult.update { null } }
    fun clearReviewResult() { _reviewResult.update { null } }
    fun clearCommentError() { _commentError.update { null } }

    fun retry(owner: String, repo: String, number: Int) {
        loadedNumber = null
        loadPullRequest(owner, repo, number)
    }



    companion object {
        /** Meta state for one review thread, used by R3 resolve/unresolve. */
        internal data class ThreadInfo(val threadId: String, val isResolved: Boolean)

        /**
         * GraphQL query listing PR review threads so we can map the REST root comment
         * id (databaseId) → GraphQL thread node id, plus the resolved flag which REST
         * doesn't surface.
         */

        /** Mark a review comment thread resolved. */

        /** Mark a previously-resolved review thread unresolved. */
    }
}
