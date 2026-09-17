package com.example.miprimeraapp

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * El mismo fluido del orbe de la referencia: ruido fbm advectado por una deriva lenta,
 * anclado abajo y recortado en círculo. `amplitude` lo agita cuando hay voz.
 *
 * AGSL necesita Android 13; por debajo se pinta una aproximación con degradados.
 */
private const val ORB_AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float3 u_color;
uniform float u_amp;

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
    mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
    u.y
  );
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.6;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p *= 2.0;
    a *= 0.5;
  }
  return v;
}

half4 main(float2 fragCoord) {
  float2 uv = fragCoord / u_resolution;
  uv.y = 1.0 - uv.y;
  float t = u_time * (0.22 + u_amp * 0.42);

  float2 drift = float2(
    sin(t) + 0.6 * sin(t * 1.7 + 1.3),
    cos(t * 0.8) + 0.6 * cos(t * 1.3 + 2.1)
  );

  float2 p = float2(uv.x * 1.8, uv.y * 1.0) + drift * 0.7;

  float2 q = float2(fbm(p + drift), fbm(p + float2(3.2, 1.5) - drift));
  float f = fbm(p + (1.2 + u_amp * 1.0) * q);

  float g = clamp(1.0 - uv.y, 0.0, 1.0);
  float anchor = smoothstep(0.0, 0.3, uv.y);
  float shade = clamp(g + (f - 0.5) * (0.8 + u_amp * 0.85) * anchor, 0.0, 1.0);

  float3 white = float3(0.99, 1.0, 1.0);
  float3 light = mix(white, u_color, 0.5);
  float3 dark = u_color;

  float3 col = white;
  col = mix(col, light, smoothstep(0.28, 0.52, shade));
  col = mix(col, dark, smoothstep(0.58, 0.88, shade));

  float edge = smoothstep(0.5, 0.49, distance(uv, float2(0.5)));

  return half4(half3(col * edge), half(edge));
}
"""

/** Envolvente con cadencia de habla: ráfagas irregulares en vez de un latido regular. */
internal fun speechEnvelope(time: Float): Float =
    (0.5f + 0.5f * sin(time * 7.3f)) *
        (0.6f + 0.4f * sin(time * 3.1f + 1.2f)) *
        (0.7f + 0.3f * sin(time * 11.7f + 0.4f))

@Composable
internal fun FluidOrb(
    size: Dp,
    amplitude: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1A73F2),
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                if (start == 0L) start = frame
                time.floatValue = (frame - start) / 1000f
            }
        }
    }
    val shaped = modifier.size(size).clip(CircleShape)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderOrb(shaped, color, time, amplitude)
    } else {
        PaintedOrb(shaped, color, time, amplitude)
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderOrb(modifier: Modifier, color: Color, time: State<Float>, amplitude: Float) {
    val shader = remember { RuntimeShader(ORB_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val level by rememberUpdatedState(amplitude)
    androidx.compose.foundation.layout.Box(
        modifier.drawWithCache {
            shader.setFloatUniform("u_resolution", size.width, size.height)
            shader.setFloatUniform("u_color", color.red, color.green, color.blue)
            onDrawBehind {
                // Lecturas en fase de dibujo: cambian el frame sin recomponer.
                shader.setFloatUniform("u_time", time.value)
                shader.setFloatUniform("u_amp", level)
                drawRect(brush)
            }
        },
    ) {}
}

/** Respaldo sin AGSL: manchas que derivan con las mismas frecuencias, sobre blanco. */
@Composable
private fun PaintedOrb(modifier: Modifier, color: Color, time: State<Float>, amplitude: Float) {
    val level by rememberUpdatedState(amplitude)
    Canvas(modifier) {
        val t = time.value * (0.22f + level * 0.42f)
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFFFCFFFF), radius, centre)
        val blobs = listOf(
            Triple(sin(t) + 0.6f * sin(t * 1.7f + 1.3f), cos(t * 0.8f) + 0.6f * cos(t * 1.3f + 2.1f), 0.86f),
            Triple(cos(t * 1.1f + 2.4f), sin(t * 0.7f + 0.9f), 0.62f),
            Triple(sin(t * 0.6f + 4.1f), cos(t * 1.4f + 3.3f), 0.46f),
        )
        blobs.forEachIndexed { index, (dx, dy, scale) ->
            val spread = 0.20f + level * 0.10f
            val hub = Offset(
                centre.x + dx * radius * spread,
                centre.y + radius * (0.34f + dy * spread),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.55f - index * 0.12f), Color.Transparent),
                    center = hub, radius = radius * scale,
                ),
                radius, centre,
            )
        }
        // El color se ancla abajo, como en el shader.
        drawCircle(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.55f to color.copy(alpha = 0.18f), 1f to color.copy(alpha = 0.72f),
                startY = centre.y - radius, endY = centre.y + radius,
            ),
            radius, centre,
        )
    }
}
