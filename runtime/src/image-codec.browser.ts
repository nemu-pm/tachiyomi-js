/**
 * Image codec for browser environments.
 * Uses native OffscreenCanvas + createImageBitmap.
 */

export async function decodeImageAsync(base64: string): Promise<{ width: number; height: number; pixels: string } | null> {
  try {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }

    const blob = new Blob([bytes]);
    const bitmap = await createImageBitmap(blob);
    const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;

    ctx.drawImage(bitmap, 0, 0);
    const imageData = ctx.getImageData(0, 0, bitmap.width, bitmap.height);

    // Convert RGBA to ARGB (Android Bitmap format)
    const pixels = new Int32Array(bitmap.width * bitmap.height);
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

    return { width: bitmap.width, height: bitmap.height, pixels: btoa(pixelBase64) };
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

    const canvas = new OffscreenCanvas(width, height);
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;

    const imageData = new ImageData(rgba, width, height);
    ctx.putImageData(imageData, 0, 0);

    const mimeType = format === "jpeg" ? "image/jpeg" : "image/png";
    const blob = await canvas.convertToBlob({
      type: mimeType,
      quality: format === "jpeg" ? quality / 100 : undefined,
    });

    const arrayBuffer = await blob.arrayBuffer();
    const outputBytes = new Uint8Array(arrayBuffer);
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

// Sync wrappers using XHR blocking trick (works in Web Workers)
function decodeImageSync(base64: string): { width: number; height: number; pixels: string } | null {
  try {
    let result: { width: number; height: number; pixels: string } | null = null;
    let done = false;

    decodeImageAsync(base64).then(r => { result = r; done = true; }).catch(() => { done = true; });

    const start = Date.now();
    while (!done && Date.now() - start < 30000) {
      const xhr = new XMLHttpRequest();
      xhr.open("GET", "data:text/plain,", false);
      try { xhr.send(); } catch {}
    }
    return result;
  } catch {
    return null;
  }
}

function encodeImageSync(
  pixelsBase64: string,
  width: number,
  height: number,
  format: string,
  quality: number
): string | null {
  try {
    let result: string | null = null;
    let done = false;

    encodeImageAsync(pixelsBase64, width, height, format, quality).then(r => { result = r; done = true; }).catch(() => { done = true; });

    const start = Date.now();
    while (!done && Date.now() - start < 30000) {
      const xhr = new XMLHttpRequest();
      xhr.open("GET", "data:text/plain,", false);
      try { xhr.send(); } catch {}
    }
    return result;
  } catch {
    return null;
  }
}

export function registerImageCodec(): void {
  (globalThis as Record<string, unknown>).tachiyomiDecodeImage = decodeImageSync;
  (globalThis as Record<string, unknown>).tachiyomiEncodeImage = encodeImageSync;
}

