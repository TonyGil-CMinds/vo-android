package com.example.miprimeraapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.miprimeraapp.ui.theme.MiPrimeraAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La experiencia abre sobre fondo claro: iconos oscuros desde el primer frame.
        val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xFF282727.toInt())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            MiPrimeraAppTheme {
                ExperienceFlow()
            }
        }
    }
}
