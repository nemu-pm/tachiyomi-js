package tachiyomi.shim

/**
 * Kotlin/JS builds every [Regex] with the `u` flag, under which a lone `]`, `}` or `{` and an
 * identity escape such as `\-` are syntax errors; the JVM accepts them all, and the corpus is
 * full of them (`[^]]*]`, `\{`-less literal braces). [install] wraps the global `RegExp` so a
 * pattern that fails to parse is retried with those JVM-isms escaped, then without `u`.
 * JVM inline flags (`(?i)`, `(?s)`, `(?m)`) are not RegExp syntax either: they are hoisted
 * into the flags, which is exact when they lead the pattern (where the corpus puts them) and
 * an approximation when they sit mid-pattern. Regex literals in the bundle never go through
 * the constructor, so they are unaffected.
 */
object RegexCompat {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        js(
            """
            (function (g) {
              var Native = g.RegExp;
              if (!Native || Native.__tachiyomiCompat) return;
              var syntax = '^$\\.*+?()[]{}|/';
              function sanitize(p) {
                var out = '', inClass = false, i = 0, n = p.length;
                while (i < n) {
                  var c = p[i];
                  if (c === '\\') {
                    var d = p[i + 1];
                    if (d === undefined) { out += '\\\\'; i++; continue; }
                    if (/[A-Za-z0-9]/.test(d) || syntax.indexOf(d) >= 0 || (inClass && d === '-')) { out += c + d; i += 2; continue; }
                    out += d; i += 2; continue;
                  }
                  if (inClass) {
                    if (c === ']') inClass = false;
                    if (c === '[') { out += '\\['; i++; continue; }
                    out += c; i++; continue;
                  }
                  if (c === '[') { inClass = true; out += c; i++; if (p[i] === '^') { out += '^'; i++; } if (p[i] === ']') { out += '\\]'; i++; } continue; }
                  if (c === ']' || c === '}') { out += '\\' + c; i++; continue; }
                  if (c === '{') {
                    var m = /^\{\d+(,\d*)?\}/.exec(p.slice(i));
                    if (m) { out += m[0]; i += m[0].length; continue; }
                    out += '\\{'; i++; continue;
                  }
                  out += c; i++;
                }
                return out;
              }
              function hoist(p, f) {
                var extra = '';
                var out = p.replace(/\(\?([imsdu]+)\)/g, function (m, fl) {
                  for (var k = 0; k < fl.length; k++) {
                    var c = fl[k];
                    if ((c === 'i' || c === 's' || c === 'm') && f.indexOf(c) < 0 && extra.indexOf(c) < 0) extra += c;
                  }
                  return '';
                });
                return out === p ? null : [out, f + extra];
              }
              function Compat(pattern, flags) {
                if (!new.target) return new Compat(pattern, flags);
                if (pattern instanceof Native && flags === undefined) return new Native(pattern);
                var source = pattern instanceof Native ? pattern.source : String(pattern);
                var f = flags === undefined ? '' : String(flags);
                try { return new Native(source, f); } catch (e) {
                  if (!(e instanceof SyntaxError)) throw e;
                  var bases = [[sanitize(source), f]];
                  var h = hoist(source, f);
                  if (h) bases.push(h, [sanitize(h[0]), h[1]]);
                  var attempts = bases.slice();
                  if (f.indexOf('u') >= 0) {
                    attempts.push([source, f.replace('u', '')]);
                    for (var j = 0; j < bases.length; j++) attempts.push([bases[j][0], bases[j][1].replace('u', '')]);
                  }
                  for (var k = 0; k < attempts.length; k++) {
                    try { return new Native(attempts[k][0], attempts[k][1]); } catch (e2) {}
                  }
                  throw e;
                }
              }
              Compat.prototype = Native.prototype;
              Compat.__tachiyomiCompat = true;
              Object.defineProperty(Compat, Symbol.species, { get: function () { return Compat; } });
              try { Object.setPrototypeOf(Compat, Native); } catch (e) {}
              g.RegExp = Compat;
            })(globalThis);
            """
        )
    }
}
