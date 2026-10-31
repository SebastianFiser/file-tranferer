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
import android.provider.OpenableColumns
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive

import org.dpdns.sebastianfiser.filetransferer.R
import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.network.ServerDiscovery
import org.dpdns.sebastianfiser.filetransferer.files.PickedFile
import org.dpdns.sebastianfiser.filetransferer.network.ServerException
import org.dpdns.sebastianfiser.filetransferer.network.TransferCancelledException

class MainViewModel(application: Application) : AndroidViewModel(application) {
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

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _uiState.value = TransferUiState.Cancelled
    }

    fun backToConnected() {
        _uiState.value = TransferUiState.Connected(deviceName)
    }

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
                val Url = if (server != null) "ws://${formatUrl(server.host)}:${server.port}/ws" else throw IllegalStateException("Server not found")
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
                if (e is ServerException) {
                    _uiState.value = TransferUiState.Error(ErrorKind.ServerUnreachable)
                } else {
                    _uiState.value = TransferUiState.Error(ErrorKind.Unknown)
                }
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
        if (_uiState.value is TransferUiState.Transferring || _uiState.value is TransferUiState.WaitingForConfirmation) return

        transferJob = viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val files = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        resolver.query(uri, null, null, null, null)!!.use { cursor ->
                            cursor.moveToFirst()
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                            val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                            PickedFile(uri = uri, name = name, size = size)
                        }
                    }
                }
                val total = files.sumOf { it.size }
                val ends = files.runningFold(0L) { acc, f -> acc + f.size }.drop(1)

                _uiState.value = TransferUiState.Transferring(0, total, 0, files.size)
                var lastUpdate = 0L
                c.sendPickedFiles(
                    resolver, files,
                    onWaiting = { _uiState.value = TransferUiState.WaitingForConfirmation},
                    onAccepted = { if (isActive) _uiState.value = TransferUiState.Transferring(0, total, 0, files.size) },
                    onProgress =  { sent ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100) {
                            lastUpdate = now
                            val idx = ends.indexOfFirst { sent < it }
                                .let { if (it < 0) files.lastIndex else it }
                            _uiState.value = TransferUiState.Transferring(sent, total, idx, files.size)
                        }
                    }
                )

                _uiState.value = TransferUiState.Done(files.size)
            } catch (e: ServerException){
                _uiState.value = when (e.code) {
                    "offer_rejected" -> TransferUiState.Rejected
                    "offer_timeout" -> TransferUiState.Timeout
                    else -> TransferUiState.Error(ErrorKind.Unknown)
                }
            } catch (e: TransferCancelledException) {
                _uiState.value = TransferUiState.Cancelled
            } catch (e: TimeoutCancellationException) {
                _uiState.value = if (_uiState.value is TransferUiState.WaitingForConfirmation) {
                    TransferUiState.Timeout
                } else {
                    TransferUiState.Error(ErrorKind.ConnectionLost)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TransferUiState.Error(ErrorKind.ConnectionLost)
            }
        }
    }

    fun formatUrl(host: String): String {
        val h = host.trim()
        if (!h.contains(':')) return h
        if (!h.startsWith('[')) return h
        return "[" + h.replace("%", "%25") + "]"
    }

}
