package com.jpyunism.ollamacloudusage

/** Puntos minimos de una serie para dibujarla (issue #92). */
const val MIN_MODEL_POINTS = 3

/** Modelos individuales en el selector; el resto se agrupa en "Otros". */
const val DEFAULT_TOP_MODELS = 5

/** Un punto de la evolucion temporal de un modelo: (timestamp, % del modelo). */
data class ModelPoint(
    val timestampMillis: Long,
    val percent: Double,
)

/** Un modelo con su % en un momento dado (para ordenar el selector). */
data class ModelShare(
    val model: String,
    val percent: Double,
)

/**
 * Desglose por modelo del ULTIMO snapshot que trae desglose, ordenado por %
 * desc (tiebreak: nombre asc, para un orden estable). Vacio si ningun snapshot
 * lo trae (historicos viejos o fuente sin modelos).
 *
 * Se usa el ultimo snapshot con datos (no el ultimo a secas) para que un
 * refresh reciente sin desglose no borre el selector.
 */
fun latestModelShares(snapshots: List<UsageSnapshot>): List<ModelShare> {
    val last = snapshots.lastOrNull { !it.models.isNullOrEmpty() } ?: return emptyList()
    return last.models.orEmpty().map { (m, p) -> ModelShare(m, p) }
        .sortedWith(compareByDescending<ModelShare> { it.percent }.thenBy { it.model })
}

/** Nombres de los [topN] modelos mas usados, ordenados por % desc. */
fun topModelNames(snapshots: List<UsageSnapshot>, topN: Int = DEFAULT_TOP_MODELS): List<String> =
    latestModelShares(snapshots).take(topN).map { it.model }

/** true si quedan modelos fuera del top N (corresponde el chip "Otros"). */
fun hasOtherModels(snapshots: List<UsageSnapshot>, topN: Int = DEFAULT_TOP_MODELS): Boolean =
    latestModelShares(snapshots).size > topN

/**
 * Serie temporal del % de un modelo: solo los snapshots que lo reportan. Los
 * snapshots sin desglose (o sin ese modelo) se omiten, NO cuentan como 0:
 * un 0 falso dibujaria una caida que nunca ocurrio.
 */
fun modelSeries(snapshots: List<UsageSnapshot>, model: String): List<ModelPoint> =
    snapshots.mapNotNull { s ->
        modelPercent(s, model)?.let { ModelPoint(s.timestampMillis, it) }
    }

/**
 * Serie "Otros": en cada snapshot con desglose, la suma de los % de los
 * modelos fuera de [exclude] (el top N). Los snapshots sin desglose, o en los
 * que no queda ningun modelo fuera de [exclude], se omiten.
 */
fun othersSeries(snapshots: List<UsageSnapshot>, exclude: Set<String>): List<ModelPoint> =
    snapshots.mapNotNull { s ->
        val models = s.models ?: return@mapNotNull null
        val rest = models.filterKeys { it !in exclude }
        if (rest.isEmpty()) null else ModelPoint(s.timestampMillis, rest.values.sum())
    }

/**
 * true si hay al menos [minPoints] snapshots con desglose por modelo. Sin eso
 * la seccion se oculta (snapshots viejos o historico demasiado corto), en vez
 * de mostrar un grafico vacio.
 */
fun hasModelHistory(snapshots: List<UsageSnapshot>, minPoints: Int = MIN_MODEL_POINTS): Boolean =
    snapshots.count { !it.models.isNullOrEmpty() } >= minPoints

/** true si la serie tiene puntos suficientes para dibujarse. */
fun modelSeriesVisible(points: List<ModelPoint>, minPoints: Int = MIN_MODEL_POINTS): Boolean =
    points.size >= minPoints
