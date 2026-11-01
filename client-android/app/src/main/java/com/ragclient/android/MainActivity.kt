package com.ragclient.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ragclient.android.core.designsystem.RagClientTheme
import com.ragclient.android.feature.splash.SplashGate
import com.ragclient.android.navigation.RagRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RagClientTheme {
                SplashGate {
                    RagRoot()
                }
            }
        }
    }
}
