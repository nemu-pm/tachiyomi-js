package keiyoushi.utils

// tachiyomi-js: shim imports (added by scripts/sync-core.sh)
import java.lang.System
import java.lang.Integer
import java.lang.Class
import java.lang.Character
import java.nio.charset.Charsets
import java.nio.charset.toString
import java.nio.charset.toByteArray
import java.util.Arrays
import tachiyomi.shim.compat.*

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val applicationContext: Application = Injekt.get()
