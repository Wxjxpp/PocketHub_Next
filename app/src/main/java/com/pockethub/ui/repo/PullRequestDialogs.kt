@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pockethub.ui.repo

// Pull request detail dialogs (merge, review, reviewers, comment edit/delete).
// Split out of PullRequestDetailScreen.kt for readability.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun AddReviewerSheet(
    working: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (List<String>) -> Unit,
) {
    // Add reviewers dialog (multi-input via chip list)
        val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var reviewerInput by remember { mutableStateOf("") }
        var pendingReviewers by remember { mutableStateOf<List<String>>(emptyList()) }
        ModalBottomSheet(
            onDismissRequest = { if (!working) onDismiss() },
            sheetState = sheetState,
        ) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.pr_add_reviewer_dialog_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = reviewerInput,
                    onValueChange = { reviewerInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.pr_add_reviewer_search_hint)) },
                    singleLine = true,
                    enabled = !working,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val v = reviewerInput.trim().removePrefix("@")
                                if (v.isNotEmpty() && pendingReviewers.none { it.equals(v, ignoreCase = true) }) {
                                    pendingReviewers = pendingReviewers + v
                                    reviewerInput = ""
                                }
                            },
                            enabled = !working,
                        ) { Icon(Icons.Outlined.Add, null) }
                    },
                    keyboardActions = KeyboardActions(onDone = {
                        val v = reviewerInput.trim().removePrefix("@")
                        if (v.isNotEmpty() && pendingReviewers.none { it.equals(v, ignoreCase = true) }) {
                            pendingReviewers = pendingReviewers + v
                            reviewerInput = ""
                        }
                    }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (pendingReviewers.isEmpty()) {
                    Text(
                        stringResource(R.string.pr_add_reviewer_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pendingReviewers.forEach { login ->
                            InputChip(
                                label = { Text("@$login", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_remove),
                                        modifier = Modifier.size(14.dp).clickable {
                                            pendingReviewers = pendingReviewers.filterNot { it.equals(login, ignoreCase = true) }
                                        },
                                    )
                                },
                                onClick = { pendingReviewers = pendingReviewers.filterNot { it.equals(login, ignoreCase = true) } },
                                selected = false,
                                enabled = !working,
                            )
                        }
                    }
                }
                error?.let { err ->
                    Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { onDismiss() },
                        enabled = !working,
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onSubmit(pendingReviewers)
                        },
                        enabled = pendingReviewers.isNotEmpty() && !working,
                    ) {
                        if (working) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.pr_add_reviewer_submit))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
}
@Composable
internal fun MergeDialog(
    prNumber: Int,
    merging: Boolean,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit,
) {
    // Merge dialog
        var mergeMethod by remember { mutableStateOf("merge") }
        AlertDialog(
            onDismissRequest = { if (!merging) onDismiss() },
            title = { Text(stringResource(R.string.pr_merge_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.pr_merge_confirm, prNumber))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("merge" to stringResource(R.string.pr_merge_method_merge), "squash" to stringResource(R.string.pr_merge_method_squash), "rebase" to stringResource(R.string.pr_merge_method_rebase)).forEach { (method, label) ->
                            OutlinedButton(
                                onClick = { mergeMethod = method },
                                colors = if (mergeMethod == method) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onDismiss(); onMerge(mergeMethod) },
                    enabled = !merging,
                ) {
                    if (merging) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.action_merge))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}
@Composable
internal fun ReviewSheet(
    reviewEvent: ReviewEvent,
    onReviewEventChange: (ReviewEvent) -> Unit,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (apiValue: String, body: String) -> Unit,
) {
    // Review submit bottom sheet (R1)
        val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var reviewBody by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { if (!sending) onDismiss() },
            sheetState = sheetState,
        ) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.pr_review_submit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                ReviewEvent.entries.forEach { ev ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !sending) { onReviewEventChange(ev) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = reviewEvent == ev,
                            onClick = { onReviewEventChange(ev) },
                            enabled = !sending,
                            colors = RadioButtonDefaults.colors(selectedColor = when (ev) {
                                ReviewEvent.APPROVE -> MaterialTheme.colorScheme.primary
                                ReviewEvent.REQUEST_CHANGES -> MaterialTheme.colorScheme.error
                                ReviewEvent.COMMENT -> MaterialTheme.colorScheme.onSurfaceVariant
                            }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (ev) {
                                    ReviewEvent.COMMENT -> stringResource(R.string.pr_review_event_comment)
                                    ReviewEvent.APPROVE -> stringResource(R.string.pr_review_event_approve)
                                    ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_review_event_request_changes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (ev) {
                                    ReviewEvent.COMMENT -> stringResource(R.string.pr_review_event_hint_comment)
                                    ReviewEvent.APPROVE -> stringResource(R.string.pr_review_event_hint_approve)
                                    ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_review_event_hint_request_changes)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reviewBody,
                    onValueChange = { reviewBody = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = {
                        Text(when (reviewEvent) {
                            ReviewEvent.COMMENT -> stringResource(R.string.pr_review_event_hint_comment)
                            ReviewEvent.APPROVE -> stringResource(R.string.pr_review_event_hint_approve)
                            ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_review_event_hint_request_changes)
                        })
                    },
                    enabled = !sending,
                    minLines = 3,
                )

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { onDismiss() },
                        enabled = !sending,
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onSubmit(reviewEvent.apiValue, reviewBody)
                        },
                        enabled = !sending,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(when (reviewEvent) {
                                ReviewEvent.COMMENT -> stringResource(R.string.pr_review_comment)
                                ReviewEvent.APPROVE -> stringResource(R.string.pr_approve)
                                ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_request_changes)
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
}
@Composable
internal fun MergeWarningDialog(
    changesRequestedCount: Int,
    onDismiss: () -> Unit,
    onMergeAnyway: () -> Unit,
) {
    // Merge warning dialog (R5) — reviews requested changes; user taps "merge anyway"
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.pr_merge_warning_title)) },
            text = {
                val count = changesRequestedCount
                Text(stringResource(R.string.pr_changes_requested_warning, count))
            },
            confirmButton = {
                Button(onClick = { onDismiss(); onMergeAnyway() }) { Text(stringResource(R.string.action_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}
@Composable
internal fun EditInlineCommentDialog(
    id: Long,
    body: String,
    onBodyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.pr_inline_edit_title)) },
            text = {
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onSave(id, body.trim()); onDismiss() },
                    enabled = body.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}
@Composable
internal fun DeleteInlineConfirmDialog(
    id: Long,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.comment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.comment_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = { onDelete(id); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}
@Composable
internal fun EditCommentDialog(
    id: Long,
    body: String,
    onBodyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.comment_edit_title)) },
            text = {
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    label = { Text(stringResource(R.string.comment_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSave(id, body.trim())
                    onDismiss()
                }, enabled = body.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}
@Composable
internal fun DeleteCommentConfirmDialog(
    id: Long,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.comment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.comment_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}
