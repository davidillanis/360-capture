Quiero desarrollar un **MVP de una aplicación Android para crear fotografías panorámicas 360° × 180°** usando la cámara de un teléfono.

### Objetivo

El usuario debe poder:

1. Iniciar una captura.
2. Girar lentamente sobre sí mismo.
3. La aplicación debe usar los sensores para determinar la orientación y tomar fotografías automáticamente.
4. Utilizar la menor cantidad razonable de fotografías, sin fijar inicialmente un número.
5. Unirlas mediante **stitching**.
6. Generar un panorama **equirectangular 2:1**.
7. Guardarlo localmente.
8. Visualizarlo en un visor 360°, permitiendo mirar en todas las direcciones, mover con el dedo, usar los sensores y hacer zoom.
9. Poder repetir la captura si el resultado es malo.

### Tecnologías disponibles

Quiero determinar cuáles utilizar y cuáles descartar:

* Android Studio
* Kotlin
* Java
* CameraX
* OpenCV
* Python
* Spring Boot
* React Native
* Flutter
* PostgreSQL

Mi preferencia inicial es **Android nativo con Kotlin + CameraX + sensores Android + OpenCV**.

Python puede utilizarse para experimentar y validar algoritmos, pero no debe ser necesario dentro de la app. Spring Boot, PostgreSQL, React Native y Flutter no deberían utilizarse en el MVP salvo que exista una razón técnica clara.

### Restricciones

* Funcionamiento completamente offline.
* Sin backend ni nube.
* Sin usuarios, mapas, GPS ni cuentas.
* Priorizar simplicidad, rendimiento y estabilidad.
* No sobreingeniería.

### Futuro

La arquitectura debe permitir posteriormente agregar:

* backend con Spring Boot;
* PostgreSQL;
* almacenamiento cloud;
* GPS y mapas;
* múltiples panoramas conectados;
* recorridos virtuales tipo Street View;
* aplicación web;
* cámaras 360 externas.

### Lo que necesito de ti

Primero analiza el proyecto y determina:

1. **Qué tecnologías usar y cuáles descartar.**
2. **Arquitectura mínima recomendada.**
3. **Cómo realizar la captura automática 360° × 180°.**
4. **Cómo determinar inteligentemente cuándo tomar cada fotografía.**
5. **Cómo realizar el stitching con OpenCV.**
6. **Cómo generar el panorama equirectangular.**
7. **Qué visor 360° utilizar.**
8. **Principales dificultades técnicas y cómo resolverlas.**

Después, propón un **plan de desarrollo paso a paso**, empezando por el prototipo más sencillo posible.

La prioridad es construir primero:

**CÁMARA → SENSORES → CAPTURA AUTOMÁTICA → STITCHING → PANORAMA 360° → VISOR 360°**
