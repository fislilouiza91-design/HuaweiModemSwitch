package com.example.huaweimodemswitch.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager

/** Maps android.hardware.usb objects into immutable display models. */
object UsbDeviceMapper {

    fun toInfo(device: UsbDevice, usbManager: UsbManager): UsbDeviceInfo {
        val interfaces = buildList {
            for (i in 0 until device.interfaceCount) {
                add(toInterfaceInfo(device.getInterface(i)))
            }
        }
        return UsbDeviceInfo(
            deviceId = device.deviceId,
            deviceName = device.deviceName,
            vid = device.vendorId,
            pid = device.productId,
            manufacturer = device.manufacturerName,
            product = device.productName,
            serialNumber = device.serialNumber,
            usbVersion = runCatching { device.version }.getOrNull(),
            deviceClass = device.deviceClass,
            deviceSubClass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            hasPermission = usbManager.hasPermission(device),
            isTargetHuawei = device.vendorId == HuaweiModeSwitcher.TARGET_VID &&
                device.productId == HuaweiModeSwitcher.TARGET_PID,
            interfaces = interfaces,
        )
    }

    private fun toInterfaceInfo(iface: android.hardware.usb.UsbInterface): UsbInterfaceInfo {
        val endpoints = buildList {
            for (i in 0 until iface.endpointCount) {
                add(toEndpointInfo(iface.getEndpoint(i)))
            }
        }
        val cls = iface.interfaceClass
        val sub = iface.interfaceSubclass

        // Serial candidates:
        //  - CDC ACM control interface (2/2/1) -> produces /dev/ttyACM*
        //  - CDC-Data interface (10/x/x)
        //  - Vendor-specific interface carrying a Bulk-IN + Bulk-OUT pair,
        //    which is how most Huawei 3G modems expose their AT/PPP ports.
        val hasBulkIn = endpoints.any { it.direction == "IN" && it.type == "Bulk" }
        val hasBulkOut = endpoints.any { it.direction == "OUT" && it.type == "Bulk" }
        val serial = (cls == UsbConstants.USB_CLASS_COMM && sub == 2) ||
            cls == UsbConstants.USB_CLASS_CDC_DATA ||
            (cls == UsbConstants.USB_CLASS_VENDOR_SPEC && hasBulkIn && hasBulkOut)

        // Network candidates:
        //  - RNDIS / wireless controller class (224/1/x) -> usb0/rndis0
        //  - CDC ECM (2/6/0) or CDC NCM (2/13/0)
        val network = (cls == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER && sub == 1) ||
            (cls == UsbConstants.USB_CLASS_COMM && (sub == 6 || sub == 13))

        return UsbInterfaceInfo(
            id = iface.id,
            interfaceClass = cls,
            subClass = sub,
            protocol = iface.interfaceProtocol,
            className = interfaceClassName(cls),
            endpoints = endpoints,
            isSerialCandidate = serial,
            isNetworkCandidate = network,
        )
    }

    private fun toEndpointInfo(ep: UsbEndpoint): UsbEndpointInfo {
        val direction = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
        val type = when (ep.type) {
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "Control"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "Isochronous"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "Bulk"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "Interrupt"
            else -> "Unknown(${ep.type})"
        }
        return UsbEndpointInfo(
            address = ep.address,
            endpointNumber = ep.endpointNumber,
            direction = direction,
            type = type,
            maxPacketSize = ep.maxPacketSize,
            interval = ep.interval,
        )
    }

    fun interfaceClassName(c: Int): String = when (c) {
        UsbConstants.USB_CLASS_PER_INTERFACE -> "Per-interface (defined at interface level)"
        UsbConstants.USB_CLASS_AUDIO -> "Audio"
        UsbConstants.USB_CLASS_COMM -> "Communications / CDC control"
        UsbConstants.USB_CLASS_HID -> "Human Interface Device"
        UsbConstants.USB_CLASS_PHYSICAL -> "Physical"
        UsbConstants.USB_CLASS_IMAGE -> "Image (PTP/MTP)"
        UsbConstants.USB_CLASS_PRINTER -> "Printer"
        UsbConstants.USB_CLASS_MASS_STORAGE -> "Mass Storage"
        UsbConstants.USB_CLASS_HUB -> "Hub"
        UsbConstants.USB_CLASS_CDC_DATA -> "CDC-Data"
        UsbConstants.USB_CLASS_CSCID -> "Smart Card"
        UsbConstants.USB_CLASS_CONTENT_SEC -> "Content Security"
        UsbConstants.USB_CLASS_VIDEO -> "Video"
        0x0F -> "Personal Healthcare"
        0x10 -> "Audio/Video"
        UsbConstants.USB_CLASS_DIAGNOSTIC -> "Diagnostic"
        UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "Wireless Controller (RNDIS)"
        UsbConstants.USB_CLASS_MISC -> "Miscellaneous"
        UsbConstants.USB_CLASS_APP_SPEC -> "Application Specific"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "Vendor Specific"
        else -> "Unknown ($c)"
    }
}
