# AGENTS.md — Ollama Cloud Usage

## Publicación de releases (obligatorio)

Al terminar cualquier cambio de funcionalidad en este repo, **publicar siempre la nueva versión** antes de dar la tarea por cerrada:

1. Bump `versionCode` (+1) y `versionName` (semver) en `app/build.gradle.kts`.
2. Correr `./gradlew testDebugUnitTest lintDebug assembleRelease` (JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8, ANDROID_HOME=/home/jyunis/android-sdk).
3. Commit + push a `main` y crear tag `v<versionName>` apuntando al commit.
4. Crear release en GitHub con el APK firmado (`app/build/outputs/apk/release/app-release.apk`) y notas de versión.
5. Enviar el APK por Telegram (chat `telegram:15710279`) con el `message` tool.

El APK debe quedar firmado con el cert CN=JuanPa (SHA-256 `05844a35e86e2cf17d604c54268f40b3f53f573e14e046baa722bf2148a5cdda`) para instalarse sobre versiones previas.
