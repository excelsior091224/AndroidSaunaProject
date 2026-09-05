package com.totonoi.sauna.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.totonoi.sauna.wear.ui.SaunaApp
import com.totonoi.sauna.wear.ui.TotonoiWearTheme

class MainActivity : ComponentActivity() {

    private val requestBodySensors = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒否された場合は心拍データが取得できないだけで、UIはそのまま動作させる */ }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒否されても計測自体は継続できるようにする */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestBodySensors.launch(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TotonoiWearTheme {
                SaunaApp()
            }
        }
    }
}
