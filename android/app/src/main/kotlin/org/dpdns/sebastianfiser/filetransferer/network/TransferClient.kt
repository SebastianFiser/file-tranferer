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
import android.content.ContentResolver
import kotlinx.coroutines.delay
import org.dpdns.sebastianfiser.filetransferer.files.PickedFile
import org.dpdns.sebastianfiser.filetransferer.files.queryFile
import org.dpdns.sebastianfiser.filetransferer.protocol.ErrorData

//why isnt hackatime tracking
private const val TAG = "TransferClient"
const val FLAG_LAST = 0x01
// Flag_last not integrated on server, once a whole batch arrives, connection closes
class TransferClient(
    private val deviceName: String
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Envelope>>()
    @Volatile private var ready = CompletableDeferred<Unit>()

    private val serverMessages = Channel<Envelope>(Channel.UNLIMITED)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val offered = ConcurrentHashMap<String, PickedFile>()

    fun connect(url: String) {
        ready = CompletableDeferred()
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendHello(deviceName)

                Log.d("TransferClient", buildChunk("f1", 0, FLAG_LAST, ByteArray(10)).joinToString(" ") { "%02x".format(it) })

            }// COMPILE YOU DUMB THING

            override fun onMessage(webSocket: WebSocket, text: String) {
                val envelope = Json.decodeFromString<Envelope>(text)
                if(envelope.type == "hello_ack") ready.complete(Unit)
                val waiter = pending[envelope.id]
                if (waiter != null) {
                    waiter.complete(envelope)
                } else {
                    Log.d("TransferClient", "server-initiated: ${envelope.type}")
                    serverMessages.trySend(envelope)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TransferClient", "onClosing code $code reason $reason")
            } // lets lock in
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                //lemme make a coffee first chat
                Log.d("TransferClient", "onClosed code=$code reason=$reason")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TransferClient", "onFailiure", t)
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
        Log.d("TransferClient", "offer response type=${response.type}")
        if (response.type == "error") {
            val err = Json.decodeFromJsonElement<ErrorData>(response.data)
            error("server: ${err.code} : ${err.message}")
        }
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
        withTimeout(timeoutMs) { //<- its this ahole i knew it
            var msg = serverMessages.receive() //HAHAHAH it compiled!
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

    suspend fun sendPickedFiles(resolver: ContentResolver, picked: List<PickedFile>) {
        withTimeout(5000) { ready.await() } // <- this lil bitchass is closing my webhsocket
        offered.clear()
        val infos = picked.mapIndexed { i, p ->
            val id = "${i + 1}"
            offered[id] = p
            FileInfo(p.name.substringAfterLast('/'), p.size, id)
        }


        val ack = sendOfferFiles(infos)
        Log.d("TransferClient", "offer accepted, transfer_id=${ack.transferId}" )

        val req = awaitServerMessage("request_files")
        val wanted = Json.decodeFromJsonElement<RequestFilesData>(req.data)

        for (id in wanted.fileIds) {
            val file = offered[id] ?: error("Server asked for unknown file $id")
            sendFile(resolver, file, id)
        }

        if (webSocket?.queueSize() > 0) delay(10)
        val totalBytes = wanted.fileIds.sumOf { id -> offered[id]?.size ?: 0L }
        val timeoutMs: Long = calscTimeoutMs(totalBytes)

        val done = awaitServerMessage("transfer_complete", timeoutMs)
            Log.d("TransferClient",  "server says:  ${done.type} ${done.data}")
    }

    //i genuenlymlove macondo, thanks to them i have thing at age i could never afford :)

    private suspend fun sendFile(resolver: ContentResolver, picked: PickedFile, fileId: String) {
        val ws = webSocket ?: error("Not connected")

        if (picked.size == 0L) {
            ws.send(buildChunk(fileId, 0L, FLAG_LAST, ByteArray(0)).toByteString())
            return
        }

        val buffer = ByteArray(64 * 1024)
        var offset = 0L
        val input = resolver.openInputStream(picked.uri) ?: error("Cannot open ${picked.name}")
        //my fuckass cat stepped on my keyboared AGAIN

        input.use {
            while(true) {
                val n = it.read(buffer)
                if (n <= 0) break
                val isLast = offset + n == picked.size
                val flags = if (isLast) FLAG_LAST else 0 //hmmm, this is weird
                while (ws.queueSize() > 4L * 1024 * 1024) delay(10)
                var ret = ws.send(buildChunk(fileId, offset, flags, buffer.copyOf(n)).toByteString())
                Log.d("TransferClient", "send returned $ret")
                offset += n
            }
        }
    }

    private fun calscTimeoutMs(totalBytes: Long): Long {
        val TimeoutBaseMs = 5_000L
        val BytesPerBase = 1 * 1024L * 1024L  //this will prolly ba aight

        return TimeoutBaseMs + totalBytes * 1000L / BytesPerBase
    }

}
