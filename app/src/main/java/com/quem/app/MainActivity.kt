package com.quem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quem.ui.theme.QueMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = (application as QueMApplication).dependencies
        setContent {
            QueMTheme {
                QueMApp(
                    queueRepository = dependencies.queueRepository,
                    driveConnectionRepository = dependencies.driveConnectionRepository
                )
            }
        }
    }
}
