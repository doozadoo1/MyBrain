package com.mhss.app.mybrain.sync

import com.mhss.app.mybrain.sync.domain.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

private const val LINK_LOCAL_IPV4_PREFIX = "169.254"

@Single
class NetworkHelperJvm : NetworkHelper {

    override fun getAllLocalIpAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                // Ignore interfaces that are down or are loopback adapters
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val name = networkInterface.name.lowercase()
                // Exclude virtual/cellular-ish interfaces
                if (name.startsWith("ppp") || name.startsWith("tun") || name.startsWith("tap")) continue

                for (address in networkInterface.inetAddresses) {
                    if (!address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        val isIPv4 = host.indexOf(':') < 0
                        if (isIPv4 && !host.startsWith(LINK_LOCAL_IPV4_PREFIX)) {
                            result.add(host)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return result.distinct().sortedBy { it.startsWith("100.") }
    }

    override fun getPrimaryLocalIpAddress(): String? {
        return getAllLocalIpAddresses().firstOrNull()
    }

    override suspend fun tryConnect(ip: String, port: Int, timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs.toInt())
                true
            }
        }.getOrDefault(false)
    }

    override suspend fun findReachableIp(
        primaryIp: String?,
        candidateIps: List<String>,
        port: Int
    ): String? = withContext(Dispatchers.IO) {
        if (!primaryIp.isNullOrBlank()) {
            if (tryConnect(primaryIp, port)) return@withContext primaryIp
        }

        for (ip in candidateIps) {
            if (ip.isBlank() || ip == primaryIp) continue
            if (tryConnect(ip, port)) return@withContext ip
        }

        null
    }

    override fun observeNetworkChanges(): Flow<List<String>> = flow {
        var previous: List<String> = emptyList()
        emit(getAllLocalIpAddresses())
        while (true) {
            delay(NETWORK_POLL_INTERVAL_MS)
            val current = getAllLocalIpAddresses()
            if (current != previous) {
                previous = current
                emit(current)
            }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        private const val NETWORK_POLL_INTERVAL_MS = 15_000L
    }
}
