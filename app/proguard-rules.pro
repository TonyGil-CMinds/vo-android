# Mapbox y Compose traen sus propias reglas de consumidor dentro de sus artefactos;
# aquí sólo van las que necesite este proyecto.

# Los nombres de los composables aparecen en los trazos de composición y en los crash reports.
-keepclassmembernames class * { @androidx.compose.runtime.Composable <methods>; }
