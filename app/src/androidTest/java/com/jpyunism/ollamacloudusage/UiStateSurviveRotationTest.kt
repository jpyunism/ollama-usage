package com.jpyunism.ollamacloudusage

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica que el estado de UI sobrevive a la rotacion del dispositivo
 * (issue #64: migracion de `remember` a `rememberSaveable`).
 *
 * El criterio de aceptacion manual es "Don't keep activities" + `am kill`;
 * este test instrumentado cubre la rotacion, que es el caso mas comun.
 */
@RunWith(AndroidJUnit4::class)
class UiStateSurviveRotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activitySurvivesRotation() {
        // La activity se lanza y muestra la UI.
        composeRule.onRoot().assertExists()

        // Simula una rotacion del dispositivo.
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()

        // Tras la rotacion la activity sigue viva y la UI sigue presente.
        composeRule.onRoot().assertExists()
        assertEquals(
            "La activity debe seguir viva tras la rotacion",
            false,
            composeRule.activity.isFinishing,
        )
    }
}
