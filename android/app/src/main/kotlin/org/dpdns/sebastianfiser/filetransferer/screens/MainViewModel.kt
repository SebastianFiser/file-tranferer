package org.dpdns.sebastianfiser.filetransferer.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import android.os.Build
import android.provider.Settings
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.network.ServerDiscovery

class viewModel(application: Application) : AndroidViewModel(application) {
    //holds app states and variables that have to survive recompose of screens

    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var client: TransferClient? = null
    private var transferJob: Job? = null

    private val deviceName = "${Build.MODEL}[${
        Settings.Secure.getString(
            getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID
        )
    }]"

    fun connect() {
        if (_uiState.value is TransferUiState.Connecting ||
            _uiState.value is TransferUiState.Connected
        ) {
            return
        }
        _uiState.value = TransferUiState.Connecting

        viewModelScope.launch {
            var newClient: TransferClient? = null
            try {
                val server = ServerDiscovery(getApplication<Application>()).find()
                    ?: throw IllegalStateException("Server not found")
                val Url = if (server != null) "ws://${server.host}:${server.port}/ws" else
                    "ws://192.168.0.116:5000/ws"
                val c = TransferClient(deviceName)
                client = c
                c.connect(Url)
                client = c
                _uiState.value = TransferUiState.Connected(server?.host ?: "unknown")
            } catch (e: CancellationException) {
                newClient?.close()
                throw e
            } catch (e: Exception) {
                newClient?.close()
                client = null
                _uiState.value = TransferUiState.Error(e.message ?: "unknown")
            }
        }

    }

    fun disconnect() {
        transferJob?.cancel()
        transferJob = null
        closeClient()
        _uiState.value = TransferUiState.Idle
    }

    private fun closeClient() {
        client?.close()
        client = null
    }

    override fun onCleared() {
        closeClient()
    }

    fun sendFile(uris: List<Uri>) {
        val c = client ?: return
        if (_uiState.value is TransferUiState.Transferring) return

        transferJob = viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val files = withContext(Dispatchers.IO) {
                    uris.mapIndexed { i, uri ->
                        resolver.query(uri, null, null, null, null)!!.use { cursor ->
                            cursor.moveToFirst()
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                            val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                            PickedFile(uri = uri, relativePath = name, size = size, fileId = (i + 1).toString())
                        }
                    }
                }
                val total = files.sumOf { it.size }
                _uiState.value = TransferUiState.Transferring(0, total)
                var lastUpdate = 0L
                c.SendPickedFiles(getApplication<Application>().contentResolver, files) { sent ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100) {
                        lastupdate = now_uiState.value = TransferUiState.Transferring(sent, total)
                    }
                }

                _uiState.value = TransferUiState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TransferUiState.Error(e.message ?: "unknown")
            }
        }
    }

}
