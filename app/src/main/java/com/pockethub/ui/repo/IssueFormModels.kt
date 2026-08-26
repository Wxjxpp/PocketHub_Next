package com.pockethub.ui.repo

/**
 * Data model for GitHub **issue form templates** (the newer `.yml` format under
 * `.github/ISSUE_TEMPLATE`). Every control found in a template's `body:` array is
 * mapped to one subclass of [IssueFormField]; templates differ wildly between repos,
 * so the UI renderer is fully data-driven over this list.
 *
 * Reference: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-in-your-projects/syntax-for-githubs-form-schema
 */
sealed class IssueFormField {

    /** Stable positional key — `id` is optional in the schema, so we key answers by body position. */
    abstract val index: Int

    abstract val label: String?
    abstract val description: String?
    open val required: Boolean = false

    /** Static informational markdown block (`type: markdown`) — not editable. */
    data class Markdown(
        override val index: Int,
        val value: String,
    ) : IssueFormField()

    /**
     * Free-text field — covers both `type: input` (single line) and `type: textarea`
     * ([multiline] = true). [render] hints how the entry will be rendered on GitHub
     * (e.g. "shell", "markdown"); purely cosmetic for us.
     */
    data class TextInput(
        override val index: Int,
        override val label: String?,
        override val description: String?,
        override val required: Boolean,
        val multiline: Boolean,
        val placeholder: String?,
        val defaultValue: String?,
        val render: String?,
    ) : IssueFormField()

    data class CheckOption(val label: String, val required: Boolean)

    /** `type: checkboxes` — an option marked `required: true` must be checked to submit. */
    data class CheckboxGroup(
        override val index: Int,
        override val label: String?,
        override val description: String?,
        val options: List<CheckOption>,
    ) : IssueFormField()

    /** `type: dropdown` — [multiple] mirrors the schema's `multiple: true` multi-select. */
    data class Dropdown(
        override val index: Int,
        override val label: String?,
        override val description: String?,
        override val required: Boolean,
        val multiple: Boolean,
        val options: List<String>,
    ) : IssueFormField()
}

/**
 * A parsed YAML issue-form template. Legacy `.md` templates don't produce one —
 * they stay free-text (see [IssueTemplate]).
 */
data class IssueForm(
    val name: String?,
    val description: String?,
    val title: String,
    val labels: List<String>,
    val assignees: List<String>,
    val fields: List<IssueFormField>,
) {
    /** First unanswered required prompt (label) for validation, or null if everything is filled. */
    fun firstMissingRequired(answers: Map<Int, IssueFormAnswer>): String? {
        for (f in fields) {
            val a = answers[f.index] ?: IssueFormAnswer()
            when (f) {
                is IssueFormField.TextInput -> if (f.required && a.text.isBlank()) return f.label
                is IssueFormField.Dropdown ->
                    if (f.required && if (f.multiple) a.checked.isEmpty() else a.selected < 0) return f.label
                is IssueFormField.CheckboxGroup ->
                    if (f.options.anyIndexed { i, o -> o.required && i !in a.checked }) return f.label
                is IssueFormField.Markdown -> Unit
            }
        }
        return null
    }

    private inline fun List<IssueFormField.CheckOption>.anyIndexed(
        predicate: (Int, IssueFormField.CheckOption) -> Boolean,
    ): Boolean {
        forEachIndexed { i, o -> if (predicate(i, o)) return true }
        return false
    }

    companion object {
        const val NO_RESPONSE = "_No response_"
    }
}

/** User's answer(s) for one form field, keyed by the field's [IssueFormField.index]. */
data class IssueFormAnswer(
    val text: String = "",
    /** Selected indices — checkbox groups and multi-select dropdowns. */
    val checked: Set<Int> = emptySet(),
    /** Selected index — single-select dropdown. */
    val selected: Int = -1,
)

/** A `contact_links` entry parsed from `.github/ISSUE_TEMPLATE/config.yml`. */
data class IssueContactLink(
    val name: String,
    val url: String,
    val about: String,
)
