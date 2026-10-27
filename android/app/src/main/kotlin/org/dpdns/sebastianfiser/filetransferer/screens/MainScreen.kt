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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.res.stringResource

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

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.sendFile(uris)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { vm.connect() },
            enabled = state is TransferUiState.Idle || state is TransferUiState.Error
        ) {
            Text("Connect")
        }

        Button(
            onClick = { vm.disconnect() },
            enabled = state !is TransferUiState.Idle && state !is TransferUiState.Connecting
        ) {
            Text("disconnect")
        }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = state is TransferUiState.Connected || state is TransferUiState.Done
        ) {
            Text("select files") //MY WRETARSTED why arent my lsps running
        }

        val s = state

        Text(
            when (s) {
                is TransferUiState.Idle -> stringResource(R.string.state_idle_title)
                is TransferUiState.NotFound -> stringResource(R.string.state_notfound_title)
                is TransferUiState.Searching -> stringResource(R.string.state_searching_title)
                is TransferUiState.Connecting -> stringResource(R.string.state_connecting_title)
                is TransferUiState.Connected -> stringResource(R.string.state_connected_title, s.deviceName)
                is TransferUiState.Transferring -> stringResource(
                    R.string.state_transferring_title,
                    s.fileIndex + 1,
                    s.fileCount,
                )
                is TransferUiState.Done -> stringResource(R.string.state_done_title)
                is TransferUiState.Error -> stringResource(s.message.messageRes())
            }
        )
        if (s is TransferUiState.Transferring) {
            LinearProgressIndicator(
                progress = { if (s.totalBytes > 0) s.bytesSent.toFloat() / s.totalBytes else 0f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
