/**
 * Image codec for Node.js/Bun environments.
 * Uses the `canvas` package (node-canvas).
 */

import { createCanvas, loadImage, ImageData } from "canvas";

export async function decodeImageAsync(base64: string): Promise<{ width: number; height: number; pixels: string } | null> {
  try {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }

    const img = await loadImage(Buffer.from(bytes));
    const canvas = createCanvas(img.width, img.height);
    const ctx = canvas.getContext("2d");
    ctx.drawImage(img, 0, 0);
    const imageData = ctx.getImageData(0, 0, img.width, img.height);

    // Convert RGBA to ARGB (Android Bitmap format)
    const pixels = new Int32Array(img.width * img.height);
    const rgba = imageData.data;
    for (let i = 0; i < pixels.length; i++) {
      const r = rgba[i * 4];
      const g = rgba[i * 4 + 1];
      const b = rgba[i * 4 + 2];
      const a = rgba[i * 4 + 3];
      pixels[i] = ((a << 24) | (r << 16) | (g << 8) | b) | 0;
    }

    // Convert to base64
    const pixelBuffer = new Uint8Array(pixels.buffer);
    let pixelBase64 = "";
    const CHUNK_SIZE = 32768;
    for (let i = 0; i < pixelBuffer.length; i += CHUNK_SIZE) {
      const chunk = pixelBuffer.subarray(i, Math.min(i + CHUNK_SIZE, pixelBuffer.length));
      pixelBase64 += String.fromCharCode.apply(null, chunk as unknown as number[]);
    }

    return { width: img.width, height: img.height, pixels: btoa(pixelBase64) };
  } catch (e) {
    console.error("[ImageCodec] decode error:", e);
    return null;
  }
}

export async function encodeImageAsync(
  pixelsBase64: string,
  width: number,
  height: number,
  format: string,
  quality: number
): Promise<string | null> {
  try {
    const binary = atob(pixelsBase64);
    const pixelBuffer = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      pixelBuffer[i] = binary.charCodeAt(i);
    }
    const pixels = new Int32Array(pixelBuffer.buffer);

    // Convert ARGB to RGBA
    const rgba = new Uint8ClampedArray(width * height * 4);
    for (let i = 0; i < pixels.length; i++) {
      const pixel = pixels[i];
      rgba[i * 4] = (pixel >> 16) & 0xff;
      rgba[i * 4 + 1] = (pixel >> 8) & 0xff;
      rgba[i * 4 + 2] = pixel & 0xff;
      rgba[i * 4 + 3] = (pixel >> 24) & 0xff;
    }

    const canvas = createCanvas(width, height);
    const ctx = canvas.getContext("2d");
    const imageData = new ImageData(rgba, width, height);
    ctx.putImageData(imageData, 0, 0);

    const buffer = format === "jpeg"
      ? canvas.toBuffer("image/jpeg", { quality: quality / 100 })
      : canvas.toBuffer("image/png");

    const outputBytes = new Uint8Array(buffer);
    let result = "";
    const CHUNK_SIZE = 32768;
    for (let i = 0; i < outputBytes.length; i += CHUNK_SIZE) {
      const chunk = outputBytes.subarray(i, Math.min(i + CHUNK_SIZE, outputBytes.length));
      result += String.fromCharCode.apply(null, chunk as unknown as number[]);
    }
    return btoa(result);
  } catch (e) {
    console.error("[ImageCodec] encode error:", e);
    return null;
  }
}

// Sync wrappers - in Node we can't truly block, so these return null
// Tests should use async versions directly
function decodeImageSync(base64: string): { width: number; height: number; pixels: string } | null {
  console.warn("[ImageCodec] Sync decode not supported in Node.js, use decodeImageAsync instead");
  return null;
}

function encodeImageSync(
  pixelsBase64: string,
  width: number,
  height: number,
  format: string,
  quality: number
): string | null {
  console.warn("[ImageCodec] Sync encode not supported in Node.js, use encodeImageAsync instead");
  return null;
}

export function registerImageCodec(): void {
  (globalThis as Record<string, unknown>).tachiyomiDecodeImage = decodeImageSync;
  (globalThis as Record<string, unknown>).tachiyomiEncodeImage = encodeImageSync;
}

