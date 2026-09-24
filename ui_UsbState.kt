package com.example.huaweimodemswitch.ui

import com.example.huaweimodemswitch.usb.UsbDeviceInfo

/** UI state for the main screen. */
data class UsbUiState(
    val devices: List<UsbDeviceInfo> = emptyList(),
    val selectedDeviceId: Int? = null,
    val switching: Boolean = false,
    /** True between sending the switch command and seeing the device re-enumerate. */
    val awaitingReconnect: Boolean = false,
    /** Populated ONLY after the device physically re-enumerated with a new VID/PID. */
    val postSwitchDevice: UsbDeviceInfo? = null,
    val lastSwitchResult: String? = null,
    val log: List<String> = emptyList(),
) {
    val selectedDevice: UsbDeviceInfo?
        get() = devices.firstOrNull { it.deviceId == selectedDeviceId }
}
