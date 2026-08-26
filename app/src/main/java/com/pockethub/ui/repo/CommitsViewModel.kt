package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommitsViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _commits = MutableStateFlow<List<GitHubApi.Commit>>(emptyList())
    val commits: StateFlow<List<GitHubApi.Commit>> = _commits

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentPage = 1
    private var canLoadMore = true
    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    /** Branch/SHA the current commit list was fetched for (null = default branch). */
    private var loadedRef: String? = null
    private var loadJob: Job? = null
    private var loadRequestId = 0

    fun loadCommits(owner: String, repo: String, ref: String? = null) {
        if (loadedOwner == owner && loadedRepo == repo && loadedRef == ref && _commits.value.isNotEmpty()) return
        loadedOwner = owner; loadedRepo = repo; loadedRef = ref
        currentPage = 1
        canLoadMore = true
        fetchCommits(owner, repo, append = false, ref = ref)
    }

    fun loadMore(owner: String, repo: String) {
        if (!canLoadMore || _isLoading.value) return
        currentPage++
        fetchCommits(owner, repo, append = true, ref = loadedRef)
    }

    fun refresh(owner: String, repo: String, ref: String? = null) {
        currentPage = 1
        canLoadMore = true
        fetchCommits(owner, repo, append = false, ref = ref)
    }

    private fun fetchCommits(owner: String, repo: String, append: Boolean, ref: String? = null) {
        if (!append) loadJob?.cancel()
        val requestId = ++loadRequestId
        loadJob = viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val result = api.getCommits(owner, repo, page = currentPage, perPage = 30, sha = ref)
                if (requestId != loadRequestId) return@launch
                _commits.update { if (append) it + result else result }
                canLoadMore = result.size >= 30
            } catch (e: Exception) {
                if (requestId != loadRequestId) return@launch
                _error.update { e.localizedMessage ?: "Failed to load commits" }
                if (!append) _commits.update { emptyList() }
            } finally {
                if (requestId == loadRequestId) _isLoading.update { false }
            }
        }
    }
}
