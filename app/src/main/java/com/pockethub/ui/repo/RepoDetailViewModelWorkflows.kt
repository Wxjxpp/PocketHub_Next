package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
internal fun RepoDetailViewModel.loadWorkflowRuns(owner: String, repo: String, branch: String? = null): Job {
    return viewModelScope.launch {
        _isLoadingWorkflowRuns.update { true }
        try {
            val resp = api.getWorkflowRuns(owner, repo, branch = branch)
            _workflowRuns.update { resp.runs }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "loadWorkflowRuns", e)
            _workflowRuns.update { emptyList() }
            _error.update { e.localizedMessage ?: "Failed to load workflows" }
        } finally {
            _isLoadingWorkflowRuns.update { false }
        }
    }
}

/** Load workflow definitions so the user can pick one to dispatch manually. */
internal fun RepoDetailViewModel.loadWorkflows(owner: String, repo: String, branch: String? = null) {
    viewModelScope.launch {
        if (_isLoadingWorkflows.value) return@launch
        _isLoadingWorkflows.update { true }
        try {
            val resp = api.getWorkflows(owner, repo, ref = branch)
            _workflows.update { resp.workflows.filter { it.state == "active" && it.deletedAt == null } }
            // Sync the tracked branch once we've resolved it from the API response.
            branch?.let { _workflowBranch.update { it } }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "loadWorkflows", e)
            _workflows.update { emptyList() }
            _dispatchMessage.update { e.localizedMessage ?: "Failed to load workflow" }
        } finally {
            _isLoadingWorkflows.update { false }
        }
    }
}

/** Reset the workflow branch; called when owner/repo changes so the dialog
 *  doesn't carry over stale branch selections from a different repo. */
internal fun RepoDetailViewModel.resetWorkflowBranch() {
    _workflowBranch.update { null }
    _branches.update { emptyList() }
}

/** Trigger a `workflow_dispatch` event for the given workflow on the given ref. */
internal fun RepoDetailViewModel.dispatchWorkflow(owner: String, repo: String, workflowId: Long, ref: String) {
    viewModelScope.launch {
        if (_isDispatching.value) return@launch
        _isDispatching.update { true }
        _dispatchMessage.update { null }
        try {
            val resp = api.dispatchWorkflow(owner, repo, workflowId, GitHubApi.WorkflowDispatchRequest(ref = ref))
            if (resp.isSuccessful) {
                _dispatchMessage.update { "Triggered: a new run will appear shortly" }
            } else {
                val err = resp.errorBody()?.string()
                val reason = when (resp.code()) {
                    403 -> "Forbidden: needs write access to this repo"
                    404 -> "Workflow or repo not found, or no Actions access"
                    422 -> "Trigger failed: the workflow may not declare `on: workflow_dispatch`, or the ref doesn't exist"
                    else -> "Trigger failed (${resp.code()}): ${err?.take(200)}"
                }
                _dispatchMessage.update { reason }
            }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "dispatchWorkflow", e)
            _dispatchMessage.update { e.localizedMessage ?: "Failed to trigger workflow" }
        } finally {
            _isDispatching.update { false }
        }
    }
}

/** Branch to use for the workflow dispatch dialog (mirrors Code tab by default). */
internal fun RepoDetailViewModel.setWorkflowBranch(owner: String, repo: String, branch: String) {
    if (_workflowBranch.value == branch) return
    _workflowBranch.update { branch }
    loadWorkflows(owner, repo, branch)
    loadBranches(owner, repo)
}

/** Load branches for the given repo; called when the dispatch dialog opens or branch changes. */
internal fun RepoDetailViewModel.loadBranches(owner: String, repo: String) {
    viewModelScope.launch {
        if (_isLoadingBranches.value) return@launch
        _isLoadingBranches.update { true }
        _branches.update { emptyList() }
        try {
            // Fetch up to 100 branches (max per_page). If a repo has more,
            // the dialog shows the first page which covers the common cases;
            // pagination is not exposed in the dialog UI.
            val resp = api.getBranches(owner, repo, perPage = 100)
            _branches.update { resp }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "loadBranches", e)
            // Silent — dialog will keep falling back to defaultBranch.
        } finally {
            _isLoadingBranches.update { false }
        }
    }
}

internal fun RepoDetailViewModel.clearDispatchMessage() {
    _dispatchMessage.update { null }
}
