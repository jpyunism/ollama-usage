package com.jpyunism.ollamacloudusage

import android.widget.LinearLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

/**
 * Test de instrumentación del widget: reproduce en el dispositivo real el
 * fallo que reporta el launcher con "Couldn't add widget".
 *
 * El launcher construye el widget con `AppWidgetHostView`, que hace
 * `RemoteViews.apply()` sobre el layout del provider. Si alguna acción del
 * RemoteViews invoca un método que el framework no permite (uno sin la
 * anotación `@RemotableViewMethod` en una vista `@RemoteView`), `apply()`
 * lanza `RemoteViews.ActionException` y el host muestra su error view en vez
 * del widget — exactamente el síntoma reportado.
 *
 * Estos tests recorren la misma ruta (`buildViews` / `buildCompactViews`) y
 * fallan si vuelve a colarse una acción no permitida.
 */
class UsageWidgetRemoteViewsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun data(): UsageData = UsageData(
        sessionPercent = 42.5,
        weeklyPercent = 61.0,
        sessionResetAt = Instant.now().plusSeconds(3600),
        weeklyResetAt = Instant.now().plusSeconds(86400),
        sessionModels = emptyList(),
        weeklyModels = emptyList(),
        plan = "Pro",
    )

    /** `apply()` es lo que hace el host: infla el layout y ejecuta cada acción. */
    private fun assertApplies(views: RemoteViews, label: String) {
        val container = LinearLayout(context)
        val applied = views.apply(context, container)
        assertNotNull("$label: apply() devolvió null", applied)
    }

    @Test
    fun widgetGrandeSePuedeAplicar() {
        assertApplies(UsageWidgetProvider.buildViews(context, data()), "4x2 con datos")
    }

    @Test
    fun widgetGrandeSinDatosSePuedeAplicar() {
        assertApplies(UsageWidgetProvider.buildViews(context, null), "4x2 sin datos")
    }

    @Test
    fun widgetCompactoSePuedeAplicar() {
        assertApplies(UsageWidgetProvider.buildCompactViews(context, data()), "2x1 con datos")
    }

    @Test
    fun widgetCompactoSinDatosSePuedeAplicar() {
        assertApplies(UsageWidgetProvider.buildCompactViews(context, null), "2x1 sin datos")
    }

    /** El caso que rompía: consumo en rojo (>= umbral crítico). */
    @Test
    fun widgetEnNivelRojoSePuedeAplicar() {
        val rojo = data().copy(sessionPercent = 99.0, weeklyPercent = 99.0)
        assertApplies(UsageWidgetProvider.buildViews(context, rojo), "4x2 rojo")
        assertApplies(UsageWidgetProvider.buildCompactViews(context, rojo), "2x1 rojo")
    }

    /** Ámbar es el nivel intermedio (entre umbral de alerta y crítico). */
    @Test
    fun widgetEnNivelAmbarSePuedeAplicar() {
        val ambar = data().copy(sessionPercent = 85.0, weeklyPercent = 85.0)
        assertApplies(UsageWidgetProvider.buildViews(context, ambar), "4x2 ámbar")
        assertApplies(UsageWidgetProvider.buildCompactViews(context, ambar), "2x1 ámbar")
    }

    /** El widget real que publica el provider tras guardar datos. */
    @Test
    fun providerCompletoConDatosGuardadosSePuedeAplicar() {
        UsageWidgetProvider.saveData(context, data())
        assertApplies(UsageWidgetProvider.buildViews(context, data()), "provider 4x2")
        assertApplies(UsageWidgetProvider.buildCompactViews(context, data()), "provider 2x1")
        // updateAll no debe lanzar aunque no haya widgets instalados.
        UsageWidgetProvider.updateAll(context)
    }
}
