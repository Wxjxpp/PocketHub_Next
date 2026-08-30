package com.pockethub.ui.history

import com.pockethub.R

import com.pockethub.ui.components.PhAsyncImage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToRepo: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    val history by vm.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_history), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { vm.clear() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            com.pockethub.ui.components.EmptyStateV2(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(history, key = { "${it.owner}/${it.repo}@${it.visitedAt}" }) { entry ->
                    SwipeDismissHistoryItem(
                        onDelete = { vm.remove(entry.owner, entry.repo) },
                        modifier = Modifier.animateItem(),
                    ) {
                    com.pockethub.ui.components.PhCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToRepo(entry.owner, entry.repo) },
                        cornerRadius = 18.dp,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Header: avatar + owner — mirrors RepositoryRow on the repos tab.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PhAsyncImage(
                                    model = "https://github.com/${entry.owner}.png?size=80",
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.owner,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = entry.repo,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.History, null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    formatAgo(entry.visitedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Swipe left → a red delete affordance slides out on the right; tapping it
 * removes the entry. Classic reveal pattern: the swipe stays open (no
 * auto-delete) so the tap is always deliberate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDismissHistoryItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            // Keep the row open past the threshold so the delete button is
            // tappable; actual deletion happens on the button tap.
            v == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
        },
        positionalThreshold = { width -> width * 0.45f },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFD1242F))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        },
    ) {
        content()
    }
}

: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
