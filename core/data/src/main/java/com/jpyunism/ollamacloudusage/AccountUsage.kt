package com.jpyunism.ollamacloudusage

/**
 * Estado del fetch de consumo de UNA cuenta para la comparativa multi-cuenta
 * (issue #93). Cada cuenta avanza de [Loading] a [Success] o [Failure] de
 * forma independiente: una API key invalida no rompe las demas.
 */
sealed interface AccountUsageState {
    /** Fetch en curso o pendiente para esta cuenta. */
    data object Loading : AccountUsageState

    /** Consumo obtenido: % semanal y % de sesion de la cuenta. */
    data class Success(val weeklyPercent: Double, val sessionPercent: Double) : AccountUsageState

    /** El fetch de esta cuenta fallo (key invalida, red, etc.). */
    data class Failure(val error: UsageError) : AccountUsageState
}

/**
 * Fila de la comparativa entre cuentas (issue #93): los datos de presentacion
 * (label, si es la activa) mas el [state] del fetch de su consumo.
 */
data class AccountUsage(
    val accountId: String,
    val label: String,
    val isActive: Boolean,
    val state: AccountUsageState,
)

/**
 * Arma la lista de comparativa: una fila por cuenta, en el orden de [accounts],
 * con el estado que le corresponda en [states] (o [AccountUsageState.Loading]
 * si todavia no se consulto). Marca la activa segun [activeId]. Logica pura.
 */
fun accountUsageList(
    accounts: List<Account>,
    states: Map<String, AccountUsageState>,
    activeId: String?,
): List<AccountUsage> = accounts.map { account ->
    AccountUsage(
        accountId = account.id,
        label = account.label,
        isActive = account.id == activeId,
        state = states[account.id] ?: AccountUsageState.Loading,
    )
}

/**
 * Aplica el resultado de una cuenta puntual sin tocar las demas filas
 * (aislamiento de errores, issue #93). No-op si el id no esta en la lista.
 */
fun List<AccountUsage>.withState(accountId: String, state: AccountUsageState): List<AccountUsage> =
    map { if (it.accountId == accountId) it.copy(state = state) else it }

/**
 * La comparativa solo tiene sentido con 2+ cuentas; con 0-1 se oculta
 * (criterio de aceptacion del issue #93).
 */
fun showAccountsComparison(accounts: List<Account>): Boolean = accounts.size >= 2
