package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookieExtractorTest {

    @Test
    fun `extrae aid y secure session del string crudo`() {
        val raw = "aid=abc123; __Secure-session=xyz789; _ga=GA1.2.1"
        assertEquals("aid=abc123; __Secure-session=xyz789", CookieExtractor.extract(raw))
    }

    @Test
    fun `mantiene el orden en que aparecen las cookies`() {
        val raw = "__Secure-session=xyz; aid=abc"
        assertEquals("__Secure-session=xyz; aid=abc", CookieExtractor.extract(raw))
    }

    @Test
    fun `tolera espacios y saltos de linea`() {
        val raw = "  aid=abc ;\n __Secure-session=xyz  "
        assertEquals("aid=abc; __Secure-session=xyz", CookieExtractor.extract(raw))
    }

    @Test
    fun `null o vacio devuelve null`() {
        assertNull(CookieExtractor.extract(null))
        assertNull(CookieExtractor.extract(""))
        assertNull(CookieExtractor.extract("   "))
    }

    @Test
    fun `sin pares validos devuelve null`() {
        assertNull(CookieExtractor.extract(";;;"))
        assertNull(CookieExtractor.extract("aid=; __Secure-session="))
    }

    @Test
    fun `falta una cookie requerida devuelve null`() {
        assertNull(CookieExtractor.extract("aid=abc; _ga=GA1.2.1"))
        assertNull(CookieExtractor.extract("__Secure-session=xyz; _ga=GA1.2.1"))
    }

    @Test
    fun `ignora cookies no requeridas`() {
        val raw = "_ga=GA1.2.1; aid=abc; __Secure-session=xyz; _gid=GA1.2.2"
        assertEquals("aid=abc; __Secure-session=xyz", CookieExtractor.extract(raw))
    }
}
