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

@Serializable
data class HelloData(
    @SerialName("device_name") val devieName: String,
    @SerialName("potocol_version") val protocolVersion: Int
)
