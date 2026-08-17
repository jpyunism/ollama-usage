package com.jpyunism.ollamacloudusage.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabPagerMappingTest {

    @Test
    fun `Tab entries tiene 3 elementos en orden Usage Stats Settings`() {
        assertEquals(3, Tab.entries.size)
        assertEquals(Tab.Usage, Tab.entries[0])
        assertEquals(Tab.Stats, Tab.entries[1])
        assertEquals(Tab.Settings, Tab.entries[2])
    }

    @Test
    fun `Round trip tabForPage pageForTab devuelve la misma tab`() {
        Tab.entries.forEach { tab ->
            assertEquals(tab, tabForPage(pageForTab(tab)))
        }
    }

    @Test
    fun `pageForTab asigna Usage 0 Stats 1 Settings 2`() {
        assertEquals(0, pageForTab(Tab.Usage))
        assertEquals(1, pageForTab(Tab.Stats))
        assertEquals(2, pageForTab(Tab.Settings))
    }

    @Test
    fun `tabForPage devuelve Usage para indices fuera de rango`() {
        assertEquals(Tab.Usage, tabForPage(-1))
        assertEquals(Tab.Usage, tabForPage(99))
    }
}
