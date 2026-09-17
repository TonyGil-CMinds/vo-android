package com.example.miprimeraapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.min

/**
 * Lo que Alex sabe del reporte. Hoy se arma con el polígono del usuario y el texto de la demo;
 * el día que exista un motor, esto es lo único que hay que darle como contexto.
 */
internal data class AlexContext(
    val title: String,
    val squareKilometres: Double,
    val perimeterKilometres: Double,
    val latitude: Double,
    val longitude: Double,
    val quality: Int?,
)

internal fun alexContext(title: String, area: AreaGeometry, quality: Int?) = AlexContext(
    title = title.ifBlank { "tu propuesta" },
    squareKilometres = area.squareMeters / 1_000_000,
    perimeterKilometres = area.perimeterMeters / 1000,
    latitude = area.center.latitude,
    longitude = area.center.longitude,
    quality = quality,
)

internal data class AlexMessage(val text: String, val fromAlex: Boolean)

/** Sustituye esta implementación por el motor de IA; la conversación no cambia de forma. */
internal interface AlexBrain {
    fun greeting(context: AlexContext): String
    fun topics(): List<String>
    suspend fun reply(question: String, context: AlexContext): String
}

internal class ScriptedAlexBrain : AlexBrain {
    private fun number(value: Double, unit: String) =
        String.format(Locale.US, "%.2f %s", value, unit)

    override fun greeting(context: AlexContext) =
        "Hola, soy Alex. Ya revisé ${context.title}: son ${number(context.squareKilometres, "kilómetros cuadrados")} " +
            "frente a Baja California. Pregúntame lo que quieras del reporte."

    override fun topics() = listOf(
        "¿Qué especies destaca el reporte?",
        "¿Qué actividades considera?",
        "¿Qué tan buenos son los datos?",
        "¿Cuánto mide mi área?",
        "¿Cuáles son los siguientes pasos?",
    )

    override suspend fun reply(question: String, context: AlexContext): String {
        delay(700) // Tarda lo que tardaría una respuesta real, para que la espera se note.
        val text = question.lowercase(Locale.ROOT)
        fun has(vararg keys: String) = keys.any { it in text }
        return when {
            has("especie", "fauna", "biodiv", "coral", "tortuga", "raya") ->
                "El documento destaca el coral Porites sverdrupi, la tortuga verde Chelonia mydas, el pepino de mar " +
                    "Isostichopus fuscus y las rayas Mobula thurstoni y Mobula birostris. En la pestaña Biodiversidad " +
                    "están las categorías de conservación de cada una."
            has("activid", "pesca", "turismo", "buceo", "comunidad") ->
                "Pesca artesanal, conservación y turismo, incluido el buceo. La propuesta busca compatibilizar esas " +
                    "actividades con la protección de los hábitats, no excluirlas."
            has("dato", "calidad", "fuente", "confia", "precis") ->
                context.quality?.let {
                    "La calidad de datos de tu polígono salió en $it %. Sentinel-2 y Copernicus Marine cubren bien la " +
                        "zona; NASA EarthData es la fuente más floja. Son datos de demostración."
                } ?: "Todavía estoy cruzando las fuentes de tu polígono. Son datos de demostración."
            has("mide", "superficie", "área", "area", "tamaño", "perímetro", "perimetro", "km") ->
                "Tu área cubre ${number(context.squareKilometres, "km²")} con un perímetro de " +
                    "${number(context.perimeterKilometres, "km")}. El centro queda en " +
                    String.format(Locale.US, "%.3f° N, %.3f° W.", context.latitude, -context.longitude)
            has("dónde", "donde", "ubica", "lugar", "centro", "coorden") ->
                "El centro de tu polígono está en " +
                    String.format(Locale.US, "%.4f° N, %.4f° W", context.latitude, -context.longitude) +
                    ", en la costa de Baja California Sur."
            has("siguiente", "paso", "después", "despues", "ahora qué", "continuar") ->
                "Revisa el polígono en Propuesta, los hábitats en Biodiversidad y los usos en Actividades. Para cerrar " +
                    "el reporte de verdad harían falta fuentes georreferenciadas, acuerdos comunitarios y objetivos de manejo."
            has("amp", "quién eres", "quien eres", "qué eres", "que eres", "hola", "buenas") ->
                "Soy Alex, el agente del reporte. Conozco el polígono que trazaste, sus medidas y las fuentes que se " +
                    "consultaron. Pregúntame por especies, actividades, datos o siguientes pasos."
            else ->
                "En esta demo mis respuestas están preparadas, así que de eso todavía no sé. Prueba con especies, " +
                    "actividades, calidad de datos, medidas de tu área o siguientes pasos."
        }
    }
}

/**
 * Acercar el teléfono a la oreja abre la llamada, como en una llamada de verdad.
 * El sensor es binario en casi todos los equipos: 0 cubierto, su rango máximo libre.
 */
@Composable
internal fun ProximityToEar(enabled: Boolean, onNear: () -> Unit) {
    val context = LocalContext.current
    val near by rememberUpdatedState(onNear)
    DisposableEffect(enabled, context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (!enabled || manager == null || sensor == null) return@DisposableEffect onDispose { }
        val threshold = min(sensor.maximumRange, 5f)
        // Arranca en "cubierto" para no disparar si el teléfono ya estaba boca abajo.
        var covered = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = event.values.firstOrNull()?.let { it < threshold } ?: return
                if (now && !covered) near()
                covered = now
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager.unregisterListener(listener) }
    }
}
