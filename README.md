# Vital Oceans — demo Android

Prototipo nativo de **Océanos Vitales**: una app que acompaña a comunidades costeras a proponer
**Áreas Marinas Protegidas (MPA)**. El usuario traza un polígono sobre el mar, la app le propone un
nombre, genera un reporte por secciones y le deja conversar con *Alex*, un agente que conoce ese reporte.

> **Es una demo.** No hay backend, ni servicio de IA, ni MPA reales. Las cifras del polígono son de
> verdad (se calculan sobre el trazado); el contenido ambiental y las respuestas de Alex están
> preparados. Cada pieza ficticia está detrás de una interfaz lista para conectar — ver
> [Puntos de integración](#puntos-de-integración).

Kotlin · Jetpack Compose · Material 3 · Mapbox Maps SDK · Android 6+ (API 23)
Se desarrolla y compila **desde la terminal**, sin Android Studio.

---

## El recorrido

| # | Pantalla | Qué pasa |
|---|----------|----------|
| 1 | **Loader** | Seis fotos que crecen mientras avanza un contador. |
| 2 | **Portada** | Presentación; el botón expande la portada a video a pantalla completa. |
| 3 | **Zona** | Baja California, con una línea de tiempo que se revela al deslizar. |
| 4 | **Trazado** | Mapa real: toca para poner vértices, mantén pulsado para moverlos, dos dedos para girar e inclinar. Al cerrar el polígono se calculan superficie, perímetro y centro. |
| 5 | **Generación** | Fondo azul, orbe de refracción cristalina y cuatro frases mientras "analiza". |
| 6 | **Nombre** | Título propuesto, editable, regenerable. |
| 7 | **Reporte** | Resumen, Biodiversidad, Actividades, Propuesta y Agente, con mapas ampliables. |
| 8 | **Alex** | Chat a pantalla completa, o llamada de voz **acercando el teléfono a la oreja**. |

Todos los cambios de pantalla usan transiciones compartidas: el elemento que tocas es el que crece.

---

## Puesta en marcha

### Requisitos

- **JDK 17** (`java` en PATH o `JAVA_HOME` apuntando al JDK).
- **Android SDK Command-line Tools** y **Platform Tools** en PATH
  (`cmdline-tools/latest/bin` y `platform-tools`).
- Gradle **8.13** lo aporta el Wrapper incluido; no hace falta instalarlo.

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

### Configuración local

Crea `local.properties` en la raíz (está en `.gitignore`):

```properties
sdk.dir=C\:/Users/TU_USUARIO/AppData/Local/android/sdk
MAPBOX_ACCESS_TOKEN=pk.tu_token_publico
```

El token de Mapbox también puede venir de la variable de entorno `MAPBOX_ACCESS_TOKEN`.
Usa un token **público** `pk.*`: acaba dentro del APK, así que nunca pongas uno secreto `sk.*`.
Sin token la app arranca igual, pero los mapas muestran "Falta configurar Mapbox".

### Compilar, validar e instalar

```bash
./gradlew assembleDebug                                   # APK de depuración
./gradlew assembleDebug lintDebug testDebugUnitTest       # validación completa
./gradlew installDebug                                    # instalar en el dispositivo conectado
adb shell am start -n com.example.miprimeraapp/.MainActivity
```

En Windows usa `.\gradlew.bat`; en Linux/macOS, `chmod +x gradlew` primero.
El APK queda en `app/build/outputs/apk/debug/app-debug.apk` y el informe de lint en
`app/build/reports/lint-results-debug.html`.

> El APK pesa unos **100 MB** porque Mapbox incluye binarios nativos de las cuatro ABI.
> Para reducirlo a ~30 MB en un dispositivo concreto: `ndk { abiFilters += "arm64-v8a" }`.

---

## Arquitectura

Un solo módulo (`:app`), una sola `Activity`, sin librería de navegación, sin ViewModel,
sin persistencia y sin red. El estado vive en los composables y sobrevive a la recreación
de la actividad con `rememberSaveable`; el flujo avanza por banderas booleanas anidadas.

Es deliberado: el prototipo cabe en la cabeza de una persona y cada pantalla se lee de arriba abajo.
Si esto crece a producción, el primer refactor es subir el estado a ViewModels.

```
MainActivity            Loader → ExperienceFlow
└── ExperienceScreen    portada, video, selector de zona
    └── TraceAreaScreen trazado sobre el mapa y resumen del área
        └── MpaProposalScreen  generación, nombre y contenedor del reporte
            ├── ReportScreen   lector por secciones, navbar y mapas
            ├── ReportActions  PDF, compartir y comentarios
            └── AlexScreens    chat y llamada (pantalla completa)
```

| Archivo | Responsabilidad |
|---------|-----------------|
| `AreaGeometry.kt` | Matemática geodésica: área por exceso esférico, perímetro haversine, validación de auto-intersección, export a GeoJSON. |
| `AreaMap.kt` | Controlador de Mapbox: anotaciones, animación por frames de puntos y líneas, arrastre de vértices, encuadre. |
| `ReportGeometry.kt` | Puntos ilustrativos dentro del polígono y formato de coordenadas. |
| `FluidOrb.kt` | Orbe de refracción cristalina: shader AGSL en Android 13+, respaldo pintado por debajo. |
| `AlexAgent.kt` | Contexto y cerebro del agente, y el sensor de proximidad. |
| `ui/theme/Theme.kt` | **Todas** las declaraciones del tema. `Color.kt` y `Type.kt` son stubs vacíos a propósito. |

### Detalles que conviene conocer antes de tocar el código

- **El tema vive entero en `Theme.kt`.** Separarlo en `Color.kt` / `Type.kt` rompía la resolución
  entre archivos de la extensión Kotlin de VS Code. No devuelvas las declaraciones a esos archivos.
- **Esquema de color claro siempre.** Es un `lightColorScheme` poblado a mano, sin variante oscura
  ni color dinámico: el modo oscuro del sistema no cambia la app.
- **Las tipografías se cargan por pantalla.** El tema usa `SansSerif`; Bowlby One y Ones se declaran
  localmente en cada archivo que las necesita. `FontVariation` requiere API 26, con respaldo a
  `SansSerif` por debajo.
- **Las animaciones del mapa son imperativas.** Las anotaciones de Mapbox no se animan desde Compose,
  así que `AreaMapController` corre su propio bucle con `postOnAnimation`, que arranca y se detiene solo.
- **Durante el arrastre de un vértice no se notifica a Compose.** Recomponer por frame rompería el
  gesto: el controlador mantiene la geometría y sólo avisa al soltar.
- **Todo el texto va por `strings.xml`** y los comentarios del código están en español.

---

## Puntos de integración

Lo ficticio está aislado detrás de interfaces. Sustituir la implementación de demo no cambia la UI.

| Interfaz | Implementación de demo | Qué haría la real |
|----------|------------------------|-------------------|
| `AreaInformationRepository` | `DemoAreaInformationRepository` | Enviar `area.toGeoJson()` a un servicio y devolver calidad de datos por fuente. |
| `MpaNameGenerator` | `DemoMpaNameGenerator` | Proponer un nombre analizando el polígono, en vez de combinar listas al azar. |
| `AlexBrain` | `ScriptedAlexBrain` | Conversar con un modelo, pasándole `AlexContext` como contexto. |
| `ExperienceFeedbackRepository` | `PendingExperienceFeedbackRepository` | Enviar la cola de comentarios y retirar cada entrada sólo tras confirmación. |

**Cambio pendiente en el modelo:** `SourceQuality.logo` es un `Int` de `R.drawable`. Un servidor no
puede enviar un ID de recurso de Android; habrá que cambiarlo por un identificador serializable
(o una URL, con una librería de carga de imágenes).

Los comentarios se guardan mientras tanto en `SharedPreferences` (`experience_feedback`), con ID
estable para permitir entregas idempotentes. Nunca salen del dispositivo.

---

## Recursos y diseño

- **Imágenes de mapa de bits** → `res/drawable-nodpi/`, para que Android no las reescale por densidad;
  el tamaño se controla en Compose.
- **Vectores** → `res/drawable/`, como VectorDrawable XML. `painterResource` **no lee `.svg`**:
  hay que convertirlos, no renombrarlos.
- **Los SVG originales** se conservan en `design/source-assets/`, fuera de `res/`.
- **Nombres de recurso** en minúsculas, dígitos y guiones bajos.
- El video de transición está comprimido a 596 KB (H.264, 720 × 830, sin audio); el original
  está en `design/source-video/`.
- El icono actual es un PNG de 512 × 512, no un icono adaptativo.

Codificación: el proyecto es **UTF-8** en todas partes (`.vscode/settings.json` y `gradle.properties`
lo fuerzan). Cuida los acentos al editar desde consolas de Windows.

---

## Permisos y sensores

- `INTERNET` — único permiso declarado, para los mosaicos de Mapbox.
- **Sensor de proximidad** (`TYPE_PROXIMITY`) — no requiere permiso. Acercar el teléfono a la oreja
  con el reporte abierto inicia la llamada con Alex. Es binario en casi todos los equipos
  (cerca/lejos, no distancia continua), así que sólo se usa la transición lejos → cerca.
- Compartir el PDF usa un `FileProvider` limitado a `cache/reports/`.

---

## Limitaciones conocidas

- Las medidas del polígono son aproximaciones esféricas: **no son medidas catastrales**.
- El contenido ambiental procede del reporte de ejemplo de Isla Espíritu Santo (16/04/2026).
  No se atribuye a ninguna zona nueva que tú traces.
- Los porcentajes de calidad y los puntos de colores en los mapas son ilustrativos. Los logos de
  las fuentes no implican consultas a sus APIs.
- Alex responde por palabras clave sobre un guion; sólo las cifras de tu polígono son reales.
- El "escuchar" de la llamada no usa el micrófono: la envolvente del orbe es sintética.
- El APK de depuración usa la firma de desarrollo. Para publicar hay que configurar firma release y
  cambiar `com.example.miprimeraapp` por un identificador propio.

---

## Documentación adicional

- [`design/MAPBOX.md`](design/MAPBOX.md) — estilo, cámara, interacción del trazado y conexión futura.
- [`CLAUDE.md`](CLAUDE.md) — notas para asistentes de código que trabajen en el repo.

## Créditos

- Fotos del loader: [Lorem Picsum](https://picsum.photos/) (IDs 1011, 1015, 1016, 1025, 1035, 1043).
- Mapas: [Mapbox](https://www.mapbox.com/).
- Tipografías: Bowlby One y Ones.
