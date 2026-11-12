package com.example.netspeed.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netspeed.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Animated launch screen: logo draws itself, then "Designed by Mr.PC" fades in. */
@Composable
fun Splash(onDone: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val arc1 = remember { Animatable(0f) }
    val arc2 = remember { Animatable(0f) }
    val arrow = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val credit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        launch { delay(250); arc1.animateTo(1f, tween(550)) }
        launch { delay(500); arc2.animateTo(1f, tween(550)) }
        launch { delay(800); arrow.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
        launch { delay(1000); title.animateTo(1f, tween(500)) }
        launch { delay(1350); credit.animateTo(1f, tween(600)) }
        delay(2600)
        onDone()
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF081B4B), Color(0xFF0B5FFF), Color(0xFF6A3DF0)))),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(150.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            val w = size.width
            // glassy rounded-square plate
            drawRoundRect(Color.White.copy(alpha = .16f), size = Size(w, w), cornerRadius = CornerRadius(w * .28f))
            val centre = Offset(w / 2, w * .60f)
            fun arc(r: Float, p: Float, c: Color) {
                drawArc(
                    c, startAngle = 225f + 90f * (1f - p) / 2f, sweepAngle = 90f * p, useCenter = false,
                    topLeft = Offset(centre.x - r, centre.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = w * .065f, cap = StrokeCap.Round)
                )
            }
            arc(w * .38f, arc1.value, Color.White)
            arc(w * .24f, arc2.value, Color(0xFFB8D2FF))
            // download arrow that drops in
            val a = arrow.value
            val dy = (1f - a) * -w * .12f
            val p = Path().apply {
                val cx = w / 2; val top = w * .50f + dy; val bot = w * .74f + dy
                moveTo(cx - w * .045f, top); lineTo(cx + w * .045f, top); lineTo(cx + w * .045f, bot - w * .09f)
                lineTo(cx + w * .11f, bot - w * .09f); lineTo(cx, bot + w * .04f); lineTo(cx - w * .11f, bot - w * .09f)
                lineTo(cx - w * .045f, bot - w * .09f); close()
            }
            drawPath(p, Color.White.copy(alpha = a.coerceIn(0f, 1f)))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.app_name), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { alpha = title.value; translationY = (1f - title.value) * 24f }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.splash_tagline), color = Color.White.copy(alpha = .8f), fontSize = 14.sp,
            modifier = Modifier.graphicsLayer { alpha = title.value }
        )
        Spacer(Modifier.height(64.dp))
        Text(
            stringResource(R.string.designed_by), color = Color.White.copy(alpha = .9f), fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.graphicsLayer { alpha = credit.value; translationY = (1f - credit.value) * 16f }
        )
    }
}
