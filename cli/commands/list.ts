import { buildCommand } from "@stricli/core";
import { loadOutputConfig } from "../config";
import { listExtensions } from "../lib/extension-loader";
import { printHeader, printJson, printListItem } from "../lib/output";

export const list = buildCommand({
  docs: {
    brief: "List available built extensions",
  },
  parameters: {
    flags: {
      json: {
        kind: "boolean",
        brief: "Output as JSON",
        optional: true,
      },
    },
  },
  func: async (flags: { json?: boolean }) => {
    const config = loadOutputConfig();
    const extensions = listExtensions(config.output);

    if (flags.json) {
      printJson(extensions);
      return;
    }

    printHeader(`Extensions (${extensions.length})`);
    if (extensions.length === 0) {
      console.log("No extensions found.");
      console.log(`Looked in: ${config.output}`);
    } else {
      for (const ext of extensions) {
        printListItem(ext);
      }
    }
  },
});

