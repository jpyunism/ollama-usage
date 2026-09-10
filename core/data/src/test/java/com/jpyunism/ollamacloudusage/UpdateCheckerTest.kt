package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `isNewer compara semver`() {
        assertTrue(UpdateChecker.isNewer("0.11.0", "0.10.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.9"))
        assertTrue(UpdateChecker.isNewer("0.10.1", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("0.10.0", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("0.10.0", "0.10.1"))
    }

    @Test
    fun `version invalida no cuenta como mas nueva`() {
        assertFalse(UpdateChecker.isNewer("abc", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("", "0.10.0"))
    }

    @Test
    fun `release con tag mas nuevo y asset apk devuelve update`() {
        val json = """
            {"tag_name":"v0.11.0","assets":[
              {"name":"app-release.apk","browser_download_url":"https://github.com/jpyunism/ollama-usage/releases/download/v0.11.0/app-release.apk","digest":"sha256:abc123"}
            ]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json, "0.10.0")
        assertEquals("0.11.0", info!!.versionName)
        assertEquals("https://github.com/jpyunism/ollama-usage/releases/download/v0.11.0/app-release.apk", info.downloadUrl)
        assertEquals("abc123", info.sha256)
    }

    @Test
    fun `release mas nuevo sin asset apk no devuelve update`() {
        val json = """{"tag_name":"v0.11.0","assets":[]}"""
        assertNull(UpdateChecker.parseRelease(json, "0.10.0"))
    }

    @Test
    fun `release no mas nuevo que lo instalado devuelve null`() {
        val json = """{"tag_name":"v0.10.0","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""
        assertNull(UpdateChecker.parseRelease(json, "0.10.0"))
        assertNull(UpdateChecker.parseRelease(json, "0.11.0"))
    }

    @Test
    fun `json invalido devuelve null`() {
        assertNull(UpdateChecker.parseRelease("no json", "0.10.0"))
    }
}
