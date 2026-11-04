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
import kotlinx.coroutines.withTimeoutOrNull

import org.dpdns.sebastianfiser.filetransferer.R
import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.network.ServerDiscovery
import org.dpdns.sebastianfiser.filetransferer.files.PickedFile
import org.dpdns.sebastianfiser.filetransferer.network.ServerException
import org.dpdns.sebastianfiser.filetransferer.network.TransferCancelledException
import org.dpdns.sebastianfiser.filetransferer.network.LastServerStore
import org.dpdns.sebastianfiser.filetransferer.protocol.formatBytes


class MainViewModel(application: Application) : AndroidViewModel(application) {
    //holds app states and variables that have to survive recompose of screens

    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var client: TransferClient? = null
    private var transferJob: Job? = null
    private val lastServerStore = LastServerStore(application)

    private val _method = MutableStateFlow(SendMethod.Wifi)
    val method: StateFlow<SendMethod> = _method.asStateFlow()
    private var serverLabel = ""
    private var pendingUris: List<Uri> = emptyList()
 

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
        _uiState.value = TransferUiState.Connected(serverLabel)
    }

    private suspend fun tryConnect(url: String): TransferClient? {
        val c = TransferClient(deviceName)
        val ok: Boolean? = try {
            withTimeoutOrNull(3000) {
                c.connect(url)
                true
            }
        } catch (e: CancellationException) {
            c.close()
            throw e
        } catch (e: Exception) {
            null
        }
        if (ok == true) return c
        c.close()
        return null
    } 



    fun connect(method: SendMethod = _method.value) {
        if (_uiState.value is TransferUiState.Searching ||
            _uiState.value is TransferUiState.Connecting ||
            _uiState.value is TransferUiState.Connected
        ) {
            return
        }
        _method.value = method
        _uiState.value = TransferUiState.Searching

        viewModelScope.launch {
            try {

                var url: String? = null
                var connected: TransferClient? = null

                val saved = lastServerStore.load()
                if (saved != null) {
                    connected = tryConnect(saved)
                    if (connected != null) url = saved
                }

                if (connected == null) {
                    val server = ServerDiscovery(getApplication<Application>()).find()
                    if (server != null) {
                        val found = "ws://${formatUrl(server.host)}:${server.port}/ws"
                        connected = tryConnect(found)
                        if (connected != null) url = found
                    }
                }

                if (connected == null || url == null) {
                    pendingUris = emptyList()
                    _uiState.value = TransferUiState.NotFound
                    return@launch
                }

                closeClient()
                client = connected
                serverLabel = Uri.parse(url).host ?: url
                _uiState.value = TransferUiState.Connected(serverLabel)
                lastServerStore.save(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingUris = emptyList()
                client = null
                _uiState.value = TransferUiState.Error(ErrorKind.Unknown)
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

    fun onSharedFiles(uris: List<Uri>) {
        val s = _uiState.value
        if (s is TransferUiState.Connected || s is TransferUiState.Done) {
            sendFile(uris)
        } else {
            pendingUris = uris
            connect(SendMethod.Wifi)
        }
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
                var lastBytes = 0L
                var speed = 0.0 //B.s smoothsajbcsal
                var startedAt = 0L
                val verify = c.sendPickedFiles(
                    resolver, files,
                    onWaiting = { _uiState.value = TransferUiState.WaitingForConfirmation},
                    onAccepted = { 
                        if (isActive){
                            val now = System.currentTimeMillis()
                            startedAt = now
                            lastUpdate = now
                            lastBytes = 0L
                            speed = 0.0
                            _uiState.value = TransferUiState.Transferring(0, total, 0, files.size)
                        } 
                    },
                    onProgress =  { sent ->
                        val now = System.currentTimeMillis()
                        if (isActive  && now - lastUpdate > 100) {
                            val dt = (now -lastUpdate) / 1000.0
                            val instant = (sent - lastBytes) / dt
                            speed = if (speed == 0.0) instant else 0.1 * instant + 0.9 * speed
                            lastUpdate = now
                            lastBytes = sent

                            val eta = if (now - startedAt > 2000 && speed > 0)
                                ((total - sent) / speed).toLong() else -1L
                            val idx = ends.indexOfFirst { sent < it }
                                .let { if (it < 0) files.lastIndex else it }
                            _uiState.value = TransferUiState.Transferring(
                                sent, total, idx, files.size, speed.toLong(), eta
                            )
                        }
                    }
                )

                _uiState.value = TransferUiState.Done(files.size, total, verify)
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
