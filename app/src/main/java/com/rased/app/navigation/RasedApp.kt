package com.rased.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rased.app.data.DummySessionStore
import com.rased.app.access.requiresPaidVersion
import com.rased.app.ui.home.HomeScreen
import com.rased.app.ui.login.LoginScreen
import com.rased.core.ui.RasedTheme
import com.rased.feature.checking.CheckingScreen
import com.rased.feature.sorting.ui.SortingRoute
import com.rased.feature.unloading.UnloadingScreen

private enum class Destination { Home, Sorting, Unloading, Checking }

@Composable
fun RasedApp() {
    val context = LocalContext.current
    val sessionStore = remember(context) { DummySessionStore(context) }
    var isLoggedIn by remember(sessionStore) { mutableStateOf(sessionStore.isLoggedIn) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    val paidRequired = remember { requiresPaidVersion() }
    val onBack = { destination = Destination.Home }
    BackHandler(enabled = isLoggedIn && destination != Destination.Home, onBack = onBack)
    RasedTheme {
        if (!isLoggedIn) {
            LoginScreen(onLogin = {
                sessionStore.login()
                destination = Destination.Home
                isLoggedIn = true
            })
        } else when (if (paidRequired) Destination.Home else destination) {
            Destination.Home -> HomeScreen(
                paidRequired = paidRequired,
                onOpenSorting = { if (!paidRequired) destination = Destination.Sorting },
                onOpenUnloading = { if (!paidRequired) destination = Destination.Unloading },
                onOpenChecking = { if (!paidRequired) destination = Destination.Checking },
                onLogout = {
                    sessionStore.logout()
                    destination = Destination.Home
                    isLoggedIn = false
                }
            )
            Destination.Sorting -> SortingRoute(onBack)
            Destination.Unloading -> UnloadingScreen(onBack)
            Destination.Checking -> CheckingScreen(onBack)
        }
    }
}
