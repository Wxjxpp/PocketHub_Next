package com.pockethub.ui.repo

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import retrofit2.HttpException
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Simple file-tree browser backed by GitHub's Contents API.
 *
 * Maintains a path stack so users can navigate into directories and back out.
 */
@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    private val api: GitHubApi,
    private val json: Json,
) : ViewModel() {

    /** Per-path last commit info (message + date), cached across navigations. */
    data class LastCommit(
        val message: String,
        val dateIso: String,
    )

    data class State(
        val owner: String = "",
        val repo: String = "",
        val ref: String? = null,
        val currentPath: String = "",
        val pathStack: List<String> = listOf(""),   // includes "" as root
        val entries: List<GitHubApi.ContentEntry> = emptyList(),
        val isLoading: Boolean = false,
        val viewingFile: GitHubApi.ContentEntry? = null,
        val fileContent: String? = null,            // decoded text (binary files stay null)
        val error: String? = null,
        /** Available branches (lazy-loaded once for the branch switcher). */
        val branches: List<GitHubApi.Branch> = emptyList(),
        val isLoadingBranches: Boolean = false,
        /** Map of entry.path → last commit for the currently visible directory. */
        val lastCommits: Map<String, LastCommit> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Persistent commit cache keyed by `${owner}/${repo}@${ref}::<path>` to avoid refetching. */
    private val commitCache = mutableMapOf<String, LastCommit>()

    fun init(owner: String, repo: String, ref: String? = null) {
        if (_state.value.owner == owner && _state.value.repo == repo) {
            // already initialized — just refresh current dir
            refreshCurrent()
            return
        }
        _state.update { State(owner = owner, repo = repo, ref = ref) }
        listDir("")
    }

    private fun refreshCurrent() {
        val s = _state.value
        listDir(s.currentPath)
    }

    fun listDir(path: String) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, viewingFile = null, fileContent = null) }
            try {
                val element = if (path.isBlank()) {
                    api.getRootContents(s.owner, s.repo, s.ref)
                } else {
                    api.getContents(s.owner, s.repo, path, s.ref)
                }
                val list: List<GitHubApi.ContentEntry> = when (element) {
                    is JsonArray -> json.decodeFromJsonElement<List<GitHubApi.ContentEntry>>(element)
                    is JsonObject -> listOf(json.decodeFromJsonElement<GitHubApi.ContentEntry>(element))
                    else -> emptyList()
                }
                // Sort: directories first, then files, alphabetical.
                val sorted = list.sortedWith(compareBy<GitHubApi.ContentEntry>
                    { if (it.type == "dir") 0 else 1 }
                    .thenBy { it.name.lowercase() })
                val newStack = if (path == s.currentPath) s.pathStack else buildPathStack(path)
                _state.update {
                    it.copy(
                        entries = sorted,
                        currentPath = path,
                        pathStack = newStack,
                        isLoading = false,
                    )
                }
                // Fire-and-forget: concurrently fetch the last commit for each visible entry.
                fetchLastCommits(sorted, s.owner, s.repo, s.ref)
            } catch (e: HttpException) {
                // GitHub returns 409 ("Git Repository is empty") for the Contents
                // API root of a newly-created repository. It is a valid empty
                // directory, not an error placeholder.
                if (e.code() == 409) {
                    _state.update {
                        it.copy(
                            entries = emptyList(),
                            currentPath = path,
                            pathStack = buildPathStack(path),
                            isLoading = false,
                            error = null,
                            lastCommits = emptyMap(),
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to list contents") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to list contents") }
            }
        }
    }

    /** Push one segment onto the path stack and list the resulting dir. */
    fun openDir(name: String) {
        val s = _state.value
        val newPath = if (s.currentPath.isBlank()) name else "${s.currentPath}/$name"
        listDir(newPath)
    }

    /** Pop one segment off the stack — returns to parent dir. */
    fun popDir(): Boolean {
        val s = _state.value
        if (s.currentPath.isBlank()) return false
        val parent = s.currentPath.substringBeforeLast('/', "")
        listDir(parent)
        return true
    }

    /** Open a single file: fetch its content (ContentEntry with base64) and show inline. */
    fun openFile(entry: GitHubApi.ContentEntry) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, viewingFile = entry, error = null) }
            try {
                val element = api.getContents(s.owner, s.repo, entry.path, s.ref)
                if (element is JsonObject) {
                    val fetched = json.decodeFromJsonElement<GitHubApi.ContentEntry>(element)
                    val decoded = if (fetched.encoding == "base64" && fetched.content.isNotBlank()) {
                        try {
                            String(Base64.decode(fetched.content.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) { null }
                    } else null
                    _state.update { it.copy(isLoading = false, viewingFile = fetched, fileContent = decoded) }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load file") }
            }
        }
    }

    fun closeFile() {
        _state.update { it.copy(viewingFile = null, fileContent = null) }
    }

    /**
     * Handle a system-back press inside the code browser.
     * Returns true when the press was consumed (closed file or popped a directory),
     * false when already at the repository root (caller should navigate back).
     */
    fun handleBack(): Boolean {
        val s = _state.value
        return when {
            s.viewingFile != null -> { closeFile(); true }
            s.currentPath.isNotBlank() -> { popDir(); true }
            else -> false
        }
    }

    /** Lazy-load the branch list (only fetched once per repo). */
    fun loadBranches() {
        val s = _state.value
        if (s.owner.isBlank() || s.branches.isNotEmpty() || s.isLoadingBranches) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingBranches = true) }
            try {
                // Fetch up to 100 branches — enough for the vast majority of repos.
                val branches = api.getBranches(s.owner, s.repo, perPage = 100)
                _state.update { it.copy(branches = branches, isLoadingBranches = false) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingBranches = false) }
            }
        }
    }

    /** Switch the browsed ref (branch) and reload the tree from its root. */
    fun switchRef(ref: String) {
        val s = _state.value
        if (s.ref == ref) return
        commitCache.clear() // ref changed → cached commit info is stale
        _state.update { it.copy(ref = ref, viewingFile = null, fileContent = null, lastCommits = emptyMap()) }
        listDir("")
    }

    private fun buildPathStack(path: String): List<String> {
        if (path.isBlank()) return listOf("")
        val parts = path.split("/")
        val stack = mutableListOf("")
        var acc = ""
        parts.forEach { part ->
            acc = if (acc.isBlank()) part else "$acc/$part"
            stack.add(acc)
        }
        return stack
    }

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Concurrently fetch the last commit for each entry (limited to 5 parallel requests).
     * Results land in [_state].lastCommits and [commitCache]. Failures silently produce
     * no entry — the UI just falls back to the size-only subtitle.
     */
    private fun fetchLastCommits(
        entries: List<GitHubApi.ContentEntry>,
        owner: String,
        repo: String,
        ref: String?,
    ) {
        if (entries.isEmpty()) return
        val cacheKey = { path: String -> "${owner}/${repo}@${ref ?: "HEAD"}::$path" }
        // Anything already cached is applied immediately; the rest gets fetched.
        val cached = entries.mapNotNull { e ->
            commitCache[cacheKey(e.path)]?.let { e.path to it }
        }.toMap()
        if (cached.isNotEmpty()) {
            _state.update { it.copy(lastCommits = it.lastCommits + cached) }
        }
        val toFetch = entries.filter { cacheKey(it.path) !in commitCache }
            .take(15) // Cap at 15 to avoid burning the API rate limit on large directories.
        if (toFetch.isEmpty()) return

        viewModelScope.launch {
            val sem = Semaphore(5)
            // Each completed request eagerly updates the UI so the user sees the
            // time appear progressively instead of waiting for every entry to finish.
            withContext(Dispatchers.IO) {
                coroutineScope {
                    toFetch.map { entry ->
                        async {
                            sem.withPermit {
                                val lc = runCatching {
                                    val commits = api.getCommits(owner, repo, perPage = 1, sha = ref, path = entry.path)
                                    val c = commits.firstOrNull()?.commit
                                    val date = c?.committer?.date ?: c?.author?.date
                                    date?.let { LastCommit(message = "", dateIso = it) }
                                }.getOrNull() ?: return@withPermit
                                commitCache[cacheKey(entry.path)] = lc
                                // Apply only if still on the same dir; otherwise cache wins next time.
                                val cur = _state.value
                                if (cur.owner == owner && cur.repo == repo && cur.ref == ref) {
                                    _state.update { it.copy(lastCommits = it.lastCommits + (entry.path to lc)) }
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }
}
