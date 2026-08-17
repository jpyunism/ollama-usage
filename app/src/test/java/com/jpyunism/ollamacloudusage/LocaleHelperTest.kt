package com.jpyunism.ollamacloudusage

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleHelperTest {

    private val systemEn = Locale.forLanguageTag("en")
    private val systemEs = Locale.forLanguageTag("es")

    @Test
    fun `explicit spanish wins over system english`() {
        assertEquals(Locale.forLanguageTag("es"), LocaleHelper.resolveLocale(AppLanguage.Spanish, systemEn))
    }

    @Test
    fun `explicit english wins over system spanish`() {
        assertEquals(Locale.forLanguageTag("en"), LocaleHelper.resolveLocale(AppLanguage.English, systemEs))
    }

    @Test
    fun `system follows device locale`() {
        assertEquals(systemEn, LocaleHelper.resolveLocale(AppLanguage.System, systemEn))
        assertEquals(systemEs, LocaleHelper.resolveLocale(AppLanguage.System, systemEs))
    }

    @Test
    fun `system never falls back to a previously applied app locale`() {
        // Simula el bug: el proceso ya aplicó español y "contaminó"
        // Locale.getDefault(); Sistema debe ignorarlo y seguir al dispositivo.
        val previousAppLocale = Locale.forLanguageTag("es")
        Locale.setDefault(previousAppLocale)
        try {
            assertEquals(systemEn, LocaleHelper.resolveLocale(AppLanguage.System, systemEn))
        } finally {
            Locale.setDefault(systemEn)
        }
    }
}
