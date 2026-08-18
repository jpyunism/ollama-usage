package com.jpyunism.ollamacloudusage

import android.content.Context
import com.jpyunism.ollamacloudusage.UpdateChecker
import com.jpyunism.ollamacloudusage.UpdateInfo

/**
 * Acceso al chequeo de actualizaciones. Envuelve [UpdateChecker] (que sigue
 * siendo el object con la lógica pura de parseo) y recibe el contexto de app
 * inyectado: el ViewModel no referencia Context.
 */
class UpdateRepository(
    private val context: Context,
) {
    /** No consultar GitHub más de una vez por día (rate limit + batería). */
    fun shouldCheck(): Boolean = UpdateChecker.shouldCheck(context)

    fun markChecked() {
        UpdateChecker.markChecked(context)
    }

    /** Consulta el release latest; null si no hay versión más nueva. */
    fun check(): UpdateInfo? = UpdateChecker.check(context)

    /** Versión instalada de la app. */
    fun currentVersion(): String = UpdateChecker.currentVersion(context)
}
