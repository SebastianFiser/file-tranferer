package org.dpdns.sebastianfiser.filetransferer.screens

sealed interface TransferUiState {
    data object Idle : TransferUiState
    data object Connecting : TransferUiState
    data object Done : TransferUiState

    data class Connected(
        val deviceName: String
    ) : TransferUiState

    data class Transferring(
        val bytesSent: Long,
        val totalBytes: Long
    ) : TransferUiState

    data class Error(
        val message: String
    ) : TransferUiState
}
