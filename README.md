# 360Capture

Aplicación Android nativa (Kotlin + Jetpack Compose) para capturar panoramas 360° con la cámara del celular.

## Cómo funciona

1. **Captura automática**: el usuario presiona el botón y gira lentamente sobre sí mismo. La app usa los sensores de orientación (rotation vector) para detectar la rotación y dispara automáticamente 10 fotos, una cada 36°.
2. **Stitching**: las fotos se unen con OpenCV (detección de puntos clave ORB, homografías RANSAC y fusión por proyección). Si no se pueden emparejar características, se usa una proyección cilíndrica determinista guiada por los ángulos del sensor.
3. **Visor 360°**: el resultado se proyecta en una esfera equirectangular (OpenGL ES 2.0). El usuario mueve la vista arrastrando con el dedo o girando el dispositivo (con el modo sensor activo).
4. **Galería**: los panoramas se guardan en el almacenamiento interno de la app y pueden verse, compartirse o borrarse.

## Tecnologías

- Kotlin + Jetpack Compose (Material 3)
- CameraX (preview + captura)
- OpenCV 4.10.0 (`org.opencv:opencv`) — features2d, calib3d, imgproc
- OpenGL ES 2.0 para el visor de esfera 360°
- Sensores: rotation vector / acelerómetro (azimuth desenrollado para rotación continua)

## Estructura

```
app/src/main/java/com/example/
├── camera/          # CameraController (CameraX bind + captura)
├── sensor/          # OrientationSensorManager (azimuth/pitch/roll)
├── stitching/       # FeatureStitcher, CylindricalStitcher, PanoramaStitcher, ImageUtils
├── viewer/          # Visor equirectangular OpenGL
├── data/            # PanoramaRepository (persistencia en filesDir)
├── model/           # Modelos
└── ui/              # ViewModel + pantallas (Capture, Processing, Viewer, Gallery)
```

## Requisitos

- Android Studio (AGP 9.x, compileSdk 36)
- Mínimo Android 7.0 (API 24)
- Teléfono con cámara trasera y giroscopio (recomendado para el modo sensor)

## Build

```bash
./gradlew :app:assembleDebug
```

El APK está firmado con el keystore de depuración incluido (`debug.keystore`). No requiere `.env` ni credenciales.

## Uso

1. Acepta el permiso de cámara.
2. Alinea el horizonte (guía en pantalla) y presiona el botón de captura.
3. Gira lentamente un giro completo. La app dispara una foto cada ~36°.
4. Cuando complete las 10 fotos, espera el procesamiento (stitching).
5. Explora el panorama arrastrando o girando el dispositivo.
6. "Guardar en Galería" lo conserva en la galería de la app.

Modo manual: desactiva el interruptor **AUTO** y toca el obturador para cada foto.

## Limitaciones del MVP

- El stitching por features asume rotación alrededor del eje vertical con poca inclinación.
- La costura entre la primera y la última foto puede ser visible (sin cierre perfecto del bucle 360°).
- Exposición/ISO se aplican de forma básica a la captura (compensación AE e ISO fijo).