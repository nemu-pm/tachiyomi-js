# @nemu.pm/tachiyomi-cli

CLI for building, testing, and exploring Tachiyomi extensions.

**Requires [Bun](https://bun.sh)**

## Install

```bash
bun add -g @nemu.pm/tachiyomi-cli
```

## Usage

```bash
# List available extensions
tachiyomi list

# Test extension APIs
tachiyomi test all/mangadex popular
tachiyomi test all/mangadex search "one piece"

# Interactive exploration
tachiyomi explore

# Build (requires full repo clone)
tachiyomi build en/mangapill
```

## Configuration

Set `TACHIYOMI_OUTPUT` environment variable to point to your built extensions directory, or create a `tachiyomi.config.json`:

```json
{
  "output": "./dist/extensions"
}
```

## Documentation

See the main repository for full documentation: https://github.com/nemu-pm/tachiyomi-js

## License

MIT

