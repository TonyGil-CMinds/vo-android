# Mi primera app

App Android nativa en Kotlin y Jetpack Compose con Material 3. Se desarrolla y compila desde la terminal, sin Android Studio.

## Herramientas

- JDK 17 (con `java` en PATH o `JAVA_HOME` apuntando al JDK).
- Android SDK Command-line Tools y Platform Tools.
- Plataforma `platforms;android-36` y `build-tools;35.0.0`.
- Gradle 8.13 mediante el Wrapper incluido; no necesitas instalar Gradle globalmente.
- Android Gradle Plugin 8.13.2, Kotlin y plugin Compose 2.2.20, Compose BOM 2025.08.01.
- Android mÃ­nimo: 6.0 (API 23). Compile/target SDK: 36.

## ConfiguraciÃ³n en otra computadora

Instala JDK 17 y las [herramientas de lÃ­nea de comandos del SDK](https://developer.android.com/tools).
Agrega `cmdline-tools/latest/bin` y `platform-tools` del SDK a PATH.

```powershell
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

Crea `local.properties` en la raÃ­z con la ruta de tu SDK, usando barras `/`:

```properties
sdk.dir=C\:/Users/TU_USUARIO/AppData/Local/android/sdk
```

Este archivo se ignora en Git. Alternativamente configura `ANDROID_HOME`.

## Compilar y revisar

Desde la raÃ­z del proyecto en PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

La primera compilaciÃ³n descarga Gradle y las dependencias. Requiere Internet.
APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
Informe de lint: `app/build/reports/lint-results-debug.html`.
En Linux/macOS usa `chmod +x gradlew` y `./gradlew assembleDebug`.

## Instalar y abrir en un telÃ©fono

Activa Opciones de desarrollador y DepuraciÃ³n USB, conecta el telÃ©fono y autoriza la computadora.

```powershell
adb devices
.\gradlew.bat installDebug
adb shell am start -n com.example.miprimeraapp/.MainActivity
```

Si hay varios dispositivos, instala sobre uno especÃ­fico:

```powershell
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL shell am start -n com.example.miprimeraapp/.MainActivity
```

## Estructura

- `app/src/main/java/com/example/miprimeraapp/MainActivity.kt`: pantalla Compose y contador animado; conserva el estado durante recreaciÃ³n de la actividad.
- `app/src/main/java/com/example/miprimeraapp/ui/theme/Theme.kt`: colores claros y oscuros segÃºn el sistema.
- `app/src/main/res/values/strings.xml`: textos en espaÃ±ol.
- `app/src/main/AndroidManifest.xml`: aplicaciÃ³n y actividad de entrada.
- `app/build.gradle.kts`: SDK, identificador y dependencias.

La pantalla limita el ancho en tablets, permite desplazamiento en pantallas pequeÃ±as y respeta las barras del sistema. El contador es una demostraciÃ³n; no es almacenamiento permanente.

El APK debug usa la firma de desarrollo. Para publicar debes configurar tu propia firma release y cambiar `com.example.miprimeraapp` por un identificador propio.

Compatibilidad de herramientas: [documentaciÃ³n oficial de AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

## Fuentes personalizadas

`ui/theme/Type.kt` define `AppTypography`, conectado en `Theme.kt`:

- `display*` y `headline*`: Bowlby One Regular, sin negrita artificial.
- `title*`, `body*` y `label*`: Ones, con pesos 100..900 del archivo variable.
- En Android 6 y 7 se usa SansSerif para Ones; las variaciones requieren Android 8+.

Usa los estilos del tema para heredar las fuentes automÃ¡ticamente:

```kotlin
Text("Bienvenido", style = MaterialTheme.typography.displaySmall)
Text("DescripciÃ³n", style = MaterialTheme.typography.bodyLarge)
Text("Continuar", style = MaterialTheme.typography.labelLarge)
```

## ImÃ¡genes e iconos sin Android Studio

Usa nombres en minÃºsculas, nÃºmeros y guiones bajos: `foto_perfil.webp`, `ic_favorito.xml`.

- PNG, JPG y WebP: `app/src/main/res/drawable-nodpi/` para conservar sus pÃ­xeles sin escalado automÃ¡tico por densidad. Controla su tamaÃ±o en Compose. Para recursos con variantes por densidad utiliza `drawable-mdpi`, `drawable-hdpi`, etc.
- Vectores Android: `app/src/main/res/drawable/`, en formato VectorDrawable XML.
- SVG originales: puedes conservarlos en una carpeta `design/svg/` en la raÃ­z. No coloques archivos `.svg` directamente en `res/drawable`: `painterResource` no los interpreta.

Dentro de un composable, despuÃ©s de aÃ±adir `foto_perfil.webp`:

```kotlin
Image(
    painter = painterResource(R.drawable.foto_perfil),
    contentDescription = "Foto de perfil",
    modifier = Modifier.size(96.dp),
    contentScale = ContentScale.Crop,
)
```

Imports: `androidx.compose.foundation.Image`, `androidx.compose.ui.res.painterResource`,
`androidx.compose.ui.Modifier`, `androidx.compose.foundation.layout.size`,
`androidx.compose.ui.layout.ContentScale`, `androidx.compose.ui.unit.dp` y el `R` de tu app.
Usa `contentDescription = null` si la imagen es puramente decorativa.

### SVG a VectorDrawable

Convierte el SVG a VectorDrawable XML; cambiar la extensiÃ³n no lo convierte.
Para un SVG sencillo basado en paths, el `viewBox` define `viewportWidth`/`viewportHeight`,
el atributo `d` pasa a `android:pathData`, y `fill` a `android:fillColor`.
Por ejemplo, guarda este vector como `app/src/main/res/drawable/ic_mas.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000"
        android:pathData="M11,4h2v7h7v2h-7v7h-2v-7h-7v-2h7z" />
</vector>
```

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_mas),
    contentDescription = "AÃ±adir",
    tint = MaterialTheme.colorScheme.primary,
)
```

`Icon` es de `androidx.compose.material3.Icon`. Para logos multicolor usa `Image`,
o `Icon(tint = Color.Unspecified, ...)`, para conservar los colores.
SVG con filtros, mÃ¡scaras o efectos complejos requieren revisar la conversiÃ³n;
puedes exportarlos a PNG/WebP desde tu herramienta de diseÃ±o si no necesitas escalarlos como vectores.
Para cargar archivos SVG originales en ejecuciÃ³n se necesita una biblioteca con decodificador SVG,
como Coil con su mÃ³dulo SVG; este proyecto no aÃ±ade esa dependencia.

Referencias: https://developer.android.com/develop/ui/compose/resources
https://developer.android.com/develop/ui/compose/text/fonts


## Configuración centralizada del tema

La paleta (ThemePalette) y la tipografía (ThemeTypography) están en ui/theme/Theme.kt. Color.kt y Type.kt quedan como archivos informativos sin declaraciones. Esta organización evita las referencias entre archivos que la extensión Kotlin no estaba resolviendo. Actualmente las familias usan SansSerif, conservando la configuración que había antes de esta corrección.

## Loader e icono personalizado

El loader está en MainActivity.kt. LoaderDurationMillis controla la duración (6000 ms). LoaderImages contiene seis fotos de demostración incluidas localmente en res/drawable-nodpi, loader_1.jpg a loader_6.jpg. Sustituye esos archivos manteniendo los nombres para usar tus fotos. Procedencia: Lorem Picsum, IDs 1011, 1015, 1016, 1025, 1035 y 1043 (https://picsum.photos/).

El contador simula progreso; no mide descargas. Termina en 100 y permanece en pantalla. Conserva el progreso al recrearse la actividad. Para reiniciarlo, cierra la app completamente y vuelve a abrirla.

Para probar tu icono sin Android Studio:
1. Exporta tu diseño a PNG cuadrado de 512 x 512, con margen alrededor del símbolo.
2. Guárdalo como app/src/main/res/drawable-nodpi/mi_icono.png.
3. En AndroidManifest.xml cambia android:icon="@drawable/ic_launcher" por android:icon="@drawable/mi_icono".
4. Ejecuta .\gradlew.bat installDebug para actualizar la app.

Para un icono adaptativo final, prepara símbolo transparente y fondo por separado: lienzo de 108 x 108 dp, símbolo importante dentro de la zona segura central de 66 x 66 dp, sin esquinas redondeadas incorporadas. Un SVG debe convertirse a VectorDrawable. Conserva una versión clásica para Android 6 y 7. Android 13 admite además una capa monocromática. Guía: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive

El icono actual se mantiene hasta que añadas tu diseño.
#   v o - a n d r o i d  
 