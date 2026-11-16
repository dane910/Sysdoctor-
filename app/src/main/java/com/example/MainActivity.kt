package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.model.ToolkitCategory
import com.example.ui.components.TtsVoicePlayerBar
import com.example.ui.screens.PulseOverviewScreen
import com.example.ui.screens.ToolkitScreen
import com.example.ui.theme.SysPulseTheme
import com.example.viewmodel.SysPulseViewModel

sealed class SysPulseDestination(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : SysPulseDestination(
        route = "pulse",
        labelResId = R.string.nav_pulse,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed
    )

    data object Toolkit : SysPulseDestination(
        route = "toolkit",
        labelResId = R.string.nav_toolkit,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build
    )
}

private val navDestinations = listOf(
    SysPulseDestination.Dashboard,
    SysPulseDestination.Toolkit
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required before setContent, ViewModels, or any Firebase AI call
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val viewModel: SysPulseViewModel = viewModel()
            val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()

            SysPulseTheme(darkTheme = darkTheme) {
                SysPulseAppShell(viewModel = viewModel, darkTheme = darkTheme)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SysPulseAppShell(
    viewModel: SysPulseViewModel,
    darkTheme: Boolean
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var selectedToolkitCategory by rememberSaveable { mutableStateOf<ToolkitCategory?>(null) }

    val storage by viewModel.storageSummary.collectAsStateWithLifecycle()
    val wifi by viewModel.wifiAudit.collectAsStateWithLifecycle()
    val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
    val duplicateState by viewModel.duplicateState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val auditBanner by viewModel.auditBanner.collectAsStateWithLifecycle()
    val cleanupFeedback by viewModel.cleanupFeedback.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val batteryTrend7Days by viewModel.batteryTrend7Days.collectAsStateWithLifecycle()
    val selectedBatteryMetric by viewModel.selectedBatteryMetric.collectAsStateWithLifecycle()
    val cleanerScanResult by viewModel.cleanerScanResult.collectAsStateWithLifecycle()
    val isScanningCleaner by viewModel.isScanningCleaner.collectAsStateWithLifecycle()
    val cleanupReport by viewModel.cleanupReport.collectAsStateWithLifecycle()
    val quarantineFiles by viewModel.quarantineFiles.collectAsStateWithLifecycle()
    val quarantineBeforeDelete by viewModel.quarantineBeforeDelete.collectAsStateWithLifecycle()
    val batteryHardware by viewModel.batteryHardware.collectAsStateWithLifecycle()
    val displayMetrics by viewModel.displayMetrics.collectAsStateWithLifecycle()
    val sensors by viewModel.sensors.collectAsStateWithLifecycle()
    val activeSensorType by viewModel.activeSensorType.collectAsStateWithLifecycle()
    val activeSensorReading by viewModel.activeSensorReading.collectAsStateWithLifecycle()
    val storageVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val recoveryScanState by viewModel.recoveryScanState.collectAsStateWithLifecycle()
    val bootableUsbCapability by viewModel.bootableUsbCapability.collectAsStateWithLifecycle()
    val bootImageInspectionState by viewModel.bootImageInspectionState.collectAsStateWithLifecycle()
    val toolkitFeedback by viewModel.toolkitFeedback.collectAsStateWithLifecycle()

    val navigateTo: (SysPulseDestination) -> Unit = { dest ->
        if (dest == SysPulseDestination.Toolkit &&
            currentDestination?.hierarchy?.any { it.route == SysPulseDestination.Toolkit.route } == true
        ) {
            selectedToolkitCategory = null
        }
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openToolkitCategory: (ToolkitCategory) -> Unit = { category ->
        selectedToolkitCategory = category
        navController.navigate(SysPulseDestination.Toolkit.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::toggleTheme,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (darkTheme) "Switch to light mode" else "Switch to dark mode"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TtsVoicePlayerBar(
                            ttsState = ttsState,
                            onTogglePauseResume = viewModel::toggleTtsPauseResume,
                            onStop = viewModel::stopTtsPlayback
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                val label = stringResource(id = dest.labelResId)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(id = dest.labelResId)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (useNavigationRail) {
                        TtsVoicePlayerBar(
                            ttsState = ttsState,
                            onTogglePauseResume = viewModel::toggleTtsPauseResume,
                            onStop = viewModel::stopTtsPlayback,
                            modifier = Modifier.widthIn(max = 760.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 880.dp)
                            .fillMaxWidth()
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = SysPulseDestination.Dashboard.route
                        ) {
                            composable(SysPulseDestination.Dashboard.route) {
                                PulseOverviewScreen(
                                    storage = storage,
                                    wifi = wifi,
                                    systemInfo = systemInfo,
                                    batteryTrend7Days = batteryTrend7Days,
                                    selectedBatteryMetric = selectedBatteryMetric,
                                    onSelectBatteryMetric = viewModel::selectBatteryMetric,
                                    isRefreshing = isRefreshing,
                                    auditBanner = auditBanner,
                                    onDismissAuditBanner = viewModel::dismissAuditBanner,
                                    onRefreshAll = viewModel::runManualAudit,
                                    onOpenToolkitCategory = openToolkitCategory
                                )
                            }
                            composable(SysPulseDestination.Toolkit.route) {
                                ToolkitScreen(
                                    selectedCategory = selectedToolkitCategory,
                                    onSelectCategory = { selectedToolkitCategory = it },
                                    storage = storage,
                                    wifi = wifi,
                                    systemInfo = systemInfo,
                                    batteryHardware = batteryHardware,
                                    displayMetrics = displayMetrics,
                                    sensors = sensors,
                                    activeSensorType = activeSensorType,
                                    activeSensorReading = activeSensorReading,
                                    storageVolumes = storageVolumes,
                                    recoveryScanState = recoveryScanState,
                                    quarantineFiles = quarantineFiles,
                                    duplicateState = duplicateState,
                                    cleanupFeedback = cleanupFeedback,
                                    cleanerScanResult = cleanerScanResult,
                                    isScanningCleaner = isScanningCleaner,
                                    cleanupReport = cleanupReport,
                                    quarantineBeforeDelete = quarantineBeforeDelete,
                                    toolkitFeedback = toolkitFeedback,
                                    onRunBatteryTest = viewModel::runBatteryDiagnosticTest,
                                    onUpdateDisplayTestStatus = viewModel::updateDisplayTestStatus,
                                    onStartSensorTest = viewModel::startSensorTest,
                                    onStopSensorTest = viewModel::stopSensorTest,
                                    onStartRecoveryScan = viewModel::startRecoveryScan,
                                    onRecoverFileToDestination = viewModel::recoverFileToUri,
                                    onResetRecoveryScan = viewModel::resetRecoveryScan,
                                    onRestoreQuarantinedFile = viewModel::restoreQuarantinedFile,
                                    onPurgeQuarantinedFile = viewModel::purgeQuarantinedFile,
                                    onToggleCleanerCategory = viewModel::toggleCleanerCategory,
                                    onToggleQuarantineBeforeDelete = viewModel::toggleQuarantineBeforeDelete,
                                    onRescanCleaner = viewModel::runCleanerDeepScan,
                                    onExecuteAdvancedClean = viewModel::executeAdvancedClean,
                                    onDismissCleanupReport = viewModel::dismissCleanupReport,
                                    onDismissCleanupFeedback = viewModel::dismissCleanupFeedback,
                                    onClearAppCache = viewModel::clearAppCache,
                                    onFolderPickedForDuplicates = viewModel::startDuplicateScan,
                                    onToggleDuplicate = viewModel::toggleDuplicateSelection,
                                    onDeleteSelectedDuplicates = viewModel::deleteSelectedDuplicates,
                                    onResetDuplicateScan = viewModel::resetDuplicateScan,
                                    onSpeakCleanupSummary = viewModel::speakCleanupSummary,
                                    onRefreshNetwork = viewModel::refreshWifiAuditOnly,
                                    onRefreshToolkit = viewModel::refreshToolkitData,
                                    onDismissFeedback = viewModel::dismissToolkitFeedback,
                                    bootableUsbCapability = bootableUsbCapability,
                                    bootImageInspectionState = bootImageInspectionState,
                                    onInspectBootImage = viewModel::inspectBootImage,
                                    onResetBootImageInspection = viewModel::resetBootImageInspection
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
