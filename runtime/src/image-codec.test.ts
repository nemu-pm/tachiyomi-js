import { describe, expect, test } from "bun:test";
import * as UPNG from "upng-js";
import {
  IMAGE_CODEC_GLOBALS,
  argbToRgba,
  base64ToBytes,
  bytesToBase64,
  decodeImage,
  encodeImage,
  encodePng,
  registerImageCodec,
  rgbaToArgb,
} from "./image-codec";

/** width*height RGBA pixels as the little-endian ARGB base64 the shim exchanges */
function pixelsBase64(rgba: number[]): string {
  return bytesToBase64(rgbaToArgb(Uint8Array.from(rgba)));
}

describe("image codec", () => {
  test("pixels are little-endian ARGB int32s (B, G, R, A), as ImageBridge.kt packs them", () => {
    const argb = rgbaToArgb(Uint8Array.from([0x11, 0x22, 0x33, 0x80]));
    expect(Array.from(argb)).toEqual([0x33, 0x22, 0x11, 0x80]);
    expect(new DataView(argb.buffer).getInt32(0, true)).toBe((0x80 << 24) | (0x11 << 16) | (0x22 << 8) | 0x33);
    expect(Array.from(argbToRgba(argb))).toEqual([0x11, 0x22, 0x33, 0x80]);
  });

  test("PNG round-trips losslessly", () => {
    const rgba = [
      255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255,
      0, 0, 0, 0, 128, 128, 128, 255, 10, 20, 30, 40,
    ];
    const png = encodeImage(pixelsBase64(rgba), 3, 2, "png", 100);
    expect(png).not.toBeNull();
    const bytes = base64ToBytes(png!);
    expect(Array.from(bytes.subarray(0, 4))).toEqual([0x89, 0x50, 0x4e, 0x47]);

    const decoded = decodeImage(png!);
    expect(decoded).toEqual({ width: 3, height: 2, pixelsBase64: pixelsBase64(rgba) });
  });

  test("JPEG round-trips within lossy tolerance", () => {
    const w = 8, h = 8;
    const rgba = Array.from({ length: w * h }, () => [220, 30, 40, 255]).flat();
    const jpg = encodeImage(pixelsBase64(rgba), w, h, "jpeg", 90);
    expect(jpg).not.toBeNull();
    const bytes = base64ToBytes(jpg!);
    expect(Array.from(bytes.subarray(0, 3))).toEqual([0xff, 0xd8, 0xff]);

    const decoded = decodeImage(jpg!);
    expect(decoded?.width).toBe(w);
    expect(decoded?.height).toBe(h);
    const out = argbToRgba(base64ToBytes(decoded!.pixelsBase64));
    expect(out.length).toBe(w * h * 4);
    for (let i = 0; i < out.length; i += 4) {
      expect(Math.abs(out[i] - 220)).toBeLessThan(12);
      expect(Math.abs(out[i + 1] - 30)).toBeLessThan(12);
      expect(Math.abs(out[i + 2] - 40)).toBeLessThan(12);
      expect(out[i + 3]).toBe(255);
    }
  });

  test("decodes a palette PNG made by another encoder", () => {
    // 16x16: UPNG.encode truncates anything much smaller (its output buffer is pixels + 100 bytes).
    const w = 16, h = 16;
    const rgba = Uint8Array.from({ length: w * h * 4 }, (_, i) => (i % 4 === 3 ? 255 : (i * 7) % 256));
    const png = new Uint8Array(UPNG.encode([rgba.buffer], w, h, 0));
    const decoded = decodeImage(bytesToBase64(png));
    expect(decoded?.width).toBe(w);
    expect(decoded?.height).toBe(h);
    expect(Array.from(argbToRgba(base64ToBytes(decoded!.pixelsBase64)))).toEqual(Array.from(rgba));
  });

  test("encodePng writes a complete PNG for tiny and incompressible images", () => {
    const sizes: [number, number][] = [[1, 1], [2, 1], [3, 2], [8, 8], [301, 17]];
    let seed = 1;
    const noise = () => (seed = (seed * 48271) % 2147483647) & 0xff;
    for (const [w, h] of sizes) {
      const rgba = Uint8Array.from({ length: w * h * 4 }, noise);
      const png = encodePng(rgba, w, h);
      // ends with IEND
      expect(Array.from(png.subarray(png.length - 8, png.length - 4))).toEqual([0x49, 0x45, 0x4e, 0x44]);
      const img = UPNG.decode(png.buffer.slice(png.byteOffset, png.byteOffset + png.byteLength));
      expect([img.width, img.height]).toEqual([w, h]);
      expect(Array.from(new Uint8Array(UPNG.toRGBA8(img)[0]))).toEqual(Array.from(rgba));
    }
  });

  test("unsupported bytes and mismatched sizes return null", () => {
    expect(decodeImage(bytesToBase64(Uint8Array.from([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45])))).toBeNull();
    expect(encodeImage(pixelsBase64([0, 0, 0, 255]), 2, 2, "png", 100)).toBeNull();
  });

  test("registerImageCodec installs the shim's globals and they return pixelsBase64", () => {
    registerImageCodec();
    const g = globalThis as Record<string, unknown>;
    for (const name of IMAGE_CODEC_GLOBALS) expect(typeof g[name]).toBe("function");
    const decode = g.tachiyomiDecodeImage as typeof decodeImage;
    const encode = g.tachiyomiEncodeImage as typeof encodeImage;
    const png = encode(pixelsBase64([9, 9, 9, 255]), 1, 1, "png", 100)!;
    const result = decode(png) as Record<string, unknown>;
    expect(Object.keys(result).sort()).toEqual(["height", "pixelsBase64", "width"]);
  });
});
