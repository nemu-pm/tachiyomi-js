import { buildCommand } from "@stricli/core";
import { loadOutputConfig } from "../config";
import { loadExtension } from "../lib/extension-loader";
import { printField, printHeader, printJson, printListItem } from "../lib/output";

export const info = buildCommand({
  docs: {
    brief: "Show extension information",
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
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { json?: boolean }, extensionId: string) => {
    const config = loadOutputConfig();
    const { manifest, sources } = loadExtension(config.output, extensionId);

    if (flags.json) {
      printJson({ manifest, sources });
      return;
    }

    printHeader("Extension Info");
    printField("Name", manifest.name);
    printField("Package", manifest.pkg);
    printField("Version", manifest.version);
    printField("NSFW", manifest.nsfw);

    printHeader(`Sources (${sources.length})`);
    for (const s of sources) {
      printListItem(`${s.name} (${s.lang}): ${s.baseUrl}`);
      console.log(`    ID: ${s.id}`);
      console.log(`    Supports Latest: ${s.supportsLatest}`);
    }

    if (manifest.authors?.length) {
      printHeader(`Authors (${manifest.authors.length})`);
      for (const a of manifest.authors) {
        printListItem(`${a.github || a.name}: ${a.commits} commits (since ${a.firstCommit})`);
      }
    }
  },
});

