package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.text.style.TextOverflow
import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.launch
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OutlineSubtle

@Composable
fun StatusPillBadge(
    text: String,
    accentColor: Color = ElectricViolet,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val cardTop = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardBottom = MaterialTheme.colorScheme.surfaceContainer
    val borderColor = MaterialTheme.colorScheme.outline
    val baseModifier = modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    cardTop.copy(alpha = 0.96f),
                    cardBottom.copy(alpha = 0.98f)
                )
            )
        )
        .border(1.dp, borderColor, shape)
        .let { mod ->
            if (onClick != null) mod.clickable(onClick = onClick) else mod
        }

    Box(modifier = baseModifier) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaUrlActionSheet(
    initialUrl: String = "",
    initialTitle: String = "",
    onPrefetchUrl: (suspend (String) -> Result<com.example.data.media.PrefetchStreamInfo>)? = null,
    onDismiss: () -> Unit,
    onStreamNow: (url: String, title: String) -> Unit,
    onDownloadOffline: (url: String, title: String) -> Unit,
    onDownloadOfflineWithOptions: ((
        url: String,
        title: String,
        preferredVariantUrl: String?,
        formatLabel: String?,
        qualityLabel: String?,
        audioLabel: String?,
        subtitleUrl: String?,
        subtitleLanguage: String?
    ) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf(initialTitle) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isPrefetching by remember { mutableStateOf(false) }
    var prefetchResult by remember { mutableStateOf<com.example.data.media.PrefetchStreamInfo?>(null) }
    var selectedVariantUrl by remember { mutableStateOf<String?>(null) }
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Direct Media Link",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Inspect formats, resolutions, audio & subtitles before saving",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    errorText = null
                    prefetchResult = null
                    selectedVariantUrl = null
                    selectedSubtitleUrl = null
                },
                label = { Text("Direct Video / Audio / Stream URL") },
                placeholder = { Text("https://.../video.mp4, .mp3 or .m3u8") },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("media_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.secondary
                )
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("e.g., Space Documentary 4K") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("media_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.secondary
                )
            )

            if (onPrefetchUrl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (prefetchResult != null) {
                        val info = prefetchResult!!
                        val detail = buildString {
                            if (info.isDrmProtected) {
                                append("DRM Protected")
                            } else {
                                append("Verified ${info.fileExtension.uppercase()}")
                                if (info.finalVerifiedHost.isNotBlank()) append(" (${info.finalVerifiedHost})")
                                info.qualityLabel?.let { append(" • $it") }
                                if (info.contentLengthBytes > 0L) {
                                    append(" • ${com.example.data.media.VideoMetadataHelper.formatFileSize(info.contentLengthBytes)}")
                                } else if (info.segmentCount > 0) {
                                    append(" • ${info.segmentCount} segs")
                                }
                            }
                        }
                        StatusPillBadge(
                            text = detail,
                            accentColor = if (info.isDrmProtected) RoseAlert else EmeraldSuccess
                        )
                    } else {
                        Text(
                            text = if (isPrefetching) "Probing TLS & stream headers..." else "Verify secure source & formats before saving",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val check = UrlSecurityValidator.validatePublicHttpsUrl(url)
                            if (check.isFailure) {
                                errorText = check.exceptionOrNull()?.localizedMessage ?: "Enter a valid HTTPS URL"
                            } else {
                                coroutineScope.launch {
                                    isPrefetching = true
                                    errorText = null
                                    val res = onPrefetchUrl(check.getOrThrow())
                                    isPrefetching = false
                                    res.onSuccess { prefetchResult = it }
                                        .onFailure { errorText = it.localizedMessage ?: "Prefetch blocked or unreachable" }
                                }
                            }
                        },
                        enabled = !isPrefetching && url.isNotBlank(),
                        modifier = Modifier.testTag("media_prefetch_button")
                    ) {
                        Text(if (isPrefetching) "Checking..." else "Inspect")
                    }
                }
            }

            prefetchResult?.let { info ->
                if (info.isDrmProtected) {
                    Text(
                        text = info.drmReason ?: "DRM-protected streams cannot be downloaded offline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert
                    )
                } else {
                    if (info.variants.isNotEmpty()) {
                        Text(
                            text = "Video Options",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedVariantUrl == null,
                                    onClick = { selectedVariantUrl = null },
                                    label = { Text("Auto Best", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                            items(info.variants, key = { it.url }) { variant ->
                                val estText = variant.estimatedSizeBytes?.takeIf { it > 0L }?.let {
                                    " • ~${com.example.data.media.VideoMetadataHelper.formatFileSize(it)}"
                                }.orEmpty()
                                FilterChip(
                                    selected = selectedVariantUrl == variant.url,
                                    onClick = { selectedVariantUrl = variant.url },
                                    label = {
                                        Text(
                                            text = "${info.fileExtension.uppercase()} – ${variant.resolutionLabel ?: variant.label}$estText",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                    if (info.audioTracks.isNotEmpty()) {
                        Text(
                            text = "Audio: " + info.audioTracks.joinToString(", ") {
                                listOfNotNull(it.language ?: it.label, it.codecs).joinToString(" ")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (info.subtitleTracks.isNotEmpty()) {
                        Text(
                            text = "Subtitles",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSubtitleUrl == null,
                                    onClick = { selectedSubtitleUrl = null },
                                    label = { Text("None", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            items(info.subtitleTracks, key = { it.url }) { sub ->
                                FilterChip(
                                    selected = selectedSubtitleUrl == sub.url,
                                    onClick = { selectedSubtitleUrl = sub.url },
                                    label = {
                                        Text("${sub.language ?: sub.label} – ${sub.format}", style = MaterialTheme.typography.labelSmall)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val check = UrlSecurityValidator.validatePublicHttpsUrl(url)
                        if (check.isFailure) {
                            errorText = check.exceptionOrNull()?.localizedMessage ?: "Enter a valid HTTPS URL"
                        } else {
                            onStreamNow(selectedVariantUrl ?: check.getOrThrow(), title.trim().ifBlank { "Navula Stream" })
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("stream_url_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Now")
                }

                Button(
                    onClick = {
                        val check = UrlSecurityValidator.validatePublicHttpsUrl(url)
                        if (check.isFailure) {
                            errorText = check.exceptionOrNull()?.localizedMessage ?: "Enter a valid HTTPS URL"
                        } else {
                            val safe = check.getOrThrow()
                            if (safe.contains(".mpd", ignoreCase = true)) {
                                errorText = "DASH (.mpd) can be streamed with Play Now; MP4/MP3/MKV/WebM and HLS (.m3u8) can be saved offline."
                            } else if (onDownloadOfflineWithOptions != null) {
                                val info = prefetchResult
                                val variantObj = info?.variants?.firstOrNull { it.url == selectedVariantUrl }
                                val subObj = info?.subtitleTracks?.firstOrNull { it.url == selectedSubtitleUrl }
                                onDownloadOfflineWithOptions(
                                    safe,
                                    title.trim(),
                                    selectedVariantUrl,
                                    info?.mediaKind,
                                    variantObj?.resolutionLabel ?: info?.qualityLabel,
                                    listOfNotNull(info?.audioLanguage, info?.audioCodec).joinToString(" • ").ifBlank { null },
                                    subObj?.url,
                                    subObj?.language ?: subObj?.label
                                )
                                onDismiss()
                            } else {
                                onDownloadOffline(safe, title.trim())
                                onDismiss()
                            }
                        }
                    },
                    enabled = prefetchResult?.isDrmProtected != true,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("download_url_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (prefetchResult?.isDrmProtected == true) "DRM Locked" else "Save Offline")
                }
            }
        }
    }
}
