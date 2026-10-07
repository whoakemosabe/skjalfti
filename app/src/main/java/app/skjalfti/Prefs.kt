package app.skjalfti

import android.content.Context
import app.skjalfti.data.Place
import app.skjalfti.data.Places
import app.skjalfti.seismo.Channel
import app.skjalfti.seismo.Sensitivity

/** The two looks. Same screens, same data, different skin. */
enum class Skin(val label: String) { PIXEL("PIXEL"), GLASS("GLASS") }

class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("skjalfti", Context.MODE_PRIVATE)

    var skin: Skin
        get() = enumOr(p.getString("skin", null), Skin.PIXEL)
        set(v) = p.edit().putString("skin", v.name).apply()

    /** The current home: a preset, a GPS fix or a saved place. */
    var home: Place
        get() = Place.fromJson(p.getString("home_place", null)) ?: legacyHome()
        set(v) = p.edit().putString("home_place", v.toJson().toString()).apply()

    /** Places the user saved, newest first. */
    var saved: List<Place>
        get() = Place.listFromJson(p.getString("saved_places", null))
        set(v) = p.edit().putString("saved_places", Place.listToJson(v)).apply()

    /** Re-check GPS when the app opens and move home if the phone has moved. */
    var autoLocate: Boolean
        get() = p.getBoolean("auto_locate", false)
        set(v) = p.edit().putBoolean("auto_locate", v).apply()

    /** The Live screen's "use my location?" card was answered. */
    var locationAsked: Boolean
        get() = p.getBoolean("location_asked", false)
        set(v) = p.edit().putBoolean("location_asked", v).apply()

    // Builds before saved places stored the home as a preset name.
    private fun legacyHome(): Place {
        val old = p.getString("home", null)?.lowercase() ?: return Places.NJARDVIK
        return Places.presets.firstOrNull { it.id == old } ?: Places.NJARDVIK
    }

    var channel: Channel
        get() = enumOr(p.getString("channel", null), Channel.Z)
        set(v) = p.edit().putString("channel", v.name).apply()

    var sensitivity: Sensitivity
        get() = enumOr(p.getString("sensitivity", null), Sensitivity.MED)
        set(v) = p.edit().putString("sensitivity", v.name).apply()

    /** Only quakes this close count, in km. */
    var radiusKm: Int
        get() = p.getInt("radius", 60)
        set(v) = p.edit().putInt("radius", v).apply()

    /** Trace gain: 1, 2, 4 or 8. */
    var gain: Int
        get() = p.getInt("gain", 2)
        set(v) = p.edit().putInt("gain", v).apply()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
