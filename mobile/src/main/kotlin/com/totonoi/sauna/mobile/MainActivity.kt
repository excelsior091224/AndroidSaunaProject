package com.totonoi.sauna.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.totonoi.sauna.mobile.theme.SaunaAppTheme
import com.totonoi.sauna.mobile.theme.ThemePreferences
import com.totonoi.sauna.mobile.ui.HistoryScreen

class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒否されても履歴一覧の表示自体は継続できるようにする */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val themePreferences = ThemePreferences(applicationContext)
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState()
            SaunaAppTheme(themeMode = themeMode) {
                HistoryScreen(themePreferences = themePreferences)
            }
        }
    }
}
