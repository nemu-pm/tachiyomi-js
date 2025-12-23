#!/usr/bin/env bun
/**
 * Interactive test script for Tachiyomi extensions.
 * 
 * Usage:
 *   bun scripts/test-tachiyomi-source.ts <extension-id> [command]
 * 
 * Commands:
 *   info             - Show extension info
 *   popular [page]   - Get popular manga
 *   latest [page]    - Get latest updates  
 *   search <query>   - Search for manga
 *   details <url>    - Get manga details
 *   chapters <url>   - Get chapter list
 *   pages <url>      - Get page list
 *   interactive      - Interactive mode
 * 
 * Examples:
 *   bun scripts/test-tachiyomi-source.ts en-mangapill info
 *   bun scripts/test-tachiyomi-source.ts en-mangapill popular
 *   bun scripts/test-tachiyomi-source.ts en-mangapill search "one piece"
 */

import * as fs from 'fs';
import * as path from 'path';
import * as readline from 'readline';

const EXTENSIONS_BASE = path.resolve(import.meta.dir, "../dev/tachiyomi-extensions");

// Extension API interface (matches what Kotlin/JS exports)
interface TachiyomiExports {
  getManifest(): string;
  getPopularManga(sourceId: string, page: number): string;
  getLatestUpdates(sourceId: string, page: number): string;
  searchManga(sourceId: string, page: number, query: string): string;
  getMangaDetails(sourceId: string, mangaJson: string): string;
  getChapterList(sourceId: string, mangaJson: string): string;
  getPageList(sourceId: string, chapterJson: string): string;
  getFilterList(sourceId: string): string;
}

interface SourceInfo {
  id: string;
  name: string;
  lang: string;
  baseUrl: string;
  supportsLatest: boolean;
}

interface MangasPage {
  mangas: Array<{
    url: string;
    title: string;
    thumbnailUrl?: string;
    author?: string;
    artist?: string;
    description?: string;
    status?: number;
    genre?: string[];
  }>;
  hasNextPage: boolean;
}

// Simple HTTP client for testing (async fetch, converted to sync-like interface)
function createHttpBridge() {
  const pendingRequests = new Map<number, Promise<any>>();
  let requestId = 0;

  (globalThis as any).tachiyomiHttpRequest = (
    url: string,
    method: string,
    headersJson: string,
    body: string | null,
    wantBytes: boolean
  ) => {
    // For test purposes, we'll use async fetch but block with a sync XHR wrapper
    // This works in Node/Bun but not in browsers
    try {
      const headers = JSON.parse(headersJson);
      const xhr = new (require('sync-rpc'))(require.resolve('./http-worker'));
      return xhr(url, method, headers, body, wantBytes);
    } catch {
      // Fallback: use sync XMLHttpRequest-like behavior with Bun's sync fetch
      // Unfortunately Bun doesn't have sync HTTP, so we'll use a workaround
      const result = Bun.spawnSync(['curl', '-s', '-X', method, url, '-o', '-']);
      return {
        status: 200,
        statusText: 'OK',
        headersJson: '{}',
        body: result.stdout.toString(),
        error: result.exitCode !== 0 ? result.stderr.toString() : null,
      };
    }
  };
}

