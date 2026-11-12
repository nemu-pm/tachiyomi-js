package eu.kanade.tachiyomi.source

import androidx.preference.PreferenceScreen

/** extensions-lib 1.6: a plain interface, no longer a [Source]. */
interface ConfigurableSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
