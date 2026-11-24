package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.OfflineVideoEntity
import com.example.data.media.DownloadLiveMetrics
import com.example.data.media.VideoDownloadManager
import com.example.data.media.VideoMetadataHelper
import com.example.ui.components.GlassCard
import com.example.ui.components.MediaUrlActionSheet
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OfflineFilter
import com.example.ui.viewmodel.OfflineSort
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineLibraryScreen(
    viewModel: MainViewModel,
    onPlayOfflineVideo: (OfflineVideoEntity) -> Unit,
    onPlayStreamUrl: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val videos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val liveMetrics by viewModel.liveDownloadMetrics.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    val liveLocalTasks = remember(downloads) {
        downloads.filter { it.status != DownloadTaskEntity.STATUS_COMPLETED }
    }

    var showImportPickerSheet by remember { mutableStateOf(false) }
    var showMediaUrlSheet by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var videoToRename by remember { mutableStateOf<OfflineVideoEntity?>(null) }
    var videoToDelete by remember { mutableStateOf<OfflineVideoEntity?>(null) }

    val videoImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.12)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
    DisposableEffect(videoImageLoader) {
        onDispose {
            videoImageLoader.shutdown()
        }
    }

    // Zero-permission Photo Picker for gallery videos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importVideos(uris)
        }
    }

    // Zero-permission Storage Access Framework picker for MKV/MP4/WebM files in device folders
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importVideos(uris)
        }
    }

    val totalBytes = remember(allVideos) { allVideos.sumOf { it.fileSizeBytes } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val columns = if (maxWidth >= 600.dp) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Banner Header
            item(span = { GridItemSpan(columns) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_spacedom),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPillBadge("OFFLINE PLAYER", MaterialTheme.colorScheme.secondary)
                            Text(
                                text = "${allVideos.size} videos • ${VideoMetadataHelper.formatFileSize(totalBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.offline_library_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Watch downloaded streams & local videos anytime with gesture controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showImportPickerSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("import_video_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_import_video))
                            }

                            OutlinedButton(
                                onClick = { showMediaUrlSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_download_sheet_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_download_url))
                            }
                        }
                    }
                }
            }

            // MovieBox-style Live Local Downloads Queue
            if (liveLocalTasks.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Local Downloads",
                                style = MaterialTheme.typography.titleLarge
                            )
                            val activeCount = liveLocalTasks.count {
                                it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                                    it.status == DownloadTaskEntity.STATUS_QUEUED
                            }
                            StatusPillBadge(
                                text = if (activeCount > 0) "$activeCount ACTIVE" else "${liveLocalTasks.size} QUEUED",
                                accentColor = if (activeCount > 0) MaterialTheme.colorScheme.secondary else AmberGold
                            )
                        }
                        liveLocalTasks.forEach { task ->
                            LiveLocalDownloadCard(
                                task = task,
                                metrics = liveMetrics[task.id],
                                onPause = { viewModel.pauseDownload(task.id) },
                                onResume = { viewModel.resumeDownload(task) },
                                onCancel = { viewModel.cancelDownload(task.id) },
                                onRetry = { viewModel.retryDownload(task) },
                                onRemove = { viewModel.deleteDownloadRecord(task) }
                            )
                        }
                    }
                }
            }

            // Continue Watching Carousel
            if (continueWatching.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.offline_continue_watching),
                            style = MaterialTheme.typography.titleLarge
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(continueWatching, key = { "cw_${it.id}" }) { video ->
                                ContinueWatchingCard(
                                    video = video,
                                    imageLoader = videoImageLoader,
                                    onClick = { onPlayOfflineVideo(video) }
                                )
                            }
                        }
                    }
                }
            }

            // Search + Filter Chips + Sort
            if (allVideos.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("Search offline vault...") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(OfflineFilter.entries) { filter ->
                                    val selected = selectedFilter == filter
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.setFilter(filter) },
                                        label = { Text(filter.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.testTag("offline_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "Sort videos",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    OfflineSort.entries.forEach { sortOption ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Sort: ${sortOption.label}",
                                                    color = if (selectedSort == sortOption) MaterialTheme.colorScheme.secondary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                viewModel.setSort(sortOption)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Empty State or Video Cards
            if (videos.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_offline),
                                contentDescription = stringResource(R.string.cd_empty_offline),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = if (allVideos.isEmpty()) stringResource(R.string.offline_empty_title)
                                else "No Matching Videos",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (allVideos.isEmpty()) stringResource(R.string.offline_empty_subtitle)
                                else "Try clearing your search query or switching filter tabs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (allVideos.isEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Button(
                                    onClick = { showImportPickerSheet = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import Local Video")
                                }
                            }
                        }
                    }
                }
            } else {
                items(videos, key = { it.id }) { video ->
                    OfflineVideoCard(
                        video = video,
                        imageLoader = videoImageLoader,
                        onPlay = { onPlayOfflineVideo(video) },
                        onToggleFavorite = { viewModel.toggleFavorite(video) },
                        onRename = { videoToRename = video },
                        onDelete = { videoToDelete = video }
                    )
                }
            }
        }
    }

    // Zero-Permission Import Source Picker Sheet
    if (showImportPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Import Offline Video",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "No storage permissions required — pick securely via Android system picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showImportPickerSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Photo & Video Gallery", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pick videos directly from your camera roll & albums",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showImportPickerSheet = false
                        documentPickerLauncher.launch(arrayOf("video/*"))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Files & Downloads (MP4, MKV, WebM)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Browse device folders via Storage Access Framework",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMediaUrlSheet) {
        MediaUrlActionSheet(
            onPrefetchUrl = { url -> viewModel.prefetchUrlDirect(url) },
            onDismiss = { showMediaUrlSheet = false },
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

    videoToRename?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameVideo(target.id, newName)
                        videoToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    videoToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Delete Offline Video?") },
            text = {
                Text("Remove \"${target.title}\" from your Offline Vault? Downloaded files stored inside Navula will be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVideo(target)
                        videoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LiveLocalDownloadCard(
    task: DownloadTaskEntity,
    metrics: DownloadLiveMetrics?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    val isRunning = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
        task.status == DownloadTaskEntity.STATUS_QUEUED
    val isPaused = task.status == DownloadTaskEntity.STATUS_PAUSED
    val statusColor = when (task.status) {
        DownloadTaskEntity.STATUS_DOWNLOADING, DownloadTaskEntity.STATUS_QUEUED -> MaterialTheme.colorScheme.secondary
        DownloadTaskEntity.STATUS_PAUSED -> AmberGold
        DownloadTaskEntity.STATUS_FAILED -> RoseAlert
        else -> AmberGold
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_local_download_${task.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "HTTPS Verified",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val metaLine = listOfNotNull(
                        task.formatLabel,
                        task.qualityLabel,
                        task.providerHost?.takeIf { it.isNotBlank() },
                        task.subtitleLanguage?.let { "Sub: $it" }
                    ).joinToString(" • ").ifBlank { task.sourceUrl }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPillBadge(task.status, statusColor)
            }

            if (isRunning || isPaused) {
                Spacer(modifier = Modifier.height(10.dp))
                if (task.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { task.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isPaused) AmberGold else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                } else if (isRunning) {
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
                val infoText = when {
                    isRunning -> buildString {
                        append(VideoMetadataHelper.formatFileSize(task.downloadedBytes))
                        if (task.totalBytes > 0L) {
                            append(" / ${VideoMetadataHelper.formatFileSize(task.totalBytes)}")
                            val pct = metrics?.percent ?: (task.progressFraction * 100).toInt()
                            append(" ($pct%)")
                        }
                        if (metrics != null && metrics.speedBytesPerSec > 0L) {
                            append(" • ${VideoDownloadManager.formatSpeed(metrics.speedBytesPerSec)}")
                        }
                        val eta = VideoDownloadManager.formatEta(metrics?.etaSeconds)
                        if (eta.isNotBlank()) append(" • $eta")
                    }
                    isPaused -> buildString {
                        append("Paused • ")
                        append(VideoMetadataHelper.formatFileSize(task.downloadedBytes))
                        if (task.totalBytes > 0L) {
                            append(" / ${VideoMetadataHelper.formatFileSize(task.totalBytes)}")
                            append(" (${(task.progressFraction * 100).toInt()}%)")
                        }
                    }
                    task.status == DownloadTaskEntity.STATUS_FAILED -> task.errorMessage ?: "Download failed"
                    else -> task.errorMessage ?: "Cancelled"
                }

                Text(
                    text = infoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isRunning -> MaterialTheme.colorScheme.secondary
                        isPaused -> AmberGold
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.testTag("pause_download_${task.id}")
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Pause download", tint = AmberGold)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel download", tint = RoseAlert)
                        }
                    } else if (isPaused) {
                        TextButton(
                            onClick = onResume,
                            modifier = Modifier.testTag("resume_download_${task.id}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldSuccess)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", color = EmeraldSuccess)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel download", tint = RoseAlert)
                        }
                    } else {
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                        IconButton(onClick = onRemove) {
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
private fun ContinueWatchingCard(
    video: OfflineVideoEntity,
    imageLoader: ImageLoader,
    onClick: () -> Unit
) {
    val progress = if (video.durationMs > 0L) {
        (video.lastPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    GlassCard(
        modifier = Modifier
            .width(210.dp)
            .testTag("continue_watching_${video.id}"),
        onClick = onClick
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black.copy(alpha = 0.85f))
            ) {
                VideoThumbnail(
                    thumbnailPath = video.thumbnailPath,
                    videoUri = video.uriString,
                    title = video.title,
                    imageLoader = imageLoader
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = VideoMetadataHelper.formatDuration(video.lastPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Resume at ${VideoMetadataHelper.formatDuration(video.lastPositionMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OfflineVideoCard(
    video: OfflineVideoEntity,
    imageLoader: ImageLoader,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_video_card_${video.id}"),
        onClick = onPlay
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black.copy(alpha = 0.85f))
            ) {
                VideoThumbnail(
                    thumbnailPath = video.thumbnailPath,
                    videoUri = video.uriString,
                    title = video.title,
                    imageLoader = imageLoader
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                StatusPillBadge(
                    text = if (video.sourceType == "DOWNLOADED") "OFFLINE" else "IMPORTED",
                    accentColor = if (video.sourceType == "DOWNLOADED") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (video.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.82f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = VideoMetadataHelper.formatDuration(video.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                if (video.lastPositionMs > 0L && video.durationMs > 0L) {
                    LinearProgressIndicator(
                        progress = { (video.lastPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Transparent
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = VideoMetadataHelper.formatFileSize(video.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (video.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (video.isFavorite) RoseAlert else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Video options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = RoseAlert) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = RoseAlert) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    thumbnailPath: String?,
    videoUri: String,
    title: String,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    val thumbFile = remember(thumbnailPath) {
        thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
    }

    if (thumbFile != null) {
        AsyncImage(
            model = thumbFile,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(42.dp)
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(videoUri))
                    .build(),
                imageLoader = imageLoader,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
