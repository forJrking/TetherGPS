package com.mazda.tgps.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.SnackbarDefaults.backgroundColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
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
import com.mazda.tgps.socket.UNI_BROADCAST_IP
import com.mazda.tgps.utils.Utils.checkAddressPort
import com.mazda.tgps.utils.Utils.getAddressPort
import com.mazda.tgps.utils.getAutomaticBroadcastIp
import com.mazda.tgps.utils.getBroadcastAddress
import com.mazda.tgps.utils.setAutomaticBroadcastIp
import com.mazda.tgps.utils.setBroadcastAddress

@Composable
fun Server(navController: NavHostController) {
    val context = LocalContext.current
    var location by remember { mutableStateOf(Location(LocationManager.GPS_PROVIDER)) }
    var isRunning by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(getBroadcastAddress(context)) }
    var automaticIp by remember { mutableStateOf(getAutomaticBroadcastIp(context)) }
    val inputError = remember { mutableStateOf(false) }

    val serviceConnection = remember {
        mutableStateOf(object : ServiceConnection {
            var service: LocationService? = null
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                (binder as? LocationService.LocalBinder)?.run {
                    service = this.locationService
                    service?.startServer(object : ReceiveLocationListener {
                        val addressPort by lazy { getBroadcastAddress(context).getAddressPort() }
                        override var ipv4: String = addressPort.first
                            set(value) {
                                automaticIp = false
                                address = "$value:$port"
                                setBroadcastAddress(context, address)
                                setAutomaticBroadcastIp(context, automaticIp)
                                field = value
                            }
                        override var port: Int = addressPort.second

                        override fun onLocationReceived(it: Location) {
                            location = it
                        }
                    })
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isRunning = false
                service?.stopServer()
                service = null
            }

            override fun onBindingDied(name: ComponentName?) {
                super.onBindingDied(name)
            }
        })
    }

    fun startServer() {
        val intent = Intent(context, LocationService::class.java)
        //发送信息给Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection.value, Context.BIND_AUTO_CREATE)
    }

    fun stopServer() {
        val intent = Intent(context, LocationService::class.java)
        context.stopService(intent)
        context.unbindService(serviceConnection.value)
    }

    Scaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                if (isRunning) {
                    stopServer()
                    isRunning = false
                } else {
                    navController.navigateUp()
                }
            }) {
                Icon(Icons.Filled.ArrowBack, null)
            }
        }, title = {
            if (isRunning) {
                Text(text = stringResource(id = R.string.server) + " $address")
            } else {
                Checkbox(checked = automaticIp, onCheckedChange = {
                    automaticIp = it
                    setAutomaticBroadcastIp(context, it)
                })
                Text(
                    text = stringResource(
                        id = if (automaticIp) R.string.automatic_ip else R.string.static_ip
                    )
                )
            }
        })
    }) { paddingValues ->
        ColumnOrRow(paddingValues = paddingValues) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = !isRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = address,
                            enabled = !automaticIp,
                            onValueChange = {
                                address = it
                                val isAddressPort = checkAddressPort(it)
                                inputError.value = !isAddressPort
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Place, null)
                            },
                            label = { Text(stringResource(R.string.text_field_label)) },
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(bottom = 10.dp),
                            singleLine = true,
                            isError = inputError.value
                        )
                    }
                }
                FloatingActionButton(
                    modifier = Modifier
                        .size(150.dp)
                        .padding(16.dp),
                    onClick = {
                        if (!inputError.value) {
                            if (isRunning) {
                                stopServer()
                            } else {
                                if (automaticIp) {
                                    val value =
                                        "$UNI_BROADCAST_IP:${address.getAddressPort().second}"
                                    address = value
                                }
                                setBroadcastAddress(context, address)
                                startServer()
                            }
                        }
                        isRunning = !isRunning
                    },
                    backgroundColor = if (isRunning) MaterialTheme.colors.error else MaterialTheme.colors.secondary
                ) {
                    Text(
                        if (isRunning) stringResource(id = R.string.stop) else stringResource(id = R.string.start),
                        fontSize = 26.sp,
                        color = Color.White,
                        fontWeight = FontWeight.W800
                    )
                }
            }
            AnimatedVisibility(
                visible = isRunning, enter = fadeIn(
                    animationSpec = tween(220, delayMillis = 500)
                ) + expandHorizontally()
            ) {
                GpsBox(location)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRunning) {
                stopServer()
                isRunning = false
            }
        }
    }
}
