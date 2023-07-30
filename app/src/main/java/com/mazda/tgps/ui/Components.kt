package com.mazda.tgps.ui

import android.location.Location
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.mazda.tgps.R
import com.mazda.tgps.utils.Utils
import java.text.SimpleDateFormat
import java.util.*

@Preview
@Composable
fun Shrink() {
    val scale = rememberInfiniteTransition().animateFloat(
        0.5f,
        1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse)
    )
    Box(
        Modifier
            .padding(5.dp)
            .scale(scale.value)
            .width(20.dp)
            .height(10.dp)
            .background(color = MaterialTheme.colors.secondary, shape = RoundedCornerShape(10.dp))
    )
}

@Composable
fun ColumnOrRow(
    paddingValues: PaddingValues,
    content: @Composable @UiComposable () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val isPhone = maxHeight.value > maxWidth.value
        if (isPhone) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content.invoke()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                content.invoke()
            }
        }
    }
}


@Composable
internal fun GpsBox(location: Location) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xff2d2f31)),
        contentAlignment = Alignment.Center
    ) {
        val isPhone = maxHeight.value > maxWidth.value
        val mainAxisSpacing = 8.dp
        //最多每行 box数量
        val maxRowCount = 3
        var maxBoxSize =
            if (isPhone) {
                ((maxWidth.value - ((maxRowCount + 1) * mainAxisSpacing.value)) / 2).toInt()
            } else ((maxHeight.value - ((maxRowCount + 1) * mainAxisSpacing.value)) / 2).toInt()
        val maxPadding =
            if (isPhone) ((maxHeight.value - maxBoxSize * maxRowCount - ((maxRowCount - 1) * mainAxisSpacing.value)) / 2).toInt()
            else ((maxWidth.value - maxBoxSize * maxRowCount - ((maxRowCount - 1) * mainAxisSpacing.value)) / 2).toInt()
        if (maxPadding <= 0) {
            //肯定超出边界 进行缩小修正
            maxBoxSize += maxPadding
        }
        FlowRow(
            mainAxisSpacing = mainAxisSpacing,
            crossAxisSpacing = mainAxisSpacing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isPhone) 0.dp else maxPadding.coerceAtLeast(0).dp,
                    vertical = if (!isPhone) 0.dp else maxPadding.coerceAtLeast(0).dp
                ),
            mainAxisAlignment = FlowMainAxisAlignment.Center,
            crossAxisAlignment = FlowCrossAxisAlignment.Center
        ) {
            AutoSizeBox(
                stringResource(R.string.latest_time),
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(location.time),
                maxBoxSize = maxBoxSize,
                fontSize = (maxBoxSize / 5).sp
            )
            AutoSizeBox(
                stringResource(R.string.lat_lon),
                "${Utils.covertGps(location.latitude)}\n${Utils.covertGps(location.longitude)}",
                maxBoxSize = maxBoxSize,
                fontSize = (maxBoxSize / 7).sp
            )
            AutoSizeBox(
                stringResource(R.string.speed_km),
                "${(location.speed * 3.6).toInt()}",
                maxBoxSize = maxBoxSize
            )
            AutoSizeBox(
                stringResource(R.string.accuracy),
                "${location.accuracy.toInt()}",
                maxBoxSize = maxBoxSize
            )
            AutoSizeBox(
                stringResource(R.string.bearing),
                "${location.bearing.toInt()}",
                maxBoxSize = maxBoxSize
            )
        }
    }
}

@Composable
fun AutoSizeBox(
    title: String,
    content: String,
    maxBoxSize: Int,
    fontSize: TextUnit = (maxBoxSize / 3).sp
) {
    Column(
        modifier = Modifier
            .size(maxBoxSize.dp)
            .border(1.dp, color = Color.LightGray)
    ) {
        Text(text = title, color = Color.White, modifier = Modifier.padding(5.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = content,
                style = TextStyle(
                    color = Color(0xFF018786),
                    fontWeight = FontWeight.W800,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = fontSize
                ),
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center
            )
        }
    }
}

