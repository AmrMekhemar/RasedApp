package com.rased.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rased.app.ui.home.HomeScreen
import com.rased.core.ui.RasedTheme
import com.rased.feature.checking.CheckingScreen
import com.rased.feature.sorting.ui.SortingRoute
import com.rased.feature.unloading.UnloadingScreen

private enum class Destination { Home, Sorting, Unloading, Checking }

@Composable
fun RasedApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    val onBack = { destination = Destination.Home }
    BackHandler(enabled = destination != Destination.Home, onBack = onBack)
    RasedTheme {
        when (destination) {
            Destination.Home -> HomeScreen(
                onOpenSorting = { destination = Destination.Sorting },
                onOpenUnloading = { destination = Destination.Unloading },
                onOpenChecking = { destination = Destination.Checking }
            )
            Destination.Sorting -> SortingRoute(onBack)
            Destination.Unloading -> UnloadingScreen(onBack)
            Destination.Checking -> CheckingScreen(onBack)
        }
    }
}
