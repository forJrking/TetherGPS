package com.mazda.tgps.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mazda.tgps.R
import com.mazda.tgps.service.LocationService
import com.mazda.tgps.service.ReceiveLocationListener
import com.mazda.tgps.utils.Utils.checkMockGPSLocation
import com.mazda.tgps.utils.Utils.getAddressPort
import com.mazda.tgps.utils.Utils.getIpAddressByWifi
import com.mazda.tgps.utils.Utils.isWifiConnected
import com.mazda.tgps.utils.getBroadcastAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun Client(navController: NavHostController) {
    val context = LocalContext.current
    val activeState = remember { mutableStateOf(false) }
    val locationState = remember { mutableStateOf(Location(LocationManager.GPS_PROVIDER)) }
    var isRunning by remember { mutableStateOf(false) }
    var openAlert by remember { mutableStateOf(false) }
    val scaffoldState: ScaffoldState = rememberScaffoldState()
    val coroutineScope: CoroutineScope = rememberCoroutineScope()

    val serviceConnection = remember {
        mutableStateOf(object : ServiceConnection {
            var service: LocationService? = null
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                (binder as? LocationService.LocalBinder)?.run {
                    service = this.locationService
                    service?.startClient(object : ReceiveLocationListener {
                        val addressPort by lazy { getBroadcastAddress(context).getAddressPort() }
                        override var ipv4: String = ""
                        override var port: Int = addressPort.second

                        override fun onLocationReceived(location: Location) {
                            locationState.value = location
                        }

                        override fun onStatusChanged(active: Boolean) {
                            activeState.value = active
                        }
                    })
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isRunning = false
                service?.stopClient()
                service = null
            }

            override fun onBindingDied(name: ComponentName?) {
                super.onBindingDied(name)
            }
        })
    }

    fun startClient() {
        val intent = Intent(context, LocationService::class.java)
        //发送信息给Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection.value, Context.BIND_AUTO_CREATE)
    }

    fun stopClient() {
        val intent = Intent(context, LocationService::class.java)
        context.stopService(intent)
        context.unbindService(serviceConnection.value)
    }

    val requester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
    }

    if (openAlert) {
        AlertDialog(
            onDismissRequest = {
                openAlert = false
            },
            title = {
                Text(text = stringResource(R.string.open_mock_location_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.open_mock_location_content,
                        stringResource(id = R.string.app_name)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    requester.launch(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    openAlert = false
                }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    openAlert = false
                }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                stopClient()
                                isRunning = false
                            } else {
                                navController.navigateUp()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.client) + " IP:${getIpAddressByWifi(context)}"
                    )
                }
            )
        }
    ) { paddingValues ->
        ColumnOrRow(paddingValues = paddingValues) {
            FloatingActionButton(
                modifier = Modifier
                    .size(150.dp)
                    .padding(16.dp),
                onClick = {
                    if (!isWifiConnected(context)) {
                        coroutineScope.launch {
                            when (scaffoldState.snackbarHostState.showSnackbar(
                                message = context.getString(R.string.connect_wifi),
                                actionLabel = context.getString(R.string.setting_network)
                            )) {
                                SnackbarResult.Dismissed -> {}
                                SnackbarResult.ActionPerformed -> {
                                    requester.launch(
                                        Intent(Settings.ACTION_WIFI_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                    } else if (checkMockGPSLocation(context)) {
                        if (isRunning) {
                            stopClient()
                        } else {
                            startClient()
                        }
                        isRunning = !isRunning
                    } else {
                        openAlert = true
                    }
                },
                backgroundColor = if (isRunning) MaterialTheme.colors.error else MaterialTheme.colors.secondary
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedVisibility(
                        activeState.value,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Shrink()
                    }
                    Text(
                        if (isRunning) stringResource(id = R.string.stop) else stringResource(id = R.string.start),
                        fontSize = 26.sp,
                        color = Color.White,
                        fontWeight = FontWeight.W800,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            AnimatedVisibility(visible = isRunning) {
                GpsBox(locationState.value)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRunning) {
                stopClient()
                isRunning = false
            }
        }
    }
}
