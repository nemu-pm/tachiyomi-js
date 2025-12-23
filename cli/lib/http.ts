/**
 * Sync HTTP bridge for Tachiyomi extensions.
 * Extensions expect synchronous HTTP - we use curl under the hood.
 */

export function syncHttpRequest(
  url: string,
  method: string,
  headers: Record<string, string>,
  body: string | null,
  wantBytes: boolean
): { status: number; body: string; headers: Record<string, string>; error: string | null } {
  const args = ["-s", "-S", "-X", method, "-w", "\n%{http_code}", "-D", "-", "-L"];

  for (const [key, value] of Object.entries(headers)) {
    args.push("-H", `${key}: ${value}`);
  }

  if (body) {
    args.push("-d", body);
  }

  args.push(url);

  const result = Bun.spawnSync(["curl", ...args]);
  
  if (result.exitCode !== 0) {
    const stderr = result.stderr.toString().trim();
    return {
      status: 0,
      body: "",
      headers: {},
      error: stderr || `curl failed with exit code ${result.exitCode}`,
    };
  }
  
  const stdout = result.stdout;

  // Parse headers (until empty line)
  let headerEndIdx = 0;
  for (let i = 0; i < stdout.length - 1; i++) {
    if (stdout[i] === 13 && stdout[i + 1] === 10) {
      // \r\n
      if (i + 3 < stdout.length && stdout[i + 2] === 13 && stdout[i + 3] === 10) {
        headerEndIdx = i + 4;
        break;
      }
    }
  }

  const headerSection = stdout.slice(0, headerEndIdx).toString();
  const bodySection = stdout.slice(headerEndIdx);

  // Parse response headers
  const responseHeaders: Record<string, string> = {};
  const headerLines = headerSection.split("\r\n");
  for (const line of headerLines.slice(1)) {
    // Skip status line
    const idx = line.indexOf(": ");
    if (idx > 0) {
      responseHeaders[line.slice(0, idx).toLowerCase()] = line.slice(idx + 2);
    }
  }

  // Get status code from last line
  const bodyStr = bodySection.toString();
  const lastNewline = bodyStr.lastIndexOf("\n");
  const statusCode = parseInt(bodyStr.slice(lastNewline + 1)) || 200;
  const responseBody = bodyStr.slice(0, lastNewline);

  // If wantBytes, convert to base64
  let finalBody: string;
  if (wantBytes) {
    const bodyBytes = Buffer.from(responseBody, "binary");
    finalBody = bodyBytes.toString("base64");
  } else {
    finalBody = responseBody;
  }

  // Check for HTTP errors
  let error: string | null = null;
  if (statusCode >= 400) {
    error = `HTTP ${statusCode}`;
  }

  return {
    status: statusCode,
    body: finalBody,
    headers: responseHeaders,
    error,
  };
}

/** Install the HTTP bridge into globalThis for Kotlin/JS extensions */
export function installHttpBridge(): void {
  (globalThis as any).tachiyomiHttpRequest = (
    url: string,
    method: string,
    headersJson: string,
    body: string | null,
    wantBytes: boolean
  ): { status: number; statusText: string; headersJson: string; body: string; error: string | null } => {
    if (process.env.DEBUG_HTTP) {
      console.log(`[HTTP] ${method} ${url}`);
    }
    const headers = JSON.parse(headersJson || "{}");
    const result = syncHttpRequest(url, method, headers, body, wantBytes);

    return {
      status: result.status,
      statusText: result.status >= 200 && result.status < 300 ? "OK" : "Error",
      headersJson: JSON.stringify(result.headers),
      body: result.body,
      error: result.error,
    };
  };
}

