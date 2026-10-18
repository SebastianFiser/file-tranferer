package org.dpdns.sebastianfiser.filetransferer.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ServerAddress(val host: String, val port: Int)

class ServerDiscovery(context: Context) {
    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    suspend fun find(timeoutMs: Long = 8_000L): ServerAddress? =
        withTimeoutOrNull(timeoutMs) { discover() }

    @Suppress("DEPRECATION")
    private suspend fun discover(): ServerAddress? =
        suspendCancellableCoroutine { cont ->

            val running = AtomicBoolean(true)
            lateinit var discoveryListener: NsdManager.DiscoveryListener

            fun stop() {
                if (running.compareAndSet(true, false)) {
                    try {
                        nsd.stopServiceDiscovery(discoveryListener)
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "stopServiceDiscovery failed", e)
                    }
                }
            }

            fun newResolverListener() = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed: ${info.serviceName}, code=$errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    Log.d(TAG, "resolved $host:${info.port}")
                    stop()
                    if (cont.isActive) cont.resume(ServerAddress(host, info.port))
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "discovery started: $serviceType")
                }

                override fun onServiceFound(info: NsdServiceInfo) {
                    Log.d(TAG, "found=${info.serviceName}, type=${info.serviceType}")

                    if (info.serviceType.trimEnd('.') == SERVICE_TYPE) {
                        nsd.resolveService(info, newResolveListener())
                    }
                }

                override fun onServiceLost(info: NsdServiceInfo) {
                    Log.d(TAG, "lost ${info.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "discovery stopped: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "start failed, code=$errorCode")
                    running.set(false)
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("NSD start failed: $errorCode"))

                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "stop faileed, code=$errorCode")
                }

            }

            cont.invokeOnCancellation { stop() }

            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        }

    private companion object {
        const val TAG = "Nsd"
        const val SERVICE_TYPE = "_filetransferer._tcp"
    }

}
