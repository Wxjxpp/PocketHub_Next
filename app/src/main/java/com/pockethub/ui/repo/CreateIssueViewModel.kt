package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A parsed GitHub issue template (an `.md` or `.yml` file under `.github/ISSUE_TEMPLATE`). */
data class IssueTemplate(
    val fileName: String,
    val name: String,
    val about: String,
    val title: String,
    val labels: List<String>,
    val assigns: List<String>,
    /** Markdown body to prefill in the editor (front-matter stripped). */
    val body: String,
)
@HiltViewModel
class CreateIssueViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    private val api: GitHubApi,
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _result = MutableStateFlow<Result<Issue>?>(null)
    val result: StateFlow<Result<Issue>?> = _result

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _templates = MutableStateFlow<List<IssueTemplate>>(emptyList())
    val templates: StateFlow<List<IssueTemplate>> = _templates.asStateFlow()

    private val _isLoadingTemplates = MutableStateFlow(false)
    val isLoadingTemplates: StateFlow<Boolean> = _isLoadingTemplates.asStateFlow()

    /** Selected template — null means "blank issue". */
    private val _selectedTemplate = MutableStateFlow<IssueTemplate?>(null)
    val selectedTemplate: StateFlow<IssueTemplate?> = _selectedTemplate.asStateFlow()

    // ── YAML issue-form support (new .yml templates) ────────────────────

    /** Parsed `.yml` form templates, shown in the chooser alongside legacy ones. */
    private val _forms = MutableStateFlow<List<IssueForm>>(emptyList())
    val forms: StateFlow<List<IssueForm>> = _forms.asStateFlow()

    /** The selected YAML form — null when a legacy template or blank issue is active. */
    private val _selectedForm = MutableStateFlow<IssueForm?>(null)
    val selectedForm: StateFlow<IssueForm?> = _selectedForm.asStateFlow()

    /** Answers for the selected form, keyed by field index. */
    private val _formAnswers = MutableStateFlow<Map<Int, IssueFormAnswer>>(emptyMap())
    val formAnswers: StateFlow<Map<Int, IssueFormAnswer>> = _formAnswers.asStateFlow()

    /** True once the user explicitly picked "Blank issue" (fixes chooser reopening). */
    private val _blankSelected = MutableStateFlow(false)
    val blankSelected: StateFlow<Boolean> = _blankSelected.asStateFlow()

    /** Leave the blank-issue editor and return to the template chooser. */
    fun clearBlankSelection() {
        _blankSelected.update { false }
    }

    /** contact_links from config.yml (external links shown in the chooser). */
    private val _contactLinks = MutableStateFlow<List<IssueContactLink>>(emptyList())
    val contactLinks: StateFlow<List<IssueContactLink>> = _contactLinks.asStateFlow()

    /** Editor state — labels selected for the new issue. Prefilled from template front-matter. */
    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()

    /** Editor state — assignees selected for the new issue. Prefilled from template front-matter. */
    private val _assignees = MutableStateFlow<List<String>>(emptyList())
    val assignees: StateFlow<List<String>> = _assignees.asStateFlow()

    fun loadTemplates(owner: String, repo: String) {
        if (_templates.value.isNotEmpty() || _forms.value.isNotEmpty() || _isLoadingTemplates.value) return
        viewModelScope.launch {
            _isLoadingTemplates.update { true }
            try {
                val result = parseTemplateDir(owner, repo)
                _forms.update { result.forms }
                _templates.update { result.legacyTemplates }
                _contactLinks.update { result.contactLinks }
            } catch (_: Exception) {
                // Non-fatal — fall back to blank form
            } finally {
                _isLoadingTemplates.update { false }
            }
        }
    }

    /**
     * Fetch and classify every entry under `.github/ISSUE_TEMPLATE` (falling back to
     * the repo root for legacy top-level `ISSUE_TEMPLATE*.md`):
     *  - `config.yml` → contact links
     *  - `.yml`/`.yaml` → structured issue forms
     *  - `.md` → legacy free-text templates
     */
    private suspend fun parseTemplateDir(owner: String, repo: String): IssueFormParser.Result {
        val arr = runCatching {
            api.getContents(owner, repo, ".github/ISSUE_TEMPLATE")
        }.getOrNull()
        val entries = decodeDirectory(arr)
            .ifEmpty {
                // Legacy repos may only have a top-level ISSUE_TEMPLATE.md
                decodeDirectory(runCatching { api.getRootContents(owner, repo) }.getOrNull())
                    .filter { it.type == "file" && it.name.startsWith("ISSUE_TEMPLATE", ignoreCase = true) && it.name.endsWith(".md", ignoreCase = true) }
            }

        var legacy = emptyList<IssueTemplate>()
        var forms = emptyList<IssueForm>()
        var contactLinks = emptyList<IssueContactLink>()
        entries.forEach { entry ->
            val raw = fetchRaw(owner, repo, entry.path)
            when {
                entry.name.equals("config.yml", true) || entry.name.equals("config.yaml", true) -> {
                    if (raw.isNotEmpty()) contactLinks = IssueFormParser.parseConfigYaml(raw).second
                }
                entry.name.endsWith(".yml", true) || entry.name.endsWith(".yaml", true) ->
                    IssueFormParser.parseFormYaml(raw)?.let { forms += it }
                entry.name.endsWith(".md", true) ->
                    legacy += IssueFormParser.parseLegacy(entry.name, raw)
            }
        }
        return IssueFormParser.Result(forms, legacy, contactLinks)
    }

    private fun decodeDirectory(el: kotlinx.serialization.json.JsonElement?): List<GitHubApi.ContentEntry> {
        if (el == null) return emptyList()
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(
                kotlinx.serialization.builtins.ListSerializer(GitHubApi.ContentEntry.serializer()),
                el,
            )
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRaw(owner: String, repo: String, path: String): String {
        val one = runCatching { api.getContents(owner, repo, path) }.getOrNull() ?: return ""
        val fileEntry = runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(GitHubApi.ContentEntry.serializer(), one)
        }.getOrNull() ?: return ""
        return IssueFormParser.decodeContent(fileEntry.content, fileEntry.encoding)
    }

    fun selectTemplate(t: IssueTemplate?) {
        _selectedTemplate.update { t }
        _selectedForm.update { null }
        _blankSelected.update { t == null }
        _labels.update { t?.labels.orEmpty() }
        _assignees.update { t?.assigns.orEmpty() }
        _formAnswers.update { emptyMap() }
    }

    /** Select a parsed YAML issue form — the UI switches to the native form renderer. */
    fun selectForm(form: IssueForm?) {
        _selectedForm.update { form }
        _selectedTemplate.update { null }
        _blankSelected.update { false }
        _labels.update { form?.labels.orEmpty() }
        _assignees.update { form?.assignees.orEmpty() }
        // Pre-seed answers with template default values so they're editable and included on submit
        val seed = mutableMapOf<Int, IssueFormAnswer>()
        form?.fields?.forEach { f ->
            if (f is IssueFormField.TextInput && !f.defaultValue.isNullOrBlank()) {
                seed[f.index] = IssueFormAnswer(text = f.defaultValue)
            }
        }
        _formAnswers.update { seed }
    }

    /** Update one field's answer (text input / checkbox / dropdown all funnel through here). */
    fun updateAnswer(index: Int, answer: IssueFormAnswer) {
        _formAnswers.update { it + (index to answer) }
    }

    private val _validationError = MutableStateFlow<String?>(null)

    /** First unanswered required prompt (label text) when submitting an incomplete form. */
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    fun clearValidationError() {
        _validationError.value = null
    }

    /**
     * Validate + submit a YAML form: builds GitHub-style markdown from the answers.
     * [titleOverride] is the title edited on screen (may fall back to the template preset).
     */
    fun submitForm(owner: String, repo: String, titleOverride: String? = null) {
        val form = _selectedForm.value ?: return
        val missing = form.firstMissingRequired(_formAnswers.value)
        if (missing != null) {
            _validationError.update { missing }
            return
        }
        val title = titleOverride?.takeIf { it.isNotBlank() } ?: effectiveTitle()
        createIssue(owner, repo, title, IssueFormParser.buildBody(form, _formAnswers.value))
    }

    private fun effectiveTitle(): String {
        val form = _selectedForm.value ?: return ""
        return form.title.ifBlank {
            // No title preset in the template: use the first short text answer as a hint
            _formAnswers.value.entries.sortedBy { it.key }
                .firstOrNull { (i, a) ->
                    (form.fields.firstOrNull { it.index == i } as? IssueFormField.TextInput)?.multiline == false && a.text.isNotBlank()
                }?.value?.text.orEmpty()
                .take(80)
        }
    }

    fun addLabel(value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        if (_labels.value.any { it.equals(v, ignoreCase = true) }) return
        _labels.update { it + v }
    }

    fun removeLabel(value: String) {
        _labels.update { list -> list.filterNot { it.equals(value, ignoreCase = true) } }
    }

    fun addAssignee(value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        if (_assignees.value.any { it.equals(v, ignoreCase = true) }) return
        _assignees.update { it + v }
    }

    fun removeAssignee(value: String) {
        _assignees.update { list -> list.filterNot { it.equals(value, ignoreCase = true) } }
    }

    fun createIssue(owner: String, repo: String, title: String, body: String?) {
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _actionError.value = null
            try {
                // Labels/assignees come from editor state — initialized from template
                // front-matter (see selectTemplate) but user-editable in the issue editor.
                val issue = api.createIssue(
                    owner, repo,
                    GitHubApi.IssueCreateRequest(
                        title = title,
                        body = body?.takeIf { it.isNotBlank() },
                        labels = _labels.value,
                        assignees = _assignees.value,
                    ),
                )
                _result.value = Result.success(issue)
            } catch (e: Exception) {
                issueReporter.reportError("CreateIssue", "createIssue", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                _actionError.value = e.localizedMessage ?: "Failed to create"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
