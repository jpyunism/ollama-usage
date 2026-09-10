package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieExpiryTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_000_000_000_000L

    @Test
    fun `sin renovacion conocida devuelve EXPIRED`() {
        val r = CookieExpiry.evaluate(renewedAtMillis = null, nowMillis = now)
        assertEquals(CookieExpiry.Status.EXPIRED, r.status)
        assertEquals(0, r.daysRemaining)
    }

    @Test
    fun `renovacion invalida devuelve EXPIRED`() {
        val r = CookieExpiry.evaluate(renewedAtMillis = 0, nowMillis = now)
        assertEquals(CookieExpiry.Status.EXPIRED, r.status)
    }

    @Test
    fun `cookie recien renovada devuelve OK`() {
        val r = CookieExpiry.evaluate(renewedAtMillis = now, nowMillis = now)
        assertEquals(CookieExpiry.Status.OK, r.status)
        assertEquals(7, r.daysRemaining)
    }

    @Test
    fun `con 4 dias restantes devuelve OK`() {
        val renewed = now - 3 * day
        val r = CookieExpiry.evaluate(renewedAtMillis = renewed, nowMillis = now)
        assertEquals(CookieExpiry.Status.OK, r.status)
        assertEquals(4, r.daysRemaining)
    }

    @Test
    fun `con 2 dias restantes devuelve EXPIRING_SOON`() {
        val renewed = now - 5 * day
        val r = CookieExpiry.evaluate(renewedAtMillis = renewed, nowMillis = now)
        assertEquals(CookieExpiry.Status.EXPIRING_SOON, r.status)
        assertEquals(2, r.daysRemaining)
    }

    @Test
    fun `con 3 dias restantes devuelve OK (umbral estricto menor a 3)`() {
        val renewed = now - 4 * day
        val r = CookieExpiry.evaluate(renewedAtMillis = renewed, nowMillis = now)
        assertEquals(CookieExpiry.Status.OK, r.status)
        assertEquals(3, r.daysRemaining)
    }

    @Test
    fun `cookie ya expirada devuelve EXPIRED`() {
        val renewed = now - 8 * day
        val r = CookieExpiry.evaluate(renewedAtMillis = renewed, nowMillis = now)
        assertEquals(CookieExpiry.Status.EXPIRED, r.status)
        assertEquals(0, r.daysRemaining)
    }

    @Test
    fun `vida util custom cambia el umbral`() {
        // Vida util de 30 dias, renovada hace 28 dias: quedan 2 -> EXPIRING_SOON.
        val renewed = now - 28 * day
        val r = CookieExpiry.evaluate(renewedAtMillis = renewed, nowMillis = now, lifetimeDays = 30)
        assertEquals(CookieExpiry.Status.EXPIRING_SOON, r.status)
        assertEquals(2, r.daysRemaining)
    }
}
