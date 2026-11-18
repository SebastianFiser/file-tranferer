package org.dpdns.sebastianfiser.filetransferer.network

const val DEFAULT_PORT = 5000

fun parseServerAddress(raw: String): String? {
    val t  = raw.trim().removePrefix("ws://").removeSuffix("/ws").trimEnd('/')
    if (t.isEmpty()) return null

    val host: String
    val port: Int

    if (t.startsWith("[")) {
        val end = t.indexOf("]")
        if (end < 0) return null
        host = t.substring(0, end + 1)
        val rest = t.substring(end + 1)
        port  = when {
            rest.isEmpty() -> DEFAULT_PORT
            rest.startsWith(":") -> rest.drop(1).toIntOrNull() ?: return null
            else -> return null
        }
    } else if (t.count { it == ':' } == 1) {
        host = t.substringBefore(':')
        port = t.substringAfter(':').toIntOrNull() ?: return null
    } else if (t.contains(':')) {
        host = "[$t]"
        port = DEFAULT_PORT
    } else {
        host = t
        port = DEFAULT_PORT
    }
    
    if (host.isEmpty() || port !in 1..65535) return null
    return "ws://$host:$port/ws"
}