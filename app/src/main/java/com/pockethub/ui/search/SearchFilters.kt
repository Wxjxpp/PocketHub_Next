package com.pockethub.ui.search

import com.pockethub.R
import com.pockethub.ui.components.PhAsyncImage

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepoFilterRow(vm: SearchViewModel) {
    val sort by vm.repoSort.collectAsState()
    val order by vm.sortOrder.collectAsState()
    val language by vm.repoLanguage.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    var customQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = sort == RepoSort.BEST_MATCH,
                    onClick = { vm.applyRepoFilters(sort = RepoSort.BEST_MATCH) },
                    label = { Text(stringResource(R.string.sort_best_match)) },
                )
            }
            // Stars / Forks / Updated: the order lives inside the chip — tap to
            // select (descending by default), tap again to flip asc/desc.
            item {
                OrderAwareSortChip(
                    selected = sort == RepoSort.STARS,
                    label = stringResource(R.string.sort_stars),
                    order = order,
                    onClick = {
                        if (sort == RepoSort.STARS) {
                            vm.applyRepoFilters(order = if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC)
                        } else {
                            vm.applyRepoFilters(sort = RepoSort.STARS)
                        }
                    },
                )
            }
            item {
                OrderAwareSortChip(
                    selected = sort == RepoSort.FORKS,
                    label = stringResource(R.string.sort_forks),
                    order = order,
                    onClick = {
                        if (sort == RepoSort.FORKS) {
                            vm.applyRepoFilters(order = if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC)
                        } else {
                            vm.applyRepoFilters(sort = RepoSort.FORKS)
                        }
                    },
                )
            }
            item {
                OrderAwareSortChip(
                    selected = sort == RepoSort.UPDATED,
                    label = stringResource(R.string.sort_updated),
                    order = order,
                    onClick = {
                        if (sort == RepoSort.UPDATED) {
                            vm.applyRepoFilters(order = if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC)
                        } else {
                            vm.applyRepoFilters(sort = RepoSort.UPDATED)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // Language row — curated chips + a "Clear" chip when active + "Custom…" chip.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (language.isNotBlank()) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { vm.applyRepoFilters(language = "") },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(language)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                            }
                        },
                    )
                }
            }
            COMMON_LANGUAGES.forEach { lang ->
                item {
                    FilterChip(
                        selected = language.equals(lang, ignoreCase = true) && language.isNotBlank(),
                        onClick = {
                            // Toggle off if already selected.
                            val next = if (language.equals(lang, ignoreCase = true)) "" else lang
                            vm.applyRepoFilters(language = next)
                        },
                        label = { Text(lang) },
                    )
                }
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        customQuery = ""
                        showLanguagePicker = true
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.language_custom))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.language_custom_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = customQuery,
                        onValueChange = { customQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.language_filter_placeholder)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Code, null) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.language_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = customQuery.trim()
                    if (trimmed.isNotBlank()) vm.applyRepoFilters(language = trimmed)
                    showLanguagePicker = false
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal fun LazyListScope.repoItems(
    repos: List<com.pockethub.data.model.Repository>,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit,
) {
    items(repos, key = { it.id }) { repo ->
        Row(Modifier.fillMaxWidth().clickable { onNavigateToRepo(repo.owner.login, repo.name) }.padding(vertical = 8.dp)) {
            PhAsyncImage(
                model = repo.owner.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(CircleShape)
                    .clickable { onNavigateToUser(repo.owner.login) },
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("${repo.owner.login}/${repo.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!repo.description.isNullOrBlank()) Text(repo.description!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun LazyListScope.userItems(
    users: List<com.pockethub.data.model.User>,
    onNavigateToUser: (String) -> Unit,
) {
    items(users, key = { it.login }) { user ->
        Row(
            Modifier.fillMaxWidth()
                .clickable { onNavigateToUser(user.login) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhAsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(user.name ?: "@${user.login}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("@${user.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun LazyListScope.codeItems(
    code: List<com.pockethub.data.remote.GitHubApi.CodeSearchItem>,
    onNavigateToRepo: (String, String) -> Unit,
) {
    items(code, key = { it.htmlUrl.ifBlank { it.path } }) { item ->
        // Preferred: the `repository` object. Fallback: parse owner/repo from the
        // file's html_url (…/owner/repo/blob/branch/path) so the row is still
        // tappable when the object is missing.
        val slug = item.repository?.let { it.owner.login to it.name }
            ?: Regex("github\\.com/([^/]+)/([^/]+)/(?:blob|raw|tree)")
                .find(item.htmlUrl)
                ?.groupValues
                ?.let { g -> if (g.size >= 3) g[1] to g[2] else null }
        Column(Modifier.fillMaxWidth().clickable(enabled = slug != null) {
            slug?.let { (o, r) -> onNavigateToRepo(o, r) }
        }.padding(vertical = 8.dp)) {
            Text(item.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                slug?.let { (o, r) -> "$o/$r" } ?: item.repository?.fullName.orEmpty().ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun LazyListScope.issueItems(
    issues: List<com.pockethub.data.model.Issue>,
    onNavigateToIssue: (String, String, Int) -> Unit,
    onNavigateToPR: (String, String, Int) -> Unit,
) {
    items(issues, key = { it.id }) { issue ->
        val isPR = issue.pullRequest != null
        val repoSlug = issueOwnerRepo(issue)
        val repoLabel = repoSlug?.let { (o, r) -> "$o/$r" } ?: "—"
        Row(
            Modifier.fillMaxWidth().clickable {
                val (owner, repo) = repoSlug ?: return@clickable
                if (isPR) onNavigateToPR(owner, repo, issue.number) else onNavigateToIssue(owner, repo, issue.number)
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isPR) Icons.Outlined.Merge else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${issue.number}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    val stateColor = when {
                        issue.state == "closed" && isPR -> MaterialTheme.colorScheme.secondary
                        issue.state == "closed" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(issue.state, style = MaterialTheme.typography.labelSmall, color = stateColor)
                }
                Text(issue.title.ifBlank { "(no title)" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(repoLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Resolve the owning "owner/repo" of a search-result issue/PR.
 *
 * GitHub stopped returning the full `repository` object in /search/issues, so
 * fall back through: repository object → `repository_url`
 * (`…/repos/owner/repo`) → `html_url` (`…/owner/repo/(issues|pull)/n`).
 * Returns null only when every source is missing.
 */
internal fun issueOwnerRepo(issue: com.pockethub.data.model.Issue): Pair<String, String>? {
    issue.repository?.let { return it.owner.login to it.name }
    issue.repositoryUrl?.let { url ->
        val m = Regex("/repos/([^/]+)/([^/?]+)").find(url)
        if (m != null) return m.groupValues[1] to m.groupValues[2]
    }
    issue.htmlUrl?.let { url ->
        val m = Regex("github\\.com/([^/]+)/([^/]+)/(?:issues|pull)").find(url)
        if (m != null) return m.groupValues[1] to m.groupValues[2]
    }
    return null
}

// ──────────────────────────────────────────────────────────────────────────────
// Per-tab filter rows (Users / Code / Issues). Share the same visual language as
// RepoFilterRow: a single LazyRow of FilterChip + optional order toggle + a
// "Custom…" chip for open-ended filters (language, extension).
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Users tab — sort by followers / repositories / joined, plus ASC/DESC toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsersFilterRow(vm: SearchViewModel) {
    val sort by vm.userSort.collectAsState()
    val order by vm.userOrder.collectAsState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                UserSort.BEST_MATCH to R.string.sort_best_match,
                UserSort.FOLLOWERS to R.string.user_sort_followers,
                UserSort.REPOSITORIES to R.string.user_sort_repositories,
                UserSort.JOINED to R.string.user_sort_joined,
            ).forEach { (s, labelRes) ->
                item {
                    FilterChip(
                        selected = sort == s,
                        onClick = { vm.applyUsersFilters(sort = s) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            if (sort != UserSort.BEST_MATCH) {
                item {
                    OrderToggleChip(
                        order = order,
                        onToggle = { vm.applyUsersFilters(order = it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Code tab — language chips (reuses COMMON_LANGUAGES) + extension "Custom…"
 * chip with a dialog where the user can type any extension or language name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CodeFilterRow(vm: SearchViewModel) {
    val language by vm.codeLanguage.collectAsState()
    val extension by vm.codeExtension.collectAsState()
    var showCustom by remember { mutableStateOf(false) }
    var customMode by remember { mutableStateOf(CodeCustomMode.LANGUAGE) }
    var customText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Active filters first (Clear chips).
        if (language.isNotBlank() || extension.isNotBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (language.isNotBlank()) {
                    item {
                        ActiveFilterChip(
                            label = stringResource(R.string.language_label_fmt, language),
                            onClear = { vm.applyCodeFilters(language = "") },
                        )
                    }
                }
                if (extension.isNotBlank()) {
                    item {
                        ActiveFilterChip(
                            label = stringResource(R.string.extension_label_fmt, extension),
                            onClear = { vm.applyCodeFilters(extension = "") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COMMON_LANGUAGES.forEach { lang ->
                item {
                    FilterChip(
                        selected = language.equals(lang, ignoreCase = true),
                        onClick = {
                            val next = if (language.equals(lang, ignoreCase = true)) "" else lang
                            vm.applyCodeFilters(language = next)
                        },
                        label = { Text(lang) },
                    )
                }
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        customMode = CodeCustomMode.LANGUAGE
                        customText = ""
                        showCustom = true
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.language_custom))
                        }
                    },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        customMode = CodeCustomMode.EXTENSION
                        customText = ""
                        showCustom = true
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.extension_custom))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCustom) {
        val titleRes = if (customMode == CodeCustomMode.LANGUAGE) R.string.language_custom_title
            else R.string.extension_custom_title
        val placeholderRes = if (customMode == CodeCustomMode.LANGUAGE) R.string.language_filter_placeholder
            else R.string.extension_filter_placeholder
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text(stringResource(titleRes)) },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(placeholderRes)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Code, null) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = customText.trim()
                    if (trimmed.isNotBlank()) {
                        if (customMode == CodeCustomMode.LANGUAGE) vm.applyCodeFilters(language = trimmed)
                        else vm.applyCodeFilters(extension = trimmed)
                    }
                    showCustom = false
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustom = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal enum class CodeCustomMode { LANGUAGE, EXTENSION }

/**
 * Issues tab — type (all / issue / pr) + state (all / open / closed) + sort
 * (created / updated / comments) + ASC/DESC toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IssuesFilterRow(vm: SearchViewModel) {
    val type by vm.issueType.collectAsState()
    val state by vm.issueState.collectAsState()
    val sort by vm.issueSort.collectAsState()
    val order by vm.issueOrder.collectAsState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                IssueType.ALL to R.string.issue_type_all,
                IssueType.ISSUE to R.string.issue_type_issue,
                IssueType.PR to R.string.issue_type_pr,
            ).forEach { (t, labelRes) ->
                item {
                    FilterChip(
                        selected = type == t,
                        onClick = { vm.applyIssuesFilters(type = t) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                IssueState.ALL to R.string.issue_state_all,
                IssueState.OPEN to R.string.issue_state_open,
                IssueState.CLOSED to R.string.issue_state_closed,
            ).forEach { (s, labelRes) ->
                item {
                    FilterChip(
                        selected = state == s,
                        onClick = { vm.applyIssuesFilters(state = s) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            listOf(
                IssueSort.CREATED to R.string.sort_created,
                IssueSort.UPDATED to R.string.sort_updated,
                IssueSort.COMMENTS to R.string.sort_comments,
            ).forEach { (s, labelRes) ->
                item {
                    FilterChip(
                        selected = sort == s,
                        onClick = { vm.applyIssuesFilters(sort = s) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            item { OrderToggleChip(order = order, onToggle = { vm.applyIssuesFilters(order = it) }) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Reusable ASC/DESC toggle chip shown next to sort filters.
 */
@Composable
internal fun OrderToggleChip(order: SortOrder, onToggle: (SortOrder) -> Unit) {
    FilterChip(
        selected = order == SortOrder.ASC,
        onClick = {
            val next = if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
            onToggle(next)
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (order == SortOrder.ASC) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (order == SortOrder.ASC) stringResource(R.string.order_asc) else stringResource(R.string.order_desc))
            }
        },
    )
}

/**
 * Sort chip with the asc/desc arrow built into the label: the arrow points the
 * active direction while the chip is selected; tapping toggles the direction.
 */
@Composable
internal fun OrderAwareSortChip(
    selected: Boolean,
    label: String,
    order: SortOrder,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                Spacer(Modifier.width(2.dp))
                Icon(
                    if (order == SortOrder.ASC) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = if (order == SortOrder.ASC) stringResource(R.string.order_asc) else stringResource(R.string.order_desc),
                    modifier = Modifier.size(13.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * Pill that shows a currently-active filter with a close icon on the right —
 * clicking it clears the filter.
 */
@Composable
internal fun ActiveFilterChip(label: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
            }
        },
    )
}
