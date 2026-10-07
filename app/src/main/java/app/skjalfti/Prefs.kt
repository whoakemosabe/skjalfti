package app.skjalfti

import android.content.Context
import app.skjalfti.data.Home
import app.skjalfti.seismo.Channel
import app.skjalfti.seismo.Sensitivity

/** The two looks. Same screens, same data, different skin. */
enum class Skin(val label: String) { PIXEL("PIXEL"), GLASS("GLASS") }

class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("skjalfti", Context.MODE_PRIVATE)

    var skin: Skin
        get() = enumOr(p.getString("skin", null), Skin.PIXEL)
        set(v) = p.edit().putString("skin", v.name).apply()

    var home: Home
        get() = enumOr(p.getString("home", null), Home.NJARDVIK)
        set(v) = p.edit().putString("home", v.name).apply()

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
