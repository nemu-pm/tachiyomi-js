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
      cwd: `${process.cwd()}/compiler`,
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

