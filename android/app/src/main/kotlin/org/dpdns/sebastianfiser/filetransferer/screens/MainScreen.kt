package org.dpdns.sebastianfiser.filetransferer.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState

import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.files.PickedFile
import org.dpdns.sebastianfiser.filetransferer.files.queryFile
import org.dpdns.sebastianfiser.filetransferer.network.ServerDiscovery
import org.dpdns.sebastianfiser.filetransferer.screens.MainViewModel
import org.dpdns.sebastianfiser.filetransferer.R

@Composable
fun MainScreen(transferClient: TransferClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vm: MainViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val method by vm.method.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.sendFile(uris)
    }

    val screen by vm.screen.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BackHandler(enabled = screen != AppScreen.Home && !drawerState.isOpen) {
        vm.openScreen(AppScreen.Home)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                AppScreen.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(item.labelRes)) },
                        selected = item == screen,
                        onClick = {
                            vm.openScreen(item)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                AppScreen.Home -> {
                    if (state is TransferUiState.Idle) {
                        MethodChooser(onChoose = { vm.connect() })
                    } else {
                        StateContent(
                            s = state,
                            method = method,
                            onPick = { picker.launch(arrayOf("*/*")) },
                            onRetry = { vm.connect() },
                            onCancel = { vm.cancelTransfer() },
                            onSendMore = { vm.backToConnected() },
                            onBack = { vm.disconnect() },
                            onManualConnect = { vm.connectManual(it) },
                            onBackToNotFound = { vm.backToNotFound() }
                        )
                    }
                    if (state is TransferUiState.Connected || state is TransferUiState.Done) {
                        TextButton(
                            onClick = { vm.disconnect() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                        ) {
                            Text(stringResource(R.string.action_disconnect))
                        }
                    }
                }
                AppScreen.History -> HistoryScreen(entries = history, onClear = { vm.clearHistory() })
                AppScreen.Settings -> SettingsScreen(onForgetServer = { vm.forgetServer() })
                AppScreen.Help -> HelpScreen()
            }

            IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
            }
        }
    }

    
}
