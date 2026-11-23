package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.PowerScopeRoot
import com.example.ui.theme.PowerScopeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PowerScopeApp
        setContent {
            PowerScopeTheme {
                PowerScopeRoot(repository = app.powerRepository)
            }
        }
    }
}
