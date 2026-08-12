package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BalanceTest {

    private val reset = Instant.parse("2026-08-09T01:00:00Z") // domingo 21:00 CLT (UTC-4)
    private val sessionDuration = Duration.ofHours(24)
    private val weekDuration = Duration.ofHours(168)

    // ── Casos base ──

    @Test
    fun `mitad del periodo con consumo mayor al esperado es deficit`() {
        val now = reset.minus(Duration.ofHours(12)) // esperado 50%
        val balance = computeBalance(58.0, reset, now, sessionDuration)
        assertEquals(BalanceStatus.DEFICIT, balance?.status)
        assertEquals(8.0, balance?.percentDelta!!, 0.001)
    }

    @Test
    fun `mitad del periodo con consumo menor al esperado es superavit`() {
        val now = reset.minus(Duration.ofHours(12)) // esperado 50%
        val balance = computeBalance(42.0, reset, now, sessionDuration)
        assertEquals(BalanceStatus.SURPLUS, balance?.status)
        assertEquals(8.0, balance?.percentDelta!!, 0.001)
    }

    @Test
    fun `inicio del periodo cualquier consumo es deficit`() {
        val now = reset.minus(sessionDuration) // esperado 0%
        val balance = computeBalance(5.0, reset, now, sessionDuration)
        assertEquals(BalanceStatus.DEFICIT, balance?.status)
        assertEquals(5.0, balance?.percentDelta!!, 0.001)
    }

    @Test
    fun `final del periodo consumo bajo es superavit`() {
        val now = reset.minus(Duration.ofMinutes(1)) // esperado ~99.93%
        val balance = computeBalance(95.0, reset, now, sessionDuration)
        assertEquals(BalanceStatus.SURPLUS, balance?.status)
        assertEquals(4.9, balance?.percentDelta!!, 0.001)
    }

    @Test
    fun `en ritmo exacto devuelve null`() {
        val now = reset.minus(Duration.ofHours(12)) // esperado 50%
        assertNull(computeBalance(50.0, reset, now, sessionDuration))
    }

    // ── Borde ──

    @Test
    fun `reset null devuelve null`() {
        assertNull(computeBalance(50.0, null, reset, sessionDuration))
    }

    @Test
    fun `despues del reset devuelve null`() {
        assertNull(computeBalance(50.0, reset, reset.plusSeconds(1), sessionDuration))
    }

    @Test
    fun `antes del inicio del periodo devuelve null`() {
        assertNull(computeBalance(50.0, reset, reset.minus(sessionDuration).minusSeconds(1), sessionDuration))
    }

    @Test
    fun `delta menor a 0_05 redondea a cero y devuelve null`() {
        val now = reset.minus(Duration.ofHours(12))
        assertNull(computeBalance(50.04, reset, now, sessionDuration))
        assertNull(computeBalance(49.96, reset, now, sessionDuration))
    }

    @Test
    fun `delta redondeado a un decimal se conserva`() {
        val now = reset.minus(Duration.ofHours(12)) // esperado 50%
        val balance = computeBalance(50.06, reset, now, sessionDuration)
        assertEquals(BalanceStatus.DEFICIT, balance?.status)
        assertEquals(0.1, balance?.percentDelta!!, 0.001)
    }

    @Test
    fun `semana usa duracion de 168 horas`() {
        val now = reset.minus(Duration.ofHours(84)) // esperado 50%
        val balance = computeBalance(60.0, reset, now, weekDuration)
        assertEquals(BalanceStatus.DEFICIT, balance?.status)
        assertEquals(10.0, balance?.percentDelta!!, 0.001)
    }

    // ── balanceLabel ──

    @Test
    fun `label de deficit en espanol`() {
        val balance = computeBalance(58.0, reset, reset.minus(Duration.ofHours(12)), sessionDuration)!!
        assertEquals(
            "Déficit 8%",
            balanceLabel(balance, "Déficit %1\$s%%", "Superávit %1\$s%%"),
        )
    }

    @Test
    fun `label de superavit en espanol`() {
        val balance = computeBalance(42.0, reset, reset.minus(Duration.ofHours(12)), sessionDuration)!!
        assertEquals(
            "Superávit 5%",
            balanceLabel(balance.copy(percentDelta = 5.0), "Déficit %1\$s%%", "Superávit %1\$s%%"),
        )
    }

    @Test
    fun `label en ingles`() {
        val balance = computeBalance(42.0, reset, reset.minus(Duration.ofHours(12)), sessionDuration)!!
        assertEquals(
            "Surplus 8%",
            balanceLabel(balance, "Deficit %1\$s%%", "Surplus %1\$s%%"),
        )
    }

    @Test
    fun `label null devuelve null`() {
        assertNull(balanceLabel(null, "Déficit %1\$s%%", "Superávit %1\$s%%"))
    }
}
