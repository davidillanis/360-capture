# 360Capture

Aplicación Android nativa (Kotlin + Jetpack Compose) para capturar panoramas 360° × 180° con la cámara del celular. Captura automática con puntos guía, alineación visual y proyección esférica.

## Cómo funciona

1. **Captura automática guiada**: el campo de visión de la cámara determina un recorrido solapado por el horizonte, bandas superiores e inferiores, cenit y nadir. La foto se dispara al centrar el objetivo (±3°), enderezar el teléfono y permanecer estable durante 850 ms. Se comprueba también el movimiento durante el enfoque/disparo; una toma movida se descarta y el objetivo sigue pendiente. Se puede pausar y continuar, y la captura se pausa al salir de la pantalla, abrir ayuda o enviar la app al fondo.
2. **Geometría de cada foto**: se utiliza el eje óptico de la cámara trasera, la rotación de pantalla, la orientación EXIF, la distancia focal y las dimensiones del sensor activo. El campo de visión no se fija a 67°.
3. **Alineación y proyección**: ORB busca coincidencias entre imágenes solapadas, incluidas las del cierre de la vuelta y distintas elevaciones. RANSAC rechaza coincidencias falsas y un ajuste global refina las rotaciones 3D con una referencia de sensor. Se equilibran brillo/color con muestras de los mismos rayos en los solapamientos. OpenCV proyecta las fotos sobre un lienzo **3072 × 1536**, conservando como máximo dos fuentes por píxel y mezclando solo una franja estrecha; los desacuerdos de alto contraste mantienen una sola fuente. La longitud envuelve correctamente 0°/360° y ±180°. No se estira una panorámica plana para simular una esfera.
4. **Cobertura**: durante la captura se estima la superficie fotografiada mediante rayos de la esfera, reservando margen para solapamiento. La rejilla de 24 zonas es un indicador, no una obligación de tomar 24 fotos. El resultado comprueba cada píxel y señala huecos, cenit y nadir; los huecos permanecen negros.
5. **Inicio, visor y galería**: el inicio ofrece «Crear foto 360°» y «Mis fotos». Al completar la captura, se crea y guarda automáticamente el JPEG con su informe de cobertura en la galería interna. Arrastra para explorar el resultado, activa «Mover con el teléfono» si lo prefieres o compártelo por WhatsApp.
6. **Calidad de cámara**: modo CameraX de máxima calidad, JPEG 98, flash desactivado y enfoque/medición antes de cada foto. El balance de blancos se bloquea tras el enfoque inicial si el dispositivo lo admite; la exposición permanece automática para adaptarse al techo y suelo. La orientación de pantalla queda fija hasta reiniciar la sesión; la vista previa ajustada conserva la geometría del punto guía.

## Tecnologías

- Kotlin + Jetpack Compose (Material 3)
- CameraX (preview + captura)
- OpenCV 4.10.0 (`org.opencv:opencv`) — ORB, correspondencias y remap por bloques
- OpenGL ES 2.0 para el visor de esfera 360°
- Sensores: se prefiere game rotation vector para evitar saltos magnéticos en interiores, con rotation vector como alternativa. Un acelerómetro por sí solo no determina el rumbo.

## Estructura

```
app/src/main/java/com/example/
├── camera/          # CameraController (captura, óptica y orientación al disparar)
├── capture/         # Planificador, control de estabilidad y geometría persistida por foto
├── sensor/          # Orientación 3D de la cámara trasera
├── stitching/       # SphericalGeometry, SphericalCoverage, EquirectangularStitcher, PanoramaStitcher
├── viewer/          # Visor equirectangular OpenGL
├── sharing/         # JPEG con metadatos GPano y envío como documento a WhatsApp
├── data/            # PanoramaRepository (persistencia en filesDir)
├── model/           # Modelos
└── ui/              # ViewModel + pantallas (Capture, Processing, Viewer, Gallery)
```

## Requisitos

- Android Studio (AGP 9.x, compileSdk 36)
- Mínimo Android 7.0 (API 24)
- Teléfono con cámara trasera, metadatos ópticos y sensor rotation vector o game rotation vector

## Build

```bash
./gradlew :app:assembleDebug
```

La configuración de depuración utiliza `debug.keystore` en la raíz del proyecto. No requiere `.env` ni credenciales para capturar y procesar.

## Uso

1. Pulsa **Crear foto 360°** en el inicio. **Mis fotos** abre las fotos guardadas.
2. Pulsa **Permitir cámara** si se solicita. Apunta al frente con el teléfono a la altura de tus ojos y pulsa **Empezar**.
3. Sigue la flecha y la instrucción grande: derecha, izquierda, arriba o abajo. Lleva el punto amarillo al círculo central. Cuando aparezca **Quédate quieto**, espera a que se llene el círculo: la foto se toma sola. La guía suaviza las pequeñas oscilaciones del sensor y tolera breves vibraciones sin reiniciar el círculo. Si el disparo automático no llega, acerca el punto al círculo y pulsa **Tomar foto ahora**; el botón solo se activa cerca del objetivo. Las tomas que se mueven durante el disparo se descartan también en modo manual.
4. La guía recorre tres etapas: **A tu alrededor → Hacia arriba → Hacia abajo**, con un porcentaje de avance. **Mantén la cámara en el mismo lugar mientras giras**; inclina el teléfono para mirar arriba y abajo, sin levantarlo ni bajarlo. **Ayuda** explica el gesto cuando lo necesites.
5. Al completar los puntos y los espacios pendientes, la app crea la foto, la guarda automáticamente en **Mis fotos** y abre el resultado. Si falla el guardado, el resultado permite **Reintentar guardado** sin mostrar una confirmación falsa.
6. Arrastra la imagen para explorarla. **Mover con el teléfono** activa el movimiento por sensores. **Compartir por WhatsApp** envía el archivo.
7. Puedes **Pausar** y **Continuar**. Volver al inicio conserva las tomas de la sesión y muestra **Continuar foto 360°**. Abrir ayuda o salir de la app pausa la captura; al volver debes continuar explícitamente. Esto no recupera sesiones tras cerrar el proceso.
8. En pausa, **Crear foto con lo capturado** permite terminar antes; las zonas sin fotografiar se ven negras y la foto se identifica como incompleta. **Completar foto** vuelve a los puntos pendientes de la sesión. **Empezar de nuevo** y **Crear otra foto** inician una captura nueva.

