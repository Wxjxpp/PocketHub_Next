package com.pockethub.ui.repo

import android.util.Base64
import org.yaml.snakeyaml.Yaml

/**
 * Parser for GitHub issue templates.
 *
 * Handles every layout found in the wild:
 *  - YAML issue forms under the ISSUE_TEMPLATE directory (structured controls)
 *  - config.yml in that directory — [contactLinks] + blank-issues toggle
 *  - legacy free-text markdown templates there or at the repo root
 *
 * Unknown/invalid content never throws: malformed fields degrade to raw text so the
 * user can still see and edit them.
 */
object IssueFormParser {

    /** Full template directory listing result for one repository. */
    data class Result(
        val forms: List<IssueForm>,
        val legacyTemplates: List<IssueTemplate>,
        val contactLinks: List<IssueContactLink>,
    )

    fun parseFormYaml(raw: String): IssueForm? = runCatching {
        val root = Yaml().load<Map<String, Any?>>(raw) ?: return null
        // name/description may be quoted or plain scalars
        val name = root.scalar("name")
        val description = root.scalar("description")
        val title = root.scalar("title").orEmpty()
        val labels = root.stringList("labels")
        val assignees = root.stringList("assignees")

        @Suppress("UNCHECKED_CAST")
        val body = root["body"] as? List<Any?> ?: emptyList()
        val fields = body.mapIndexedNotNull { i, item ->
            @Suppress("UNCHECKED_CAST")
            parseField(i, item as? Map<String, Any?>)
        }
        if (fields.isEmpty() && title.isEmpty()) return null
        IssueForm(
            name = name,
            description = description,
            title = title,
            labels = labels,
            assignees = assignees,
            fields = fields,
        )
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun parseField(index: Int, map: Map<String, Any?>?): IssueFormField? {
        if (map == null) return null
        val type = map.scalar("type") ?: return null
        val attrs = map["attributes"] as? Map<String, Any?>
        val validations = map["validations"] as? Map<String, Any?>
        val required = validations?.get("required") == true
        val label = attrs?.scalar("label")
        val description = attrs?.scalar("description")

        return when (type) {
            "markdown" -> IssueFormField.Markdown(
                index = index,
                value = attrs?.scalar("value").orEmpty(),
            )
            "input", "textarea" -> IssueFormField.TextInput(
                index = index,
                label = label,
                description = description,
                required = required,
                multiline = type == "textarea",
                placeholder = attrs?.scalar("placeholder"),
                defaultValue = attrs?.scalar("value"),
                render = attrs?.scalar("render"),
            )
            "checkboxes" -> {
                val options = (attrs?.get("options") as? List<Any?>)
                    ?.mapNotNull { o ->
                        val om = o as? Map<String, Any?>
                        if (om == null) null
                        else IssueFormField.CheckOption(
                            label = om.scalar("label") ?: "",
                            required = om["required"] == true,
                        )
                    }
                    ?.filter { it.label.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: return fallbackToMarkdown(index, label, description, attrs, type)
                IssueFormField.CheckboxGroup(
                    index = index,
                    label = label,
                    description = description,
                    options = options,
                )
            }
            "dropdown" -> {
                val options = (attrs?.get("options") as? List<Any?>)
                    ?.mapNotNull { scalarOf(it) }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: return fallbackToMarkdown(index, label, description, attrs, type)
                IssueFormField.Dropdown(
                    index = index,
                    label = label,
                    description = description,
                    required = required,
                    multiple = attrs?.get("multiple") == true,
                    options = options,
                )
            }
            // Unknown control type — show its raw YAML snippet as informational text
            else -> fallbackToMarkdown(index, label, description, attrs, type)
        }
    }

    /** Degradation path: render whatever we couldn't model as static markdown. */
    private fun fallbackToMarkdown(
        index: Int,
        label: String?,
        description: String?,
        attrs: Map<String, Any?>?,
        type: String,
    ): IssueFormField.Markdown = IssueFormField.Markdown(
        index = index,
        value = buildString {
            append("**[$type]** ")
            append(label.orEmpty())
            if (!description.isNullOrBlank()) append("\n\n").append(description)
            val opts = attrs?.get("options")
            if (opts is List<*>) {
                opts.filterIsInstance<Map<*, *>>()
                    .mapNotNull { it["label"] as? String }
                    .joinToString("\n") { "- $it" }
                    .takeIf { it.isNotBlank() }
                    ?.let { append("\n\n").append(it) }
            }
        },
    )

    fun parseConfigYaml(raw: String): Pair<Boolean, List<IssueContactLink>> {
        return try {
            val root: Map<String, Any?> = Yaml().load<Map<String, Any?>>(raw) ?: return true to emptyList()
            val links = (root["contact_links"] as? List<Any?>)
                ?.mapNotNull { entry ->
                    val m = entry as? Map<String, Any?>
                    val name = m?.scalar("name")
                    val url = m?.scalar("url")
                    if (name == null || url == null) null
                    else IssueContactLink(
                        name = name,
                        url = url,
                        about = m.scalar("about").orEmpty(),
                    )
                }.orEmpty()
            val blankEnabled = root["blank_issues_enabled"] != false
            blankEnabled to links
        } catch (_: Exception) {
            true to emptyList()
        }
    }

    /**
     * Parse a legacy `.md` template (front-matter + body) into an [IssueTemplate].
     * Kept byte-compatible with the previous ViewModel behavior so existing UI keeps working.
     */
    fun parseLegacy(fileName: String, raw: String): IssueTemplate {
        if (!raw.startsWith("---")) {
            return IssueTemplate(fileName, fileName, "", "", emptyList(), emptyList(), raw)
        }
        val endIdx = raw.indexOf("\n---", 3)
        if (endIdx < 0) return IssueTemplate(fileName, fileName, "", "", emptyList(), emptyList(), raw)
        val fm = raw.substring(3, endIdx).trim()
        val body = raw.substring(endIdx + 4).trimStart('-', '\n')
        var name = ""
        var about = ""
        var title = ""
        val labels = mutableListOf<String>()
        val assigns = mutableListOf<String>()
        fm.split("\n").forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val colon = trimmed.indexOf(':')
            if (colon < 0) return@forEach
            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim()
            when (key) {
                "name" -> name = value.trim('"', '\'')
                "about", "description" -> about = value.trim('"', '\'')
                "title" -> title = value.trim('"', '\'')
                "labels" -> labels.addAll(parseYamlStringList(value))
                "assignees", "assigns" -> assigns.addAll(parseYamlStringList(value))
            }
        }
        return IssueTemplate(
            fileName = fileName,
            name = name.ifEmpty { fileName },
            about = about,
            title = title,
            labels = labels,
            assigns = assigns,
            body = body,
        )
    }

    private fun parseYamlStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length - 1)
                .split(",")
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotEmpty() }
        }
        return value.split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
    }

    // ── small YAML helpers ──────────────────────────────────────────────

    private fun Map<String, Any?>.scalar(key: String): String? =
        (this[key] as? Any?)?.let { scalarOf(it) }

    private fun scalarOf(any: Any?): String? = when (any) {
        null -> null
        is String -> any
        is Number, is Boolean -> any.toString()
        is List<*> -> any.joinToString(", ") { scalarOf(it).orEmpty() }.takeIf { it.isNotBlank() }
        else -> any.toString()
    }

    private fun Map<String, Any?>.stringList(key: String): List<String> {
        val v = this[key] ?: return emptyList()
        return when (v) {
            is List<*> -> v.mapNotNull { scalarOf(it) }
            is String -> v.split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    /** Decode base64 content from the GitHub contents API. */
    fun decodeContent(content: String, encoding: String?): String =
        if (encoding == "base64" && content.isNotBlank()) {
            runCatching { Base64.decode(content, Base64.DEFAULT).toString(Charsets.UTF_8) }.getOrDefault("")
        } else content

    /** Build the final markdown body from form answers, mimicking GitHub's web rendering. */
    fun buildBody(form: IssueForm, answers: Map<Int, IssueFormAnswer>): String = buildString {
        form.fields.forEach { field ->
            val a = answers[field.index] ?: IssueFormAnswer()
            when (field) {
                is IssueFormField.Markdown -> Unit // informational only, not included by GitHub either
                is IssueFormField.TextInput -> {
                    appendLine("### ${field.label ?: ""}")
                    appendLine()
                    appendLine(if (a.text.isBlank()) IssueForm.NO_RESPONSE else a.text)
                    appendLine()
                }
                is IssueFormField.CheckboxGroup -> {
                    appendLine("### ${field.label ?: ""}")
                    appendLine()
                    field.options.forEachIndexed { i, opt ->
                        val mark = if (i in a.checked) "x" else " "
                        appendLine("- [$mark] ${opt.label}")
                    }
                    appendLine()
                }
                is IssueFormField.Dropdown -> {
                    appendLine("### ${field.label ?: ""}")
                    appendLine()
                    if (field.multiple) {
                        field.options.forEachIndexed { i, opt ->
                            val mark = if (i in a.checked) "x" else " "
                            appendLine("- [$mark] $opt")
                        }
                    } else {
                        val chosen = field.options.getOrNull(a.selected)
                        appendLine(chosen ?: IssueForm.NO_RESPONSE)
                    }
                    appendLine()
                }
            }
        }
    }.trimEnd() + "\n"
}
