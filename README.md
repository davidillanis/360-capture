# 360Capture

Aplicación Android nativa (Kotlin + Jetpack Compose) para capturar panoramas 360° × 180° con la cámara del celular. Primera etapa: captura manual guiada y proyección esférica verificable.

## Cómo funciona

1. **Captura manual**: cada pulsación toma una foto. CameraX registra la orientación del sensor en su callback de inicio de captura, antes de guardar el JPEG. Mantén el teléfono quieto durante el disparo.
2. **Geometría de cada foto**: se utiliza el eje óptico de la cámara trasera, la rotación de pantalla, la orientación EXIF, la distancia focal y las dimensiones del sensor activo. El campo de visión no se fija a 67°.
3. **Proyección equirectangular**: OpenCV proyecta los rayos de cada foto directamente sobre un lienzo de **2048 × 1024**, con rotación 3D completa y fusión ponderada de bordes. La longitud envuelve correctamente 0°/360° y ±180°. No se recorta ni estira una panorámica plana para simular una esfera.
4. **Cobertura**: durante la captura se estima la superficie fotografiada mediante rayos de la esfera, reservando margen para solapamiento. La rejilla de 24 zonas es un indicador, no una obligación de tomar 24 fotos. El resultado comprueba cada píxel y señala huecos, cenit y nadir; los huecos permanecen negros.
5. **Visor y galería**: arrastra o gira el teléfono para explorar el resultado. Se puede guardar el JPEG y su informe de cobertura en la galería interna, compartirlo o borrarlo.

## Tecnologías

- Kotlin + Jetpack Compose (Material 3)
- CameraX (preview + captura)
- OpenCV 4.10.0 (`org.opencv:opencv`) — remap y fusión por bloques
- OpenGL ES 2.0 para el visor de esfera 360°
- Sensores: rotation vector / game rotation vector. Un acelerómetro por sí solo no determina el rumbo.

## Estructura

```
app/src/main/java/com/example/
├── camera/          # CameraController (captura, óptica y orientación al disparar)
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

1. Acepta el permiso de cámara.
2. Apunta al horizonte, mantén el teléfono quieto y pulsa el obturador.
3. Sigue el azimut y la elevación sugeridos. Haz fotos solapadas alrededor, hacia arriba y hacia abajo. Intenta rotar alrededor de la cámara, evitando desplazarla.
4. Consulta el porcentaje de cobertura y las zonas de la rejilla. Repetir la misma dirección no completa la esfera.
5. Pulsa **Ver panorama parcial** en cualquier momento, o **Ver panorama** cuando la estimación esté completa.
6. Revisa los huecos negros y el informe de cobertura. La flecha de volver conserva las tomas para añadir fotos y procesar otra vez. **Reiniciar** o **Retomar** comienza una sesión nueva.
7. **Guardar en Galería** conserva el resultado y su cobertura en la galería interna.

## Compartir por WhatsApp

1. Abre el panorama recién creado o selecciónalo en la galería de la app.
2. Pulsa **Enviar por WhatsApp** (también disponible en el icono de compartir).
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

- Pruebas JVM: cambios de orientación, eje óptico, rotación de pantalla, focal/crop, cruce de longitudes, polos, cobertura, persistencia del informe y matrices del visor.
- Compartición: validación del XMP, comparación del JPEG comprimido antes/después de exportar, permisos de lectura de la URI, MIME de documento y selección de WhatsApp/WhatsApp Business.
- Pruebas instrumentadas: OpenCV nativo reconstruye una esfera de colores conocida a partir de seis fotos sintéticas; comprueba también fotos inclinadas, brillo, ausencia de contaminación fuera de la máscara y el pipeline JPEG → equirectangular.
- Para validar en un teléfono real: fotografía una escena estática con líneas rectas y referencias reconocibles alrededor, en el techo y en el suelo. Comprueba el horizonte, la unión al volver al inicio y ambas regiones polares. Una captura limitada al horizonte debe quedar marcada como incompleta.

## Limitaciones del MVP

- La cobertura es geométrica: no mide nitidez, exposición ni calidad visual de las uniones.
- El alineamiento depende de los sensores y de una aproximación pinhole con recorte centrado. Quedan por calibrar la distorsión de lente y las particularidades de cada dispositivo; los errores de rumbo, el movimiento y el paralaje pueden producir dobles bordes.
- La fusión suaviza los bordes; todavía no hay refinamiento por características, compensación de exposición ni cierre global del bucle. `FeatureStitcher` se conserva como código experimental y no participa en esta etapa.
- La captura automática y el zoom del visor quedan para etapas posteriores. Los controles de HDR y exposición/ISO aún requieren completar su integración; no hay síntesis por IA.
- Las tomas de una sesión están en caché y aún no se recuperan tras cerrar el proceso.
