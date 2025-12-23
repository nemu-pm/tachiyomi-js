/**
 * HTTP Bridge interface for Tachiyomi extensions
 * 
 * Extensions use synchronous HTTP calls internally (via OkHttp shim).
 * Consumers must provide an implementation that bridges to their environment.
 */

export interface HttpRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: string | null;
}

export interface HttpResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  /** Response body as string. For binary responses, this should be base64 encoded. */
  body: string;
  /** If true, body is base64 encoded binary data */
  isBinary?: boolean;
}

/**
 * HTTP bridge that extensions call for network requests.
 * 
 * IMPORTANT: Extensions expect SYNCHRONOUS responses.
 * In browsers, this typically means running in a Web Worker with sync XHR.
 */
export interface HttpBridge {
  /**
   * Perform an HTTP request synchronously.
   * 
   * @param request - The HTTP request to make
   * @param wantBytes - If true, return body as base64-encoded binary
   * @returns HTTP response (must be synchronous!)
   */
  request(request: HttpRequest, wantBytes: boolean): HttpResponse;
}

/**
 * Internal format used by Kotlin/JS extensions.
 * The runtime converts between this and the public HttpBridge interface.
 */
export interface KotlinHttpResult {
  status: number;
  statusText: string;
  headersJson: string;
  body: string;
  error: string | null;
}

