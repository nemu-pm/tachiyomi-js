# @nemu.pm/tachiyomi-runtime

Runtime for loading and executing Tachiyomi extensions compiled to JavaScript.

## Install

```bash
bun add @nemu.pm/tachiyomi-runtime
# or
npm install @nemu.pm/tachiyomi-runtime
```

## Usage

```typescript
import { TachiyomiRuntime } from "@nemu.pm/tachiyomi-runtime";

const runtime = new TachiyomiRuntime();
const source = await runtime.loadExtension(extensionCode);

const mangas = source.getPopularManga(1);
```

## Documentation

See the main repository for full documentation: https://github.com/nemu-pm/tachiyomi-js

## License

MIT

