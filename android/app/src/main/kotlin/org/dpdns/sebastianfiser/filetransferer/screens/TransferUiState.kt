package org.dpdns.sebastianfiser.filetransferer.screens

import androidx.annotation.StringRes
import org.dpdns.sebastianfiser.filetransferer.R
sealed interface TransferUiState {
    data object Idle : TransferUiState
    data object Searching : TransferUiState
    data object Connecting : TransferUiState
    data object NotFound : TransferUiState
    data object WaitingForConfirmation : TransferUiState
    data object Rejected : TransferUiState
    data object Cancelled : TransferUiState
    data object Timeout : TransferUiState

    data class Connected(
        val deviceName: String
    ) : TransferUiState

    data class Transferring(
        val bytesSent: Long,
        val totalBytes: Long,
        val fileIndex: Int,
        val fileCount: Int,
        val speedBps: Long = 0,
        val etaSeconds: Long = -1
    ) : TransferUiState

    data class Done(
        val fileCount: Int
    ) : TransferUiState

    data class Error(
        val message: ErrorKind
    ) : TransferUiState


}

enum class ErrorKind {
    ConnectionLost,
    ServerUnreachable,
    Rejected,
    Unknown 
}

@StringRes
fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.ConnectionLost -> R.string.error_connection_lost
    ErrorKind.ServerUnreachable -> R.string.error_server_unreachable
    ErrorKind.Rejected -> R.string.error_rejected
    ErrorKind.Unknown -> R.string.error_unknown
}
