package com.example.miprimeraapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.miprimeraapp.ui.theme.MiPrimeraAppTheme

private const val LoaderDurationMillis = 6000
private val LoaderImages = listOf(
    R.drawable.loader_1, R.drawable.loader_2, R.drawable.loader_3,
    R.drawable.loader_4, R.drawable.loader_5, R.drawable.loader_6,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MiPrimeraAppTheme {
                var ready by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(ready) {
                    val bars = if (ready) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xFF282727.toInt())
                        else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                Crossfade(targetState = ready, animationSpec = tween(450), label = "Loader to start") { started ->
                    if (started) StartScreen() else LoaderScreen(onFinished = { ready = true })
                }
            }
        }
    }
}

@Composable
private fun LoaderScreen(onFinished: () -> Unit) {
    // Progreso de demostración, independiente de descargas o peticiones reales.
    var savedProgress by rememberSaveable { mutableFloatStateOf(0f) }
    val progress = remember { Animatable(savedProgress) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ((1f - savedProgress) * LoaderDurationMillis).toInt(),
                easing = LinearEasing,
            ),
        ) { savedProgress = value }
        delay(300)
        onFinished()
    }

    val fraction = progress.value
    val step = fraction * LoaderImages.size
    val imageIndex = step.toInt().coerceAtMost(LoaderImages.lastIndex)
    val localProgress = if (fraction >= 1f) 1f else step - imageIndex
    // Crece durante el 75% del intervalo y permanece completa el resto.
    val scale = FastOutSlowInEasing.transform((localProgress / 0.75f).coerceIn(0f, 1f))

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)
            .safeDrawingPadding(),
    ) {
        val imageSize = minOf(maxWidth * 0.4f, maxHeight * 0.42f, 320.dp)
        Box(
            modifier = Modifier.size(imageSize).align(Alignment.Center).clipToBounds(),
        ) {
            // La imagen anterior permanece debajo mientras la siguiente crece desde el centro.
            if (imageIndex > 0) {
                Image(
                    painter = painterResource(LoaderImages[imageIndex - 1]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Image(
                painter = painterResource(LoaderImages[imageIndex]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
        Text(
            text = (fraction * 100).toInt().coerceIn(0, 100).toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * 0.06f)
                .semantics {
                    contentDescription = "Cargando"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
        )
    }
}