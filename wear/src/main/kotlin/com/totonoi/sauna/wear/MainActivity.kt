package com.totonoi.sauna.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.wear.compose.material.MaterialTheme
import com.totonoi.sauna.wear.ui.SaunaApp

class MainActivity : ComponentActivity() {

    private val requestBodySensors = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒否された場合は心拍データが取得できないだけで、UIはそのまま動作させる */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBodySensors.launch(Manifest.permission.BODY_SENSORS)

        setContent {
            MaterialTheme {
                SaunaApp()
            }
        }
    }
}
