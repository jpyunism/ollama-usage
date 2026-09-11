package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la logica de agregacion de la comparativa entre cuentas (issue #93):
 * armado de la lista de filas, aislamiento del estado por cuenta y criterio de
 * visibilidad (0-1 cuentas -> oculta).
 */
class AccountUsageTest {

    private fun account(id: String, label: String) = Account(id = id, label = label, apiKey = "key-$id")

    @Test
    fun `arma una fila por cuenta en el orden de entrada`() {
        val accounts = listOf(account("a1", "Personal"), account("a2", "Trabajo"))

        val rows = accountUsageList(accounts, emptyMap(), activeId = "a1")

        assertEquals(2, rows.size)
        assertEquals(listOf("a1", "a2"), rows.map { it.accountId })
        assertEquals(listOf("Personal", "Trabajo"), rows.map { it.label })
    }

    @Test
    fun `marca solo la cuenta activa`() {
        val accounts = listOf(account("a1", "Personal"), account("a2", "Trabajo"))

        val rows = accountUsageList(accounts, emptyMap(), activeId = "a2")

        assertFalse(rows.first { it.accountId == "a1" }.isActive)
        assertTrue(rows.first { it.accountId == "a2" }.isActive)
    }

    @Test
    fun `sin estado conocido queda en Loading`() {
        val rows = accountUsageList(listOf(account("a1", "Personal")), emptyMap(), activeId = null)
        assertEquals(AccountUsageState.Loading, rows.single().state)
    }

    @Test
    fun `aplica el estado provisto por cuenta`() {
        val states = mapOf(
            "a1" to AccountUsageState.Success(weeklyPercent = 41.5, sessionPercent = 12.0),
            "a2" to AccountUsageState.Failure(UsageError.InvalidApiKey),
        )

        val rows = accountUsageList(
            listOf(account("a1", "Personal"), account("a2", "Trabajo")),
            states,
            activeId = "a1",
        )

        assertEquals(
            AccountUsageState.Success(41.5, 12.0),
            rows.first { it.accountId == "a1" }.state,
        )
        assertEquals(
            AccountUsageState.Failure(UsageError.InvalidApiKey),
            rows.first { it.accountId == "a2" }.state,
        )
    }

    @Test
    fun `withState actualiza solo la cuenta indicada y aisla el error`() {
        val rows = accountUsageList(
            listOf(account("a1", "Personal"), account("a2", "Trabajo")),
            mapOf("a1" to AccountUsageState.Success(10.0, 5.0)),
            activeId = "a1",
        )

        val updated = rows.withState("a2", AccountUsageState.Failure(UsageError.InvalidApiKey))

        // La cuenta con error queda en Failure sin tocar a la que ya tenia datos.
        assertEquals(
            AccountUsageState.Success(10.0, 5.0),
            updated.first { it.accountId == "a1" }.state,
        )
        assertEquals(
            AccountUsageState.Failure(UsageError.InvalidApiKey),
            updated.first { it.accountId == "a2" }.state,
        )
        // El label y el flag de activa se preservan.
        assertEquals("Trabajo", updated.first { it.accountId == "a2" }.label)
        assertTrue(updated.first { it.accountId == "a1" }.isActive)
    }

    @Test
    fun `withState con id inexistente no cambia nada`() {
        val rows = accountUsageList(listOf(account("a1", "Personal")), emptyMap(), activeId = "a1")
        val updated = rows.withState("no-existe", AccountUsageState.Success(1.0, 1.0))
        assertEquals(rows, updated)
    }

    @Test
    fun `comparativa oculta con 0 o 1 cuentas y visible con 2 o mas`() {
        assertFalse(showAccountsComparison(emptyList()))
        assertFalse(showAccountsComparison(listOf(account("a1", "Personal"))))
        assertTrue(
            showAccountsComparison(listOf(account("a1", "Personal"), account("a2", "Trabajo"))),
        )
    }
}
