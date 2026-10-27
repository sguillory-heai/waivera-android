package com.waivera.waivera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.ui.TodayPlaceholderScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WaiveraTheme {
                TodayPlaceholderScreen()
            }
        }
    }
}
