#!/usr/bin/env bash
# Copies the keiyoushi `core` and `lib/i18n` files that the shim takes verbatim from a pinned
# extensions-source checkout. Files listed under PORTED are maintained by hand in the shim and
# are not touched; re-check them against upstream when bumping CORE_REV.
#
# Usage: scripts/sync-core.sh [path-to-extensions-source]
set -euo pipefail

CORE_REV="7c6a7c2fc"
SRC="${1:-${EXTENSIONS_SOURCE:-$HOME/Development/extensions-source}}"
DEST="$(cd "$(dirname "$0")/.." && pwd)/shim/src/jsMain/kotlin/keiyoushi"

if [[ ! -d "$SRC/core/src/main/kotlin/keiyoushi" ]]; then
  echo "extensions-source checkout not found at $SRC" >&2
  exit 1
fi

if command -v git >/dev/null && git -C "$SRC" rev-parse --verify -q "$CORE_REV" >/dev/null; then
  head="$(git -C "$SRC" rev-parse --short HEAD)"
  if [[ "$head" != "$CORE_REV"* && "$CORE_REV" != "$head"* ]]; then
    echo "warning: checkout is at $head, shim is pinned to $CORE_REV" >&2
  fi
fi

# Verbatim: compile against the shim unchanged.
VERBATIM=(
  annotation/Source.kt
  network/BaseUrlFetchInterceptor.kt
  network/BrowserRequests.kt
  network/CacheControlInterceptor.kt
  network/OkHttp.kt
  network/XorInterceptor.kt
  source/FetchableBaseUrl.kt
  source/MirrorPreferences.kt
  utils/Binary.kt
  utils/Collections.kt
  utils/Context.kt
  utils/GraphQL.kt
  utils/Inflater.kt
  utils/JsonElement.kt
  utils/NextJs.kt
  utils/Preferences.kt
  utils/SeedRandom.kt
  utils/reactFlight/ReactFlightNumber.kt
)

# Ported by hand (JVM-only APIs replaced): see the header comment in each file.
PORTED=(
  network/BrowserHeaders.kt
  network/Cookie.kt
  network/RateLimit.kt
  source/CustomUrlPreferences.kt
  source/KeiSource.kt
  utils/Crypto.kt
  utils/Date.kt
  utils/Json.kt
  utils/Jsoup.kt
  utils/Markdown.kt
  utils/Protobuf.kt
  utils/protobuf/ProtobufDecoder.kt
  utils/protobuf/ProtobufEncoder.kt
  utils/TurnstileHelper.kt
  utils/WebView.kt
  utils/ui/DialogHelper.kt
  zip/Zip.kt
  zip/RemoteZip.kt
  lib/i18n/Intl.kt
)

# Not shipped: network/Deflate.kt (internal, replaced by okio inflate), source/UrlActivity.kt,
# utils/reactFlight/ReactFlightBigInt.kt, utils/ui/ActivityTracker.kt, webview/internal/*.

# The compiler prepends these to every extension source (CodeTransformations.jsImports); the
# verbatim core files need the same so java.lang.*, charset helpers and KClass.java resolve on JS.
JS_IMPORTS='// tachiyomi-js: shim imports (added by scripts/sync-core.sh)
import java.lang.System
import java.lang.Integer
import java.lang.Class
import java.lang.Character
import java.nio.charset.Charsets
import java.nio.charset.toString
import java.nio.charset.toByteArray
import java.util.Arrays
import tachiyomi.shim.compat.*'

inject_imports() {
  local file="$1"
  grep -q '^// tachiyomi-js: shim imports' "$file" && return
  JS_IMPORTS="$JS_IMPORTS" perl -0pi -e 's/^(package [^\n]*\n)/$1\n$ENV{JS_IMPORTS}\n/m' "$file"
}

for f in "${VERBATIM[@]}"; do
  mkdir -p "$DEST/$(dirname "$f")"
  cp "$SRC/core/src/main/kotlin/keiyoushi/$f" "$DEST/$f"
  inject_imports "$DEST/$f"
  echo "synced $f"
done

for f in "${PORTED[@]}"; do
  if [[ ! -f "$DEST/$f" ]]; then
    echo "missing ported file: $f" >&2
  fi
done
echo "core pinned at $CORE_REV; ${#VERBATIM[@]} verbatim, ${#PORTED[@]} ported"
