package com.example.huaweimodemswitch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.huaweimodemswitch.ui.UsbScreen
import com.example.huaweimodemswitch.ui.UsbViewModel
import com.example.huaweimodemswitch.ui.theme.HuaweiModemSwitchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: UsbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HuaweiModemSwitchTheme {
                UsbScreen(viewModel)
            }
        }
        // Handle a cold start triggered by USB_DEVICE_ATTACHED.
        viewModel.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a new USB attach arrives here when the activity is alive.
        viewModel.handleIntent(intent)
    }
}
