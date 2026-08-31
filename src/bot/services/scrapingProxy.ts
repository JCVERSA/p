import type { AxiosProxyConfig } from "axios";

/**
 * Optional egress proxy for the anime scraping pipeline.
 *
 * Anime-sama.to sits behind Cloudflare and blocks many datacenter / VPS IP
 * ranges with HTTP 403 (see ANIME_DOWNLOAD_AUDIT.md, finding R3). Setting
 *
 *     NEBULA_ANIME_PROXY=http://user:pass@proxy-host:8080
 *
 * routes every anime-related axios request (search, seasons, episodes.js,
 * player mirrors, HLS manifests, direct MP4 downloads) through that proxy.
 *
 * - Supported: http:// and https:// (CONNECT) proxies, with optional
 *   userinfo credentials (percent-encoded or raw).
 * - SOCKS proxies are NOT supported by axios' built-in proxy support; they
 *   are ignored here (export https_proxy + a socks agent instead if needed).
 * - When unset, axios' default behavior applies (standard http_proxy /
 *   https_proxy environment variables still work).
 */

export const ANIME_PROXY_ENV = "NEBULA_ANIME_PROXY";

/** Parses a proxy URL into an axios proxy config. Exported for tests. */
export function parseProxyUrl(raw: string | undefined | null): AxiosProxyConfig | undefined {
  const s = (raw || "").trim();
  if (!s) return undefined;
  let u: URL;
  try {
    u = new URL(s);
  } catch {
    return undefined;
  }
  if (u.protocol !== "http:" && u.protocol !== "https:") return undefined;
  const port = Number(u.port) || (u.protocol === "https:" ? 443 : 80);
  const cfg: AxiosProxyConfig = {
    protocol: u.protocol.replace(":", ""),
    host: u.hostname,
    port
  };
  if (u.username) {
    cfg.auth = {
      username: decodeURIComponent(u.username),
      password: decodeURIComponent(u.password || "")
    };
  }
  return cfg;
}

let warnedInvalidProxy = false;

/** Axios `proxy` config for the anime pipeline (undefined = default egress). */
export function getAnimeProxyConfig(): AxiosProxyConfig | undefined {
  const raw = process.env[ANIME_PROXY_ENV];
  if (raw && raw.trim() && !warnedInvalidProxy) {
    const cfg = parseProxyUrl(raw);
    if (!cfg) {
      warnedInvalidProxy = true;
      console.warn(
        `[AnimeProxy] WARNING: ${ANIME_PROXY_ENV} is set but could not be parsed (${raw}). ` +
          `It is being IGNORED — requests go out directly. 'http://user:pass@host:port' is a ` +
          `TEMPLATE: replace user/pass/host/port with a REAL working proxy, or remove the variable.`
      );
    }
  }
  return parseProxyUrl(raw);
}

/** Human-readable description for logs / diagnostics. */
export function describeAnimeProxy(raw: string | undefined | null = process.env[ANIME_PROXY_ENV]): string {
  const cfg = parseProxyUrl(raw);
  if (!cfg) return "none (direct egress)";
  return `${cfg.protocol}://${cfg.host}:${cfg.port}${cfg.auth ? " (auth)" : ""}`;
}
