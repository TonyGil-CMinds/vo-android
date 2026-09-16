# Trazado de áreas

La pantalla se abre con **Seleccionar** en Baja California. Usa Mapbox Maps SDK nativo para Android 11.30.1, integrado en Compose mediante AndroidView. No hay WebView ni Mapbox GL JS.

- Estilo: `mapbox://styles/tonygil1/cmu4p1p5t006b01sa847xh68d`.
- Cámara inicial: longitud `-110.35242858294092`, latitud `24.25292356062559`, zoom `10.46011603411317`, bearing y pitch cero.
- Token público: propiedad `MAPBOX_ACCESS_TOKEN` en `local.properties` (excluido de Git), o variable de entorno del mismo nombre. Gradle genera el recurso Android. No añadir tokens secretos `sk.*`. El token público se incluye necesariamente en el APK.

## Interacción

El tutorial muestra el mismo estilo, con un ejemplo de trazado animado. Al cerrarlo, cada toque añade un vértice geográfico. Arrastrar desplaza el mapa, pellizcar cambia el zoom y Deshacer elimina el último punto. Con al menos tres vértices se puede tocar el primero o pulsar Ver área. Se rechazan polígonos cruzados, puntos duplicados y áreas degeneradas. Volver desde el resumen permite seguir editando. Salir de un trazado pide confirmación.

El resumen calcula área esférica aproximada en km², perímetro geodésico aproximado en km y centro medio de los vértices. No son medidas catastrales. El GeoJSON exportable cierra el anillo y usa longitud, latitud. La aplicación no envía ese GeoJSON a un servidor.

## Datos de demostración y futura conexión

`AreaInformationRepository.evaluate(AreaGeometry)` es el punto de integración. Sustituir `DemoAreaInformationRepository` por una implementación que envíe `area.toGeoJson()` al servicio y transforme su respuesta en `AreaInformation`. La calidad global y los porcentajes de las fuentes son ficticios y se identifican como datos de demostración. El mapa publicado no contiene capas científicas ni campos consultables propios; no se infieren coberturas reales de esas fuentes. Los logos no implican consultas a sus APIs.

Crear MPA / Continuar abre por ahora un aviso de área preparada; no crea una MPA real. Los SVG originales están en `design/source-assets`; sus versiones VectorDrawable están en `res/drawable`.

Validación: `./gradlew.bat assembleDebug testDebugUnitTest lintDebug`.

Documentación: [instalación del SDK](https://docs.mapbox.com/android/maps/guides/install/), [anotaciones](https://docs.mapbox.com/android/maps/guides/add-your-data/annotations/).
