import axios from "axios";
import { spawn } from "child_process";
import fs from "fs";
import os from "os";
import path from "path";
import crypto from "crypto";
import ffmpegPath from "ffmpeg-static";
import { execSync } from "child_process";
import { downloadHlsAppLevel, robustFetchText, robustFetchBuffer, resolveAbsoluteUrl, resolveMediaPlaylistUrl } from "./hlsDownloader.js";
import { getAnimeProxyConfig } from "./scrapingProxy.js";

let resolvedFfmpegPath = "ffmpeg";
try {
  execSync("ffmpeg -version", { stdio: "ignore" });
  resolvedFfmpegPath = "ffmpeg";
} catch {
  resolvedFfmpegPath = ffmpegPath || "ffmpeg";
}

export interface StreamQualityTrack {
  resolution: string; // "360P", "480P", "720P", "1080P", "Original"
  url: string;
  bandwidth?: number;
  fileSizeBytes?: number;
  type: "direct_mp4" | "hls";
  headers?: Record<string, string>;
}

export interface ExtractedStreamResult {
  hostName: string;
  url: string;
  type: "direct_mp4" | "hls";
  headers: Record<string, string>;
  availableTracks?: StreamQualityTrack[];
  originalResolution?: string;
  estimatedSizeMB?: number;
}

/**
 * Decodes a JavaScript string literal the way `eval` would for the simple
 * escaped strings found in Dean Edwards packed scripts — without eval.
 * Supports the escapes actually emitted by packers (\xNN, \uNNNN, \n \r \t \\ \' \").
 */
export function decodeJsStringLiteral(literal: string): string {
  const trimmed = literal.trim();
  const quote = trimmed[0];
  if ((quote !== '"' && quote !== "'") || trimmed[trimmed.length - 1] !== quote) {
    return trimmed;
  }
  const body = trimmed.slice(1, -1);
  let out = "";
  for (let i = 0; i < body.length; i++) {
    const ch = body[i];
    if (ch !== "\\") {
      out += ch;
      continue;
    }
    const next = body[i + 1];
    if (next === undefined) {
      out += "\\";
      break;
    }
    switch (next) {
      case "n": out += "\n"; i++; break;
      case "r": out += "\r"; i++; break;
      case "t": out += "\t"; i++; break;
      case "\\": out += "\\"; i++; break;
      case "'": out += "'"; i++; break;
      case '"': out += '"'; i++; break;
      case "x": {
        const hex = body.slice(i + 2, i + 4);
        if (/^[0-9a-fA-F]{2}$/.test(hex)) {
          out += String.fromCharCode(parseInt(hex, 16));
          i += 3;
        } else {
          out += "x";
        }
        break;
      }
      case "u": {
        const hex = body.slice(i + 2, i + 6);
        if (/^[0-9a-fA-F]{4}$/.test(hex)) {
          out += String.fromCharCode(parseInt(hex, 16));
          i += 5;
        } else {
          out += "u";
        }
        break;
      }
      default:
        out += next;
        i++;
    }
  }
  return out;
}

/**
 * Parses the simple flat array literal used as the `k` parameter of packed
 * scripts (['a','b',...]) without eval.
 */
export function decodeJsArrayLiteral(literal: string): string[] {
  const trimmed = literal.trim();
  if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return [];
  const inner = trimmed.slice(1, -1);
  if (!inner.trim()) return [];
  return inner.split(",").map((part) => decodeJsStringLiteral(part));
}

/**
 * Unpacks Dean Edwards JavaScript packers (eval(function(p,a,c,k,e,d)...))
 * safely without executing untrusted third-party code.
 */
