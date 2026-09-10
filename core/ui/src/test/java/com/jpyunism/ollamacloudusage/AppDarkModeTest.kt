package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDarkModeTest {

    @Test
    fun `System sigue al sistema`() {
        assertEquals(true, AppDarkMode.resolveDarkMode(AppDarkMode.System, systemDark = true))
        assertEquals(false, AppDarkMode.resolveDarkMode(AppDarkMode.System, systemDark = false))
    }

    @Test
    fun `Light fuerza modo claro`() {
        assertEquals(false, AppDarkMode.resolveDarkMode(AppDarkMode.Light, systemDark = true))
        assertEquals(false, AppDarkMode.resolveDarkMode(AppDarkMode.Light, systemDark = false))
    }

    @Test
    fun `Dark fuerza modo oscuro`() {
        assertEquals(true, AppDarkMode.resolveDarkMode(AppDarkMode.Dark, systemDark = true))
        assertEquals(true, AppDarkMode.resolveDarkMode(AppDarkMode.Dark, systemDark = false))
    }
}
