package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Una cuenta de Ollama Cloud (método API key) con su credencial.
 * [id] es estable (UUID) y sirve para direccionar el histórico por cuenta.
 */
data class Account(
    val id: String,
    val label: String,
    val apiKey: String,
)

/**
 * Store de cuentas multi-cuenta (Feature A, issue #25).
 *
 * Persiste la lista de cuentas como JSON en prefs (`accounts`) y el id de la
 * activa (`active_account_id`). Sin Room ni DataStore: JSON en prefs como el
 * resto de la app (YAGNI). Lógica testeable en JVM con prefs fake.
 *
 * Migración (REQ-105): si existe una API key única previa y no hay cuentas,
 * [migrateIfNeeded] la importa como primera cuenta activa ("Cuenta 1").
 * La cookie queda como método legacy de una sola cuenta — fuera de alcance.
 */
class AccountStore(
    private val prefs: SharedPreferences,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** Lista de cuentas persistidas, en orden de creación. */
    fun list(): List<Account> = parseAccounts(prefs.getString(KEY_ACCOUNTS, null).orEmpty())

    fun activeId(): String? =
        prefs.getString(KEY_ACTIVE_ID, null)?.takeIf { id -> list().any { it.id == id } }
            ?: list().firstOrNull()?.id

    /** Cuenta activa, o la primera; null si no hay cuentas. */
    fun active(): Account? {
        val accounts = list()
        if (accounts.isEmpty()) return null
        val id = prefs.getString(KEY_ACTIVE_ID, null)
        return accounts.firstOrNull { it.id == id } ?: accounts.first()
    }

    /** Key de la cuenta activa; null si no hay cuentas. */
    fun activeApiKey(): String? = active()?.apiKey

    /** Agrega una cuenta. La primera queda activa automáticamente. */
    fun add(label: String, apiKey: String): Account {
        val account = Account(id = idGenerator(), label = label, apiKey = apiKey)
        val accounts = list() + account
        save(accounts)
        if (activeId() == null) setActive(account.id)
        return account
    }

    /** Renombra sin tocar la credencial. No-op si el id no existe. */
    fun rename(id: String, label: String) {
        val accounts = list()
        val updated = accounts.map { if (it.id == id) it.copy(label = label) else it }
        if (updated != accounts) save(updated)
    }

    /** Elimina la cuenta. Si era la activa, activa la primera restante. */
    fun remove(id: String) {
        val accounts = list().filterNot { it.id == id }
        save(accounts)
        if (prefs.getString(KEY_ACTIVE_ID, null) == id) {
            accounts.firstOrNull()?.let { setActive(it.id) } ?: prefs.edit().remove(KEY_ACTIVE_ID).apply()
        }
    }

    /** Cambia la cuenta activa. No-op si el id no existe. */
    fun setActive(id: String) {
        if (list().any { it.id == id }) prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    /**
     * Migración de la API key única previa (REQ-105): si no hay cuentas y
     * existe una key legacy, la importa como "Cuenta 1" activa. Devuelve la
     * cuenta creada o null si no correspondía migrar.
     */
    fun migrateIfNeeded(legacyApiKey: String?): Account? {
        if (legacyApiKey.isNullOrBlank() || list().isNotEmpty()) return null
        return add(label = "Cuenta 1", apiKey = legacyApiKey)
    }

    private fun save(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("label", a.label)
                    .put("apiKey", a.apiKey),
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ACTIVE_ID = "active_account_id"

        /** Parsea el JSON de cuentas; ante cualquier corrupción devuelve vacío. */
        fun parseAccounts(raw: String): List<Account> = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "")
                    val key = o.optString("apiKey", "")
                    if (id.isNotBlank() && key.isNotBlank()) {
                        add(Account(id, o.optString("label", ""), key))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}