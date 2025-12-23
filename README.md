# tachiyomi-js

Compiler and runtime for running [Tachiyomi](https://github.com/keiyoushi/extensions-source) extensions in JavaScript environments.

> **Disclaimer:** This repository does not contain any extension source code.

## Packages

- **`packages/shim`** - Android/JVM API shims for Kotlin/JS
- **`packages/compiler`** - Kotlin → JavaScript compiler
- **`packages/runtime`** - TypeScript runtime for loading compiled extensions

## Usage

This is a **tooling repository**. It does not contain any extension source code.

To build extensions, use [tachiyomi-js-sources](https://github.com/user/tachiyomi-js-sources) which combines this tooling with the extension sources.

### As a library

```bash
npm install @tachiyomi-js/runtime
```

```typescript
import { createRuntime } from '@tachiyomi-js/runtime';

const runtime = createRuntime(httpBridge);
const extension = await runtime.loadExtension(extensionJs, manifest);
const manga = await extension.getPopularManga(1);
```

## Development

```bash
# Build the shim
cd packages/shim && ./gradlew compileKotlinJs

# Build the runtime
cd packages/runtime && bun run build
```

## License

MIT