// Simple sync HTTP using curl
function syncHttpRequest(
  url: string,
  method: string,
  headers: Record<string, string>,
  body: string | null,
  wantBytes: boolean
): { status: number; body: string; headers: Record<string, string>; error: string | null } {
  const args = ['-s', '-X', method, '-w', '\n%{http_code}', '-D', '-'];
  
  for (const [key, value] of Object.entries(headers)) {
    args.push('-H', `${key}: ${value}`);
  }
  
  if (body) {
    args.push('-d', body);
  }
  
  args.push(url);
  
  const result = Bun.spawnSync(['curl', ...args]);
  const stdout = result.stdout;
  
  // Parse headers (until empty line)
  let headerEndIdx = 0;
  for (let i = 0; i < stdout.length - 1; i++) {
    if (stdout[i] === 13 && stdout[i + 1] === 10) { // \r\n
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
  const headerLines = headerSection.split('\r\n');
  for (const line of headerLines.slice(1)) { // Skip status line
    const idx = line.indexOf(': ');
    if (idx > 0) {
      responseHeaders[line.slice(0, idx).toLowerCase()] = line.slice(idx + 2);
    }
  }
  
  // Get status code from last line
  const bodyStr = bodySection.toString();
  const lastNewline = bodyStr.lastIndexOf('\n');
  const statusCode = parseInt(bodyStr.slice(lastNewline + 1)) || 200;
  const responseBody = bodyStr.slice(0, lastNewline);
  
  // If wantBytes, convert to base64
  let finalBody: string;
  if (wantBytes) {
    const bodyBytes = Buffer.from(responseBody, 'binary');
    finalBody = bodyBytes.toString('base64');
  } else {
    finalBody = responseBody;
  }
  
  return {
    status: statusCode,
    body: finalBody,
    headers: responseHeaders,
    error: result.exitCode !== 0 ? result.stderr.toString() : null,
  };
}

// Set up HTTP bridge for Kotlin/JS
(globalThis as any).tachiyomiHttpRequest = (
  url: string,
  method: string,
  headersJson: string,
  body: string | null,
  wantBytes: boolean
): { status: number; statusText: string; headersJson: string; body: string; error: string | null } => {
  const headers = JSON.parse(headersJson || '{}');
  const result = syncHttpRequest(url, method, headers, body, wantBytes);
  
  return {
    status: result.status,
    statusText: result.status >= 200 && result.status < 300 ? 'OK' : 'Error',
    headersJson: JSON.stringify(result.headers),
    body: result.body,
    error: result.error,
  };
};

// Unwrap Kotlin/JS result format
function unwrapResult<T>(json: string): T {
  const result = JSON.parse(json) as { ok: boolean; data?: T; error?: any };
  if (!result.ok) {
    const errMsg = typeof result.error === 'string' 
      ? result.error 
      : JSON.stringify(result.error, null, 2);
    throw new Error(errMsg || 'Unknown error');
  }
  return result.data as T;
}

// Load and execute extension
function loadExtension(extensionId: string): { manifest: any; exports: TachiyomiExports; sources: SourceInfo[] } {
  const extDir = path.join(EXTENSIONS_BASE, extensionId);
  const manifestPath = path.join(extDir, 'manifest.json');
  const jsPath = path.join(extDir, 'extension.js');
  
  if (!fs.existsSync(manifestPath)) {
    throw new Error(`Extension not found: ${extDir}`);
  }
  
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
  const code = fs.readFileSync(jsPath, 'utf-8');
  
  // Execute the extension code
  const fn = new Function(code);
  fn();
  
  // Find the exports in globalThis
  const g = globalThis as any;
  let exports: TachiyomiExports | null = null;
  
  for (const key of Object.keys(g)) {
    if (g[key]?.tachiyomi?.generated) {
      exports = g[key].tachiyomi.generated;
      break;
    }
  }
  
  if (!exports || typeof exports.getManifest !== 'function') {
    throw new Error('Could not find tachiyomi.generated exports');
  }
  
  // Get sources from extension
  const sourcesJson = exports.getManifest();
  const sources = unwrapResult<SourceInfo[]>(sourcesJson);
  
  return { manifest, exports, sources };
}

// Pretty print results
function prettyPrint(data: any) {
  console.log(JSON.stringify(data, null, 2));
}

// Main
async function main() {
  const args = process.argv.slice(2);
  const extensionId = args[0];
  const command = args[1] || 'info';
  
  if (!extensionId) {
    console.log(`
Usage: bun scripts/test-tachiyomi-source.ts <extension-id> [command]

Commands:
  info                Show extension info
  popular [page]      Get popular manga
  latest [page]       Get latest updates
  search <query>      Search for manga
  details <url>       Get manga details
  chapters <url>      Get chapter list
  pages <url>         Get page list
  interactive         Interactive mode

Examples:
  bun scripts/test-tachiyomi-source.ts en-mangapill info
  bun scripts/test-tachiyomi-source.ts en-mangapill popular
  bun scripts/test-tachiyomi-source.ts en-mangapill search "one piece"
`);
    process.exit(1);
  }
  
  console.log(`Loading extension: ${extensionId}...`);
  const { manifest, exports, sources } = loadExtension(extensionId);
  
  if (sources.length === 0) {
    console.error('No sources found in extension');
    process.exit(1);
  }
  
  const source = sources[0];
  const sourceId = source.id;
  console.log(`Loaded: ${source.name} (${source.lang}) - ID: ${sourceId}\n`);
  
  switch (command) {
    case 'info': {
      console.log('=== Extension Info ===');
      console.log(`Name: ${manifest.name}`);
      console.log(`Package: ${manifest.pkg}`);
      console.log(`Version: ${manifest.version}`);
      console.log(`NSFW: ${manifest.nsfw}`);
      console.log(`\n=== Sources (${sources.length}) ===`);
      for (const s of sources) {
        console.log(`- ${s.name} (${s.lang}): ${s.baseUrl}`);
        console.log(`  ID: ${s.id}`);
        console.log(`  Supports Latest: ${s.supportsLatest}`);
      }
      if (manifest.authors?.length) {
        console.log(`\n=== Authors (${manifest.authors.length}) ===`);
        for (const a of manifest.authors) {
          console.log(`- ${a.github || a.name}: ${a.commits} commits (since ${a.firstCommit})`);
        }
      }
      break;
    }
    
    case 'popular': {
      const page = parseInt(args[2]) || 1;
      console.log(`Fetching popular manga (page ${page})...`);
      const json = exports.getPopularManga(sourceId, page);
      const result = unwrapResult<MangasPage>(json);
      console.log(`\n=== Popular Manga (${result.mangas?.length || 0} items, hasNextPage: ${result.hasNextPage}) ===\n`);
      for (const m of result.mangas?.slice(0, 10) || []) {
        console.log(`• ${m.title}`);
        console.log(`  URL: ${m.url}`);
        if (m.thumbnailUrl) console.log(`  Thumb: ${m.thumbnailUrl}`);
        console.log();
      }
      if ((result.mangas?.length || 0) > 10) {
        console.log(`... and ${result.mangas.length - 10} more`);
      }
      break;
    }
    
    case 'latest': {
      const page = parseInt(args[2]) || 1;
      console.log(`Fetching latest updates (page ${page})...`);
      const json = exports.getLatestUpdates(sourceId, page);
      const result = unwrapResult<MangasPage>(json);
      console.log(`\n=== Latest Updates (${result.mangas?.length || 0} items, hasNextPage: ${result.hasNextPage}) ===\n`);
      for (const m of result.mangas?.slice(0, 10) || []) {
        console.log(`• ${m.title}`);
        console.log(`  URL: ${m.url}`);
        console.log();
      }
      break;
    }
    
    case 'search': {
      const query = args.slice(2).join(' ');
      if (!query) {
        console.error('Usage: search <query>');
        process.exit(1);
      }
      console.log(`Searching for "${query}"...`);
      const json = exports.searchManga(sourceId, 1, query);
      const result = unwrapResult<MangasPage>(json);
      console.log(`\n=== Search Results (${result.mangas?.length || 0} items) ===\n`);
      for (const m of result.mangas?.slice(0, 10) || []) {
        console.log(`• ${m.title}`);
        console.log(`  URL: ${m.url}`);
        console.log();
      }
      break;
    }
    
    case 'details': {
      const url = args[2];
      if (!url) {
        console.error('Usage: details <url>');
        process.exit(1);
      }
      console.log(`Fetching manga details...`);
      const json = exports.getMangaDetails(sourceId, JSON.stringify({ url }));
      const manga = unwrapResult<any>(json);
      console.log('\n=== Manga Details ===\n');
      prettyPrint(manga);
      break;
    }
    
    case 'chapters': {
      const url = args[2];
      if (!url) {
        console.error('Usage: chapters <url>');
        process.exit(1);
      }
      console.log(`Fetching chapter list...`);
      const json = exports.getChapterList(sourceId, JSON.stringify({ url }));
      const chapters = unwrapResult<any[]>(json);
      console.log(`\n=== Chapters (${chapters?.length || 0}) ===\n`);
      for (const c of chapters?.slice(0, 10) || []) {
        console.log(`• ${c.name}`);
        console.log(`  URL: ${c.url}`);
        console.log();
      }
      if ((chapters?.length || 0) > 10) {
        console.log(`... and ${chapters.length - 10} more`);
      }
      break;
    }
    
    case 'pages': {
      const url = args[2];
      if (!url) {
        console.error('Usage: pages <url>');
        process.exit(1);
      }
      console.log(`Fetching page list...`);
      const json = exports.getPageList(sourceId, JSON.stringify({ url }));
      const pages = unwrapResult<any[]>(json);
      console.log(`\n=== Pages (${pages?.length || 0}) ===\n`);
      for (const p of pages?.slice(0, 5) || []) {
        console.log(`Page ${p.index + 1}: ${p.imageUrl || p.url}`);
      }
      if ((pages?.length || 0) > 5) {
        console.log(`... and ${pages.length - 5} more`);
      }
      break;
    }
    
    case 'interactive': {
      const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
      });
      
      console.log('\n=== Interactive Mode ===');
      console.log('Commands: popular, latest, search <query>, details <url>, chapters <url>, pages <url>, quit\n');
      
      const prompt = () => {
        rl.question('> ', (line) => {
          const [cmd, ...rest] = line.trim().split(' ');
          
          if (cmd === 'quit' || cmd === 'exit') {
            rl.close();
            return;
          }
          
          try {
            switch (cmd) {
              case 'popular': {
                const json = exports.getPopularManga(sourceId, parseInt(rest[0]) || 1);
                const result = unwrapResult<MangasPage>(json);
                console.log(`${result.mangas?.length || 0} manga found`);
                for (const m of result.mangas?.slice(0, 5) || []) {
                  console.log(`  • ${m.title}`);
                }
                break;
              }
              case 'latest': {
                const json = exports.getLatestUpdates(sourceId, parseInt(rest[0]) || 1);
                const result = unwrapResult<MangasPage>(json);
                console.log(`${result.mangas?.length || 0} manga found`);
                for (const m of result.mangas?.slice(0, 5) || []) {
                  console.log(`  • ${m.title}`);
                }
                break;
              }
              case 'search': {
                const json = exports.searchManga(sourceId, 1, rest.join(' '));
                const result = unwrapResult<MangasPage>(json);
                console.log(`${result.mangas?.length || 0} results`);
                for (const m of result.mangas?.slice(0, 5) || []) {
                  console.log(`  • ${m.title}`);
                }
                break;
              }
              default:
                console.log('Unknown command');
            }
          } catch (e: any) {
            console.error(`Error: ${e.message}`);
          }
          
          prompt();
        });
      };
      
      prompt();
      return; // Don't exit, keep interactive
    }
    
    default:
      console.error(`Unknown command: ${command}`);
      process.exit(1);
  }
}

main().catch(e => {
  console.error(`Error: ${e.message}`);
  process.exit(1);
});
