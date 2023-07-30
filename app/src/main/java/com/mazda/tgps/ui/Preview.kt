package com.mazda.tgps.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.coroutineScope
import com.mazda.tgps.R
import com.mazda.tgps.utils.Utils.getAddressPort
import com.mazda.tgps.utils.Utils.logger
import com.mazda.tgps.utils.Utils.portAvailable
import com.mazda.tgps.utils.getBroadcastAddress
import com.mazda.tgps.utils.setBroadcastAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@Composable
fun GreetingChose(
    onNavigateToServer: () -> Unit,
    onNavigateToClient: () -> Unit,
) {
    // 取得 context
    val current = LocalContext.current
    var openPortSetting by remember { mutableStateOf(false) }
    // 檢查完 permission 後將答案存在 remember 中
    var hasPermission by remember { mutableStateOf(false) }
    var isLocationEnabled by remember { mutableStateOf(false) }
    var portAvailable by remember { mutableStateOf(false) }
    val broadcastAddress = getBroadcastAddress(current)
    var staticPort by remember { mutableStateOf(broadcastAddress.getAddressPort().second.toString()) }
    val permissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { mutableMap ->
        hasPermission = mutableMap.all { it.value }
    }

    if (openPortSetting) {
        var inputError by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = {
            openPortSetting = false
        }, title = {
            Text(text = stringResource(R.string.setting_port_title))
        }, text = {
            OutlinedTextField(
                value = staticPort,
                onValueChange = {
                    staticPort = it
                    inputError =
                        !(staticPort.isNotEmpty() && staticPort.isDigitsOnly() && staticPort.toInt() in 0..65535)
                },
                label = {
                    Text(text = stringResource(R.string.setting_port_content))
                },
                modifier = Modifier
                    .padding(top = 5.dp)
                    .wrapContentWidth(),
                singleLine = true,
                isError = inputError
            )
        }, confirmButton = {
            TextButton(onClick = {
                if (!inputError) {
                    val value = """${broadcastAddress.getAddressPort().first}:$staticPort"""
                    logger("新端口 $value")
                    setBroadcastAddress(current, value)
                    openPortSetting = false
                }
            }) {
                Text(text = stringResource(R.string.ok))
            }
        }, dismissButton = {
            TextButton(onClick = {
                openPortSetting = false
            }) {
                Text(text = stringResource(R.string.cancel))
            }
        })
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar {
            StateBox(stringResource(id = R.string.gps_permission), hasPermission, onClick = {
                permissionRequester.launch(REQUIRED_PERMISSIONS.toTypedArray())
            })
            StateBox(stringResource(id = R.string.location_server), isLocationEnabled, onClick = {
                val intent =
                    Intent(ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                current.startActivity(intent)
            })
            StateBox(stringResource(id = R.string.port), portAvailable, onClick = {
                openPortSetting = true
            })
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(modifier = Modifier.size(100.dp), onClick = onNavigateToServer) {
                Text(text = stringResource(id = R.string.server))
            }
            Button(modifier = Modifier.size(100.dp), onClick = onNavigateToClient) {
                Text(text = stringResource(id = R.string.client))
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, current) {
        // Make MapView follow the current lifecycle
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    lifecycle.coroutineScope.launch {
                        snapshotFlow {
                            portAvailable(staticPort.toInt())
                        }.flowOn(Dispatchers.IO).catch {
                            portAvailable = false
                        }.collect {
                            logger("端口可用 $it")
                            portAvailable = it
                        }
                    }
                    hasPermission = checkPermissionsGranted(current)
                    isLocationEnabled = LocationManagerCompat.isLocationEnabled(
                        current.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    )
                }
                else -> {
                    logger("生命周期 $event")
                }
            }
        }
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
}

@Composable
fun StateBox(content: String, open: Boolean, onClick: () -> Unit = {}) {
    Text(text = content, modifier = Modifier
        .padding(start = 16.dp)
        .clickable { onClick.invoke() })
    Canvas(modifier = Modifier.size(16.dp)) {
        drawCircle(
            color = if (open) Color.Green else Color.Red, radius = size.minDimension / 4
        )
    }
}

val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
)

//  checkPermission
fun checkPermissionsGranted(context: Context) = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}