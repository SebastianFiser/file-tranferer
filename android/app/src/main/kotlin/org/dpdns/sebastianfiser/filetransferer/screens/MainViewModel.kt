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

import org.dpdns.sebastianfiser.filetransferer.network.TransferClient
import org.dpdns.sebastianfiser.filetransferer.network.ServerDiscovery

class viewModel(application: Application) : AndroidViewModel(application) {
    //holds app states and variables that have to survive recompose of screens

    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var client: TransferClient? = null
    private var transferJob: Job? = null

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
                val server = ServerDiscovery(application).find()
                    ?: throw IllegalStateException("Server not found")
                val Url = if (server != null) "ws:${server.host}:${server.port}/ws" else
                    "ws://192.168.0.116:5000/ws"
                newClient.connect(Url)
                client = newClient
                _uiState.value = TransferUiState.Connected(deviceName = "TODO")
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

}
