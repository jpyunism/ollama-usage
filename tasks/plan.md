# Implementation Plan: Modo claro/oscuro (ollama-usage)

## Overview

Selector de modo claro/oscuro en Configuración → Apariencia con 3 opciones
(Sistema / Claro / Oscuro), persistente y de aplicación instantánea. Independiente
del tema de color (AppTheme) existente. El widget de home screen sigue al sistema
(vía resources night, no lee la preferencia in-app).

## Architecture Decisions

- **Nuevo enum `AppDarkMode`** (System/Light/Dark) con `labelRes`, mismo patrón que
  `AppTheme` y `AppLanguage`. Persistencia con key nueva `KEY_DARK_MODE` en las
  mismas prefs cifradas.
- **Resolución pura** `resolveDarkMode(mode, systemDark): Boolean` — testeable sin
  Android, default `System` (sigue al sistema).
- **`OllamaUsageTheme` recibe `darkMode`** en vez de calcular `isSystemInDarkTheme()`
  por defecto; el booleano `darkTheme` se resuelve con `resolveDarkMode`.
- **UI**: chips (FilterChip, mismo patrón del selector de idioma) en un Card dentro
  de la sección Apariencia, arriba de los temas de color. Renombra el SectionHeader
  a "Apariencia"; "Temas de color" pasa a mini-título dentro de la sección.
- **`values-night/themes.xml`** nuevo: parent `android:Theme.Material.NoActionBar`
  para que la ventana/splash no flashee en blanco al arrancar en modo oscuro.
  El layout del widget usa `@drawable/widget_background` + colores fijos (excepción
  RemoteViews ya documentada en AGENTS.md) — se mantiene igual, sigue al sistema
  vía night resources si se agregan.

## Task List

- [ ] Task 1: AppDarkMode + resolución + ViewModel (state flow, update, load, persist)
- [ ] Task 2: Theme.kt + MainActivity + SettingsTab UI + strings (es/en)
- [ ] Task 3: values-night/themes.xml (ventana oscura al arrancar)
- [ ] Task 4: Release v0.16.0 (bump versionCode 23, tests+lint+assemble, tag, GitHub release, APK por Telegram)

### Checkpoint: Tasks 1-2
- [ ] Tests nuevos pasan (AppDarkModeTest + UsageViewModelTest)
- [ ] Suite completa verde + lint limpio
- [ ] Release v0.16.0 publicado

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Flash blanco al arrancar en modo oscuro | Med | values-night/themes.xml con parent oscuro |
| Romper tema de color existente | Med | darkMode es ortogonal a theme; tests existentes de AppTheme siguen cubriendo |
| Widget con colores fijos en oscuro | Bajo | Decisión aprobada: sigue al sistema; RemoteViews no lee prefs in-app |

## Open Questions

Ninguna (resueltas en revisión de spec: widget sigue al sistema; modo arriba de temas de color).
