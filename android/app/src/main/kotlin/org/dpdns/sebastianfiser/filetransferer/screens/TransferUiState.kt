package org.dpdns.sebastianfiser.filetransferer.screens

sealed interface TransferUiSTate {
    data object Idle
    data object Connecting
    data object Done
}

data class Connected {
    val deviceName: String
}

data class Transferring {
    val bytesSent: Long
    val totalBytes: Long
}

data class Error {
    val message: String
}
