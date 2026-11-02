package com.mhss.app.mybrain.sync

import com.mhss.app.mybrain.sync.domain.DiscoveredDevice
import com.mhss.app.mybrain.sync.domain.NetworkDiscoveryManager
import org.koin.core.annotation.Single

/**
 * Phase 1: no local-network (NSD/mDNS) service discovery on desktop. Devices
 * pair by manually entering the IP address instead.
 */
@Single(binds = [NetworkDiscoveryManager::class])
class NetworkDiscoveryManagerJvm : NetworkDiscoveryManager {

    override fun registerService(deviceId: String, port: Int) {
        // Not supported on desktop in Phase 1.
    }

    override fun startDiscovery(onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        // Not supported on desktop in Phase 1.
    }
}
