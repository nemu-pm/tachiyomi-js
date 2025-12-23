import { existsSync } from "fs";
import { join } from "path";
import { buildCommand } from "@stricli/core";
import { loadConfig } from "../config";
import { printError, printSuccess } from "../lib/output";

export const build = buildCommand({
  docs: {
    brief: "Build an extension",
  },
  parameters: {
    positional: {
      kind: "tuple",
      parameters: [
        {
          brief: "Extension path (e.g., en/mangapill)",
          parse: String,
          placeholder: "extension",
        },
      ],
    },
    flags: {
      all: {
        kind: "boolean",
        brief: "Build all extensions",
        optional: true,
      },
    },
  },
  func: async (flags: { all?: boolean }, extensionPath: string) => {
    const config = loadConfig();

    // Check if compiler is available
    const compilerDir = join(process.cwd(), "compiler");
    const gradlew = join(compilerDir, "gradlew");

    if (!existsSync(compilerDir) || !existsSync(gradlew)) {
      printError("Build requires the tachiyomi-js compiler");
      console.log();
      console.log("The build command requires the full tachiyomi-js repository.");
      console.log("To build extensions:");
      console.log();
      console.log("  1. Clone the repository:");
      console.log("     git clone https://github.com/nemu-pm/tachiyomi-js.git");
      console.log();
      console.log("  2. Run builds from the repo root:");
      console.log("     cd tachiyomi-js");
      console.log("     tachiyomi build en/mangapill");
      console.log();
      console.log("For testing pre-built extensions, use:");
      console.log("  tachiyomi test <extension>");
      console.log("  tachiyomi explore");
      process.exit(1);
    }

    const gradleArgs = flags.all
      ? ["buildAllExtensions"]
      : [
          "devBuild",
          `-Pextension=${extensionPath}`,
          `-PextensionsRoot=${config.source}`,
          `-PoutputDir=${config.output}`,
        ];

    console.log(`Building ${flags.all ? "all extensions" : extensionPath}...`);

    const result = Bun.spawnSync(["./gradlew", ...gradleArgs, "--build-cache", "--parallel"], {
      cwd: compilerDir,
      stdout: "inherit",
      stderr: "inherit",
    });

    if (result.exitCode !== 0) {
      printError("Build failed");
      process.exit(result.exitCode ?? 1);
    }

    printSuccess(`Built successfully`);
  },
});

