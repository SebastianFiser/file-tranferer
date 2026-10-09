package org.dpdns.sebastianfiser.filetransferer.protocol

import kotlinx.serialization.Serializable
import koltinx.serialization.json.JsonElement
kotlinx.serialization.SerialName

@Serializable
data class Envelope(
    val id: String,
    val type: String,
    val data: JsonElement
)

@Serializable
data class HelloData(
    @SerialName("device_name") val devieName: String,
    @SerialName("potocol_version") val protocolVersion: Int
)
