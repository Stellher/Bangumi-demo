package com.example.bangumioauthdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

class BangumiHomeActivity : ComponentActivity() {
    private val vm: BangumiBrowseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OAuthSessionManager.initialize(application)
        if (OAuthSessionManager.session.value != null) RefreshScheduler.schedule(application)
        window.statusBarColor = android.graphics.Color.rgb(243, 247, 255)
        window.navigationBarColor = android.graphics.Color.rgb(249, 243, 250)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = BangumiColors) {
                BangumiHomeApp(vm = vm) {
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            OAuthSessionManager.ensureValidToken()
        }
    }
}

@Composable
private fun BangumiHomeApp(
    vm: BangumiBrowseViewModel,
    openOAuthLab: () -> Unit
) {
    // The page including the real background must be captured before the
    // floating bars and sheets sample it. No glass surface captures itself.
    val backdrop = rememberLayerBackdrop {
        drawRect(Pale)
        drawContent()
    }
    // Separate capture layers prevent nested glass from sampling itself.
    val wallpaperBackdrop = rememberLayerBackdrop()
    val snackHost = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var seasonFiltersOpen by remember { mutableStateOf(false) }
    var collectionEditorOpen by remember { mutableStateOf(false) }
    var quickPreview by remember { mutableStateOf<AnimeCard?>(null) }

    val subjectId = vm.selectedSubject
    val personId = vm.selectedPerson
    val overlay = searchOpen || subjectId != null || personId != null
    val visibleRoute = when {
        personId != null -> "person:${personId.first}"
        subjectId != null -> "subject:$subjectId"
        searchOpen -> "search"
        else -> "tab:$currentTab"
    }
    fun back() {
        when {
            personId != null -> vm.closePerson()
            subjectId != null -> vm.closeSubject()
            searchOpen -> searchOpen = false
            currentTab != 0 -> currentTab = 0
        }
    }
    BackHandler(enabled = overlay || currentTab != 0) { back() }

    // Reset collection sheet when moving to a different title.
    LaunchedEffect(subjectId) {
        if (subjectId == null) collectionEditorOpen = false
    }
    val feedback = vm.actionFeedback
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackHost.showSnackbar(feedback)
            vm.dismissFeedback()
        }
    }

    val topTitle = when (currentTab) {
        0 -> "Bangumi · 发现"
        1 -> "每日放送"
        2 -> "季度新番"
        3 -> "我的收藏"
        else -> "个人中心"
    }

    CompositionLocalProvider(
        LocalAnimePreviewHandler provides { item: AnimeCard ->
            quickPreview = item
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Box(Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop)) {
                    GlassBackdropBackground()
                }
                // Inline controls sample ONLY wallpaperBackdrop. The page
                // capture can include these without recursive glass feedback.
                CompositionLocalProvider(LocalGlassBackdrop provides wallpaperBackdrop) {
                Box(
                    Modifier.fillMaxSize().statusBarsPadding()
                        .padding(top = if (overlay) 0.dp else 72.dp)
                ) {
                    AnimatedContent(
                        targetState = visibleRoute,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val enteringDetail = targetState.startsWith("subject:") ||
                                targetState.startsWith("person:") ||
                                targetState == "search"
                            val forward = enteringDetail ||
                                targetState.substringAfter("tab:", "0").toIntOrNull()
                                    .let { (it ?: 0) >= (initialState.substringAfter("tab:", "0").toIntOrNull() ?: 0) }
                            val horizontal = if (forward) 1 else -1
                            (
                                fadeIn(tween(210)) +
                                    slideInHorizontally(tween(340)) { it * horizontal / 5 }
                            ) togetherWith (
                                fadeOut(tween(190)) +
                                    slideOutHorizontally(tween(330)) { -it * horizontal / 6 }
                            )
                        },
                        label = "bangumiGlassNavigation"
                    ) { route ->
                        pageStateHolder.SaveableStateProvider(route) {
                        when {
                            route.startsWith("person:") -> PersonPage(
                                vm = vm, back = { vm.closePerson() }
                            )
                            route.startsWith("subject:") -> SubjectPage(
                                vm = vm,
                                back = { vm.closeSubject() },
                                openSubject = vm::openSubject,
                                openPerson = vm::openPerson,
                                openLogin = openOAuthLab,
                                onEditCollection = { collectionEditorOpen = true }
                            )
                            route == "search" -> SearchPage(
                                vm = vm,
                                onBack = { searchOpen = false },
                                openSubject = vm::openSubject,
                                openPerson = vm::openPerson
                            )
                            route == "tab:0" -> DiscoverPage(
                                vm = vm,
                                openSubject = vm::openSubject,
                                openSearch = { searchOpen = true },
                                goToTab = { currentTab = it }
                            )
                            route == "tab:1" -> CalendarPage(
                                vm = vm, openSubject = vm::openSubject
                            )
                            route == "tab:2" -> SeasonPage(
                                vm = vm,
                                openSubject = vm::openSubject,
                                openFilters = { seasonFiltersOpen = true }
                            )
                            route == "tab:3" -> LibraryPage(
                                vm = vm,
                                openSubject = vm::openSubject,
                                onLogin = openOAuthLab
                            )
                            else -> ProfilePage(
                                vm = vm,
                                onLogin = openOAuthLab,
                                onOpenLibrary = { currentTab = 3 },
                                onOpenApiLab = openOAuthLab,
                                onOpenSearch = { searchOpen = true }
                            )
                        }
                        }
                    }
                }
                }
            }

            // Floating bars sample pageBackdrop. Sheets use exportedBackdrop
            // for a safe second layer (never capture their own glass).
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            // Foreground layers intentionally live outside layerBackdrop.
            if (!overlay) {
                LiquidTopBar(
                    title = topTitle,
                    leadingIcon = Icons.Outlined.AutoAwesome,
                    onLeading = { currentTab = 0 },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                    actions = {
                        when (currentTab) {
                            0 -> LiquidIconAction(
                                Icons.Outlined.Search, "搜索",
                                onClick = { searchOpen = true },
                                modifier = Modifier.size(38.dp)
                            )
                            1 -> LiquidIconAction(
                                Icons.Outlined.Refresh, "刷新放送",
                                onClick = { vm.loadCalendar(force = true) },
                                modifier = Modifier.size(38.dp)
                            )
                            2 -> {
                                LiquidIconAction(
                                    Icons.Outlined.FilterList, "筛选动画",
                                    onClick = { seasonFiltersOpen = true },
                                    modifier = Modifier.size(38.dp)
                                )
                                LiquidIconAction(
                                    Icons.Outlined.Refresh, "刷新季度",
                                    onClick = { vm.loadSeason(force = true) },
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            3 -> LiquidIconAction(
                                Icons.Outlined.Refresh, "刷新收藏",
                                onClick = { vm.loadLibrary(force = true) },
                                modifier = Modifier.size(38.dp)
                            )
                            else -> LiquidIconAction(
                                Icons.Outlined.Settings, "授权与设置",
                                onClick = openOAuthLab,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                )
                LiquidNavigation(
                    backdrop = backdrop,
                    selected = currentTab,
                    onSelected = { currentTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }

            SnackbarHost(
                snackHost, modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (overlay) 10.dp else 101.dp)
            )

            GlassSeasonFilterSheet(
                visible = seasonFiltersOpen && !overlay,
                vm = vm,
                onDismiss = { seasonFiltersOpen = false }
            )

            val editable = vm.subject.data
            GlassCollectionEditor(
                visible = collectionEditorOpen && editable != null && subjectId != null,
                existing = editable?.collection,
                busy = vm.actionBusy,
                onDismiss = { collectionEditorOpen = false },
                onSave = { edit ->
                    subjectId?.let { id ->
                        vm.updateCollection(id, edit) { collectionEditorOpen = false }
                    }
                }
            )

            GlassQuickPreviewSheet(
                subject = quickPreview,
                onDismiss = { quickPreview = null },
                onOpen = { id ->
                    quickPreview = null
                    vm.openSubject(id)
                },
                onCollect = { id ->
                    quickPreview = null
                    vm.openSubject(id)
                    if (vm.authorization.value == null) {
                        openOAuthLab()
                    } else {
                        // Subject details load before opening the editing form.
                        collectionEditorOpen = true
                    }
                }
            )
            }
        }
    }
}
