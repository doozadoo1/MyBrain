package com.mhss.app.mybrain.sync

import com.mhss.app.mybrain.sync.repository.DeviceKeyStore
import com.mhss.app.mybrain.sync.repository.PairedDevicesRepository
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import com.mhss.app.preferences.domain.use_case.SavePreferenceUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Single
class DeviceKeyStoreJvm(
    private val getPreference: GetPreferenceUseCase,
    private val savePreference: SavePreferenceUseCase,
    private val pairedDevicesRepository: PairedDevicesRepository
) : DeviceKeyStore {

    private val deviceIdKey = stringPreferencesKey("sync_device_id")
    private val deviceCustomNameKey = stringPreferencesKey("sync_custom_device_name")
    private val deviceIdMutex = Mutex()

    override suspend fun getCurrentDeviceId(): String {
        val current = getPreference(deviceIdKey, "").first()
        if (current.isNotBlank()) return current
        return deviceIdMutex.withLock {
            val existing = getPreference(deviceIdKey, "").first()
            if (existing.isNotBlank()) {
                existing
            } else {
                UUID.randomUUID().toString().also {
                    savePreference(deviceIdKey, it)
                }
            }
        }
    }

    override suspend fun getCurrentDeviceName(): String {
        val customName = getPreference(deviceCustomNameKey, "").first()
        if (customName.isNotBlank()) return customName
        return try {
            val hostName = InetAddress.getLocalHost().hostName
            if (hostName.isNullOrBlank()) "Desktop Device" else hostName
        } catch (_: Exception) {
            "Desktop Device"
        }
    }

    override suspend fun updateCurrentDeviceName(name: String) {
        savePreference(deviceCustomNameKey, name)
    }

    override fun generateEncryptionKey(): String {
        val bytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    override suspend fun getDeviceKey(deviceId: String): String? {
        return pairedDevicesRepository.getPairedDevice(deviceId)?.encryptionKey
    }

    override suspend fun getCurrentDeviceVersion(): Int = 1
}
