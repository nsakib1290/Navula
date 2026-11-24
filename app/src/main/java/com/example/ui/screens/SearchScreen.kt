package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ProviderExtensionEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.StreamBookmarkEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onLaunchUrlInHome: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onPlayDirectStream: (url: String, title: String) -> Unit,
    onOpenExtensionsInHub: () -> Unit
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    val enabledProviders = remember(providers) {
        providers.filter { it.isEnabled && it.baseUrl.isNotBlank() }
    }
    var selectedProviderId by remember(activeProvider) {
        mutableStateOf(activeProvider?.id)
    }
    val targetProvider = remember(enabledProviders, selectedProviderId, activeProvider) {
        enabledProviders.firstOrNull { it.id == selectedProviderId }
            ?: activeProvider
            ?: enabledProviders.firstOrNull()
    }

    var queryText by remember { mutableStateOf("") }

    fun submitSearch(rawQuery: String) {
        val searchUrl = viewModel.executeSearch(rawQuery, targetProvider)
        if (searchUrl != null) {
            onLaunchUrlInHome(searchUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.search_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Search across ${targetProvider?.name ?: "Navula"} & quick bookmarks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenExtensionsInHub) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Providers")
                    }
                }
            }

            // Provider Selector Chips + 1-Tap Open
            if (enabledProviders.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(enabledProviders, key = { it.id }) { provider ->
                            val isSelected = targetProvider?.id == provider.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedProviderId = provider.id },
                                label = { Text(provider.name) },
                                leadingIcon = if (provider.isActive) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        if (targetProvider != null) {
                            item {
                                TextButton(
                                    onClick = {
                                        viewModel.activateAndOpenProvider(targetProvider)
                                        onNavigateToHome()
                                    },
                                    modifier = Modifier.testTag("search_open_provider_button")
                                ) {
                                    Text("Open ${targetProvider.name}", color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }

            // Search Input Box
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = {
                            Text("Search ${targetProvider?.name ?: "spacedom.live"}...")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingIcon = {
                            if (queryText.isNotEmpty()) {
                                IconButton(onClick = { queryText = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch(queryText) }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_screen_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )

                    Button(
                        onClick = { submitSearch(queryText) },
                        enabled = queryText.isNotBlank(),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("search_submit_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Go")
                    }
                }
            }

            // Search History Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Searches (${searchHistory.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (searchHistory.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAllSearchHistory() },
                            modifier = Modifier.testTag("clear_search_history_button")
                        ) {
                            Text("Clear All", color = RoseAlert)
                        }
                    }
                }
            }

            if (searchHistory.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("No Search History", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Your recent searches across cloud providers will appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(searchHistory, key = { it.id }) { entry ->
                    SearchHistoryRow(
                        entry = entry,
                        onRunAgain = {
                            val matchingProv = providers.firstOrNull { it.id == entry.providerId } ?: targetProvider
                            val url = viewModel.executeSearch(entry.query, matchingProv)
                            if (url != null) onLaunchUrlInHome(url)
                        },
                        onFillInput = { queryText = entry.query },
                        onDelete = { viewModel.deleteSearchHistory(entry.id) }
                    )
                }
            }

            // Quick Bookmarks Section
            if (bookmarks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pinned Bookmarks (${bookmarks.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(bookmarks, key = { "sb_${it.id}" }) { bookmark ->
                    QuickBookmarkRow(
                        bookmark = bookmark,
                        onOpen = {
                            if (bookmark.category == "Direct Stream") {
                                onPlayDirectStream(bookmark.url, bookmark.title)
                            } else {
                                onLaunchUrlInHome(bookmark.url)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(
    entry: SearchHistoryEntity,
    onRunAgain: () -> Unit,
    onFillInput: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = remember(entry.searchedAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(entry.searchedAt))
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_history_${entry.id}"),
        onClick = onRunAgain
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.query,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPillBadge(entry.providerName, MaterialTheme.colorScheme.primary)
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onFillInput) {
                Icon(
                    imageVector = Icons.Rounded.NorthWest,
                    contentDescription = "Fill search query",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickBookmarkRow(
    bookmark: StreamBookmarkEntity,
    onOpen: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookmark.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
