package com.jhonatan.notas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jhonatan.notas.ui.login.LoginScreen
import com.jhonatan.notas.ui.notas.NotasScreen

object NotasRoutes {
    const val LOGIN = "login"
    const val NOTAS = "notas"
}

@Composable
fun NotasNavHost(appViewModel: AppViewModel = hiltViewModel()) {
    val startDestination by appViewModel.startDestination.collectAsStateWithLifecycle()
    val sessionEndedMessage by appViewModel.sessionEndedMessage.collectAsStateWithLifecycle()

    when (startDestination) {
        StartDestination.LOADING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        StartDestination.LOGIN, StartDestination.NOTAS -> {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }
            val resolvedStart = if (startDestination == StartDestination.NOTAS) NotasRoutes.NOTAS else NotasRoutes.LOGIN

            // Dispositivo revocado: PowerSync ya se desconecto y limpio (SessionManager).
            // Aqui solo saltamos a login y avisamos con un mensaje.
            LaunchedEffect(sessionEndedMessage) {
                val message = sessionEndedMessage ?: return@LaunchedEffect
                navController.navigate(NotasRoutes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
                snackbarHostState.showSnackbar(message)
                appViewModel.consumeSessionEndedMessage()
            }

            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = resolvedStart,
                    modifier = Modifier.padding(paddingValues),
                ) {
                    composable(NotasRoutes.LOGIN) {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate(NotasRoutes.NOTAS) {
                                    popUpTo(NotasRoutes.LOGIN) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(NotasRoutes.NOTAS) { NotasScreen() }
                }
            }
        }
    }
}
