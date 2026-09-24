package com.example.huaweimodemswitch.ui

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.huaweimodemswitch.usb.HuaweiModeSwitcher
import com.example.huaweimodemswitch.usb.SwitchResult
import com.example.huaweimodemswitch.usb.UsbDeviceInfo
import com.example.huaweimodemswitch.usb.UsbDeviceMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsbViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.huaweimodemswitch.USB_PERMISSION"
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }

    private val usbManager: UsbManager =
        application.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _uiState = MutableStateFlow(UsbUiState())
    val uiState: StateFlow<UsbUiState> = _uiState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    IntentCompat
                        .getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        ?.let { onDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    IntentCompat
                        .getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        ?.let { onDeviceDetached(it) }
                }
                ACTION_USB_PERMISSION -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    onPermissionResult(device, granted)
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        refreshDevices()
        log("USB host monitoring started. Target: VID 0x12D1 / PID 0x1F01 (Huawei storage mode).")
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
        super.onCleared()
    }

    /** Called by MainActivity for USB_DEVICE_ATTACHED launch/new-intent events. */
    fun handleIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        IntentCompat
            .getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            ?.let { onDeviceAttached(it) }
    }

    fun refreshDevices() {
        val infos = usbManager.deviceList.values.map { UsbDeviceMapper.toInfo(it, usbManager) }
        _uiState.update { state ->
            val selected = state.selectedDeviceId
                ?.takeIf { id -> infos.any { it.deviceId == id } }
                ?: infos.firstOrNull { it.isTargetHuawei }?.deviceId
                ?: infos.firstOrNull()?.deviceId
            state.copy(devices = infos, selectedDeviceId = selected)
        }
    }

    fun selectDevice(deviceId: Int) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
    }

    /** Triggers the system USB permission dialog for the selected device. */
    fun requestPermissionForSelected() {
        val id = _uiState.value.selectedDeviceId ?: return
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == id } ?: run {
            log("ERROR: selected device $id is no longer attached.")
            return
        }
        log("Requesting USB permission for %s (VID 0x%04X / PID 0x%04X)..."
            .format(device.deviceName, device.vendorId, device.productId))
        val intent = Intent(ACTION_USB_PERMISSION)
            .setPackage(getApplication<Application>().packageName)
        // USB permission intents MUST be mutable (Android 12+ requirement).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pendingIntent =
            PendingIntent.getBroadcast(getApplication(), device.deviceId, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    /** Sends the Huawei mode-switch control transfer on a background thread. */
    fun switchSelectedToModem() {
        val id = _uiState.value.selectedDeviceId ?: return
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(switching = true) }
            log("Attempting mode switch on VID 0x%04X / PID 0x%04X..."
                .format(device.vendorId, device.productId))
            when (val result = HuaweiModeSwitcher.switchToModemMode(usbManager, device)) {
                is SwitchResult.Success -> {
                    log(result.message)
                    log("Command ACKed by the device. It should now disconnect and re-enumerate as a modem.")
                    log(">>> UNPLUG THE MODEM, WAIT ~10 SECONDS, THEN RECONNECT IT <<<")
                    _uiState.update {
                        it.copy(
                            switching = false,
                            awaitingReconnect = true,
                            postSwitchDevice = null,
                            lastSwitchResult = "Command sent — waiting for re-enumeration. " +
                                "Unplug and reconnect the modem to verify.",
                        )
                    }
                }
                is SwitchResult.Error -> {
                    log("ERROR: ${result.message}")
                    _uiState.update {
                        it.copy(
                            switching = false,
                            awaitingReconnect = false,
                            lastSwitchResult = "Switch failed: ${result.message}",
                        )
                    }
                }
            }
        }
    }

    private fun onDeviceAttached(device: UsbDevice) {
        val isTarget = device.vendorId == HuaweiModeSwitcher.TARGET_VID &&
            device.productId == HuaweiModeSwitcher.TARGET_PID
        log("USB ATTACHED: %s  VID 0x%04X / PID 0x%04X%s"
            .format(device.deviceName, device.vendorId, device.productId,
                if (isTarget) "  <-- target (storage mode)" else ""))

        val state = _uiState.value
        if (state.awaitingReconnect) {
            val stillTarget = isTarget
            if (!stillTarget) {
                // The device came back with a DIFFERENT identity: the switch worked.
                val info = UsbDeviceMapper.toInfo(device, usbManager)
                log("SUCCESS: device re-enumerated with NEW identity " +
                    "VID 0x%04X / PID 0x%04X — the mode switch is CONFIRMED."
                    .format(device.vendorId, device.productId))
                log(describePostSwitchInterfaces(info))
                _uiState.update {
                    it.copy(
                        awaitingReconnect = false,
                        postSwitchDevice = info,
                        selectedDeviceId = device.deviceId,
                        lastSwitchResult = "CONFIRMED: device is now VID " +
                            "0x%04X / PID 0x%04X".format(device.vendorId, device.productId),
                    )
                }
            } else {
                log("WARNING: re-attached device still shows the ORIGINAL VID/PID (storage mode). " +
                    "The switch did NOT take effect.")
                _uiState.update {
                    it.copy(
                        awaitingReconnect = false,
                        postSwitchDevice = null,
                        lastSwitchResult = "NOT confirmed: device is still in storage mode.",
                    )
                }
            }
        }
        refreshDevices()
    }

    private fun describePostSwitchInterfaces(info: UsbDeviceInfo): String = buildString {
        val serial = info.interfaces.count { it.isSerialCandidate }
        val net = info.interfaces.count { it.isNetworkCandidate }
        append("Interface check: ${info.interfaces.size} interface(s) — ")
        append(if (serial > 0) "serial candidate(s): $serial; " else "no serial interface; ")
        append(if (net > 0) "USB network interface(s): $net." else "no network interface.")
    }

    private fun onDeviceDetached(device: UsbDevice) {
        log("USB DETACHED: %s  VID 0x%04X / PID 0x%04X"
            .format(device.deviceName, device.vendorId, device.productId))
        if (_uiState.value.awaitingReconnect) {
            log("Device disconnected after the switch command — expected. Reconnect it to verify.")
        }
        refreshDevices()
    }

    private fun onPermissionResult(device: UsbDevice?, granted: Boolean) {
        if (device == null) {
            log("USB permission result received with no device extra.")
            return
        }
        log(if (granted) "USB permission GRANTED for ${device.deviceName}."
            else "USB permission DENIED for ${device.deviceName}.")
        refreshDevices()
    }

    private fun log(message: String) {
        val line = "[${TIME_FMT.format(Date())}] $message"
        _uiState.update { it.copy(log = (it.log + line).takeLast(300)) }
    }
}