export function unpackDeanEdwards(html: string): string {
  if (!html) return "";
  let result = html;
  const packedRegex = /eval\(function\(p,a,c,k,e,d\)\{[\s\S]*?return\s+p;?\}\((?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)")\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)")\.split\(['"]\|['"]\)\)/gi;
  let match: RegExpExecArray | null;
  while ((match = packedRegex.exec(html)) !== null) {
    try {
      const pRaw = match[1] !== undefined ? match[1] : match[2];
      const aVal = parseInt(match[3], 10);
      const cVal = parseInt(match[4], 10);
      const kRaw = match[5] !== undefined ? match[5] : match[6];
      const kVal = kRaw.split("|");
      const pVal = decodeJsStringLiteral("'" + pRaw + "'");
      let count = cVal;
      let unpacked = pVal;
      while (count--) {
        if (kVal[count]) {
          unpacked = unpacked.replace(new RegExp('\\b' + count.toString(aVal) + '\\b', 'g'), kVal[count]);
        }
      }
      result += "\n" + unpacked;
    } catch {
      // Safe failover
    }
  }
  return result;
}

const DEFAULT_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

/**
 * Extract direct streams from streaming player mirrors (Smoothpre, Sibnet, Sendvid, VidMoly, Ansembed)
 */
export async function extractMultiHostStream(playerUrl: string): Promise<ExtractedStreamResult | null> {
  try {
    if (!playerUrl) return null;
    const lowerUrl = playerUrl.toLowerCase();

    // 1. Smoothpre & generic Dean-Edwards packed players (Priority 1: Multi-quality HLS streams)
    if (lowerUrl.includes("smoothpre") || lowerUrl.includes("dramiyos") || lowerUrl.includes("embed") || lowerUrl.includes("player")) {
      try {
        const resp = await axios.get(playerUrl, {
          headers: {
            "User-Agent": DEFAULT_USER_AGENT,
            "Referer": "https://anime-sama.to/"
          },
          timeout: 7000,
          validateStatus: () => true,
    proxy: getAnimeProxyConfig()
        });

        if (resp.status === 200) {
          const rawHtml = typeof resp.data === "string" ? resp.data : "";
          const unpackedHtml = unpackDeanEdwards(rawHtml);

          const m3u8Matches =
            unpackedHtml.match(/https?:\/\/[^"\x27\s<>]+\.(?:m3u8|txt)[^"\x27\s<>]*/gi) ||
            rawHtml.match(/https?:\/\/[^"\x27\s<>]+\.(?:m3u8|txt)[^"\x27\s<>]*/gi);

          if (m3u8Matches && m3u8Matches.length > 0) {
            // Pick master.m3u8 or the first valid HLS URL
            const masterUrl = m3u8Matches.find(u => u.includes("master.m3u8")) || m3u8Matches[0];
            const originMatch = playerUrl.match(/^(https?:\/\/[^/]+)/i);
            const playerOrigin = originMatch ? originMatch[1] : "https://Smoothpre.com";

            const streamHeaders = {
              "User-Agent": DEFAULT_USER_AGENT,
              "Referer": `${playerOrigin}/`,
              "Origin": playerOrigin
            };

            const parsedTracks = await fetchHlsTracksAndSizes(masterUrl, `${playerOrigin}/`, playerOrigin);

            return {
              hostName: "Smoothpre",
              url: masterUrl,
              type: "hls",
              headers: streamHeaders,
              availableTracks: parsedTracks
            };
          }
        }
      } catch (err: any) {
        if (process.env.DEBUG_MEDIA) console.debug(`[STREAM_EXTRACTOR] Smoothpre probe error:`, err.message);
      }
    }

    // 2. Sibnet (Fast, direct MP4 delivery)
    if (lowerUrl.includes("sibnet.ru")) {
      try {
        const resp = await axios.get(playerUrl, {
          headers: {
            "User-Agent": DEFAULT_USER_AGENT,
            "Referer": "https://video.sibnet.ru/"
          },
          timeout: 6000,
          validateStatus: () => true,
    proxy: getAnimeProxyConfig()
        });
        if (resp.status === 200) {
          const html = typeof resp.data === "string" ? resp.data : "";
          const match = html.match(/player\.src\(\[\{src:\s*["']([^"']+)["']/i) || html.match(/src:\s*["'](\/v\/[^"']+)["']/i);
          if (match && match[1]) {
            let streamPath = match[1];
            if (streamPath.startsWith("/")) {
              streamPath = "https://video.sibnet.ru" + streamPath;
            }
            return {
              hostName: "Sibnet",
              url: streamPath,
              type: "direct_mp4",
              headers: {
                "User-Agent": DEFAULT_USER_AGENT,
                "Referer": playerUrl
              },
              availableTracks: [
                {
                  resolution: "480P",
                  url: streamPath,
                  fileSizeBytes: 85 * 1024 * 1024,
                  type: "direct_mp4",
                  headers: { "User-Agent": DEFAULT_USER_AGENT, "Referer": playerUrl }
                },
                {
                  resolution: "360P",
                  url: streamPath,
                  fileSizeBytes: 55 * 1024 * 1024,
                  type: "direct_mp4",
                  headers: { "User-Agent": DEFAULT_USER_AGENT, "Referer": playerUrl }
                }
              ]
            };
          }
        }
      } catch (err: any) {
        if (process.env.DEBUG_MEDIA) console.debug(`[STREAM_EXTRACTOR] Sibnet probe error:`, err.message);
      }
    }

    // 3. Sendvid (Direct MP4 with exact resolutions)
    if (lowerUrl.includes("sendvid.com")) {
      try {
        const resp = await axios.get(playerUrl, {
          headers: {
            "User-Agent": DEFAULT_USER_AGENT,
            "Referer": "https://anime-sama.to/"
          },
          timeout: 6000,
          validateStatus: () => true,
    proxy: getAnimeProxyConfig()
        });
        if (resp.status === 200) {
          const html = typeof resp.data === "string" ? resp.data : "";
          const videoSrcMatch = html.match(/<source\s+src="([^"]+)"\s+type="video\/mp4"/i) || html.match(/var\s+video_source\s*=\s*["']([^"']+)["']/i);
          if (videoSrcMatch && videoSrcMatch[1]) {
            const streamUrl = videoSrcMatch[1];
            return {
              hostName: "Sendvid",
              url: streamUrl,
              type: "direct_mp4",
              headers: {
                "User-Agent": DEFAULT_USER_AGENT,
                "Referer": playerUrl
              },
              availableTracks: [
                {
                  resolution: "480P",
                  url: streamUrl,
                  fileSizeBytes: 80 * 1024 * 1024,
                  type: "direct_mp4",
                  headers: { "User-Agent": DEFAULT_USER_AGENT, "Referer": playerUrl }
                },
                {
                  resolution: "360P",
                  url: streamUrl,
                  fileSizeBytes: 50 * 1024 * 1024,
                  type: "direct_mp4",
                  headers: { "User-Agent": DEFAULT_USER_AGENT, "Referer": playerUrl }
                }
              ]
            };
          }
        }
      } catch (err: any) {
        if (process.env.DEBUG_MEDIA) console.debug(`[STREAM_EXTRACTOR] Sendvid probe error:`, err.message);
      }
    }

    // 4. VidMoly / Ansembed / Topembed (HLS with multi-quality playlist)
    if (lowerUrl.includes("vidmoly.") || lowerUrl.includes("ansembed.") || lowerUrl.includes("topembed.") || lowerUrl.includes("vmpx.")) {
      try {
        const resp = await axios.get(playerUrl, {
          headers: {
            "User-Agent": DEFAULT_USER_AGENT,
            "Referer": "https://anime-sama.to/"
          },
          timeout: 6000,
          validateStatus: () => true,
    proxy: getAnimeProxyConfig()
        });
        if (resp.status === 200) {
          const html = typeof resp.data === "string" ? resp.data : "";
          const unpacked = unpackDeanEdwards(html);
          const combined = html + "\n" + unpacked;

          const sourcesMatch = combined.match(/sources:\s*\[([\s\S]*?)\]/i) || combined.match(/file:\s*["'](https?:\/\/[^"']+\.(?:m3u8|txt)[^"']*)["']/i);
          let fileUrl = "";
          if (sourcesMatch) {
            if (sourcesMatch[1].startsWith("http")) {
              fileUrl = sourcesMatch[1];
            } else {
              const fileInArr = sourcesMatch[1].match(/file:\s*["']([^"']+)["']/i);
              if (fileInArr) fileUrl = fileInArr[1];
            }
          }

          if (!fileUrl) {
            const anyM3u8 = combined.match(/https?:\/\/[^"\x27\s<>]+\.(?:m3u8|txt)[^"\x27\s<>]*/i);
            if (anyM3u8) fileUrl = anyM3u8[0];
          }

          if (fileUrl) {
            const originMatch = playerUrl.match(/^(https?:\/\/[^/]+)/i);
            const playerOrigin = originMatch ? originMatch[1] : "https://vidmoly.to";
            const parsedTracks = await fetchHlsTracksAndSizes(fileUrl, playerUrl, playerOrigin);
            return {
              hostName: "VidMoly",
              url: fileUrl,
              type: "hls",
              headers: {
                "User-Agent": DEFAULT_USER_AGENT,
                "Referer": playerUrl,
                "Origin": playerOrigin
              },
              availableTracks: parsedTracks
            };
          }
        }
      } catch (err: any) {
        if (process.env.DEBUG_MEDIA) console.debug(`[STREAM_EXTRACTOR] VidMoly probe error:`, err.message);
      }
    }
  } catch (err: any) {
    if (process.env.DEBUG_MEDIA) {
      console.debug(`[STREAM_EXTRACTOR] Probe note for ${playerUrl}:`, err.message);
    }
  }
  return null;
}

/**
 * Parses HLS playlist to detect available bandwidths, sub-variant URLs, and estimate episode sizes
 */
export async function fetchHlsTracksAndSizes(
  masterUrl: string,
  refererUrl: string,
  originUrl?: string
): Promise<StreamQualityTrack[]> {
  const tracks: StreamQualityTrack[] = [];
  const reqHeaders: Record<string, string> = {
    "User-Agent": DEFAULT_USER_AGENT,
    "Referer": refererUrl
  };
  if (originUrl) {
    reqHeaders["Origin"] = originUrl;
  }

  try {
    const resp = await axios.get(masterUrl, {
      headers: reqHeaders,
      timeout: 8000,
      proxy: getAnimeProxyConfig()
    });
    const manifest = typeof resp.data === "string" ? resp.data : "";
    const lines = manifest.split("\n");

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith("#EXT-X-STREAM-INF")) {
        const bwMatch = line.match(/BANDWIDTH=(\d+)/i);
        const resMatch = line.match(/RESOLUTION=\d+x(\d+)/i);
        const nextUrlLine = lines[i + 1]?.trim();

        if (nextUrlLine && !nextUrlLine.startsWith("#")) {
          const streamUrl = resolveAbsoluteUrl(masterUrl, nextUrlLine);
          const height = resMatch ? parseInt(resMatch[1], 10) : 0;
          const bandwidth = bwMatch ? parseInt(bwMatch[1], 10) : 800000;

          // Estimate 24 minute anime episode size in bytes: (bandwidth bps * 1440 sec) / 8
          const durationSeconds = 1440;
          const fileSizeBytes = Math.round((bandwidth * durationSeconds) / 8);

          let label = "480P";
          if (height >= 1000) label = "1080P";
          else if (height >= 700) label = "720P";
          else if (height >= 450) label = "480P";
          else if (height > 0) label = "360P";

          // Prevent duplicates for the same resolution label
          if (!tracks.some(t => t.resolution === label)) {
            tracks.push({
              resolution: label,
              url: streamUrl,
              bandwidth,
              fileSizeBytes,
              type: "hls",
              headers: reqHeaders
            });
          }
        }
      }
    }
  } catch (err: any) {
    if (process.env.DEBUG_MEDIA) {
      console.warn(`[STREAM_EXTRACTOR] Error inspecting HLS master:`, err.message);
    }
  }

  // Ensure standard options exist
  const has360 = tracks.some(t => t.resolution === "360P");
  const lowestTrack = tracks.length > 0 ? tracks[0] : null;

  if (tracks.length > 0) {
    if (!has360 && lowestTrack) {
      tracks.push({
        resolution: "360P",
        url: lowestTrack.url,
        bandwidth: Math.round((lowestTrack.bandwidth || 600000) * 0.65),
        fileSizeBytes: Math.round((lowestTrack.fileSizeBytes || 90 * 1024 * 1024) * 0.65),
        type: "hls",
        headers: reqHeaders
      });
    }
  } else {
    // Fallback defaults if master parsing returns empty
    tracks.push(
      { resolution: "480P", url: masterUrl, bandwidth: 700000, fileSizeBytes: 80 * 1024 * 1024, type: "hls", headers: reqHeaders },
      { resolution: "360P", url: masterUrl, bandwidth: 400000, fileSizeBytes: 50 * 1024 * 1024, type: "hls", headers: reqHeaders },
      { resolution: "720P", url: masterUrl, bandwidth: 1400000, fileSizeBytes: 180 * 1024 * 1024, type: "hls", headers: reqHeaders },
      { resolution: "1080P", url: masterUrl, bandwidth: 2600000, fileSizeBytes: 350 * 1024 * 1024, type: "hls", headers: reqHeaders }
    );
  }

  // Sort: 480P, 360P, 720P, 1080P
  const orderMap: Record<string, number> = { "480P": 1, "360P": 2, "720P": 3, "1080P": 4, "ORIGINAL": 5 };
  tracks.sort((a, b) => (orderMap[a.resolution.toUpperCase()] || 99) - (orderMap[b.resolution.toUpperCase()] || 99));

  return tracks;
}

/**
 * Resolves best mirror stream among all player URLs for an episode
 */
export async function resolveBestMirrorStream(mirrorUrls: string[], preferredRes: string = "480P"): Promise<ExtractedStreamResult> {
  const sortedMirrors = [...mirrorUrls].sort((a, b) => {
    // Prefer Smoothpre (clean HLS) > Sibnet > Sendvid > VidMoly for reliable direct downloads
    const priority = (url: string) => {
      const l = url.toLowerCase();
      if (l.includes("smoothpre")) return 1;
      if (l.includes("sibnet")) return 2;
      if (l.includes("sendvid")) return 3;
      if (l.includes("ansembed") || l.includes("vidmoly")) return 4;
      return 5;
    };
    return priority(a) - priority(b);
  });

  for (const mirror of sortedMirrors) {
    const extracted = await extractMultiHostStream(mirror);
    if (extracted && extracted.url) {
      return extracted;
    }
  }

  // Fallback
  return {
    hostName: "Direct Stream",
    url: mirrorUrls[0] || "",
    type: "hls",
    headers: { "Referer": "https://anime-sama.to/" }
  };
}

/**
 * Selects optimal stream based on user's priority for 480p/360p fast downloads
 */
export function pickOptimalStream(tracks: StreamQualityTrack[], requestedRes?: string): StreamQualityTrack {
  if (!tracks || tracks.length === 0) {
    return { resolution: "480P", url: "", type: "hls" };
  }

  if (requestedRes) {
    const match = tracks.find(t => t.resolution.toUpperCase() === requestedRes.toUpperCase());
    if (match) return match;
  }

  // Priority order: 480p > 360p > 720p > 1080p
  const p480 = tracks.find(t => t.resolution.toUpperCase() === "480P");
  if (p480) return p480;

  const p360 = tracks.find(t => t.resolution.toUpperCase() === "360P");
  if (p360) return p360;

  const p720 = tracks.find(t => t.resolution.toUpperCase() === "720P");
  if (p720) return p720;

  return tracks[0];
}

/**
 * Downloads direct MP4 or HLS stream directly to disk with proper browser headers
 */
export async function downloadStreamToDisk(stream: ExtractedStreamResult, outputPath: string, timeoutMs: number = 40000): Promise<boolean> {
  return executeDirectOrFfmpegDownload(stream, outputPath, timeoutMs);
}

// Re-export core robust fetchers and URL tools from hlsDownloader
export { robustFetchText, robustFetchBuffer, resolveAbsoluteUrl };

/**
 * Iterates through all available mirrors for an episode and executes the download,
 * guaranteeing seamless fallback if any host encounters a 403 Forbidden or network failure.
 */
export async function downloadWithAllMirrorsFallback(
  mirrorUrls: string[],
  preferredRes: string,
  outputPath: string,
  timeoutMs: number = 240000
): Promise<{ success: boolean; hostName: string; usedUrl: string }> {
  if (!mirrorUrls || mirrorUrls.length === 0) {
    return { success: false, hostName: "None", usedUrl: "" };
  }

  // Sort mirrors by reliability: Smoothpre > Sibnet > Sendvid > VidMoly > other
  const sortedMirrors = [...mirrorUrls].sort((a, b) => {
    const priority = (url: string) => {
      const l = url.toLowerCase();
      if (l.includes("smoothpre")) return 1;
      if (l.includes("sibnet")) return 2;
      if (l.includes("sendvid")) return 3;
      if (l.includes("ansembed") || l.includes("vidmoly")) return 4;
      return 5;
    };
    return priority(a) - priority(b);
  });

  for (let i = 0; i < sortedMirrors.length; i++) {
    const mirrorUrl = sortedMirrors[i];
    try {
      console.log(`[MIRROR_FALLBACK] Probing mirror ${i + 1}/${sortedMirrors.length}: ${mirrorUrl}`);
      const extracted = await extractMultiHostStream(mirrorUrl);
      if (!extracted || !extracted.url) {
        console.warn(`[MIRROR_FALLBACK] Could not extract stream from mirror ${mirrorUrl}`);
        continue;
      }

      // Pick preferred resolution track if available
      let targetStream = extracted;
      if (extracted.availableTracks && extracted.availableTracks.length > 0) {
        const picked = pickOptimalStream(extracted.availableTracks, preferredRes);
        if (picked && picked.url) {
          targetStream = {
            ...extracted,
            url: picked.url,
            type: picked.type || extracted.type,
            headers: picked.headers || extracted.headers
          };
        }
      }

      console.log(`[MIRROR_FALLBACK] Attempting download with host "${targetStream.hostName}" on URL: ${targetStream.url}`);
      const dlSuccess = await executeDirectOrFfmpegDownload(targetStream, outputPath, timeoutMs);

      if (dlSuccess && fs.existsSync(outputPath) && fs.statSync(outputPath).size > 1000) {
        console.log(`[MIRROR_FALLBACK] Mirror ${targetStream.hostName} succeeded! Size: ${fs.statSync(outputPath).size} bytes`);
        return { success: true, hostName: targetStream.hostName, usedUrl: targetStream.url };
      }

      console.warn(`[MIRROR_FALLBACK] Mirror ${targetStream.hostName} download failed or produced empty file. Trying next mirror...`);
    } catch (err: any) {
      console.warn(`[MIRROR_FALLBACK] Error on mirror ${mirrorUrl}: ${err.message}. Trying next mirror...`);
    }
  }

  return { success: false, hostName: "Failed", usedUrl: "" };
}

/**
 * Prepares a local HLS playlist by making segment URLs absolute and appending original query params to bypass CDN 403 Forbidden checks.
 */
export async function prepareLocalHlsPlaylist(
  playlistUrl: string,
  headers: Record<string, string>
): Promise<string | null> {
  try {
    // Resolve master or variant playlist down to media segments
    const resolved = await resolveMediaPlaylistUrl(playlistUrl, headers);
    if (!resolved || !resolved.content) {
      console.warn(`[HLS_PREPARE] Invalid or unreachable playlist content from: ${playlistUrl}`);
      return null;
    }

    const { mediaPlaylistUrl, content: playlistContent } = resolved;

    // Rewrite lines to make segment and key URLs absolute with original search params
    const lines = playlistContent.split(/\r?\n/);
    const modifiedLines: string[] = [];

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) {
        modifiedLines.push(line);
        continue;
      }

      if (trimmed.startsWith("#")) {
        // Handle #EXT-X-KEY if present for AES-128 streams
        if (trimmed.startsWith("#EXT-X-KEY:")) {
          const uriMatch = trimmed.match(/URI=["']([^"']+)["']/i);
          if (uriMatch && uriMatch[1]) {
            const keyUri = uriMatch[1];
            const absoluteKeyUrl = resolveAbsoluteUrl(mediaPlaylistUrl, keyUri);
            const rewrittenKey = trimmed.replace(uriMatch[1], absoluteKeyUrl);
            modifiedLines.push(rewrittenKey);
            continue;
          }
        }
        // Handle #EXT-X-MAP:URI if present
        if (trimmed.startsWith("#EXT-X-MAP:")) {
          const mapMatch = trimmed.match(/URI=["']([^"']+)["']/i);
          if (mapMatch && mapMatch[1]) {
            const absoluteMapUrl = resolveAbsoluteUrl(mediaPlaylistUrl, mapMatch[1]);
            const rewrittenMap = trimmed.replace(mapMatch[1], absoluteMapUrl);
            modifiedLines.push(rewrittenMap);
            continue;
          }
        }
        modifiedLines.push(line);
      } else {
        // Segment URL
        const absoluteSegmentUrl = resolveAbsoluteUrl(mediaPlaylistUrl, trimmed);
        modifiedLines.push(absoluteSegmentUrl);
      }
    }

    // Write to a temporary file in the OS temp directory
    const tempFilename = `auth_playlist_${crypto.randomBytes(8).toString("hex")}.m3u8`;
    const tempPath = path.join(os.tmpdir(), tempFilename);
    fs.writeFileSync(tempPath, modifiedLines.join("\n"), "utf-8");

    if (process.env.DEBUG_MEDIA !== "false") {
      console.log(`[HLS_PREPARE] Generated local authenticated HLS playlist at: ${tempPath}`);
    }
    return tempPath;
  } catch (err: any) {
    console.error(`[HLS_PREPARE] Error preparing local HLS playlist:`, err.message);
    return null;
  }
}

/**
 * Executes direct stream download or ffmpeg compilation with custom headers
 */
export async function executeDirectOrFfmpegDownload(
  stream: { url: string; type?: string; headers?: Record<string, string> },
  outputPath: string,
  timeoutMs: number = 40000
): Promise<boolean> {
  try {
    if (!stream.url) return false;

    // Direct HTTP download for clean MP4 sources
    if (stream.type === "direct_mp4" || stream.url.endsWith(".mp4")) {
      const writer = fs.createWriteStream(outputPath);
      const response = await axios({
        method: "get",
        url: stream.url,
        responseType: "stream",
        headers: stream.headers || {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        },
        timeout: timeoutMs,
        proxy: getAnimeProxyConfig()
      });

      response.data.pipe(writer);
      return new Promise((resolve) => {
        writer.on("finish", () => {
          if (fs.existsSync(outputPath) && fs.statSync(outputPath).size > 1000) {
            resolve(true);
          } else {
            resolve(false);
          }
        });
        writer.on("error", () => resolve(false));
      });
    }

    // HLS Stream download: Try Cat-Catch style application-level parallel downloader first
    const headers = stream.headers || {};
    try {
      console.log(`[STREAM_EXTRACTOR] Launching primary Cat-Catch style HLS downloader for: ${stream.url}`);
      // Give the application-level downloader a comfortable timeout (e.g. 4 minutes)
      const catCatchSuccess = await downloadHlsAppLevel(stream.url, outputPath, headers, 240000);
      if (catCatchSuccess && fs.existsSync(outputPath) && fs.statSync(outputPath).size > 1000) {
        console.log(`[STREAM_EXTRACTOR] Primary Cat-Catch HLS download succeeded! Output size: ${fs.statSync(outputPath).size} bytes`);
        return true;
      }
      console.warn(`[STREAM_EXTRACTOR] Primary Cat-Catch HLS download failed or returned invalid file. Falling back to legacy network FFmpeg...`);
    } catch (catErr: any) {
      console.warn(`[STREAM_EXTRACTOR] Error in primary Cat-Catch HLS downloader: ${catErr.message}. Falling back to legacy network FFmpeg...`);
    }

    // Secondary fallback: HLS Stream download via direct network FFmpeg
    let userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    let headerStr = "";
    for (const [key, value] of Object.entries(headers)) {
      if (key.toLowerCase() === "user-agent") {
        userAgent = value;
      } else {
        headerStr += `${key}: ${value}\r\n`;
      }
    }

    // Pre-resolve segments with tokens to prevent 403 CDN Forbidden errors
    const localPlaylistPath = await prepareLocalHlsPlaylist(stream.url, headers);
    const ffmpegInput = localPlaylistPath || stream.url;

    return new Promise((resolve) => {
      const args = [
        "-y",
        "-user_agent", userAgent,
        "-headers", headerStr,
        "-reconnect", "1",
        "-reconnect_at_eof", "1",
        "-reconnect_streamed", "1",
        "-reconnect_delay_max", "3",
        "-rw_timeout", "10000000",
        "-analyzeduration", "5M",
        "-probesize", "5M",
        "-i", ffmpegInput,
        "-c", "copy",
        "-bsf:a", "aac_adtstoasc",
        "-movflags", "+faststart",
        outputPath
      ];

      const proc = spawn(resolvedFfmpegPath, args, { stdio: ["ignore", "ignore", "pipe"] });
      
      let stderrLog = "";
      proc.stderr?.on("data", (chunk) => {
        stderrLog += chunk.toString();
      });

      const timer = setTimeout(() => {
        try { proc.kill("SIGKILL"); } catch {}
        if (localPlaylistPath && fs.existsSync(localPlaylistPath)) {
          try { fs.unlinkSync(localPlaylistPath); } catch {}
        }
        resolve(false);
      }, timeoutMs);

      proc.on("close", (code) => {
        clearTimeout(timer);
        if (localPlaylistPath && fs.existsSync(localPlaylistPath)) {
          try { fs.unlinkSync(localPlaylistPath); } catch {}
        }
        if (code === 0 && fs.existsSync(outputPath) && fs.statSync(outputPath).size > 1000) {
          resolve(true);
        } else {
          console.warn(`[STREAM_EXTRACTOR] Secondary FFmpeg fallback close code: ${code}. Output size: ${fs.existsSync(outputPath) ? fs.statSync(outputPath).size : 0} bytes. stderr:`, stderrLog);
          resolve(false);
        }
      });

      proc.on("error", () => {
        clearTimeout(timer);
        if (localPlaylistPath && fs.existsSync(localPlaylistPath)) {
          try { fs.unlinkSync(localPlaylistPath); } catch {}
        }
        resolve(false);
      });
    });
  } catch (err: any) {
    console.warn("[STREAM_EXTRACTOR] Download error:", err.message);
    return false;
  }
}
