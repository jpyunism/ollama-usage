package com.jpyunism.ollamacloudusage

/**
 * Extrae la cookie de sesión de ollama.com desde el string crudo que devuelve
 * [android.webkit.CookieManager]. El scraper autentica con al menos `aid` y
 * `__Secure-session`; si faltan, la sesión no es válida y se devuelve null.
 */
object CookieExtractor {

    /** Cookies que la sesión de ollama.com debe incluir para autenticar. */
    private val REQUIRED = listOf("aid", "__Secure-session")

    /**
     * Devuelve "aid=...; __Secure-session=..." (solo las cookies requeridas,
     * en el orden en que aparecen). null si el string está vacío, no tiene
     * pares válidos o falta alguna cookie requerida.
     */
    fun extract(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val pairs = raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') && it.substringAfter('=').isNotEmpty() }
        if (pairs.isEmpty()) return null
        val names = pairs.map { it.substringBefore('=') }
        if (!REQUIRED.all { required -> names.any { it == required } }) return null
        return pairs.filter { it.substringBefore('=') in REQUIRED }.joinToString("; ")
    }
}
