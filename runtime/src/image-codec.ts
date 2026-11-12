/**
 * Image codec for the shim's android.graphics.Bitmap.
 *
 * Sources descramble pages synchronously (decode → shuffle pixels → compress), so the shim calls
 * `globalThis.tachiyomiDecodeImage(base64)` and `globalThis.tachiyomiEncodeImage(pixelsBase64, width,
 * height, format, quality)` and needs the answer on the same call. Browser canvases are promise-based
 * and a Worker cannot pump its event loop while blocked, so this codec is pure JS: jpeg-js for JPEG,
 * UPNG.js to decode PNG and a small RGBA writer over pako to encode it (UPNG.encode sizes its output at
 * the raw pixel count plus 100 bytes and truncates small or noisy images). It runs unchanged in
 * browsers, workers, Bun and Node.
 *
 * Pixels cross the bridge as little-endian ARGB int32s (the Android Bitmap layout), base64-encoded;
 * see shim/src/jsMain/kotlin/android/graphics/ImageBridge.kt.
 */
import * as jpeg from "jpeg-js";
import { deflate } from "pako";
import * as UPNG from "upng-js";

export interface DecodedImage {
  width: number;
  height: number;
  /** base64 of width*height little-endian ARGB int32s */
  pixelsBase64: string;
}

/** The globals the shim calls; the shim-contract test checks them against the Kotlin source. */
export const IMAGE_CODEC_GLOBALS = ["tachiyomiDecodeImage", "tachiyomiEncodeImage"] as const;

const CHUNK = 0x8000;

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK) as unknown as number[]);
  }
  return btoa(binary);
}

export function base64ToBytes(base64: string): Uint8Array<ArrayBuffer> {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** RGBA bytes → little-endian ARGB int32 bytes (B, G, R, A per pixel). */
export function rgbaToArgb(rgba: Uint8Array): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(rgba.length);
  for (let i = 0; i + 3 < rgba.length; i += 4) {
    out[i] = rgba[i + 2];
    out[i + 1] = rgba[i + 1];
    out[i + 2] = rgba[i];
    out[i + 3] = rgba[i + 3];
  }
  return out;
}

/** Little-endian ARGB int32 bytes → RGBA bytes. The swap is its own inverse. */
export function argbToRgba(argb: Uint8Array): Uint8Array<ArrayBuffer> {
  return rgbaToArgb(argb);
}

function isPng(bytes: Uint8Array): boolean {
  return bytes.length > 8 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
}

function isJpeg(bytes: Uint8Array): boolean {
  return bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

/**
 * jpeg-js's encoder ends with `typeof module === 'undefined' ? new Uint8Array(bytes) : Buffer.from(bytes)`.
 * Bundlers define `module` for CommonJS, so in a browser it reaches for a Buffer that does not exist.
 * Lend it one for the duration of the call.
 */
function withBuffer<T>(fn: () => T): T {
  const g = globalThis as { Buffer?: unknown };
  if (g.Buffer !== undefined) return fn();
  g.Buffer = { from: (bytes: ArrayLike<number>) => Uint8Array.from(bytes) };
  try {
    return fn();
  } finally {
    delete g.Buffer;
  }
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function pngChunk(type: string, data: Uint8Array): Uint8Array {
  const chunk = new Uint8Array(12 + data.length);
  const view = new DataView(chunk.buffer);
  view.setUint32(0, data.length);
  for (let i = 0; i < 4; i++) chunk[4 + i] = type.charCodeAt(i);
  chunk.set(data, 8);
  view.setUint32(8 + data.length, crc32(chunk.subarray(4, 8 + data.length)));
  return chunk;
}

/** A truecolour-with-alpha PNG (colour type 6, 8 bits, filter 0 on every row), zlib-compressed by pako. */
export function encodePng(rgba: Uint8Array, width: number, height: number): Uint8Array {
  const stride = width * 4;
  const raw = new Uint8Array((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw.set(rgba.subarray(y * stride, (y + 1) * stride), y * (stride + 1) + 1);
  }
  const ihdr = new Uint8Array(13);
  const view = new DataView(ihdr.buffer);
  view.setUint32(0, width);
  view.setUint32(4, height);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // colour type RGBA
  const chunks = [
    Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", deflate(raw)),
    pngChunk("IEND", new Uint8Array(0)),
  ];
  const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0));
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

/** Decode a JPEG or PNG. Returns null for anything else (WebP, GIF, AVIF are not supported). */
export function decodeImage(base64: string): DecodedImage | null {
  try {
    const bytes = base64ToBytes(base64);
    let width: number;
    let height: number;
    let rgba: Uint8Array;
    if (isPng(bytes)) {
      const img = UPNG.decode(bytes.buffer);
      width = img.width;
      height = img.height;
      rgba = new Uint8Array(UPNG.toRGBA8(img)[0]);
    } else if (isJpeg(bytes)) {
      const img = jpeg.decode(bytes, { useTArray: true, formatAsRGBA: true });
      width = img.width;
      height = img.height;
      rgba = img.data;
    } else {
      console.warn("[ImageCodec] unsupported image format; only JPEG and PNG decode");
      return null;
    }
    return { width, height, pixelsBase64: bytesToBase64(rgbaToArgb(rgba)) };
  } catch (e) {
    console.error("[ImageCodec] decode error:", e);
    return null;
  }
}

/** Encode little-endian ARGB pixels as JPEG (with quality 0-100) or PNG (lossless). Returns base64 bytes. */
export function encodeImage(
  pixelsBase64: string,
  width: number,
  height: number,
  format: string,
  quality: number
): string | null {
  try {
    const rgba = argbToRgba(base64ToBytes(pixelsBase64));
    if (rgba.length !== width * height * 4) {
      console.error(`[ImageCodec] encode: ${rgba.length} bytes of pixels for ${width}x${height}`);
      return null;
    }
    const bytes =
      format === "jpeg"
        ? withBuffer(() => jpeg.encode({ data: rgba, width, height }, quality).data)
        : encodePng(rgba, width, height);
    return bytesToBase64(bytes);
  } catch (e) {
    console.error("[ImageCodec] encode error:", e);
    return null;
  }
}

export async function decodeImageAsync(base64: string): Promise<DecodedImage | null> {
  return decodeImage(base64);
}

export async function encodeImageAsync(
  pixelsBase64: string,
  width: number,
  height: number,
  format: string,
  quality: number
): Promise<string | null> {
  return encodeImage(pixelsBase64, width, height, format, quality);
}

/** Install the `tachiyomi*Image` globals. Safe to call more than once. */
export function registerImageCodec(): void {
  const g = globalThis as Record<string, unknown>;
  g.tachiyomiDecodeImage = decodeImage;
  g.tachiyomiEncodeImage = encodeImage;
}
