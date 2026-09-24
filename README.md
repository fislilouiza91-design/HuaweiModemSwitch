Huawei Modem Switcher (Android, Kotlin + Jetpack Compose)
=========================================================

Switches a Huawei 3G USB modem from Mass Storage mode to Modem/Network mode
on non-rooted Android phones via the USB Host API.

Target device (storage mode):  VID 0x12D1 (Huawei), PID 0x1F01.
USB version: Android 15 (API 35) target, minSdk 24. Tested design target:
Samsung Galaxy S21 + USB-C OTG adapter.

The switch command
------------------
This app implements the "Huawei mode switch" from usb_modeswitch
(device reference 12d1:1f01, "HuaweiMode = 1"):

    usb_modeswitch -v 0x12d1 -p 0x1f01 -H

which sends exactly ONE USB control transfer and nothing else
(no bulk transfer, no fake vendor payload):

    bmRequestType = 0x00  (standard, recipient=device, host->device)
    bRequest      = 0x01
    wValue        = 0x0001
    wIndex        = 0x0000
    data stage    = none

In code: connection.controlTransfer(0x00, 0x01, 0x01, 0x00, null, 0, 1000)

The device acknowledges, drops off the bus and re-enumerates with a new PID
exposing CDC-ACM serial and/or RNDIS network interfaces. The app NEVER claims
success until the device physically re-enumerates with a different VID/PID.

Build
-----
1. Open the project in Android Studio (Ladybug or newer).
2. Let Gradle sync (AGP 8.6.0, Kotlin 2.0.20, Compose BOM 2024.06.00).
3. Run on a USB-host capable device.
   No gradle wrapper jar is bundled; run `gradle wrapper` once if you build
   from the command line.

Usage
-----
1. Connect the modem through a USB-C OTG adapter (OTG power may be required;
   some modems draw too much current for a passive adapter).
2. Tap the device, then "Request USB Permission" and allow in the system dialog.
3. Inspect the Diagnostics card (VID/PID, descriptors, interfaces, endpoints).
4. Tap "Switch to Modem Mode".
5. When prompted, unplug the modem, wait ~10 seconds, reconnect.
6. The app shows the NEW VID/PID and reports whether serial (CDC/ACM or
   vendor bulk) and/or network (RNDIS/ECM/NCM) interfaces appeared.

Notes
-----
- No root is required: everything uses android.hardware.usb.UsbManager.
- USB host permission is per-device and per-app; Android shows its own
  permission dialog.
- If the switch "ACKs" but the device returns with the same PID, the app says
  so explicitly instead of claiming success.
