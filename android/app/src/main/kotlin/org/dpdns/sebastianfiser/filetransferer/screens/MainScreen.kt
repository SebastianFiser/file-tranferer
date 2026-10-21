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

import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.files.PickedFile
import org.dpdns.sebastianfiser.filetransferer.files.queryFile
import org.dpdns.sebastianfiser.filetransferer.network.ServerDiscovery
import org.dpdns.sebastianfiser.filetransferer.screens.viewModel

@Composable
fun MainScreen(transferClient: TransferClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vm: viewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.SendFiles(uris)
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

        Text(
            when (val s = state) {
                is TransferUiState.Idle -> "Not Connected"
                is TransferUiState.Connecting -> "Connecting..."
                is TransferUiState.Connected -> "Connected to ${s.deviceName}"
                is TransferUiState.Transferring -> {
                    LinearProgressIndicator(
                        progress = {
                            if (s.totalBytes > 0) s.bytesSent.toFloat() / s.totalBytes else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is TransferUiState.Done -> "done"
                is TransferUiState.Error -> "Error: ${s.message}"
            }
        )
    }
}
