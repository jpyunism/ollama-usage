package com.jpyunism.ollamacloudusage

/**
 * Motor que ejecuta el refresco de fondo según la frecuencia elegida.
 * Se expone a la UI para que el usuario vea qué precisión puede esperar.
 */
enum class RefreshEngine {
    /** Servicio en primer plano: refresco exacto mientras viva, con notificación persistente. */
    FOREGROUND_SERVICE,

    /** WorkManager: mínimo 15 min y ejecución no exacta (batching, Doze). */
    WORK_MANAGER,
}
