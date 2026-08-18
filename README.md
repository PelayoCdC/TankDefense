# Tower Defense 3D

Tower defense 3D hecho con libGDX (Java) para **Windows** y **Android**. Todo el
arte se genera por código con primitivas (`ModelBuilder`): no hay assets
externos salvo los JSON de datos.

## Características

- **100 niveles** agrupados en **10 biomas** (uno por decena). Cada bioma tiene
  paleta propia, skin de enemigos, una **mecánica de mapa** (tormentas de arena,
  arbustos, hielo, lava, niebla, cristales, portales, viento, combos) y un
  **rasgo especial de enemigos** (sprint, regeneración, armadura, escudos,
  división, evasión, élites con aura...).
- **8 torres base** con 4 tipos de ataque: proyectil, hitscan (francotirador),
  área continua (lanzallamas) y rayo con daño creciente (láser).
- **Fusión de torres**: construye una torre encima de otra con receta
  desbloqueada y se combinan (8 recetas, p.ej. veneno + ralentizador = Plaga).
- **Progresión meta**: árbol de mejoras permanente comprado con cristales y
  base con edificios (mina, laboratorio, taller) comprados con **medallas**
  (1 por cada estrella nueva conseguida).
- **Tutorial** guiado en los niveles 1-5. Guardado local con escritura atómica.

## Compilar y ejecutar

```bash
# Windows (desktop)
gradlew :lwjgl3:run

# Android (dispositivo por USB o emulador arrancado)
gradlew :android:installDebug

# APK release (sin firmar)
gradlew :android:assembleRelease
```

Flags de desarrollo (solo desktop): `-Dtd.level=42` arranca directo en un
nivel; `-Dtd.autotowers=true` coloca torres automáticamente (pruebas).

## Herramientas (módulo `tools`)

```bash
# Regenera los niveles 6-100 (los 1-5, tutoriales, son a mano y no se tocan)
gradlew :tools:generateLevels           # no sobrescribe existentes
gradlew :tools:generateLevels -Pforce   # regenera todo

# Simula los 100 niveles sin gráficos con una IA sencilla (validación de balance)
gradlew :tools:simulate

# Test del ciclo de serialización del guardado
gradlew :tools:checkSave
```

## Estructura

```
assets/            datos JSON (towers, enemies, fusions, biomes, upgrades,
                   buildings) y niveles (levels/level_XXX.json + índice)
core/              todo el juego (mundo, render, UI, meta), sin código de plataforma
lwjgl3/            launcher de Windows
android/           launcher de Android (minSdk 21, target 35)
tools/             generador de niveles, simulador de balance
```

Puntos clave del código:

- `core/.../world/GameWorld.java` — simulación completa (timestep fijo, sin render)
- `core/.../world/BiomeMechanics.java` — las 10 mecánicas de mapa
- `core/.../render/ModelFactory.java` — todo el "arte" 3D por primitivas
- `core/.../data/Balance.java` + `tools/.../DifficultyCurve.java` — todo el
  balance en fórmulas; cambiar y regenerar niveles
- `core/.../meta/MetaEffects.java` — único punto de aplicación de bonus meta

## Audio

Todo el audio se **sintetiza por código** (`core/src/.../audio/`): 13 efectos y
11 temas musicales generativos (uno por bioma + menú), cada uno con su escala,
tempo e instrumentación. Los WAV se generan la primera vez que suenan y se
cachean en el almacenamiento local (`audio_v1/`); el APK no lleva ni un byte de
audio. Para regenerarlos tras cambiar el sinte, sube `CACHE_VERSION` en
`SoundManager`.

## Pendiente / ideas

- Fuente TTF empaquetada (ahora usa la BitmapFont por defecto de libGDX).
- Perfilado en dispositivo Android real (GLProfiler) y firma del APK release.
