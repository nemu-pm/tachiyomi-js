package androidx.preference

import android.content.Context
import android.content.SharedPreferences

/** The app-wide preferences file, `<packageName>_preferences`, the way androidx names it. */
object PreferenceManager {
    fun getDefaultSharedPreferencesName(context: Context): String = "${context.packageName}_preferences"

    fun getDefaultSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE)
}
