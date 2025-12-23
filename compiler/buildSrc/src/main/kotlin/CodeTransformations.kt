/**
 * Code transformations for Kotlin/JS compatibility.
 * Applied during source preprocessing to fix JS-specific issues.
 */
object CodeTransformations {
    
    val transforms: List<Pair<Regex, String>> = listOf(
        // In JS Unicode mode, [^] (negated character class with just ]) needs the ] escaped
        // Match [^] and replace with [^\\] so it becomes [^\]] in the regex
        // Input: "[^]+" -> Output: "[^\\]+" (Kotlin string) -> [^\]]+ (regex pattern)
        Regex("""\[\^]""") to """[^\\\\]""",
        
        // Replace okhttp3.ResponseBody.Companion.asResponseBody import with okio.asResponseBody
        Regex("""import okhttp3\.ResponseBody\.Companion\.asResponseBody""") to "import okio.asResponseBody",
        
        // CSS attribute selectors with escaped = cause ksoup regex issues
        // Match: [attr*=value\\=] -> [attr*=\"value=\"] (escaped quotes for Kotlin string)
        // Note: In replacement string, need \\\\ for literal backslash (Kotlin string + regex replacement)
        Regex("""(\[[\w-]+\*=)([^\]"]*?)\\\\=]""") to "\$1\\\\\"\$2=\\\\\"]",
        
        // Regex with unescaped ] after escaped [ causes "Lone quantifier brackets" error
        // Match: (\\[.*?])".toRegex -> (\\[.*?\\])".toRegex
        // Need \\\\] in raw string to produce \\] in output (which is \] in the Kotlin string's regex)
        Regex("""(\\\\\[.*?)\](\)"\.toRegex)""") to """$1\\\\]$2""",
        
        // Fix JS name clashes: property and function have same name
        Regex("""fun useLoadMoreRequest\(\)""") to "fun shouldUseLoadMore()",
        Regex("""\buseLoadMoreRequest\(\)""") to "shouldUseLoadMore()",
        Regex("""fun fetchGenres\(\)""") to "fun doFetchGenres()",
        Regex("""(?<!\.)fetchGenres\(\)""") to "doFetchGenres()"
    )
    
    /** Imports to add to extension source files for JS compatibility */
    val jsImports = listOf(
        "import java.lang.System",
        "import java.lang.Integer", 
        "import java.lang.Class",
        "import java.lang.Character",
        "import java.nio.charset.Charsets",
        "import java.nio.charset.toString",
        "import java.nio.charset.toByteArray",
        "import java.util.Arrays",
        "import tachiyomi.shim.compat.*"
    )
    
    /** Files that have JS-compatible shims - skip preprocessing */
    val shimmedFiles = setOf(
        "eu/kanade/tachiyomi/lib/i18n/Intl.kt"
    )
}

