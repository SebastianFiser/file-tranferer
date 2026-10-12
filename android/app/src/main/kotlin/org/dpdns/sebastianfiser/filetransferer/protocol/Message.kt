package org.dpdns.sebastianfiser.filetransferer.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.SerialName

@Serializable
data class Envelope(
    val id: String,
    val type: String,
    val data: JsonElement
)

//hello stuff

@Serializable
data class HelloData(
    @SerialName("device_name") val devieName: String,
    @SerialName("potocol_version") val protocolVersion: Int
)

//offer files stuff

@Serializable
data class FileInfo(
    @SerialName("relative_path") val relativePath: String,
    val size: Long,
    @SerialName("file_id") val fileId: String
)

@Serializable
data class OfferFilesData(
    val files: List<FileInfo>
)

@Serializable
data class OfferAckData(
    val accepted: Boolean,
    @SerialName("transfer_id") val transferId: String,
    @SerialName("max_batch_files") val maxBatchFiles: Int,
    @SerialName("max_batch_bytes") val maxBatchBytes
)

//send files stuff

@Serializable
data class RequestFilesData(
    @SerialName("transfer_id") val transferId: String,
    @SerialName("fil_ids") val fileIds: List<String>
)
//eausdvykghvj <- my mental state rn
