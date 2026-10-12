package org.dpdns.sebastianfiser.filetransferer.network

import android.util.Log
import okhttp3.*
import org.dpdns.sebastianfiser.filetransferer.protocol.Envelope
import org.dpdns.sebastianfiser.filetransferer.protocol.HelloData
import org.dpdns.sebastianfiser.filetransferer.protocol.FileInfo
import org.dpdns.sebastianfiser.filetransferer.protocol.OfferFilesData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

private const val TAG = "TransferClient"

class TransferClient(
    private val deviceName: String
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null



    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendHello(webSocket)
                val files = listOf(
                    FileInfo("a.txt", 10, "f1"),
                    FileInfo("b.txt", 20, "f2")
                )
                sendOfferFiles(webSocket, files)

            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "icoming: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "error", t)
            }
        })
    }

    fun close() {
        webSocket?.close(1000, null)
    }
}

fun sendHello(webSocket: WebSocket) {
    val hello = Envelope(
        id = UUID.randomUUID().toString(),
        type = "hello",
        data = Json.encodeToJsonElement(HelloData(deviceName, 1))
    )
    webSocket.send(Json.encodeToString(hello))
}

fun sendOfferFiles(webSocket: WebSocket, files: List<FileInfo>) {
    val data = Json.encodeToJsonElement(OfferFilesData(files))
    val envelope = Envelope(
        UUID.randomUUID().toString(),
        "offer_files",
        data
    )
    val text = Json.encodeToString(envelope)
    webSocket.send(text)
}
