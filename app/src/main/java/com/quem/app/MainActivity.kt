package com.quem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quem.ui.theme.QueMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val queueRepository = (application as QueMApplication).dependencies.queueRepository
        setContent {
            QueMTheme {
                QueMApp(queueRepository = queueRepository)
            }
        }
    }
}
