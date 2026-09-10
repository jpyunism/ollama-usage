# Spec: Modularizar a Gradle multi-module

Issue: #65
Estado: draft
Autor: Hermes (subagente spec, 2026-09-10)

## Contexto y objetivo

Todo el codigo de la app vive en el modulo `app` (72 archivos .kt,
1 solo modulo Gradle). El clean build es lento, cualquier cambio
reconstruye todo, y los boundaries entre features/capas son
informales (paquetes Kotlin, no enforcement de Gradle).

**Objetivo:** dividir el modulo `app` en sub-modulos por capa y
feature, mejorando el incremental build time y forzando boundaries
mas claros entre componentes.

**No es objetivo:** reescribir codigo existente (refactor mecanico
de `package` a modulo), ni cambiar la API publica, ni dividir en
mas de 9 modulos (el sweet spot para este tamaño).

## Assumptions

1. El refactor es puramente mecanico: los archivos se mueven a
   otros modulos y se ajustan los imports. NO cambia la logica.
2. Los tests van con su codigo (mismo modulo).
3. R8 sigue funcionando con la nueva estructura (verificar
   explicitly que el mapping file se genera).
4. El `:app` final solo tiene: Application class, MainActivity,
   manifest, y DI wiring.
5. Se mantienen las mismas versiones de todas las dependencias.
6. No usamos `version catalog` distinto (seguimos con `libs.versions.toml`
   compartido en el root).

## Tech stack

- Gradle 9.7.1 (ya actualizado)
- AGP 9.4.0 (ya actualizado)
- Kotlin 1.9.x
- Compose multiplatform no (mantenemos single-platform Android)
- Convention plugins NO (seria over-engineering para 9 modulos;
  cada `build.gradle.kts` repite el boilerplate minimo)

## Comandos

```bash
# Build incremental esperado < 5s para cambio en un feature module
./gradlew :feature:usage:assembleDebug
# Build full
./gradlew assembleDebug
# Tests
./gradlew test
# Verificar R8
./gradlew assembleRelease
# APK output esperado: < 2 MB (similar al actual 1.4 MB)
ls -la app/build/outputs/apk/release/app-release.apk
```

## Estructura propuesta

```
ollama-cloud-usage/
  build.gradle.kts
  settings.gradle.kts
  gradle/
    libs.versions.toml              # version catalog compartido
  app/                              # :app - entry point
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/jpyunism/ollamacloudusage/
        OllamaUsageApp.kt           # Application class
        MainActivity.kt             # entry activity
        di/
          AppContainer.kt           # DI manual wiring (conoce todos los modulos)
  core/
    model/                         # :core:model
      build.gradle.kts             # library, JVM only
      src/main/.../data/            # data classes puras (UsageData, Account, AlertSettings)
      src/test/.../
    data/                          # :core:data (Android library)
      build.gradle.kts
      src/main/.../repository/      # UsageRepository, UsageHistoryStore
      src/main/.../scraper/         # OllamaUsageScraper, OllamaApiUsage
      src/test/.../
    net/                           # :core:net (Android library)
      src/main/.../http/            # OkHttpClient builder, interceptors
    notify/                        # :core:notify (Android library)
      src/main/.../notifier/        # UsageNotifier, channels
      src/main/.../widget/          # UsageWidgetProvider, layouts
      src/main/.../service/         # UsageMonitorService, UsageWorker, UpdaterService
    ui/                            # :core:ui (Android library, Compose)
      src/main/.../theme/           # Theme, colors, shapes
      src/main/.../components/      # IconBox, SettingsSection, etc.
  feature/
    usage/                         # :feature:usage
      src/main/.../UsageTab.kt
      src/main/.../UsageViewModel.kt
      src/main/.../UsageScreen.kt
      src/main/res/                # layouts, drawables de la feature
    settings/                      # :feature:settings
      src/main/.../SettingsTab.kt
      src/main/.../settings/*.kt
    stats/                         # :feature:stats
      src/main/.../StatsTab.kt
      src/main/.../DailySummary*.kt
```

