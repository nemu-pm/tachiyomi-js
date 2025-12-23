/**
 * Node.js/Bun sync HTTP using child_process
 * Uses curl for actual HTTP requests
 */
import type { HttpBridge, HttpRequest, HttpResponse } from "../http";
import { spawnSync } from "child_process";

export interface SyncNodeOptions {
  /**
   * Proxy URL base - transforms target URL
   * Not typically needed in Node.js (no CORS)
   * Example: "https://proxy.example.com/?url="
   */
  proxyUrl?: string;
}

/**
 * Create an HttpBridge using synchronous child_process (curl)
 * Works in Node.js and Bun
 */
export function createSyncNodeBridge(options: SyncNodeOptions = {}): HttpBridge {
  const { proxyUrl } = options;

  return {
    request(req: HttpRequest, wantBytes: boolean): HttpResponse {
      try {
        const url = proxyUrl ? `${proxyUrl}${encodeURIComponent(req.url)}` : req.url;

        // Build curl command
        const args: string[] = [
          "-s", // silent
          "-S", // show errors
          "-L", // follow redirects
          "-D", "-", // dump headers to stdout
          "-X", req.method,
        ];

        // Add headers
        for (const [key, value] of Object.entries(req.headers)) {
          args.push("-H", `${key}: ${value}`);
        }

        // Add body for POST/PUT
        if (req.body && (req.method === "POST" || req.method === "PUT" || req.method === "PATCH")) {
          args.push("-d", req.body);
        }

        // Request binary output if needed
        if (wantBytes) {
          args.push("-o", "-"); // output to stdout
        }

        args.push(url);

        // Execute curl
        const result = spawnSync("curl", args, {
          encoding: wantBytes ? "buffer" : "utf-8",
          maxBuffer: 50 * 1024 * 1024, // 50MB
        });

        if (result.error) {
          throw result.error;
        }

        // Parse response (headers + body)
        const output = wantBytes
          ? (result.stdout as Buffer)
          : (result.stdout as string);

        // Find header/body separator
        let headerEnd: number;
        let bodyStart: number;
        const separator = "\r\n\r\n";

        if (wantBytes) {
          const buf = output as Buffer;
          headerEnd = buf.indexOf(separator);
          bodyStart = headerEnd + 4;
        } else {
          headerEnd = (output as string).indexOf(separator);
          bodyStart = headerEnd + 4;
        }

        // Parse headers
        const headerSection = wantBytes
          ? (output as Buffer).slice(0, headerEnd).toString("utf-8")
          : (output as string).slice(0, headerEnd);

        const headerLines = headerSection.split("\r\n");
        const statusLine = headerLines[0] ?? "";
        const statusMatch = statusLine.match(/HTTP\/[\d.]+ (\d+) (.*)/);
        const status = statusMatch ? parseInt(statusMatch[1], 10) : 0;
        const statusText = statusMatch ? statusMatch[2] : "";

        const headers: Record<string, string> = {};
        for (let i = 1; i < headerLines.length; i++) {
          const line = headerLines[i];
          const idx = line.indexOf(": ");
          if (idx > 0) {
            const key = line.substring(0, idx).toLowerCase();
            const value = line.substring(idx + 2);
            headers[key] = headers[key] ? `${headers[key]}, ${value}` : value;
          }
        }

        // Extract body
        let body: string;
        if (wantBytes) {
          const bodyBuf = (output as Buffer).slice(bodyStart);
          body = bodyBuf.toString("base64");
        } else {
          body = (output as string).slice(bodyStart);
        }

        return { status, statusText, headers, body };
      } catch (e) {
        return {
          status: 0,
          statusText: String(e),
          headers: {},
          body: "",
        };
      }
    },
  };
}

