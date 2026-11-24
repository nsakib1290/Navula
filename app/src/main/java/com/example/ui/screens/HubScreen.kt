package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.ProviderExtensionEntity
import com.example.data.local.SpacedomDatabase
import com.example.data.local.StreamBookmarkEntity
import com.example.data.media.DownloadLiveMetrics
import com.example.data.media.VideoDownloadManager
import com.example.data.media.VideoMetadataHelper
import com.example.data.provider.ProviderHealthResult
import com.example.data.provider.ProviderManifestParser
import com.example.security.UrlSecurityValidator
import com.example.ui.components.GlassCard
import com.example.ui.components.MediaUrlActionSheet
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HubScreen(
    viewModel: MainViewModel,
    onOpenUrlInHome: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayDownloadedVideo: (videoId: String) -> Unit,
    onPlayStreamUrl: (url: String, title: String) -> Unit
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val liveMetrics by viewModel.liveDownloadMetrics.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val providerHealthMap by viewModel.providerHealthMap.collectAsStateWithLifecycle()
    val testingProviderIds by viewModel.testingProviderIds.collectAsStateWithLifecycle()
    val isInstallingProvider by viewModel.isInstallingProvider.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val isCurrentBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()

    var showMediaSheet by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showInstallRepoDialog by remember { mutableStateOf(false) }
    var showAddCustomProviderDialog by remember { mutableStateOf(false) }
    var providerToEdit by remember { mutableStateOf<ProviderExtensionEntity?>(null) }
    var extSearchQuery by remember { mutableStateOf("") }
    var selectedExtFilter by remember { mutableStateOf("All") }

    val filteredProviders = remember(providers, extSearchQuery, selectedExtFilter) {
        val query = extSearchQuery.trim()
        providers.filter { provider ->
            val manifest = provider.manifestUrl.orEmpty()
            val matchesFilter = when (selectedExtFilter) {
                "Movie" -> provider.category.equals("Movie", ignoreCase = true)
                "Live TV & Sports" -> provider.category.equals("Live TV & Sports", ignoreCase = true) ||
                    provider.category.contains("live", ignoreCase = true) ||
                    provider.category.contains("sports", ignoreCase = true)
                "Enabled" -> provider.isEnabled
                "Vega" -> manifest.contains("vega", ignoreCase = true) ||
                    provider.category.equals("Vega", ignoreCase = true) ||
                    provider.category.contains("vega", ignoreCase = true)
                "CloudStream" -> provider.category.contains("cloudstream", ignoreCase = true) ||
                    manifest.contains("cloudstream", ignoreCase = true)
                "Stremio" -> provider.category.contains("stremio", ignoreCase = true) ||
                    manifest.contains("stremio", ignoreCase = true)
                "Aniyomi" -> provider.category.contains("aniyomi", ignoreCase = true) ||
                    provider.category.contains("tachiyomi", ignoreCase = true) ||
                    manifest.contains("index.min.json", ignoreCase = true)
                "MegaRepo" -> provider.category.contains("mega", ignoreCase = true)
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                provider.name.contains(query, ignoreCase = true) ||
                provider.baseUrl.contains(query, ignoreCase = true) ||
                provider.category.contains(query, ignoreCase = true) ||
                provider.description.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hub Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hub_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Cloud provider extensions, bookmarks & downloads",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("hub_open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        OutlinedButton(
                            onClick = { showMediaSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("hub_new_media_button")
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Media URL")
                        }
                    }
                }
            }

            // Active Stream Controls (accessible while Home floating buttons stay hidden)
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_active_stream_controls")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = streamState.pageTitle.ifBlank { "Navula Stream" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = streamState.currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (streamState.detectedStreams.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPillBadge(
                                    text = "${streamState.detectedStreams.size} STREAMS",
                                    accentColor = EmeraldSuccess
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.toggleCurrentPageBookmark() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hub_bookmark_current_button")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isCurrentBookmarked) "Saved" else "Save", maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    viewModel.requestOpenDownloadPluginInHome()
                                    onNavigateToHome()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .testTag("hub_open_stream_plugin_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.requestCinemaFullscreenOnHome()
                                    onNavigateToHome()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("hub_cinema_fullscreen_button")
                            ) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Full", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Cloud Provider Extensions Section (Vega-style Extension Manager)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.hub_extensions_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPillBadge("${providers.size} INSTALLED", MaterialTheme.colorScheme.primary)
                            IconButton(
                                onClick = { viewModel.refreshAllInstalledRepos() },
                                enabled = !isInstallingProvider,
                                modifier = Modifier.testTag("hub_refresh_repos_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = "Refresh installed repositories",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showInstallRepoDialog = true },
                            enabled = !isInstallingProvider,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("install_manifest_repo_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isInstallingProvider) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Rounded.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install Repo")
                        }

                        OutlinedButton(
                            onClick = { showAddCustomProviderDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_custom_provider_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Custom Site")
                        }
                    }

                    AnimatedVisibility(visible = providers.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = extSearchQuery,
                                onValueChange = { extSearchQuery = it },
                                placeholder = { Text("Filter extensions by name, URL, or type...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (extSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { extSearchQuery = "" }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear filter")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hub_ext_search_input")
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val filters = listOf("All", "Movie", "Live TV & Sports", "Enabled", "Vega", "CloudStream", "Stremio", "Aniyomi", "MegaRepo")
                                items(filters) { filter ->
                                    FilterChip(
                                        selected = selectedExtFilter == filter,
                                        onClick = { selectedExtFilter = filter },
                                        label = { Text(filter) },
                                        modifier = Modifier.testTag("hub_ext_filter_${filter.lowercase()}"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filteredProviders.isEmpty() && providers.isNotEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No extensions match your filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    extSearchQuery = ""
                                    selectedExtFilter = "All"
                                }
                            ) {
                                Text("Show All")
                            }
                        }
                    }
                }
            } else {
                items(filteredProviders, key = { "prov_${it.id}" }) { provider ->
                    Box(modifier = Modifier.animateItem()) {
                        ProviderExtensionCard(
                            provider = provider,
                            health = providerHealthMap[provider.id],
                            isTesting = testingProviderIds.contains(provider.id),
                            onActivate = {
                                if (provider.baseUrl.isBlank()) {
                                    providerToEdit = provider
                                } else {
                                    viewModel.activateAndOpenProvider(provider)
                                    onNavigateToHome()
                                }
                            },
                            onTest = { viewModel.testProviderConnection(provider) },
                            onToggleEnabled = { enabled -> viewModel.setProviderEnabled(provider, enabled) },
                            onEdit = { providerToEdit = provider },
                            onDelete = { viewModel.deleteProvider(provider) }
                        )
                    }
                }
            }

            // Bookmarks Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.hub_bookmarks_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(
                        onClick = { showAddBookmarkDialog = true },
                        modifier = Modifier.testTag("add_bookmark_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Bookmark")
                    }
                }
            }

            items(bookmarks, key = { it.id }) { bookmark ->
                Box(modifier = Modifier.animateItem()) {
                    BookmarkCard(
                        bookmark = bookmark,
                        onOpen = {
                            if (bookmark.category == "Direct Stream") {
                                onPlayStreamUrl(bookmark.url, bookmark.title)
                            } else {
                                onOpenUrlInHome(bookmark.url)
                            }
                        },
                        onDelete = { viewModel.removeBookmark(bookmark.id) }
                    )
                }
            }

            // Downloads Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.hub_downloads_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    StatusPillBadge(
                        text = "${downloads.count { it.status == DownloadTaskEntity.STATUS_DOWNLOADING }} ACTIVE",
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (downloads.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No Downloads Yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Use Download in Stream Controls above, or Media URL, to save videos offline via WorkManager.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(downloads, key = { it.id }) { task ->
                    Box(modifier = Modifier.animateItem()) {
                        DownloadTaskCard(
                            task = task,
                            metrics = liveMetrics[task.id],
                            onPause = { viewModel.pauseDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task) },
                            onCancel = { viewModel.cancelDownload(task.id) },
                            onRetry = { viewModel.retryDownload(task) },
                            onPlay = { onPlayDownloadedVideo(task.id) },
                            onDeleteRecord = { viewModel.deleteDownloadRecord(task) }
                        )
                    }
                }
            }

            // Shield & Settings now live on the dedicated Settings tab
        }
    }

    if (showMediaSheet) {
        MediaUrlActionSheet(
            onPrefetchUrl = { url -> viewModel.prefetchUrlDirect(url) },
            onDismiss = { showMediaSheet = false },
            onStreamNow = { url, title -> onPlayStreamUrl(url, title) },
            onDownloadOffline = { url, title -> viewModel.enqueueDownload(url, title) },
            onDownloadOfflineWithOptions = { url, title, variantUrl, formatLabel, qualityLabel, audioLabel, subUrl, subLang ->
                viewModel.enqueueDownload(
                    url = url,
                    title = title,
                    preferredVariantUrl = variantUrl,
                    formatLabel = formatLabel,
                    qualityLabel = qualityLabel,
                    audioLabel = audioLabel,
                    subtitleUrl = subUrl,
                    subtitleLanguage = subLang
                )
            }
        )
    }

    if (showInstallRepoDialog) {
        var repoInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInstallRepoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Install Any Repo / Extension") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Universal HTTPS installer: supports Vega (manifest.json / .git), CloudStream (repo.json / plugins.json), MegaRepo (pluginLists), Stremio (stremio:// / manifest.json), Aniyomi (index.min.json), or any GitHub/GitLab/Codeberg owner/repo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("Repo URL, .git, owner/repo, or manifest.json") },
                        placeholder = { Text("https://github.com/vega-org/vega-app.git") },
                        singleLine = true,
                        modifier = Modifier.testTag("manifest_repo_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            val vegaAppGit = "https://github.com/vega-org/vega-app.git"
                            FilterChip(
                                selected = repoInput == vegaAppGit,
                                onClick = { repoInput = vegaAppGit },
                                label = { Text("Vega App Repo") },
                                modifier = Modifier.testTag("repo_preset_vega_app_git"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = repoInput == ProviderManifestParser.RECOMMENDED_VEGA_ORG_TEMPLATE,
                                onClick = { repoInput = ProviderManifestParser.RECOMMENDED_VEGA_ORG_TEMPLATE },
                                label = { Text("Vega Org: ${ProviderManifestParser.RECOMMENDED_VEGA_ORG_TEMPLATE}") },
                                modifier = Modifier.testTag("repo_preset_vega_org_template"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = repoInput == ProviderManifestParser.RECOMMENDED_VEGA_REPO,
                                onClick = { repoInput = ProviderManifestParser.RECOMMENDED_VEGA_REPO },
                                label = { Text("Vega: ${ProviderManifestParser.RECOMMENDED_VEGA_REPO}") },
                                modifier = Modifier.testTag("repo_preset_vega_repo"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = repoInput == ProviderManifestParser.RECOMMENDED_CLOUDSTREAM_HINT,
                                onClick = { repoInput = ProviderManifestParser.RECOMMENDED_CLOUDSTREAM_HINT },
                                label = { Text("CloudStream: ${ProviderManifestParser.RECOMMENDED_CLOUDSTREAM_HINT}") },
                                modifier = Modifier.testTag("repo_preset_cloudstream"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.installProviderManifest(repoInput)
                        showInstallRepoDialog = false
                    },
                    enabled = repoInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddCustomProviderDialog) {
        var name by remember { mutableStateOf("") }
        var baseUrl by remember { mutableStateOf("https://") }
        var searchTemplate by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCustomProviderDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Add Cloud Provider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Provider Name") },
                        placeholder = { Text("e.g., Cinema Cloud") },
                        singleLine = true,
                        modifier = Modifier.testTag("custom_provider_name_input")
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://example.com/") },
                        singleLine = true,
                        modifier = Modifier.testTag("custom_provider_url_input")
                    )
                    OutlinedTextField(
                        value = searchTemplate,
                        onValueChange = { searchTemplate = it },
                        label = { Text("Search URL Pattern (optional)") },
                        placeholder = { Text("https://example.com/?s={query}") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomProvider(name, baseUrl, searchTemplate)
                        showAddCustomProviderDialog = false
                    },
                    enabled = baseUrl.length > 8,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add & Use")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomProviderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    providerToEdit?.let { target ->
        var editName by remember(target.id) { mutableStateOf(target.name) }
        var editBaseUrl by remember(target.id) { mutableStateOf(target.baseUrl) }
        var editSearchUrl by remember(target.id) { mutableStateOf(target.searchUrlTemplate) }
        val isBuiltIn = target.id == SpacedomDatabase.DEFAULT_PROVIDER_ID
        AlertDialog(
            onDismissRequest = { providerToEdit = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Configure ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Provider Name") },
                        placeholder = { Text("Spacedom Live") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editBaseUrl,
                        onValueChange = { editBaseUrl = it },
                        label = { Text("Provider Base URL") },
                        placeholder = { Text("https://site.com/") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editSearchUrl,
                        onValueChange = { editSearchUrl = it },
                        label = { Text("Search URL Template") },
                        placeholder = { Text("https://site.com/?s={query}") },
                        singleLine = true
                    )
                    if (isBuiltIn) {
                        TextButton(
                            onClick = {
                                viewModel.resetDefaultProvider(openInHome = true)
                                providerToEdit = null
                                onNavigateToHome()
                            }
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset to spacedom.live")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProviderUrl(
                            provider = target,
                            newBaseUrl = editBaseUrl,
                            newSearchTemplate = editSearchUrl,
                            newName = editName,
                            activateAndOpen = true
                        )
                        providerToEdit = null
                        onNavigateToHome()
                    },
                    enabled = editBaseUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddBookmarkDialog) {
        var title by remember { mutableStateOf("") }
        var url by remember { mutableStateOf(SpacedomDatabase.DEFAULT_STARTUP_URL) }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Save Navula Bookmark") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Label") },
                        placeholder = { Text("e.g., Trending Sci-Fi") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val safe = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull()
                        if (safe != null) {
                            val isDirect = safe.endsWith(".mp4", true) || safe.endsWith(".m3u8", true)
                            viewModel.addBookmark(
                                url = safe,
                                title = title.trim().ifBlank { safe },
                                category = if (isDirect) "Direct Stream" else "Page"
                            )
                            showAddBookmarkDialog = false
                        } else {
                            viewModel.emitMessage("Enter a valid public HTTPS URL")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProviderExtensionCard(
    provider: ProviderExtensionEntity,
    health: ProviderHealthResult?,
    isTesting: Boolean,
    onActivate: () -> Unit,
    onTest: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val needsMirror = provider.baseUrl.isBlank()

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}"),
        onClick = onActivate
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (provider.isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (provider.isActive) Icons.Rounded.CheckCircle else Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = if (provider.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            StatusPillBadge("v${provider.version}", MaterialTheme.colorScheme.primary)
                            if (provider.isActive) {
                                StatusPillBadge("ACTIVE", MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Text(
                            text = if (needsMirror) "Tap to configure HTTPS mirror URL" else provider.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (needsMirror) AmberGold else MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (provider.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPillBadge(provider.category.uppercase(), MaterialTheme.colorScheme.onSurfaceVariant)
                    if (health != null) {
                        StatusPillBadge(
                            text = health.message,
                            accentColor = if (health.isReachable) EmeraldSuccess else RoseAlert
                        )
                    } else if (needsMirror) {
                        StatusPillBadge("MIRROR NEEDED", AmberGold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!needsMirror) {
                        IconButton(onClick = onTest, enabled = !isTesting) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = "Test provider connection",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    TextButton(onClick = onActivate) {
                        Text(
                            text = when {
                                needsMirror -> "Set URL"
                                provider.isActive -> "Open"
                                else -> "Open & Use"
                            },
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Configure provider",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (provider.id != SpacedomDatabase.DEFAULT_PROVIDER_ID) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = RoseAlert
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    metrics: DownloadLiveMetrics?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onDeleteRecord: () -> Unit
) {
    val statusColor = when (task.status) {
        DownloadTaskEntity.STATUS_COMPLETED -> EmeraldSuccess
        DownloadTaskEntity.STATUS_DOWNLOADING, DownloadTaskEntity.STATUS_QUEUED -> MaterialTheme.colorScheme.secondary
        DownloadTaskEntity.STATUS_PAUSED -> AmberGold
        DownloadTaskEntity.STATUS_FAILED -> RoseAlert
        else -> AmberGold
    }
    val metaSummary = listOfNotNull(
        task.formatLabel ?: task.fileExtension?.uppercase(),
        task.qualityLabel,
        task.audioLabel,
        task.subtitleLanguage?.let { "Sub: $it" },
        task.providerHost
    ).filter { it.isNotBlank() }.joinToString(" • ")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metaSummary.ifBlank { task.sourceUrl },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPillBadge(task.status, statusColor)
            }

            if (
                task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                task.status == DownloadTaskEntity.STATUS_QUEUED ||
                task.status == DownloadTaskEntity.STATUS_PAUSED
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                if (task.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { task.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (task.status == DownloadTaskEntity.STATUS_PAUSED) AmberGold else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                } else if (task.status != DownloadTaskEntity.STATUS_PAUSED) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val pct = (task.progressFraction * 100f).toInt().coerceIn(0, 100)
                val infoText = when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING -> buildString {
                        val dl = VideoMetadataHelper.formatFileSize(task.downloadedBytes)
                        append(dl)
                        if (task.totalBytes > 0L) {
                            append(" / ${VideoMetadataHelper.formatFileSize(task.totalBytes)} ($pct%)")
                        }
                        if (metrics != null && metrics.speedBytesPerSec > 0L) {
                            append(" • ${VideoDownloadManager.formatSpeed(metrics.speedBytesPerSec)}")
                        }
                        val eta = VideoDownloadManager.formatEta(metrics?.etaSeconds)
                        if (eta.isNotBlank()) append(" • $eta")
                    }
                    DownloadTaskEntity.STATUS_PAUSED -> buildString {
                        append("Paused")
                        if (task.downloadedBytes > 0L) {
                            append(" • ${VideoMetadataHelper.formatFileSize(task.downloadedBytes)}")
                            if (task.totalBytes > 0L) {
                                append(" / ${VideoMetadataHelper.formatFileSize(task.totalBytes)} ($pct%)")
                            }
                        }
                    }
                    DownloadTaskEntity.STATUS_COMPLETED -> VideoMetadataHelper.formatFileSize(task.totalBytes)
                    DownloadTaskEntity.STATUS_FAILED -> task.errorMessage ?: "Download failed"
                    else -> task.errorMessage ?: "Stopped"
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == DownloadTaskEntity.STATUS_DOWNLOADING || task.status == DownloadTaskEntity.STATUS_QUEUED) {
                        if (task.supportsPauseResume && task.status == DownloadTaskEntity.STATUS_DOWNLOADING) {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Rounded.Pause, contentDescription = "Pause download", tint = AmberGold)
                            }
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel download", tint = RoseAlert)
                        }
                    } else if (task.status == DownloadTaskEntity.STATUS_PAUSED) {
                        TextButton(onClick = onResume) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel download", tint = RoseAlert)
                        }
                    } else {
                        if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
                            TextButton(onClick = onPlay) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play")
                            }
                        } else if (task.status == DownloadTaskEntity.STATUS_FAILED || task.status == DownloadTaskEntity.STATUS_CANCELLED) {
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry")
                            }
                        }
                        IconButton(onClick = onDeleteRecord) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: StreamBookmarkEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
            if (bookmark.id != "default_spacedom_home") {
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
}
