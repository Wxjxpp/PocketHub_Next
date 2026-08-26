package com.pockethub.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native (Compose) renderer for GitHub YAML issue forms.
 *
 * Fully data-driven over [IssueForm.fields] — templates differ wildly between repos,
 * but every control maps to one branch below. Unknown types were already degraded to
 * [IssueFormField.Markdown] by the parser, so this renderer can never crash on new shapes.
 */
@Composable
fun IssueFormView(
    form: IssueForm,
    answers: Map<Int, IssueFormAnswer>,
    enabled: Boolean,
    onAnswerChange: (Int, IssueFormAnswer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!form.description.isNullOrBlank()) {
            Text(
                form.description!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        form.fields.forEach { field ->
            val answer = answers[field.index] ?: IssueFormAnswer()
            when (field) {
                is IssueFormField.Markdown -> MarkdownBlock(field.value)
                is IssueFormField.TextInput -> TextInputField(field, answer, enabled) {
                    onAnswerChange(field.index, it)
                }
                is IssueFormField.CheckboxGroup -> CheckboxGroupField(field, answer, enabled) {
                    onAnswerChange(field.index, it)
                }
                is IssueFormField.Dropdown -> DropdownField(field, answer, enabled) {
                    onAnswerChange(field.index, it)
                }
            }
        }
    }
}

// ── markdown ────────────────────────────────────────────────────────────

/** Lightweight markdown rendering for template hints: **bold**, `code`, code fences, lists. */
@Composable
private fun MarkdownBlock(value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Column(modifier = modifier.fillMaxWidth()) {
        var inFence = false
        value.split("\n").forEach { line ->
            when {
                line.trimStart().startsWith("```") -> inFence = !inFence
                inFence -> CodeLine(line)
                line.startsWith("- ") || line.startsWith("* ") ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodySmall)
                        Text(inlineMarkdown(line.substring(2)), style = MaterialTheme.typography.bodySmall)
                    }
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(
                    inlineMarkdown(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeLine(line: String) {
    Text(
        line,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Parses `**bold**`, `` `code` `` and links `[text](url)` → text into an [AnnotatedString]. */
internal fun inlineMarkdown(text: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    b.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    b.append(text.substring(i + 2, end))
                    b.pop()
                    i = end + 2
                } else {
                    b.append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    b.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                    b.append(text.substring(i + 1, end))
                    b.pop()
                    i = end + 1
                } else {
                    b.append(text[i]); i++
                }
            }
            else -> {
                b.append(text[i]); i++
            }
        }
    }
    return b.toAnnotatedString()
}

// ── text input ──────────────────────────────────────────────────────────

@Composable
private fun TextInputField(
    field: IssueFormField.TextInput,
    answer: IssueFormAnswer,
    enabled: Boolean,
    onChange: (IssueFormAnswer) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldHeader(label = field.label, required = field.required)
        field.description?.let {
            MarkdownBlock(it)
        }
        OutlinedTextField(
            value = answer.text,
            onValueChange = { onChange(answer.copy(text = it)) },
            placeholder = { field.placeholder?.let { Text(it, maxLines = 2) } },
            minLines = if (field.multiline) 5 else 1,
            singleLine = !field.multiline,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── checkboxes ──────────────────────────────────────────────────────────

@Composable
private fun CheckboxGroupField(
    field: IssueFormField.CheckboxGroup,
    answer: IssueFormAnswer,
    enabled: Boolean,
    onChange: (IssueFormAnswer) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldHeader(label = field.label, required = false)
        field.description?.let { MarkdownBlock(it) }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                field.options.forEachIndexed { i, opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                val next = answer.checked.toMutableSet()
                                if (!next.add(i)) next.remove(i)
                                onChange(answer.copy(checked = next))
                            }
                            .padding(start = 8.dp),
                    ) {
                        Checkbox(
                            checked = i in answer.checked,
                            onCheckedChange = null, // row handles clicks
                            enabled = enabled,
                        )
                        Text(
                            inlineMarkdown(opt.label),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── dropdown ────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    field: IssueFormField.Dropdown,
    answer: IssueFormAnswer,
    enabled: Boolean,
    onChange: (IssueFormAnswer) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldHeader(label = field.label, required = field.required)
        field.description?.let { MarkdownBlock(it) }
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it },
        ) {
            OutlinedTextField(
                value = if (field.multiple) {
                    field.options.filterIndexed { i, _ -> i in answer.checked }.joinToString(", ")
                } else {
                    field.options.getOrNull(answer.selected) ?: ""
                },
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = null,
                placeholder = {
                    val hint = stringResource(if (field.multiple) R.string.issue_form_select_multi else R.string.issue_form_select_one)
                    Text(hint)
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                field.options.forEachIndexed { i, opt ->
                    DropdownMenuItem(
                        text = {
                            val prefix = if (i in answer.checked || i == answer.selected) "✓ " else ""
                            Text(prefix + opt)
                        },
                        onClick = {
                            if (field.multiple) {
                                val next = answer.checked.toMutableSet()
                                if (!next.add(i)) next.remove(i)
                                onChange(answer.copy(checked = next))
                            } else {
                                onChange(answer.copy(selected = i))
                                expanded = false
                            }
                        },
                    )
                }
            }
        }
    }
}

// ── shared header ───────────────────────────────────────────────────────

@Composable
private fun FieldHeader(label: String?, required: Boolean) {
    if (label.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            inlineMarkdown(label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (required) {
            Spacer(Modifier.width(4.dp))
            Text("*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
    }
}
