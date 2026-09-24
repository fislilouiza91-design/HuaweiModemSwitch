package com.example.huaweimodemswitch.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

sealed class SwitchResult {
    data class Success(val message: String) : SwitchResult()
    data class Error(val message: String) : SwitchResult()
}

object HuaweiModeSwitcher {

    const val TARGET_VID = 0x12D1   // HUAWEI
    const val TARGET_PID = 0x1F01   // storage-mode Huawei 3G modem

    /*
     * THE COMMAND (real, not a placeholder):
     *
     * This is the "Huawei mode switch" used by usb_modeswitch for device
     * reference 12d1:1f01 ("HuaweiMode = 1"):
     *
     *   usb_modeswitch -v 0x12d1 -p 0x1f01 -H
     *
     * which sends exactly one USB control transfer:
     *
     *   libusb_control_transfer(devh,
     *       LIBUSB_REQUEST_TYPE_STANDARD,  // bmRequestType = 0x00 (standard, recipient=device, host->device)
     *       0x01,                          // bRequest
     *       0x0001,                        // wValue
     *       0x0000,                        // wIndex
     *       NULL, 0, 1000);                // no data stage, 1 s timeout
     *
     * The device interprets bRequest=0x01 with wValue=0x0001 as
     * "enable modem mode". It then detaches from the bus and re-enumerates
     * with a different product ID exposing CDC-serial and/or RNDIS
     * interfaces. There is NO data stage and NO bulk transfer involved.
     */
    private const val HUAWEI_BM_REQUEST_TYPE = 0x00  // standard / device / host-to-device
    private const val HUAWEI_B_REQUEST = 0x01
    private const val HUAWEI_W_VALUE = 0x0001
    private const val HUAWEI_W_INDEX = 0x0000
    private const val TIMEOUT_MS = 1000

    /**
     * Sends the Huawei mode-switch control request. Returns an error string
     * describing any failure; success only means the request was ACKed by the
     * device — the actual mode change is verified later by re-enumeration.
     */
    fun switchToModemMode(usbManager: UsbManager, device: UsbDevice): SwitchResult {
        if (device.vendorId != TARGET_VID || device.productId != TARGET_PID) {
            return SwitchResult.Error(
                "Refusing to switch: attached device is VID 0x%04X / PID 0x%04X, " +
                    "expected 0x%04X / 0x%04X."
                    .format(device.vendorId, device.productId, TARGET_VID, TARGET_PID)
            )
        }

        val connection = try {
            usbManager.openDevice(device)
        } catch (sec: SecurityException) {
            return SwitchResult.Error("openDevice() threw SecurityException — USB permission not granted.")
        } ?: return SwitchResult.Error("openDevice() returned null — USB permission not granted?")

        return try {
            val usbInterface = device.getInterface(0)
            if (!connection.claimInterface(usbInterface, true)) {
                return SwitchResult.Error("claimInterface(interface 0) failed — is another app using the device?")
            }
            try {
                val ret = connection.controlTransfer(
                    HUAWEI_BM_REQUEST_TYPE,
                    HUAWEI_B_REQUEST,
                    HUAWEI_W_VALUE,
                    HUAWEI_W_INDEX,
                    null,   // no data stage
                    0,
                    TIMEOUT_MS,
                )
                if (ret >= 0) {
                    SwitchResult.Success(
                        "Huawei mode-switch control transfer sent " +
                            "(bmRequestType=0x%02X bRequest=0x%02X wValue=0x%04X wIndex=0x%04X), " +
                            "device ACKed (returned %d)."
                            .format(HUAWEI_BM_REQUEST_TYPE, HUAWEI_B_REQUEST, HUAWEI_W_VALUE, HUAWEI_W_INDEX, ret)
                    )
                } else {
                    SwitchResult.Error("controlTransfer() returned $ret (negative value = USB I/O error).")
                }
            } finally {
                connection.releaseInterface(usbInterface)
            }
        } finally {
            connection.close()
        }
    }
}
