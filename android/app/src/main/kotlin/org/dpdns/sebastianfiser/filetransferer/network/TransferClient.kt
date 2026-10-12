package org.dpdns.sebastianfiser.filetransferer.network

import android.util.Log
import okhttp3.*
import org.dpdns.sebastianfiser.filetransferer.protocol.Envelope
import org.dpdns.sebastianfiser.filetransferer.protocol.HelloData
import org.dpdns.sebastianfiser.filetransferer.protocol.FileInfo
import org.dpdns.sebastianfiser.filetransferer.protocol.OfferFilesData
import org.dpdns.sebastianfiser.filetransferer.protocol.OfferAckData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutine.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private const val TAG = "TransferClient"

class TransferClient(
    private val deviceName: String
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Envelope>>()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendHello(deviceName)
                val files = listOf(
                    FileInfo("a.txt", 10, "f1"),
                    FileInfo("b.txt", 20, "f2")
                )

                scope.lauch {
                    val ack = sendOfferFiles(files)
                    Log.d("TransferClient", "offer accepted, transfer_id=${ack.transferId}")
                }

            }// COMPILE YOU DUMB THING

            override fun onMessage(webSocket: WebSocket, text: String) {
                val envelope = Json.decodeFromString<Envelope>(text)
                val waiter = pending[envelope.id]
                if (waiter != null) {
                    waiter.complete(envelope)
                } else {
                    Log.d("TransferClient", "server-initated: {envelope.type}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "error", t)
            }
        })
    }

    fun close() {
        webSocket?.close(1000, null)
    }

    fun sendHello(deviceName: String) {
        val hello = Envelope(
            id = UUID.randomUUID().toString(),
            type = "hello",
            data = Json.encodeToJsonElement(HelloData(deviceName, 1))
        )
        val ws = webSocket ?: error("Not Connected")
        ws.send(Json.encodeToString(hello))
    }

    susped fun sendOfferFiles(files: List<FileInfo>): OfferAckData {
        val data = Json.encodeToJsonElement(OfferFilesData(files))
        val response = request("offer_files", data)
        val ack = Json.decodeFromJsonElement<OfferAckData>(response.data)
        check(ack.accepted) { "server declined offer_files" }
        return ack
    }

    suspend fun request(type: String, data: JsonElement, timeoutMs: Long = 5000): Envelope  {
        val id = UUID.randomUUID().toString()
        val waiter = CompletableDeferred<Envelope>()
        pending[id] = waiter
        val text = Json.encodeToString(Envelope(id, type, data))
        Log.d("TransferClient", "out going id=$id type=$type")
        val ws = webSocket ?: error("Not Connected")
        ws.send(text)
        try {
            return withTimeout(timeoutMs) { waiter.await() }
        } finally {
            pending.remove(id)
        }
    }
}
