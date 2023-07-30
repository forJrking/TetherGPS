package com.mazda.tgps

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mazda.tgps.ui.Client
import com.mazda.tgps.ui.GreetingChose
import com.mazda.tgps.ui.Server
import com.mazda.tgps.ui.theme.MVIHiltTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val CHOSE = "CHOSE"
        const val SERVER = "SERVER"
        const val CLIENT = "CLIENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MVIHiltTheme {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background,
                ) {
                    NavHost(navController = navController, startDestination = CHOSE) {
                        composable(CHOSE) {
                            GreetingChose(
                                onNavigateToClient = {
                                    navController.navigate(CLIENT)
                                },
                                onNavigateToServer = {
                                    navController.navigate(SERVER)
                                }
                            )
                        }
                        composable(SERVER) { Server(navController) }
                        composable(CLIENT) { Client(navController) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onResume()
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }
}