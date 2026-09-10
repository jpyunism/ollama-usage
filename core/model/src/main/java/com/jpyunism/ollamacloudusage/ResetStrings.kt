package com.jpyunism.ollamacloudusage

/**
 * Templates localizados para [formatReset]. Se arman en la UI con
 * string resources; las funciones puras siguen testeables sin Android.
 */
data class ResetStrings(
    /** "resetea pronto" / "resets soon" */
    val resetsSoon: String,
    /** "resetea en %s" / "resets in %s" */
    val resetsIn: String,
    /** "resetea en <1 min" / "resets in <1 min" */
    val lessThanMin: String,
    /** "resetea el %s" / "resets on %s" */
    val resetsOn: String,
)
