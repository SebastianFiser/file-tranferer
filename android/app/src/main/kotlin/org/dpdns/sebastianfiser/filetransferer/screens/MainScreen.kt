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
        val picked = uris.mapNotNull { queryFile(context.contentResolver, it) }
        if (picked.size < uris.size) {
            Log.w("MainScreen", "some files have an unknown size")
        }
        scope.launch {
            try {
                transferClient.sendPickedFiles(context.contentResolver, picked)
            } catch (e: Exception) {
                Log.e("MainScreen", "transfer failed", e)
            }
        }
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

        Button(onClick = { picker.launch(arrayOf("*/*")) }) {
            Text("select files") //MY WRETARSTED why arent my lsps running
        }

        Text(
            when (val s = state) {
                is TransferUiState.Idle -> "Not Connected"
                is TransferUiState.Connecting -> "Connecting..."
                is TransferUiState.Connected -> "Connected to ${s.deviceName}"
                is TransferUiState.Transferring -> "${s.bytesSent} / ${s.totalBytes}"
                is TransferUiState.Done -> "done"
                is TransferUiState.Error -> "Error: ${s.message}"
            }
        )
    }
}
