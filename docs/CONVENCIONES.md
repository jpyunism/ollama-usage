# Convenciones de trabajo (agente + humano)

Destilado del BMAD Method, recortado a lo que paga: **la story como contrato de
contexto** y **el review adversarial**. Sin la capa de roles (PM / Scrum Master / PO);
en este repo el orquestador y JuanPa cubren eso. Estas reglas son de cumplimiento
obligatorio y complementan a `AGENTS.md` (que sigue siendo la fuente de verdad de
arquitectura, releases y UI).

## 1. La story es un contrato de contexto

Toda unidad de trabajo (feature, fix, refactor) nace como un archivo versionado en
`docs/specs/NN-<slug>.md`, commiteado a `main` **antes** de escribir código. Es el
contrato que el agente ejecuta, no una nota de intención.

Un contrato sirve si un agente puede implementarlo **sin releer el repo**. Obligatorio:

- **Objetivo** — una frase, en términos de comportamiento observable.
- **Por qué** — el problema real que resuelve. Sin esto el agente optimiza lo incorrecto.
- **Alcance y límites** — qué módulos/archivos tocar y **qué NO** (cambio quirúrgico:
  nada de refactors de paso).
- **Contexto embebido** — rutas exactas, firmas, nombres de símbolos, invariantes del
  módulo y trampas conocidas que afectan la tarea. Si el agente tiene que ir a buscarlo,
  falta contexto: la falla es del contrato, no del agente.
- **Criterios de aceptación verificables** — dado / cuando / entonces, cada uno atado a
  una evidencia concreta (un test que pasa, un comando, una captura).
- **Evidencia requerida** — qué salida real hay que producir para cerrar (ver §3).

Tamaño: un contrato = una unidad coherente, implementable y revisable de una sentada.
Si no cabe, son varios contratos. Un cambio trivial (typo, texto de string) no necesita
contrato — no todo se documenta, pero todo lo que se documenta manda.

## 2. Review adversarial

El review no confirma: busca romper. Reglas:

- **Encontrar problemas es el objetivo.** "Cero hallazgos" no es aprobación, es señal de
  review superficial: se re-analiza con otra lente (casos borde, concurrencia, estado
  persistido, regresiones, memoria/proceso) antes de aprobar.
- **Severidad explícita** — HIGH / MEDIUM / LOW. HIGH bloquea el merge; MEDIUM se resuelve
  o se justifica por escrito en el PR; LOW se anota.
- **Hallazgo = evidencia + reproducción.** Cada hallazgo cita `archivo:línea` y cómo
  reproducirlo. Prohibido el "podría fallar en…" sin prueba.
- **El autor no se auto-revisa.** Otra instancia de agente (o el humano) revisa.
- **Cierre del gate.** No se mergea con HIGH abiertos ni con criterios de aceptación sin
  evidencia. La frase "ya está" de quien implementó no es evidencia.

### Lentes obligatorias en este repo (Kotlin/Compose)

1. **Shadowing Kotlin / recursión infinita** — la regresión más cara del proyecto:
   una `fun` local homónima de un lambda del constructor o de una función del **mismo
   paquete `ui`** produce `StackOverflowError` en el Recomposer → **UI congelada sin
   crash visible**. Síntoma: capturas idénticas (mismo md5) + `crash.log`. Verificar el
   nombre de toda función/lambda nueva contra su scope y su paquete.
2. **Fronteras de módulo** — respetar los 9 módulos y sus dependencias (`:core:model` no
   depende de nada; `:feature:*` → `:core:*`; `:app` → todos). Test con su código.
3. **Persistencia tras force-stop** — los settings (idioma, tema, modo oscuro, frecuencia)
   deben sobrevivir a cerrar y relanzar, y cambiar idioma **no** debe resetear la pestaña
   activa (regresión v0.22.1).
4. **UI Material 3** — sólo `androidx.compose.material3.*` y `MaterialTheme.*`; la única
   excepción documentada es el widget de home screen.

## 3. Verificación empírica (nada de auto-reporte)

Se cierra con salida real de herramientas:

1. **Build + tests + lint**:
   ```bash
   JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8 ANDROID_HOME=/home/jyunis/android-sdk \
     ./gradlew testDebugUnitTest lintDebug assembleRelease
   ```
2. **Emulador (obligatorio antes de publicar)** — AVD `test64` headless, instalar el APK
   release y navegar las pantallas afectadas con `adb shell input tap` / `uiautomator dump`
   / `screencap`. Procedimiento exacto en `AGENTS.md` §Validación en emulador.
3. **Sin crashes ni ANR**: `adb logcat -d -b crash` → buscar `FATAL EXCEPTION` / `ANR in
   com.jpyunism.ollamacloudusage`.
4. **Release obligatorio** al cerrar funcionalidad (bump `versionCode`/`versionName`, tag,
   release de GitHub con el APK firmado CN=JuanPa, APK por Telegram). Ver `AGENTS.md`
   §Publicación de releases.
5. **Grafos sincronizados** al terminar: `codegraph sync .` y `graphify update .`, y
   verificar por contenido (`source_file`/`label`), no por sello.

La declaración de un subagente ("subí el PR", "los tests pasan") es un **auto-reporte**:
el orquestador verifica el estado real del PR/CI antes de darlo por cierto.

## 4. Trazabilidad

`spec (NN) → issue → rama/worktree → PR → review → merge → CHANGELOG + versión`. El PR
referencia el spec; el spec sobrevive al merge: es la historia del *por qué*, no basura.