## Diagnóstico de capturas

Los originales nuevos se guardan en `cache/captures/<uuid>/slice_*.jpg`. Cada JPEG tiene un `.json` con la base 3D de la cámara, intrínsecos, fecha y objetivo de captura. `CaptureSessionStore.read` permite recuperar esa geometría para reproducir la unión. Cada resultado en `cache/session/<uuid>/` incluye `alignment.json` con las rutas de los originales, poses corregidas, ganancias y número de parejas aceptadas. No se leen fotos ajenas a la app.

El archivo `alignment.json` informa cuántas fotos tienen apoyo visual. Si faltan detalles o las coincidencias no son fiables, esas fotos conservan su orientación de sensor. La cobertura se recalcula tras el ajuste y **Completar foto** permite volver a los huecos pendientes de la sesión.

## Compartir por WhatsApp

1. Abre el panorama recién creado o selecciónalo en la galería de la app.
2. Pulsa **Compartir por WhatsApp**.
3. Selecciona el contacto o grupo y confirma el envío en WhatsApp.

Se adjunta un **documento `.jpg`**, con la resolución y los píxeles JPEG originales, y metadatos **XMP/GPano** para que los visores compatibles reconozcan la proyección equirectangular. No se pasa por el editor de fotos de WhatsApp. Si tienes WhatsApp y WhatsApp Business, puedes elegir cuál abrir; si no está instalado ninguno, la app lo indica.

El destinatario descarga el documento y lo abre/importa en un visor 360° compatible. El archivo no se convierte en una vista 360° interactiva dentro del chat. Los panoramas parciales se identifican con `_parcial` en el nombre y conservan sus huecos; el informe de cobertura también se incluye en un espacio de nombres propio de XMP, independiente de GPano.

La preparación se realiza localmente. Los documentos temporales con más de siete días se eliminan al preparar nuevos envíos. No hace falta guardar primero el panorama en la galería.

## Verificación

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Con un emulador o dispositivo de pruebas conectado:
./gradlew :app:connectedDebugAndroidTest
```

- Pruebas JVM: geometría, cobertura de planes con tolerancia de apuntado y diferentes ópticas, bloqueo del disparo ante movimiento/roll/interrupciones del sensor, ajuste robusto con coincidencias falsas, cierre global, exposición, persistencia de geometría y matrices del visor.
- Usabilidad: navegación inicio → galería → captura y regreso, indicaciones relativas a la cámara (incluidos polos y cruce 0°/360°), suavizado del sensor, tolerancia al pulso al llenar el círculo, disponibilidad del disparo manual cerca del objetivo, actualización de fotos parciales sin duplicados y conservación del resultado anterior si falla el guardado. Capturas de referencia de inicio y guía con `-Proborazzi.test.record=true`.
- Compartición: validación del XMP, comparación del JPEG comprimido antes/después de exportar, permisos de lectura de la URI, MIME de documento y selección de WhatsApp/WhatsApp Business.
- Pruebas instrumentadas: reconstrucción de una esfera conocida, máscara de cobertura, transiciones sin promediar objetos incompatibles y pipeline JPEG → equirectangular. Un conjunto JPEG sintético texturizado introduce errores de sensor y exposición para medir el ajuste visual, incluyendo cierre de vuelta y polos; otro verifica el caso sin textura.
- Para validar en un teléfono real: fotografía una escena estática con líneas rectas y referencias reconocibles alrededor, en el techo y en el suelo. Comprueba el horizonte, la unión al volver al inicio y ambas regiones polares. Una captura limitada al horizonte debe quedar marcada como incompleta.

## Limitaciones del MVP

- La cobertura es geométrica: no mide nitidez, exposición ni calidad visual de las uniones.
- La alineación visual presupone una escena estática y rotación alrededor de la cámara. No corrige por completo el paralaje, la distorsión de lente ni errores grandes de calibración. No puede recuperar zonas nunca fotografiadas, detalle quemado o desenfoque óptico. La estabilidad se mide con sensores; no es un análisis de nitidez de la foto.
- Las paredes lisas, reflejos y patrones repetidos pueden no ofrecer coincidencias fiables. Se limita la corrección y se conserva el sensor cuando falla el ajuste. `FeatureStitcher` sigue siendo código planar experimental y no participa en este pipeline.
- No hay HDR por múltiples exposiciones, ISO manual, zoom del visor ni síntesis por IA. La interfaz simplificada usa exposición y enfoque automáticos.
- Las tomas de una sesión están en caché y aún no se recuperan tras cerrar el proceso.
- Las pruebas sintéticas no sustituyen una captura real: la calidad de las uniones debe verificarse en el teléfono objetivo con los originales de una sesión.
