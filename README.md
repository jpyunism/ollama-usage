# Ollama Cloud Usage

App Android para ver el consumo del plan de **Ollama Cloud** (session usage, weekly usage y desglose por modelo).

## Cómo funciona

La página `https://ollama.com/settings` muestra el consumo en tiempo real pero no tiene API pública. La app:

1. Guarda tu cookie de sesión (`aid` + `__Secure-session`) localmente.
2. Hace GET a `https://ollama.com/settings` con esa cookie.
3. Parse el HTML (JSoup) y muestra:
   - **Session usage** (%) y tiempo de reset
   - **Weekly usage** (%)
   - **Desglose por modelo** (requests por modelo)

## Stack

- Kotlin + Jetpack Compose (Material 3)
- OkHttp + JSoup (scraping)
- ViewModel + StateFlow
- Cookie almacenada en `SharedPreferences` (local, no se sube a ningún lado)

## Setup

1. Abre el proyecto en Android Studio (o `./gradlew assembleDebug`).
2. En la app, pega tu cookie de `ollama.com` (las que ves en DevTools → Application → Cookies: `aid=...; __Secure-session=...`).
3. Toca "Actualizar" para refrescar el consumo.

> ⚠️ La cookie de sesión expira. Cuando la página web deje de funcionar, copia una nueva.

## Disclaimer

Uso personal. No afiliado con Ollama. Scraping de tu propia cuenta con tu propia cookie — respeta los términos de servicio.
