package com.pockethub.ui.repo

// Repo overview tab: stats row + README/readme sections.
// Split out of RepoDetailScreen.kt for readability.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.remember
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pockethub.data.model.Repository
import com.pockethub.ui.markdown.MarkdownText
import com.pockethub.ui.components.pressScale

@Composable
internal fun StatsRow(
    data: Repository,
    onNavigateToUser: (String) -> Unit = {},
    isStarred: Boolean = false,
    isForking: Boolean = false,
    onToggleStar: () -> Unit = {},
    onFork: () -> Unit = {},
) {
    val userClickModifier = Modifier.clickable { onNavigateToUser(data.owner.login) }
    val starInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val forkInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = data.owner.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape).then(userClickModifier),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.stats_by, data.owner.login),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = userClickModifier,
        )
        Spacer(Modifier.weight(1f))
        // Star chip — tappable to toggle star. Filled star when starred.
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .pressScale(interactionSource = starInteraction)
                .clickable(interactionSource = starInteraction, indication = null, onClick = onToggleStar)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isStarred) stringResource(R.string.cd_unstar) else stringResource(R.string.cd_star),
                modifier = Modifier.size(20.dp),
                tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(data.stars.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        // Fork chip — tappable to fork. Shows loading state while forking.
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .pressScale(interactionSource = forkInteraction)
                .clickable(interactionSource = forkInteraction, indication = null, onClick = onFork, enabled = !isForking)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isForking) Icons.Outlined.ForkRight else Icons.Outlined.ForkRight,
                contentDescription = stringResource(R.string.action_fork),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${data.forks} ${stringResource(R.string.stat_forks)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewTab(
    owner: String,
    repo: String,
    repoData: Repository?,
    readme: String?,
    isLoading: Boolean,
    translatedReadme: String? = null,
    showTranslated: Boolean = false,
    isTranslating: Boolean = false,
    translateTarget: String? = null,
    onToggleTranslation: () -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit,
) {
    if (isLoading && repoData == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth(0.85f).height(18.dp))
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth().height(120.dp), cornerRadius = 18.dp)
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth().height(220.dp), cornerRadius = 18.dp)
        }
        return
    }
    repoData?.let { data ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!data.description.isNullOrBlank()) {
                Text(data.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            // Fork source chip — shown only when this repo is itself a fork and
            // the upstream parent slug is available, matching GitHub's
            // "forked from owner/repo" affordance. Tapping navigates into the
            // parent detail screen within the app (not an external browser), so
            // users can keep browsing without losing context.
            if (data.fork && data.parent != null) {
                val p = data.parent
                val parentOwner = p.owner.login
                val parentName = p.name
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onNavigateToRepo(parentOwner, parentName) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.ForkRight,
                            null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.repo_forked_from, "$parentOwner/$parentName"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (!data.homepage.isNullOrBlank()) {
                Text(
                    data.homepage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (data.topics.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.topics.forEach {
                        AssistChip(
                            onClick = { onTopicClick(it) },
                            label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
            HorizontalDivider()
            // README header with optional translation toggle — hidden entirely
            // when there is no README and we're not still loading it, so empty
            // repos don't show a dangling "README" title followed by "unavailable".
            val showReadmeSection = readme != null || isLoading
            if (showReadmeSection) {
                Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.readme_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (readme != null && translateTarget != null) {
                    // Capsule toggle: 原文 / 译文
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        // 原文 button
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (!showTranslated) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = !isTranslating) { if (showTranslated) onToggleTranslation() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.translate_original),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!showTranslated) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 译文 button
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (showTranslated) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = !isTranslating) { if (!showTranslated) onToggleTranslation() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.translate_translated),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showTranslated) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // README content — show translated or original
            val displayReadme = if (showTranslated && translatedReadme != null) translatedReadme else readme
            if (displayReadme != null) {
                MarkdownText(
                    markdown = displayReadme,
                    modifier = Modifier.fillMaxWidth(),
                    repoContext = "$owner/$repo",
                    defaultBranch = repoData?.defaultBranch,
                    onLinkClick = onLinkClick,
                )
            } else if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.readme_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            } // showReadmeSection
            Spacer(Modifier.height(40.dp))
        }
    }
}

