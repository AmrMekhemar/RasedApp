package com.rased.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import com.rased.app.data.DummySessionStore
import com.rased.app.access.requiresPaidVersion
import com.rased.app.access.requiresPaidVersionWithNetwork
import com.rased.app.ui.home.HomeScreen
import com.rased.app.ui.login.LoginScreen
import com.rased.core.ui.RasedTheme
import com.rased.feature.checking.CheckingScreen
import com.rased.feature.sorting.ui.SortingRoute
import com.rased.feature.unloading.UnloadingScreen
import com.rased.feature.sorting.ui.SortingViewModel
import com.rased.feature.sorting.data.SortingRepository
import androidx.compose.runtime.LaunchedEffect

private enum class Destination { Home, Sorting, Unloading, Checking }

@Composable
fun RasedApp(sharedFileUri: Uri? = null, onSharedFileHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val sessionStore = remember(context) { DummySessionStore(context) }
    var isLoggedIn by remember(sessionStore) { mutableStateOf(sessionStore.isLoggedIn) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var paidRequired by remember { mutableStateOf(requiresPaidVersion()) }
    LaunchedEffect(Unit) {
        paidRequired = requiresPaidVersionWithNetwork()
    }
    val sortingViewModel: SortingViewModel = viewModel()
    var hasPrimaryData by remember { mutableStateOf(false) }
    LaunchedEffect(sharedFileUri) {
        hasPrimaryData = SortingRepository(context).hasPrimaryData()
    }
    val onBack = { destination = Destination.Home }
    BackHandler(enabled = isLoggedIn && destination != Destination.Home, onBack = onBack)
    RasedTheme {
        if (!isLoggedIn) {
            LoginScreen(onLogin = {
                sessionStore.login()
                destination = Destination.Home
                isLoggedIn = true
            })
        } else when (if (paidRequired || sharedFileUri != null) Destination.Home else destination) {
            Destination.Home -> Box {
                HomeScreen(
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
                if (sharedFileUri != null) SharedFileImportDialog(
                    uri = sharedFileUri,
                    hasPrimaryData = hasPrimaryData,
                    onImport = { target ->
                        sortingViewModel.importSharedFile(sharedFileUri, target)
                        onSharedFileHandled()
                    },
                    onDismiss = onSharedFileHandled
                )
            }
            Destination.Sorting -> SortingRoute(onBack)
            Destination.Unloading -> UnloadingScreen(onBack)
            Destination.Checking -> CheckingScreen(onBack)
        }
    }
}
