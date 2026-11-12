package com.example.netspeed.core.network

import android.net.Network
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds OkHttp clients whose sockets AND DNS lookups are pinned to one Android [Network].
 *
 * - socketFactory = network.socketFactory  -> every TCP socket is bound to that network
 * - dns = network.getAllByName             -> name resolution also goes through that network
 * - own ConnectionPool per network         -> a pooled Wi-Fi connection can never be reused
 *   for a request that is meant for mobile data (OkHttp's newBuilder() shares the pool otherwise)
 * - HTTP/1.1 only                          -> each worker gets its own TCP connection,
 *   instead of being multiplexed onto one HTTP/2 stream
 */
@Singleton
class NetworkBindingManager @Inject constructor() {

    private val clients = ConcurrentHashMap<Long, OkHttpClient>()

    private val base = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun clientFor(network: Network): OkHttpClient =
        clients.getOrPut(network.networkHandle) { build(network) }

    /** Drop the cached client when a network is lost so a stale pool is not reused. */
    fun forget(network: Network?) {
        network ?: return
        clients.remove(network.networkHandle)?.let {
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
        }
    }

    private fun build(network: Network): OkHttpClient =
        base.newBuilder()
            .socketFactory(network.socketFactory)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    network.getAllByName(hostname).toList()
            })
            .proxy(Proxy.NO_PROXY)
            .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
}
