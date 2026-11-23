package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.repository.PowerRepository
import com.example.ui.screens.AppDrainScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryExportScreen
import com.example.ui.screens.PermissionOnboardingScreen
import com.example.ui.screens.TrendsScreen
import com.example.ui.viewmodel.AppDrainViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.PowerViewModelFactory
import com.example.ui.viewmodel.TrendsViewModel
import com.example.util.PermissionHelper

private enum class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Rounded.Speed, "nav_tab_dashboard"),
    APP_DRAIN("app_drain", R.string.nav_app_drain, Icons.Rounded.Apps, "nav_tab_app_drain"),
    TRENDS("trends", R.string.nav_trends, Icons.Rounded.Insights, "nav_tab_trends"),
    HISTORY("history", R.string.nav_history, Icons.Rounded.Timeline, "nav_tab_history")
}

@Composable
fun PowerScopeRoot(repository: PowerRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasUsageAccess by remember {
        mutableStateOf(PermissionHelper.hasUsageStatsPermission(context))
    }
    var batteryOnlyBypassed by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = PermissionHelper.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasUsageAccess && !batteryOnlyBypassed) {
        PermissionOnboardingScreen(
            onPermissionsReady = { hasUsageAccess = true },
            onContinueBatteryOnly = { batteryOnlyBypassed = true }
        )
        return
    }

    val navController = rememberNavController()
    val factory = remember(repository) { PowerViewModelFactory(repository) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(navController, tab.route) },
                                icon = { Icon(imageVector = tab.icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }

                    PowerScopeNavHost(
                        navController = navController,
                        factory = factory,
                        hasUsageAccess = hasUsageAccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(navController, tab.route) },
                                icon = { Icon(imageVector = tab.icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                PowerScopeNavHost(
                    navController = navController,
                    factory = factory,
                    hasUsageAccess = hasUsageAccess,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PowerScopeNavHost(
    navController: NavHostController,
    factory: PowerViewModelFactory,
    hasUsageAccess: Boolean,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BottomTab.DASHBOARD.route,
        modifier = modifier
    ) {
        composable(BottomTab.DASHBOARD.route) {
            val vm: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = vm,
                onNavigateToAppDrain = {
                    navigateToTab(navController, BottomTab.APP_DRAIN.route)
                }
            )
        }

        composable(BottomTab.APP_DRAIN.route) {
            val vm: AppDrainViewModel = viewModel(factory = factory)
            AppDrainScreen(
                viewModel = vm,
                hasUsageAccess = hasUsageAccess
            )
        }

        composable(BottomTab.TRENDS.route) {
            val vm: TrendsViewModel = viewModel(factory = factory)
            TrendsScreen(viewModel = vm)
        }

        composable(BottomTab.HISTORY.route) {
            val vm: HistoryViewModel = viewModel(factory = factory)
            HistoryExportScreen(viewModel = vm)
        }
    }
}
