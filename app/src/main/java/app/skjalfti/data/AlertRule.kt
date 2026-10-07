package app.skjalfti.data

/** Which quakes deserve a big-quake alert. Pure, so it's unit-tested. */
object AlertRule {
    const val MAX_AGE_MS = 3 * 3600_000L

    fun pick(
        quakes: List<Quake>,
        minMag: Float,
        radiusKm: Int,
        home: Place,
        since: Long,
        already: Collection<String>,
        now: Long,
    ): List<Quake> {
        if (minMag <= 0f) return emptyList()
        return quakes.filter {
            it.magnitude >= minMag &&
                it.timeMs >= maxOf(since, now - MAX_AGE_MS) &&
                it.id !in already &&
                Geo.epicentreKm(it, home) <= radiusKm
        }.sortedBy { it.timeMs }
    }
}
