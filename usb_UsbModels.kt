package com.example.huaweimodemswitch.usb

/** Immutable snapshot of one USB endpoint, for display in the UI. */
data class UsbEndpointInfo(
    val address: Int,
    val endpointNumber: Int,
    val direction: String,   // "IN" or "OUT"
    val type: String,        // Control / Bulk / Interrupt / Isochronous
    val maxPacketSize: Int,
    val interval: Int,
)

/** Immutable snapshot of one USB interface, for display in the UI. */
data class UsbInterfaceInfo(
    val id: Int,
    val interfaceClass: Int,
    val subClass: Int,
    val protocol: Int,
    val className: String,
    val endpoints: List<UsbEndpointInfo>,
    val isSerialCandidate: Boolean,
    val isNetworkCandidate: Boolean,
) {
    val descriptor: String get() = "%d/%d/%d".format(interfaceClass, subClass, protocol)
}

/** Immutable snapshot of one USB device, for display in the UI. */
data class UsbDeviceInfo(
    val deviceId: Int,
    val deviceName: String,
    val vid: Int,
    val pid: Int,
    val manufacturer: String?,
    val product: String?,
    val serialNumber: String?,
    val usbVersion: String?,
    val deviceClass: Int,
    val deviceSubClass: Int,
    val deviceProtocol: Int,
    val hasPermission: Boolean,
    val isTargetHuawei: Boolean,
    val interfaces: List<UsbInterfaceInfo>,
) {
    val vidHex: String get() = "0x%04X".format(vid)
    val pidHex: String get() = "0x%04X".format(pid)
}
