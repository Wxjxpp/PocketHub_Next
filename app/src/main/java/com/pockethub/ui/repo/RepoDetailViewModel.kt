package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import com.pockethub.util.userMessage
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

enum class RepoTab { OVERVIEW, CODE, ISSUES, PRS, RELEASES, COMMITS, WORKFLOWS }

/**
 * Three-state watch subscription on a repository — `NOT_WATCHING`, `WATCHING` and `MUTED`.
 * `UNKNOWN` until [RepoDetailViewModel.checkWatch] resolves the subscription.
 */
enum class WatchState {
    UNKNOWN,
    NOT_WATCHING,
    WATCHING,
    MUTED,
}

/** Issue / PR list state filter. Maps to the GitHub `state` query param. */
enum class IssueStateFilter(val apiValue: String) {
    OPEN("open"), CLOSED("closed"), ALL("all"),
}

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    internal val api: GitHubApi,
    internal val cache: CachedRepository,
    internal val history: com.pockethub.data.remote.HistoryRepository,
    internal val settings: SettingsRepository,
    internal val accountRepository: AccountRepository,
    internal val issueReporter: com.pockethub.data.reporting.IssueReporter,
    internal val okHttp: OkHttpClient,
) : ViewModel() {

    internal val _repo = MutableStateFlow<Repository?>(null)
    val repo: StateFlow<Repository?> = _repo

    internal val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues

    internal val _pulls = MutableStateFlow<List<Issue>>(emptyList())
    val pulls: StateFlow<List<Issue>> = _pulls

    /** Current state filter shared by the Issues and PRs tabs. */
    internal val _issueStateFilter = MutableStateFlow(IssueStateFilter.OPEN)
    val issueStateFilter: StateFlow<IssueStateFilter> = _issueStateFilter

    /** True while a further page is being fetched. */
    internal val _isLoadingMoreIssues = MutableStateFlow(false)
    val isLoadingMoreIssues: StateFlow<Boolean> = _isLoadingMoreIssues

    // ── Ren: first-load flags so tabs can show a progress indicator
    // before the first page arrives. isLoadingMoreIssues only covers append
    // loads, which left the screen visibly empty for a few seconds on the
    // first switch to Issues / PRs / Releases.
    internal val _isLoadingIssues = MutableStateFlow(false)
    val isLoadingIssues: StateFlow<Boolean> = _isLoadingIssues.asStateFlow()

    internal val _isLoadingReleases = MutableStateFlow(false)
    val isLoadingReleases: StateFlow<Boolean> = _isLoadingReleases.asStateFlow()

    // Pagination state for the issues/PRs list.
    internal var issuePage = 1
    internal var issuesCanLoadMore = true
    internal var loadedIssueState: String? = null

    // PRs paginate independently (dedicated /pulls endpoint) — sharing the
    // issues page counter meant PRs drowned out by issues never appeared.
    internal var prPage = 1
    internal var pullsCanLoadMore = true
    internal var loadedPullState: String? = null
    internal val _isLoadingPulls = MutableStateFlow(false)
    val isLoadingPulls: StateFlow<Boolean> = _isLoadingPulls.asStateFlow()
    internal val _isLoadingMorePulls = MutableStateFlow(false)
    val isLoadingMorePulls: StateFlow<Boolean> = _isLoadingMorePulls.asStateFlow()

    /** Select a state explicitly and reload the current issue/PR source. */
    fun setIssueStateFilter(owner: String, repo: String, filter: IssueStateFilter) {
        if (_issueStateFilter.value == filter) return
        _issueStateFilter.value = filter
        // The filter is shared by the Issues and PRs tabs — refresh whichever
        // list is on screen (each has its own fetch path now).
        if (currentTab.value == RepoTab.PRS) loadPulls(owner, repo, force = true)
        else loadIssues(owner, repo, force = true)
    }

    internal val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    internal val _releases = MutableStateFlow<List<GitHubApi.Release>>(emptyList())
    val releases: StateFlow<List<GitHubApi.Release>> = _releases

    // ── Release delete state ───────────────────────────────
    internal val _isDeletingRelease = MutableStateFlow(false)
    val isDeletingRelease: StateFlow<Boolean> = _isDeletingRelease

    internal val _releaseDeleteMessage = MutableStateFlow<String?>(null)
    val releaseDeleteMessage: StateFlow<String?> = _releaseDeleteMessage

    /** Whether the signed-in user can delete releases on the current repo. */
    val canManageReleases: StateFlow<Boolean> =
        combine(_repo, accountRepository.activeAccount) { r, account ->
            if (r == null || account == null) {
                false
            } else {
                r.owner.login == account.login || r.permissions?.admin == true
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    internal val _workflowRuns = MutableStateFlow<List<GitHubApi.WorkflowRun>>(emptyList())
    val workflowRuns: StateFlow<List<GitHubApi.WorkflowRun>> = _workflowRuns

    internal val _workflows = MutableStateFlow<List<GitHubApi.Workflow>>(emptyList())
    val workflows: StateFlow<List<GitHubApi.Workflow>> = _workflows

    internal val _isLoadingWorkflows = MutableStateFlow(false)
    val isLoadingWorkflows: StateFlow<Boolean> = _isLoadingWorkflows.asStateFlow()

    // Ren: drives the spinner on the Workflows *tab* — the existing
    // _isLoadingWorkflows above is owned by loadWorkflows() (the dispatch
    // dialog's definitions list), not loadWorkflowRuns() (the tab's run list),
    // so the tab previously saw a permanently-false flag and never spun.
    internal val _isLoadingWorkflowRuns = MutableStateFlow(false)
    val isLoadingWorkflowRuns: StateFlow<Boolean> = _isLoadingWorkflowRuns.asStateFlow()

    // Branch that the workflow list / dispatch dialog are currently pinned to.
    // Mirrors the Code tab's branch so switching branches in Code also updates
    // what workflows/runs the user sees. Reset to null when the repo changes.
    internal val _workflowBranch = MutableStateFlow<String?>(null)
    val workflowBranch: StateFlow<String?> = _workflowBranch.asStateFlow()

    internal val _isDispatching = MutableStateFlow(false)
    val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    internal val _dispatchMessage = MutableStateFlow<String?>(null)
    val dispatchMessage: StateFlow<String?> = _dispatchMessage.asStateFlow()

    /** Branches for the repo; used by the dispatch dialog's branch picker. */
    internal val _branches = MutableStateFlow<List<GitHubApi.Branch>>(emptyList())
    val branches: StateFlow<List<GitHubApi.Branch>> = _branches.asStateFlow()

    internal val _isLoadingBranches = MutableStateFlow(false)
    val isLoadingBranches: StateFlow<Boolean> = _isLoadingBranches.asStateFlow()

    internal val _readme = MutableStateFlow<String?>(null)
    val readme: StateFlow<String?> = _readme

    // ── Translation state ─────────────────────────────────────
    internal val _translatedReadme = MutableStateFlow<String?>(null)
    val translatedReadme: StateFlow<String?> = _translatedReadme

    internal val _showTranslated = MutableStateFlow(false)
    val showTranslated: StateFlow<Boolean> = _showTranslated

    internal val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    /** One-shot translate failure message, surfaced as a Snackbar. */
    internal val _translateMessage = MutableStateFlow<String?>(null)
    val translateMessage: StateFlow<String?> = _translateMessage.asStateFlow()

    val translateTarget: StateFlow<String?> = settings.translateTarget
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    internal val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Pull-to-refresh state for tabs that do not use the repo loader. */
    internal val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    internal val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    /** Whether this repo is pinned locally (independent from GitHub star). */
    val isPinned: StateFlow<Boolean> = settings.pinnedRepos
        .map { list -> list.contains(_currentSlug) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    internal var _currentSlug: String = ""

    /** Whether the current user is watching (subscribed to) this repo's notifications. */
    internal val _watchState = MutableStateFlow<WatchState>(WatchState.UNKNOWN)
    val watchState: StateFlow<WatchState> = _watchState.asStateFlow()

    internal val _isForking = MutableStateFlow(false)
    val isForking: StateFlow<Boolean> = _isForking.asStateFlow()

    internal val _forkMessage = MutableStateFlow<String?>(null)
    val forkMessage: StateFlow<String?> = _forkMessage.asStateFlow()

    // ── Delete state ──────────────────────────────────────────
    internal val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    internal val _deleteMessage = MutableStateFlow<String?>(null)
    val deleteMessage: StateFlow<String?> = _deleteMessage.asStateFlow()

    /** One-shot signal: the repository was deleted successfully (navigate back). */
    internal val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    /**
     * Whether the signed-in user is allowed to delete the current repository.
     * GitHub grants deletion rights to the repository owner and to collaborators
     * with admin permission.
     */
    val canDelete: StateFlow<Boolean> =
        combine(_repo, accountRepository.activeAccount) { r, account ->
            if (r == null || account == null) {
                false
            } else {
                r.owner.login == account.login || r.permissions?.admin == true
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var currentTab = MutableStateFlow(RepoTab.OVERVIEW)
    internal var loadedOwner: String? = null
    internal var loadedRepo: String? = null

    fun loadRepo(owner: String, repo: String, force: Boolean = false): Job? {
        if (!force && loadedOwner == owner && loadedRepo == repo && _repo.value != null) return null
        loadedOwner = owner; loadedRepo = repo
        // Reset workflow branch when switching repos — the previous repo's branch
        // has no meaning here. Branches list also gets cleared so the dialog
        // doesn't show stale selections from a different repo.
        _workflowBranch.update { null }
        _branches.update { emptyList() }
        _currentSlug = "$owner/$repo"
        return viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                if (force) {
                    cache.invalidateRepo(owner, repo)
                    _repo.value = null
                    _readme.value = null
                }
                _repo.update { cache.getRepository(owner, repo) }
                history.recordVisit(owner, repo)
                loadReadme(owner, repo)
                checkStar(owner, repo)
                checkWatch(owner, repo)
            } catch (e: Exception) {
                issueReporter.reportError("RepoDetail", "loadRepo", e)
                _error.update { e.userMessage("Failed to load repo") }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    /** Force-refresh the visible repository tab and its related data. */
    fun refreshCurrentTab(owner: String, repo: String) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                when (currentTab.value) {
                    RepoTab.OVERVIEW,
                    RepoTab.CODE,
                    RepoTab.COMMITS -> {
                        loadRepo(owner, repo, force = true)?.join()
                        // Commits live in a separate ViewModel — the old code only
                        // reloaded the repo metadata here, so pulling to refresh on
                        // the Commits tab spun the spinner while the commit list
                        // never changed (fake refresh). Bump a counter that
                        // CommitsTab observes and re-fetches on.
                        _commitsRefreshTick.value++
                    }
                    // loadIssues(force=true) already bypasses the cache via fetchIssuesPage.
                    RepoTab.ISSUES -> loadIssues(owner, repo, force = true)?.join()
                    RepoTab.PRS -> loadPulls(owner, repo, force = true)?.join()
                    RepoTab.RELEASES -> {
                        cache.invalidateReleases(owner, repo)
                        loadReleases(owner, repo)?.join()
                    }
                    // Run list shows all runs — branch-independent (see RepoDetailScreen).
                    RepoTab.WORKFLOWS -> loadWorkflowRuns(owner, repo)?.join()
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Incremented when pull-to-refresh should also reload the Commits tab. */
    internal val _commitsRefreshTick = MutableStateFlow(0)
    val commitsRefreshTick: StateFlow<Int> = _commitsRefreshTick.asStateFlow()

    /** Branch the current README was loaded for — used to skip redundant reloads. */
    internal var readmeRef: String? = null

    internal fun loadReadme(owner: String, repo: String, ref: String? = null): Job = viewModelScope.launch {
        try {
            val resp = cache.getReadme(owner, repo, ref = ref)
            val markdown = if (resp.encoding == "base64" && resp.content.isNotBlank()) {
                decodeBase64(resp.content)
            } else {
                resp.content
            }
            _readme.update { markdown }
            readmeRef = ref
        } catch (_: Exception) {
            _readme.update { null }
        }
    }

    /**
     * Called by the screen when the Code tab's branch selection changes:
     * reloads the README for [ref] and resets the translation view (the cached
     * translated text belongs to the previous branch's README).
     * Also mirrors the chosen branch to the workflows tab so the workflow run
     * list and dispatch dialog follow the current Code tab branch automatically.
     */
    fun onBranchChanged(owner: String, repo: String, ref: String?) {
        if (readmeRef == ref && _readme.value != null) return
        _translatedReadme.update { null }
        _showTranslated.update { false }
        loadReadme(owner, repo, ref)
        // Mirror to workflows tab so the workflow run list & dispatch dialog follow
        // the current Code tab branch automatically.
        val branch = ref ?: _repo.value?.defaultBranch
        if (branch != null && branch != _workflowBranch.value) {
            _workflowBranch.update { branch }
            loadWorkflows(owner, repo, branch)
            loadBranches(owner, repo)
        }
    }

    internal fun checkStar(owner: String, repo: String) = viewModelScope.launch {
        try {
            val resp = api.checkStarred(owner, repo)
            _isStarred.update { resp.isSuccessful }
        } catch (_: Exception) {
            _isStarred.update { false }
        }
    }

    /** Resolves the current user's subscription status on this repo. */
    internal fun checkWatch(owner: String, repo: String) = viewModelScope.launch {
        try {
            val resp = api.getSubscription(owner, repo)
            if (resp.isSuccessful) {
                val sub = resp.body()
                _watchState.update {
                    when {
                        sub?.ignored == true -> WatchState.MUTED
                        sub?.subscribed == true -> WatchState.WATCHING
                        else -> WatchState.NOT_WATCHING
                    }
                }
            } else {
                _watchState.update { WatchState.NOT_WATCHING }
            }
        } catch (_: Exception) {
            _watchState.update { WatchState.UNKNOWN }
        }
    }

    /**
     * Cycle watch state: NOT_WATCHING → WATCHING → NOT_WATCHING.
     * Muting is intentionally a separate action ([muteRepo]) since it is more
     * destructive (stops all notifications, including @mentions); watches only
     * stay on the main toggle path so the common case stays one tap.
     */
    fun toggleWatch(owner: String, repo: String) {
        if (_isWatchToggling) return
        val current = _watchState.value
        if (current == WatchState.UNKNOWN) return
        _isWatchToggling = true
        viewModelScope.launch {
            try {
                if (current == WatchState.WATCHING) {
                    api.unwatch(owner, repo)
                    _watchState.update { WatchState.NOT_WATCHING }
                } else {
                    api.watch(owner, repo, GitHubApi.WatchSubscriptionRequest(subscribed = true, ignored = false))
                    _watchState.update { WatchState.WATCHING }
                }
            } catch (e: Exception) {
                issueReporter.reportError("RepoDetail", "toggleWatch", e)
                _error.update { e.userMessage("Failed to toggle subscription") }
            } finally {
                _isWatchToggling = false
            }
        }
    }

    /** Mute the repo entirely from notifications. Toggle back via [toggleWatch]. */
    fun muteRepo(owner: String, repo: String) {
        if (_isWatchToggling) return
        _isWatchToggling = true
        viewModelScope.launch {
            try {
                api.watch(owner, repo, GitHubApi.WatchSubscriptionRequest(subscribed = false, ignored = true))
                _watchState.update { WatchState.MUTED }
            } catch (e: Exception) {
                issueReporter.reportError("RepoDetail", "muteRepo", e)
                _error.update { e.userMessage("Failed to mute") }
            } finally {
                _isWatchToggling = false
            }
        }
    }

    internal var _isWatchToggling: Boolean = false

    fun toggleStar(owner: String, repo: String) {
        viewModelScope.launch {
            try {
                if (_isStarred.value) {
                    api.unstar(owner, repo)
                    _isStarred.update { false }
                } else {
                    api.star(owner, repo)
                    _isStarred.update { true }
                }
                cache.invalidateRepo(owner, repo)
            } catch (e: Exception) {
                issueReporter.reportError("RepoDetail", "toggleStar", e)
                _error.update { e.userMessage("Operation failed") }
            }
        }
    }

    /** Pin / unpin the current repo locally — purely client-side, no GitHub API. */
    fun togglePin() {
        val slug = _currentSlug
        if (slug.isBlank()) return
        viewModelScope.launch {
            val current = settings.pinnedRepos.first()
            if (current.contains(slug)) settings.unpinRepo(slug) else settings.pinRepo(slug)
        }
    }

    // ── Visibility toggle (private ⇄ public) ──────────────────
    internal val _isTogglingVisibility = MutableStateFlow(false)
    val isTogglingVisibility: StateFlow<Boolean> = _isTogglingVisibility.asStateFlow()
    internal val _visibilityMessage = MutableStateFlow<String?>(null)
    val visibilityMessage: StateFlow<String?> = _visibilityMessage.asStateFlow()
}
