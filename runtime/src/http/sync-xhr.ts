/**
 * Browser sync HTTP using XMLHttpRequest
 * Must run in a Web Worker for sync XHR to work
 */
import type { HttpBridge, HttpRequest, HttpResponse } from "../http";

export interface SyncXhrOptions {
  /**
   * Proxy URL function - transforms target URL for CORS bypass
   * Example: (url) => `https://proxy.example.com/?url=${encodeURIComponent(url)}`
   */
  proxyUrl?: (url: string) => string;
}

/**
 * Create an HttpBridge using synchronous XMLHttpRequest
 * Only works in Web Workers (sync XHR is deprecated on main thread)
 */
export function createSyncXhrBridge(options: SyncXhrOptions = {}): HttpBridge {
  const { proxyUrl } = options;

  return {
    request(req: HttpRequest, wantBytes: boolean): HttpResponse {
      try {
        const xhr = new XMLHttpRequest();
        const url = proxyUrl ? proxyUrl(req.url) : req.url;
        xhr.open(req.method, url, false); // false = synchronous
        xhr.responseType = wantBytes ? "arraybuffer" : "text";

        // Set headers with x-proxy- prefix for CORS proxy
        for (const [key, value] of Object.entries(req.headers)) {
          try {
            xhr.setRequestHeader(`x-proxy-${key}`, value);
          } catch {
            // Some headers can't be set in browsers
          }
        }

        xhr.send(req.body);

        // Collect response headers
        const responseHeaders: Record<string, string> = {};
        const headerLines = xhr.getAllResponseHeaders().split("\r\n");
        for (const line of headerLines) {
          const idx = line.indexOf(": ");
          if (idx > 0) {
            const key = line.substring(0, idx).toLowerCase();
            const value = line.substring(idx + 2);
            responseHeaders[key] = responseHeaders[key]
              ? `${responseHeaders[key]}, ${value}`
              : value;
          }
        }

        // Get body - text or base64
        let responseBody: string;
        if (wantBytes) {
          const bytes = new Uint8Array(xhr.response as ArrayBuffer);
          let binary = "";
          for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
          }
          responseBody = btoa(binary);
        } else {
          responseBody = xhr.responseText;
        }

        return {
          status: xhr.status,
          statusText: xhr.statusText,
          headers: responseHeaders,
          body: responseBody,
        };
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

