package com.example.huaweimodemswitch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.huaweimodemswitch.usb.UsbDeviceInfo
import com.example.huaweimodemswitch.usb.UsbInterfaceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbScreen(viewModel: UsbViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Huawei Modem Switcher") }) },
    ) { padding ->
        if (state.devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No USB devices attached.\n\nConnect the Huawei modem via USB OTG\n" +
                        "(a USB-C OTG adapter is required on the Galaxy S21).",
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusBanner(state) }

            item { Text("Detected USB devices", style = MaterialTheme.typography.titleMedium) }
            items(state.devices, key = { it.deviceId }) { device ->
                DeviceCard(
                    device = device,
                    selected = device.deviceId == state.selectedDeviceId,
                    onClick = { viewModel.selectDevice(device.deviceId) },
                )
            }

            state.selectedDevice?.let { device ->
                item { DiagnosticsCard(device) }
                item { ActionButtons(state, device, viewModel) }
            }

            state.postSwitchDevice?.let { device ->
                item { PostSwitchCard(device) }
            }

            item { LogCard(state.log) }
        }
    }
}

@Composable
private fun StatusBanner(state: UsbUiState) {
    val (container, message) = when {
        state.awaitingReconnect ->
            Color(0xFFFFF3CD) to
                "SWITCH COMMAND SENT. Unplug the modem, wait ~10 seconds, then reconnect it. " +
                "Success is confirmed ONLY when the device re-appears with a new VID/PID."
        state.postSwitchDevice != null ->
            Color(0xFFD4EDDA) to "MODE SWITCH CONFIRMED — see details below."
        state.lastSwitchResult != null ->
            Color(0xFFF8D7DA) to (state.lastSwitchResult + "")
        else ->
            MaterialTheme.colorScheme.surfaceVariant to
                "Attach the Huawei modem (VID 0x12D1 / PID 0x1F01) and grant USB permission."
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeviceCard(device: UsbDeviceInfo, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.deviceName, style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace)
                Text("${device.vidHex} : ${device.pidHex}  —  ${device.product ?: "unknown product"}")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (device.hasPermission) "permission OK" else "no permission",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (device.isTargetHuawei) {
                    Text("TARGET", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(device: UsbDeviceInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            DetailRow("VID / PID", "${device.vidHex} (${device.vid})  /  ${device.pidHex} (${device.pid})")
            DetailRow("Manufacturer", device.manufacturer ?: "n/a")
            DetailRow("Product", device.product ?: "n/a")
            DetailRow("Serial number", device.serialNumber ?: "n/a")
            DetailRow("USB version", device.usbVersion ?: "n/a")
            DetailRow("Device class",
                "%d / %d / %d".format(device.deviceClass, device.deviceSubClass, device.deviceProtocol))
            DetailRow("USB permission", if (device.hasPermission) "GRANTED" else "NOT GRANTED")

            HorizontalDivider()
            Text("Interfaces (${device.interfaces.size})",
                style = MaterialTheme.typography.titleSmall)
            device.interfaces.forEach { InterfaceCard(it) }
        }
    }
}

@Composable
private fun InterfaceCard(iface: UsbInterfaceInfo) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Interface #${iface.id}   class/subclass/protocol = ${iface.descriptor}",
                style = MaterialTheme.typography.titleSmall)
            Text(iface.className, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (iface.isSerialCandidate) {
                    Text("· serial candidate", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium)
                }
                if (iface.isNetworkCandidate) {
                    Text("· network candidate", color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("Endpoints (${iface.endpoints.size}):",
                style = MaterialTheme.typography.labelLarge)
            iface.endpoints.forEach { ep ->
                Text(
                    "  EP 0x%02X (#%d)  %s  %-10s maxPacket=%d  interval=%d"
                        .format(ep.address, ep.endpointNumber, ep.direction, ep.type,
                            ep.maxPacketSize, ep.interval),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(state: UsbUiState, device: UsbDeviceInfo, viewModel: UsbViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::refreshDevices) { Text("Refresh") }
            Button(
                onClick = viewModel::requestPermissionForSelected,
                enabled = !device.hasPermission,
            ) { Text("Request USB Permission") }
        }
        Button(
            onClick = viewModel::switchSelectedToModem,
            enabled = device.hasPermission && device.isTargetHuawei && !state.switching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.switching) "Sending switch command…" else "Switch to Modem Mode")
        }
        if (!device.isTargetHuawei) {
            Text(
                "The switch button is enabled only for VID 0x12D1 / PID 0x1F01 (storage mode).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PostSwitchCard(device: UsbDeviceInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD4EDDA))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mode switch confirmed", style = MaterialTheme.typography.titleMedium)
            DetailRow("New VID / PID", "${device.vidHex} (${device.vid}) / ${device.pidHex} (${device.pid})")
            val serialCount = device.interfaces.count { it.isSerialCandidate }
            val netCount = device.interfaces.count { it.isNetworkCandidate }
            DetailRow("Serial interfaces", "$serialCount")
            DetailRow("Network interfaces", "$netCount")
            Text(
                when {
                    netCount > 0 ->
                        "A USB network interface is present — Android should bring up usb0/rndis0 " +
                            "and you can enable USB tethering / use it as a data connection."
                    serialCount > 0 ->
                        "Serial (CDC / vendor bulk) interfaces are present — dial-up/AT access needs " +
                            "a USB-serial aware app with USB permission (ports appear as /dev/ttyACM* " +
                            "or /dev/ttyUSB*)."
                    else ->
                        "No serial or network interfaces were detected — inspect the interface list " +
                            "in Diagnostics above."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Log", style = MaterialTheme.typography.titleMedium)
            log.asReversed().forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(130.dp),
            style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(0f))
        Text(value, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium)
    }
}
