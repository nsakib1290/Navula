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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AppThemeMode
import com.example.data.local.SpacedomDatabase
import com.example.security.ContentBlockerEngine
import com.example.security.FilterCategory
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SETTINGS_SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit,
    onOpenCloudExtensions: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val shieldMetrics by viewModel.shieldMetrics.collectAsStateWithLifecycle()
    val filterStatuses by viewModel.filterSubscriptionStatuses.collectAsStateWithLifecycle()
    val isSyncingFilters by viewModel.isSyncingFilters.collectAsStateWithLifecycle()

    val builtInProvider = remember(providers) {
        providers.firstOrNull { it.id == SpacedomDatabase.DEFAULT_PROVIDER_ID }
            ?: SpacedomDatabase.defaultSpacedomProvider()
    }

    var builtInName by remember(builtInProvider.id) { mutableStateOf(builtInProvider.name) }
    var builtInUrl by remember(builtInProvider.id) { mutableStateOf(builtInProvider.baseUrl) }
    var builtInSearchTemplate by remember(builtInProvider.id) { mutableStateOf(builtInProvider.searchUrlTemplate) }

    LaunchedEffect(builtInProvider.name, builtInProvider.baseUrl, builtInProvider.searchUrlTemplate) {
        builtInName = builtInProvider.name
        builtInUrl = builtInProvider.baseUrl
        builtInSearchTemplate = builtInProvider.searchUrlTemplate
    }

    var showCustomRulesDialog by remember { mutableStateOf(false) }

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
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Theme, built-in provider, redirect shield & player preferences",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenCloudExtensions,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("settings_open_extensions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extensions")
                    }
                }
            }

            // Appearance & Theme Section (Smooth Dark / Light / System)
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Brightness4,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App Theme", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Smoothly transition between Dark, Light, or System theme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themeOptions = listOf(
                                Triple(AppThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                                Triple(AppThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                                Triple(AppThemeMode.SYSTEM, "System", Icons.Rounded.Brightness4)
                            )
                            themeOptions.forEach { (mode, label, icon) ->
                                val selected = preferences.themeMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_mode_${mode.name.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Built-in Spacedom Provider Customization
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Built-in Spacedom Site",
                        style = MaterialTheme.typography.titleLarge
                    )
                    StatusPillBadge(
                        text = if (builtInProvider.isActive) "ACTIVE" else "BUILT-IN",
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Change Built-in Provider URL", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Customize the default streaming domain, mirror, or search route",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = builtInName,
                            onValueChange = { builtInName = it },
                            label = { Text("Provider Name") },
                            placeholder = { Text("Spacedom Live") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("builtin_provider_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        OutlinedTextField(
                            value = builtInUrl,
                            onValueChange = { builtInUrl = it },
                            label = { Text("Built-in Site URL (HTTPS)") },
                            placeholder = { Text("https://spacedom.live/") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("builtin_provider_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        OutlinedTextField(
                            value = builtInSearchTemplate,
                            onValueChange = { builtInSearchTemplate = it },
                            label = { Text("Search Template (optional)") },
                            placeholder = { Text("https://spacedom.live/?s={query}") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("builtin_provider_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetDefaultProvider(openInHome = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("reset_builtin_provider_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Default")
                            }

                            Button(
                                onClick = {
                                    viewModel.updateProviderUrl(
                                        provider = builtInProvider,
                                        newBaseUrl = builtInUrl,
                                        newSearchTemplate = builtInSearchTemplate,
                                        newName = builtInName,
                                        activateAndOpen = true
                                    )
                                    onNavigateToHome()
                                },
                                enabled = builtInUrl.isNotBlank(),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(46.dp)
                                    .testTag("save_builtin_provider_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Open")
                            }
                        }
                    }
                }
            }

            // Streaming Redirect & Security Shield Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Streaming Shield & Redirects",
                        style = MaterialTheme.typography.titleLarge
                    )
                    StatusPillBadge(
                        text = "${shieldMetrics.activeRuleCount} RULES",
                        accentColor = if (preferences.adGuardEnabled) EmeraldSuccess else AmberGold
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Rounded.PhonelinkLock,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Block Streaming Redirect Pages",
                            subtitle = "Prevent external redirect pages from replacing your stream",
                            checked = preferences.blockRedirectsEnabled,
                            onCheckedChange = viewModel::setBlockRedirectsEnabled,
                            testTag = "setting_block_redirects"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        SettingsToggleRow(
                            icon = Icons.Rounded.Shield,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Ad Blocking",
                            subtitle = "Built-in compiled AdGuard, EasyList & uBlock network/cosmetic engine",
                            checked = preferences.adGuardEnabled,
                            onCheckedChange = viewModel::setAdGuardEnabled,
                            testTag = "setting_ad_guard"
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        val filterUpdateLabel = remember(shieldMetrics.lastSyncedMs, shieldMetrics.cacheLoadedFast, isSyncingFilters) {
                            when {
                                isSyncingFilters -> "Updating filter lists..."
                                shieldMetrics.lastSyncedMs > 0L -> {
                                    val formatted = SimpleDateFormat("MMM d, HH:mm", Locale.US)
                                        .format(Date(shieldMetrics.lastSyncedMs))
                                    "Updated $formatted" + if (shieldMetrics.cacheLoadedFast) " • Compiled cache" else ""
                                }
                                shieldMetrics.cacheLoadedFast -> "Compiled disk cache active"
                                else -> "Built-in rules active • Ready to sync"
                            }
                        }
                        Text(
                            text = "Blocked Requests: ${shieldMetrics.totalBlocked}  •  $filterUpdateLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("shield_blocked_summary")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { StatusPillBadge("${shieldMetrics.adsBlocked} ADS", MaterialTheme.colorScheme.secondary) }
                            item { StatusPillBadge("${shieldMetrics.trackersBlocked} TRACKERS", MaterialTheme.colorScheme.primary) }
                            item { StatusPillBadge("${shieldMetrics.popupsBlocked} POPUPS", AmberGold) }
                            item { StatusPillBadge("${shieldMetrics.malwareBlocked} MALWARE", RoseAlert) }
                        }

                        if (preferences.adGuardEnabled) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.Shield,
                                iconTint = MaterialTheme.colorScheme.primary,
                                title = "Banner Blocking",
                                subtitle = "Block banner ad networks & standard display ad slots",
                                checked = preferences.blockAdsEnabled,
                                onCheckedChange = viewModel::setBlockAdsEnabled,
                                testTag = "setting_block_ads"
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.NetworkCheck,
                                iconTint = AmberGold,
                                title = "Tracker Blocking",
                                subtitle = "EasyPrivacy + AdGuard Tracking Protection beacons",
                                checked = preferences.blockTrackersEnabled,
                                onCheckedChange = viewModel::setBlockTrackersEnabled,
                                testTag = "setting_block_trackers"
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.PhonelinkLock,
                                iconTint = AmberGold,
                                title = "Pop-up Blocking",
                                subtitle = "Block popunder hosts, push traps & window.open popups",
                                checked = preferences.blockPopupsEnabled,
                                onCheckedChange = viewModel::setBlockPopupsEnabled,
                                testTag = "setting_block_popups"
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.VisibilityOff,
                                iconTint = EmeraldSuccess,
                                title = "Cosmetic Filtering",
                                subtitle = "Hide empty ad containers, banners & sponsored overlays",
                                checked = preferences.cosmeticFilteringEnabled,
                                onCheckedChange = viewModel::setCosmeticFilteringEnabled,
                                testTag = "setting_cosmetic_filter"
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.Close,
                                iconTint = RoseAlert,
                                title = "Malware Shield",
                                subtitle = "Protect cloud provider manifests, redirects & video downloads",
                                checked = preferences.blockMalwareEnabled,
                                onCheckedChange = viewModel::setBlockMalwareEnabled,
                                testTag = "setting_block_malware"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.syncFilterLists() },
                                enabled = !isSyncingFilters,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("sync_filter_lists_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isSyncingFilters) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSyncingFilters) "Updating..." else "Update Filters")
                            }

                            OutlinedButton(
                                onClick = { showCustomRulesDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("custom_shield_rules_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Rules (${preferences.customBlockedDomains.size + preferences.whitelistedDomains.size})")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetFilterSettings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("reset_filter_settings_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Filter Settings")
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Text(
                            text = "Filter List Subscriptions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        ContentBlockerEngine.SUBSCRIPTIONS.forEachIndexed { idx, sub ->
                            val isEnabled = preferences.enabledFilterListIds.contains(sub.id)
                            val status = filterStatuses[sub.id]
                            val catColor = when (sub.category) {
                                FilterCategory.MALWARE -> RoseAlert
                                FilterCategory.TRACKERS -> AmberGold
                                FilterCategory.ANNOYANCES -> EmeraldSuccess
                                FilterCategory.ADS -> MaterialTheme.colorScheme.secondary
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = sub.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        StatusPillBadge(sub.vendor, catColor)
                                    }
                                    val countInfo = status?.ruleCount?.takeIf { it > 0 }?.let { "$it rules" } ?: "Built-in"
                                    val syncTag = if (status?.isCached == true) "Synced" else "Core active"
                                    Text(
                                        text = "${sub.description} • $countInfo ($syncTag)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked -> viewModel.setFilterListEnabled(sub.id, checked) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            if (idx < ContentBlockerEngine.SUBSCRIPTIONS.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Browser & Player Settings Section
            item {
                Text(
                    text = stringResource(R.string.hub_settings_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Rounded.DesktopWindows,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Desktop Browser Mode",
                            subtitle = "Request desktop site layout in Navula stream browser",
                            checked = preferences.desktopModeEnabled,
                            onCheckedChange = viewModel::setDesktopModeEnabled,
                            testTag = "setting_desktop_mode"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        SettingsToggleRow(
                            icon = Icons.Rounded.AutoAwesome,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Show Floating Home Controls",
                            subtitle = "Show Download, Save & Fullscreen buttons on Home (always enabled via Hub & player)",
                            checked = preferences.showHomeFloatingControls,
                            onCheckedChange = viewModel::setShowHomeFloatingControls,
                            testTag = "setting_home_floating_controls"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        SettingsToggleRow(
                            icon = Icons.Rounded.History,
                            iconTint = EmeraldSuccess,
                            title = "Auto-Resume Offline Playback",
                            subtitle = "Continue offline videos from your last saved timestamp",
                            checked = preferences.autoResumeEnabled,
                            onCheckedChange = viewModel::setAutoResumeEnabled,
                            testTag = "setting_auto_resume"
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = AmberGold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Default Playback Speed", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Applied automatically in the Media3 Video Player",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SETTINGS_SPEED_OPTIONS) { speed ->
                                val selected = preferences.defaultPlaybackSpeed == speed
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setDefaultPlaybackSpeed(speed) },
                                    label = { Text("${speed}x") },
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

            // About / Credits Card
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
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Navula • Streaming app",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "A project By sakib",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomRulesDialog) {
        var domainInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomRulesDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Custom Shield Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Add any ad, popup, or tracking domain to block, or manage whitelisted sites.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Domain (e.g. ads.example.com)") },
                        singleLine = true,
                        modifier = Modifier.testTag("custom_shield_domain_input")
                    )
                    if (preferences.customBlockedDomains.isNotEmpty()) {
                        Text("Blocked Domains", style = MaterialTheme.typography.labelLarge, color = RoseAlert)
                        preferences.customBlockedDomains.take(6).forEach { dom ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dom, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.removeCustomBlockedDomain(dom) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = RoseAlert)
                                }
                            }
                        }
                    }
                    if (preferences.whitelistedDomains.isNotEmpty()) {
                        Text("Whitelisted Sites (Ads Allowed)", style = MaterialTheme.typography.labelLarge, color = EmeraldSuccess)
                        preferences.whitelistedDomains.take(6).forEach { dom ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dom, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.toggleWhitelistedDomain(dom) }) {
                                    Text("Re-protect", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomBlockedDomain(domainInput)
                        domainInput = ""
                    },
                    enabled = domainInput.contains("."),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Block Domain")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRulesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
