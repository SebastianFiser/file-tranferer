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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import okio.ByteString.Companion.toByteString
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.dpdns.sebastianfiser.filetransferer.protocol.RequestFilesData

//why isnt hackatime tracking
private const val TAG = "TransferClient"
const val FLAG_LAST = 0x01

class TransferClient(
    private val deviceName: String
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Envelope>>()

    private val serverMessages = Channel<Envelope>(Channel.UNLIMITED)

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

                scope.launch {
                    val ack = sendOfferFiles(files)
                    Log.d("TransferClient", "offer accepted, transfer_id=${ack.transferId}")
                }

                Log.d("TransferClient", buildChunk("f1", 0, FLAG_LAST, ByteArray(10)).joinToString(" ") { "%02x".format(it) })

            }// COMPILE YOU DUMB THING

            override fun onMessage(webSocket: WebSocket, text: String) {
                val envelope = Json.decodeFromString<Envelope>(text)
                val waiter = pending[envelope.id]
                if (waiter != null) {
                    waiter.complete(envelope)
                } else {
                    serverMessages.trySend(envelope)
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

    suspend fun sendOfferFiles(files: List<FileInfo>): OfferAckData {
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

    suspend fun awaitServerMessage(type: String, timeoutMs: Long = 5000): Envelope =
        withTimeout(timeoutMs) {
            var msg = serverMessages.recieve()
            while(msg.type != type) msg = serverMessages.receive()//lowk lock in
            msg//i would like to watch a movie but i cant find it lowk
        }

    fun buildChunk(fileId: String, offset: Long, flags: Int, data: ByteArray): ByteArray {
        val idBytes = fileId.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(1 + idBytes.size + 8 + 1 + data.size)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put(idBytes.size.toByte())
        buf.put(idBytes)
        buf.putLong(offset)
        buf.put(flags.toByte())
        buf.put(data)
        return buf.array()
    }

    suspend fun sendTestTransfer() {
        val files = listOf(
            FileInfo("a.txt", 10, "f1"),//why is my life taking so long
            FileInfo("b.txt", 20, "f2")
        )

        val ack = sendOfferFiles(files)
        Log.d("TransferClient", "offer accepted, tzransfer_id=${ack.transferId}")

        val req = awaitServerMessage("request_files")
        val wanted = Json.decodeFromJsonElement<RequestFilesData>(req.data)

        val ws = webSocket ?: error("Not connected")
        for (id in wanted.fileIds) {
            val file = files.first { it.fileId == id }
            val data = ByteArray(file.size.toInt())
            val chunk = buildChunk(id, 0L, FLAG_LAST, data)
            ws.send(chunk.toByteString())
        }
    }

}
