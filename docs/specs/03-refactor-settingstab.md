# Spec: Refactor de SettingsTab en secciones colapsables

Issue: #58
Estado: draft
Autor: Hermes (subagente spec, 2026-09-10)

## Contexto y objetivo

`SettingsTab.kt` tiene **997 lineas** y contiene 6 subsecciones
(alertas, tema, cuentas, backup, update, resumen diario, refresco).
Es dificil de navegar, requiere scroll largo para llegar a una
seccion especifica, y futuros anadidos lo van a hacer peor.

**Objetivo:** mantener un solo archivo pero refactorizar internamente
en sub-composables colapsables, cada una con icono + titulo + contenido,
estado de expandido persistido, y lineas por composable < 200.

**No es objetivo de este spec:** dividir en archivos separados (queda
como follow-up si el archivo sigue creciendo), ni cambiar la
funcionalidad existente (solo reorganizar visualmente).

## Assumptions

1. Cada seccion existente sigue funcionando identica, solo cambia su
   presentacion (colapsable vs siempre expandida).
2. El estado de expandido/colapsado sobrevive rotacion de pantalla
   (`rememberSaveable`).
3. El usuario espera ver todas las secciones colapsadas al primer
   launch (no asumimos que "la ultima visitada" deba quedar abierta).
4. La primera seccion (Alertas) puede quedar abierta por defecto como
   pista visual de "hay contenido aqui".
5. Las animaciones de expandir/colapsar son suaves (M3 tiene
   `AnimatedVisibility` o se puede hacer con `animateContentSize`).

## Tech stack

- Kotlin 1.9.x
- Compose con `animateContentSize` para la animacion
- `rememberSaveable` para el estado de cada seccion
- Sin nuevas dependencias

## Comandos

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
# Validacion manual en emulador (rotacion + dark mode + ES/EN)
```

## Estructura propuesta

```
app/src/main/java/com/jpyunism/ollamacloudusage/ui/
  SettingsTab.kt                     # refactor: queda como composable raiz
  settings/
    SettingsSection.kt               # composable generico colapsable
    AlertSection.kt                  # extraido de SettingsTab
    ThemeSection.kt                  # extraido
    AccountsSection.kt               # extraido
    BackupSection.kt                 # extraido
    UpdateSection.kt                 # extraido
    DailySummarySection.kt           # extraido
    RefreshSection.kt                # extraido
```

Migracion incremental: empezar moviendo un seccion a la vez con
`git mv` para no perder el blame.

## Code style

```kotlin
// SettingsSection.kt - composable generico reutilizable
@Composable
fun SettingsSection(
    titleRes: Int,
    subtitleRes: Int? = null,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = { expanded = !expanded }, // toda la card es tappable
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(icon)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    subtitleRes?.let { res ->
                        Text(
                            text = stringResource(res),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}
```

## Testing strategy

- **Snapshot test (Roborazzi o Paparazzi):** capturar las 7 secciones
  en estado colapsado y expandido, ES y EN, light y dark theme.
  - Colapsado: ~150 lineas de composables tomadas de `SettingsTab`
    original no se rompen visualmente.
- **Unit test minimo:** `SettingsSection` con un test que verifica que
  `expanded` cambia al hacer click (con `createComposeRule`).
- **No testear:** contenido especifico de cada seccion (ya tienen sus
  propios tests o se asume que el refactor no los rompe).

## Boundaries

- **Always do:**
  - Mantener TODAS las funcionalidades existentes.
  - Persistir estado de expandido con `rememberSaveable`.
  - Localizar el icono chevron de expandir/colapsar.
  - Validar en emulador (rotacion, dark mode, ES/EN, accesibilidad con
    TalkBack).
  - Hacer `git mv` para que el blame no se rompa.
  - Si una seccion se mueve a un archivo nuevo, actualizar
    `SettingsTab.kt` para que la llame desde ahi.
- **Ask first:**
  - Cambiar el comportamiento por defecto de "primera seccion abierta".
  - Agregar un nuevo tipo de seccion que no existe.
  - Cambiar el orden de las secciones.
- **Never do:**
  - Cambiar la logica de alertas, tema, cuentas, backup, etc. (solo UI).
  - Eliminar secciones existentes sin aprobacion explicita.
  - Romper el acceso a settings desde MainActivity.

## Success criteria

1. `SettingsTab.kt` reducido a < 100 lineas (solo orquestacion).
2. 7 archivos nuevos en `ui/settings/`, cada uno < 200 lineas.
3. Todas las funcionalidades accesibles y operativas.
4. Estado de expandido sobrevive rotacion.
5. Screenshot tests pasan para los 4 escenarios (ES/EN x light/dark).
6. Lint limpio, build release firmado en verde.
7. Validado en emulador API 35.

## Estimacion

3h de refactor mecanico + 1h de screenshot tests + 30min de QA.
Total: 4.5h.
