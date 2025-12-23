import java.io.File

/**
 * Parsed metadata from an extension's build.gradle file.
 */
data class ExtensionMetadata(
    val extClass: String?,
    val extName: String?,
    val extVersionCode: Int?,
    val isNsfw: Boolean?,
    val themePkg: String?,
    val libDeps: List<String>
) {
    companion object {
        /**
         * Parse extension metadata from a keiyoushi build.gradle file.
         * Extracts ext { ... } block values and library dependencies.
         */
        fun parse(buildGradleFile: File): ExtensionMetadata {
            if (!buildGradleFile.exists()) {
                return ExtensionMetadata(null, null, null, null, null, emptyList())
            }
            
            val content = buildGradleFile.readText()
            var extClass: String? = null
            var extName: String? = null
            var extVersionCode: Int? = null
            var isNsfw: Boolean? = null
            var themePkg: String? = null
            val libDeps = mutableListOf<String>()
            
            // Parse ext { ... } block
            val extBlockMatch = Regex("""ext\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL).find(content)
            if (extBlockMatch != null) {
                val extBlock = extBlockMatch.groupValues[1]
                
                // Extract string values: extName = 'MangaDex' or extClass = '.MangaDex'
                Regex("""(\w+)\s*=\s*['"]([^'"]+)['"]""").findAll(extBlock).forEach { m ->
                    when (m.groupValues[1]) {
                        "extClass" -> extClass = m.groupValues[2].removePrefix(".")
                        "extName" -> extName = m.groupValues[2]
                        "themePkg" -> themePkg = m.groupValues[2]
                    }
                }
                
                // Extract int values: extVersionCode = 204
                Regex("""(\w+)\s*=\s*(\d+)""").findAll(extBlock).forEach { m ->
                    when (m.groupValues[1]) {
                        "extVersionCode" -> extVersionCode = m.groupValues[2].toInt()
                    }
                }
                
                // Extract boolean values: isNsfw = true
                Regex("""(\w+)\s*=\s*(true|false)""").findAll(extBlock).forEach { m ->
                    when (m.groupValues[1]) {
                        "isNsfw" -> isNsfw = m.groupValues[2].toBoolean()
                    }
                }
            }
            
            // Parse dependencies for lib detection
            Regex("""implementation\s*\(\s*project\s*\(\s*":lib:(\w+)"\s*\)\s*\)""").findAll(content).forEach { m ->
                libDeps.add(m.groupValues[1])
            }
            
            return ExtensionMetadata(extClass, extName, extVersionCode, isNsfw, themePkg, libDeps)
        }
    }
}

