package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.media.VideoDownloadWorker
import com.example.ui.components.StartupSplashOverlay
import com.example.ui.navigation.HubRoute
import com.example.ui.navigation.OfflineRoute
import com.example.ui.navigation.PlayerRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StreamRoute
import com.example.ui.screens.HubScreen
import com.example.ui.screens.NavulaWebViewHolder
import com.example.ui.screens.OfflineLibraryScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StreamScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.SpacedomTheme
import com.example.ui.util.FullscreenSystemBars
import com.example.ui.viewmodel.MainViewModel

private data class TopLevelDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    private val pendingOpenVideoIdState = mutableStateOf<String?>(null)

    @Volatile
    private var isImmersiveWindowActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenVideoIdState.value = intent?.getStringExtra(VideoDownloadWorker.EXTRA_OPEN_OFFLINE_VIDEO_ID)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
            var showStartupSplash by rememberSaveable { mutableStateOf(true) }
            val pendingOpenVideoId by pendingOpenVideoIdState

            SpacedomTheme(themeMode = preferences.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavulaApp(
                        mainViewModel = mainViewModel,
                        pendingOpenVideoId = pendingOpenVideoId,
                        onConsumedOpenVideoId = { pendingOpenVideoIdState.value = null },
                        onImmersiveChanged = { immersive ->
                            isImmersiveWindowActive = immersive
                        }
                    )
                    StartupSplashOverlay(
                        visible = showStartupSplash,
                        onDismiss = { showStartupSplash = false }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isImmersiveWindowActive) {
            FullscreenSystemBars.applyImmersive(this, true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openId = intent.getStringExtra(VideoDownloadWorker.EXTRA_OPEN_OFFLINE_VIDEO_ID)
        if (!openId.isNullOrBlank()) {
            pendingOpenVideoIdState.value = openId
        }
    }
}

@Composable
fun NavulaApp(
    mainViewModel: MainViewModel = viewModel(),
    pendingOpenVideoId: String? = null,
    onConsumedOpenVideoId: () -> Unit = {},
    onImmersiveChanged: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val webViewHolder = remember { NavulaWebViewHolder() }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.dispose()
        }
    }

    LaunchedEffect(pendingOpenVideoId) {
        val targetId = pendingOpenVideoId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        onConsumedOpenVideoId()
        navController.navigate(
            PlayerRoute(
                videoId = targetId,
                streamUrl = null,
                title = "Offline Video"
            )
        )
    }

    LaunchedEffect(Unit) {
        mainViewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.completedDownloadEvents.collect { notice ->
            val result = snackbarHostState.showSnackbar(
                message = "Download Complete: ${notice.title}",
                actionLabel = "Watch",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                mainViewModel.dismissCompletedDownloadNotice()
                navController.navigate(
                    PlayerRoute(
                        videoId = notice.taskId,
                        streamUrl = null,
                        title = notice.title
                    )
                )
            }
        }
    }

    val destinations = remember {
        listOf(
            TopLevelDestination(
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                route = StreamRoute(),
                testTag = "nav_tab_home"
            ),
            TopLevelDestination(
                labelRes = R.string.nav_search,
                selectedIcon = Icons.Rounded.Search,
                unselectedIcon = Icons.Outlined.Search,
                route = SearchRoute,
                testTag = "nav_tab_search"
            ),
            TopLevelDestination(
                labelRes = R.string.nav_offline,
                selectedIcon = Icons.Rounded.VideoLibrary,
                unselectedIcon = Icons.Outlined.VideoLibrary,
                route = OfflineRoute,
                testTag = "nav_tab_offline"
            ),
            TopLevelDestination(
                labelRes = R.string.nav_hub,
                selectedIcon = Icons.Rounded.Extension,
                unselectedIcon = Icons.Outlined.Extension,
                route = HubRoute,
                testTag = "nav_tab_hub"
            ),
            TopLevelDestination(
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                route = SettingsRoute,
                testTag = "nav_tab_settings"
            )
        )
    }

    val isStreamFullscreen by mainViewModel.isStreamFullscreen.collectAsStateWithLifecycle()
    val isPlayerScreen = currentDestination?.hasRoute<PlayerRoute>() == true
    val hideAppChrome = isPlayerScreen || isStreamFullscreen

    LaunchedEffect(hideAppChrome) {
        onImmersiveChanged(hideAppChrome)
    }

    fun switchToHomeTab() {
        if (currentDestination?.hasRoute<StreamRoute>() == true) {
            return
        }
        if (!navController.popBackStack<StreamRoute>(inclusive = false)) {
            navController.navigate(StreamRoute()) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
            }
        }
    }

    fun navigateToCurrentProviderHomeFromButton() {
        mainViewModel.navigateToCurrentProviderHome()
        switchToHomeTab()
    }

    fun navigateToTopLevel(route: Any) {
        if (route is StreamRoute) {
            navigateToCurrentProviderHomeFromButton()
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openUrlInHome(url: String) {
        mainViewModel.requestLoadInHome(url)
        switchToHomeTab()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavRail = maxWidth >= 600.dp && !hideAppChrome

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    destinations.forEach { dest ->
                        val selected = when (dest.route) {
                            is StreamRoute -> currentDestination?.hasRoute<StreamRoute>() == true
                            SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
                            OfflineRoute -> currentDestination?.hasRoute<OfflineRoute>() == true
                            HubRoute -> currentDestination?.hasRoute<HubRoute>() == true
                            SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(dest.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag(dest.testTag),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            actionColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                bottomBar = {
                    if (!hideAppChrome && !useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            destinations.forEach { dest ->
                                val selected = when (dest.route) {
                                    is StreamRoute -> currentDestination?.hasRoute<StreamRoute>() == true
                                    SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
                                    OfflineRoute -> currentDestination?.hasRoute<OfflineRoute>() == true
                                    HubRoute -> currentDestination?.hasRoute<HubRoute>() == true
                                    SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(dest.testTag),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = StreamRoute(),
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                    popExitTransition = { fadeOut(animationSpec = tween(180)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable<StreamRoute> { entry ->
                        val route = entry.toRoute<StreamRoute>()
                        StreamScreen(
                            initialUrl = route.targetUrl,
                            viewModel = mainViewModel,
                            onOpenOfflineLibrary = { navigateToTopLevel(OfflineRoute) },
                            onPlayStreamUrl = { url, title ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = null,
                                        streamUrl = url,
                                        title = title
                                    )
                                )
                            },
                            onPlayOfflineVideo = { videoId ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = videoId,
                                        streamUrl = null,
                                        title = "Offline Video"
                                    )
                                )
                            },
                            webViewHolder = webViewHolder
                        )
                    }

                    composable<SearchRoute> {
                        SearchScreen(
                            viewModel = mainViewModel,
                            onLaunchUrlInHome = { url -> openUrlInHome(url) },
                            onNavigateToHome = { switchToHomeTab() },
                            onPlayDirectStream = { url, title ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = null,
                                        streamUrl = url,
                                        title = title
                                    )
                                )
                            },
                            onOpenExtensionsInHub = { navigateToTopLevel(HubRoute) }
                        )
                    }

                    composable<OfflineRoute> {
                        OfflineLibraryScreen(
                            viewModel = mainViewModel,
                            onPlayOfflineVideo = { video ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = video.id,
                                        streamUrl = null,
                                        title = video.title
                                    )
                                )
                            },
                            onPlayStreamUrl = { url, title ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = null,
                                        streamUrl = url,
                                        title = title
                                    )
                                )
                            }
                        )
                    }

                    composable<HubRoute> {
                        HubScreen(
                            viewModel = mainViewModel,
                            onOpenUrlInHome = { url -> openUrlInHome(url) },
                            onNavigateToHome = { switchToHomeTab() },
                            onOpenSettings = { navigateToTopLevel(SettingsRoute) },
                            onPlayDownloadedVideo = { videoId ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = videoId,
                                        streamUrl = null,
                                        title = "Offline Video"
                                    )
                                )
                            },
                            onPlayStreamUrl = { url, title ->
                                navController.navigate(
                                    PlayerRoute(
                                        videoId = null,
                                        streamUrl = url,
                                        title = title
                                    )
                                )
                            }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = mainViewModel,
                            onNavigateToHome = { switchToHomeTab() },
                            onOpenCloudExtensions = { navigateToTopLevel(HubRoute) }
                        )
                    }

                    composable<PlayerRoute> { entry ->
                        val route = entry.toRoute<PlayerRoute>()
                        VideoPlayerScreen(
                            initialVideoId = route.videoId,
                            streamUrl = route.streamUrl,
                            initialTitle = route.title,
                            viewModel = mainViewModel,
                            onBack = {
                                mainViewModel.triggerHomePluginRefresh()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
