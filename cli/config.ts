import * as fs from "fs";
import * as path from "path";

export interface Config {
  /** Path to extensions source repo (Kotlin sources) */
  source: string;
  /** Path to built extensions output */
  output: string;
}

const CONFIG_FILE_NAMES = ["tachiyomi.config.json", ".tachiyomirc.json"];

function findConfigFile(): string | null {
  for (const name of CONFIG_FILE_NAMES) {
    const p = path.join(process.cwd(), name);
    if (fs.existsSync(p)) return p;
  }
  return null;
}

function loadConfigFile(): Partial<Config> {
  const file = findConfigFile();
  if (!file) return {};
  
  try {
    const content = fs.readFileSync(file, "utf-8");
    return JSON.parse(content);
  } catch {
    return {};
  }
}

export function loadConfig(): Config {
  const fileConfig = loadConfigFile();
  
  const source = process.env.TACHIYOMI_SOURCE ?? fileConfig.source;
  const output = process.env.TACHIYOMI_OUTPUT ?? fileConfig.output;
  
  const missing: string[] = [];
  if (!source) missing.push("source");
  if (!output) missing.push("output");
  
  if (missing.length > 0) {
    console.error(`Missing config: ${missing.join(", ")}

Set via environment variables:
  TACHIYOMI_SOURCE  - path to extensions source repo
  TACHIYOMI_OUTPUT  - path for built extensions

Or create tachiyomi.config.json:
  {
    "source": "/path/to/extensions-source",
    "output": "./dist/extensions"
  }
`);
    process.exit(1);
  }
  
  // Resolve to absolute paths
  return {
    source: path.resolve(source!),
    output: path.resolve(output!),
  };
}

/** Load config, but only require output (for test-only commands) */
export function loadOutputConfig(): Pick<Config, "output"> {
  const fileConfig = loadConfigFile();
  const output = process.env.TACHIYOMI_OUTPUT ?? fileConfig.output;
  
  if (!output) {
    console.error(`Missing config: output

Set via environment variable:
  TACHIYOMI_OUTPUT  - path to built extensions

Or create tachiyomi.config.json:
  {
    "output": "./dist/extensions"
  }
`);
    process.exit(1);
  }
  
  return { output: path.resolve(output) };
}

