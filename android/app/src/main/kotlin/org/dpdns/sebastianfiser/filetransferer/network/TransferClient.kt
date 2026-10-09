package org.dpdns.sebastianfiser.filetransferer.network

import android.util.Log
import okhttp3.*
import org.dpdns.sebastianfiser.filetransferer.protocol.Envelope
import org.dpdns.sebastianfiser.filetransferer.protocol.HelloData
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

    private val jseon = Json

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = Envelope(
                    id = UUID.randomUUID().toString(),
                    type = "hello",
                    data = json.encodeToJsonElement(HelloData(deviceName, 1))
                )
                webSocket.send(json.encodeToString(hello))
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