## Code style - ejemplo de build.gradle.kts por modulo

```kotlin
// core/data/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.jpyunism.ollamacloudusage.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:net"))
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

## Migracion paso a paso (orden recomendado)

1. **Crear `core:model`** (no-android, pure Kotlin). Mover data classes.
   - Build verde en <5 min.
2. **Crear `core:net`** con el OkHttpClient builder.
3. **Crear `core:data`** con `UsageRepository`, `UsageHistoryStore`,
   scrapers. Depende de `:core:model` y `:core:net`.
4. **Crear `core:notify`** con `UsageNotifier`, `UsageWidgetProvider`,
   services. Depende de `:core:data` (para el modelo).
5. **Crear `core:ui`** con Theme y components. Sin dependencias de
   features.
6. **Crear `feature:usage`**, `feature:settings`, `feature:stats`.
   Cada una depende de las `:core:*` que necesite.
7. **Actualizar `:app`** para que solo tenga `OllamaUsageApp`,
   `MainActivity`, manifest, y `AppContainer` que hace el wiring
   de todos los modulos.
8. **Borrar archivos duplicados** del modulo `app` que ya se movieron.
9. **Verificar**: clean build, tests, R8, release firmado.

Cada paso es un commit independiente con `git mv` para preservar
blame. Despues de cada paso: build + tests + commit.

## Testing strategy

- **Tests existentes:** todos deben seguir pasando sin modificacion
  (estan en el mismo modulo que su codigo).
- **Test de smoke:** `./gradlew clean assembleDebug` desde cero
  debe terminar < 60s (vs >120s actual con todo en un modulo).
- **Test de incremental:** cambiar un solo archivo en
  `:feature:usage`, correr `:app:assembleDebug`, debe terminar
  en <10s.
- **R8:** verificar que `mapping.txt` se genera y que el APK
  release funciona (instalar en emulador, abrir, ver todos los
  tabs).

## Boundaries

- **Always do:**
  - `git mv` para preservar blame.
  - Build + tests verdes despues de cada paso.
  - Documentar la nueva estructura en AGENTS.md (seccion
    "Estructura de modulos").
  - Verificar que R8 funciona.
  - Mantener el CHANGELOG con el refactor.
- **Ask first:**
  - Combinar modulos (ej. unir `core:data` con `core:model`).
  - Agregar un modulo nuevo no listado arriba.
  - Cambiar el nombre de un paquete existente.
- **Never do:**
  - Romper la API publica durante la migracion (todos los `import`s
    deben seguir funcionando con el wrapper o la reexportacion).
  - Eliminar archivos antes de moverlos (mover = `git mv`).
  - Cambiar dependencias a versiones distintas.

## Success criteria

1. 9 modulos Gradle en `settings.gradle.kts`.
2. Clean build < 60s.
3. Incremental build (cambio en `:feature:usage`) < 10s.
4. Todos los tests existentes pasan.
5. R8 genera mapping file y APK release funciona.
6. APK release firmado funciona end-to-end en emulador API 35.
7. AGENTS.md actualizado con la nueva estructura.
8. CHANGELOG con el refactor bajo la seccion de la proxima version.

## Estimacion

6-8h de refactor mecanico (mover archivos, ajustar imports, build).
Sin incluir descubrimiento de bugs latentes (pueden aparecer).
Considerar hacerlo en 2-3 sesiones para no fatigar.

## Riesgos

- **Imports circulares entre modulos** — resolver con un `:core:model`
  puro que no dependa de nada.
- **Recursos compartidos** (icons, strings, themes) — van en `:core:ui`
  y se exponen via `api` en el `build.gradle.kts` del modulo.
- **Manifests con permisos y services** — solo en `:app` y `:core:notify`
  (los services son del feature de notify).
- **R8 keep rules** — los services y providers ya estan en
  `proguard-rules.pro`; no deberia haber cambio, pero verificar.
