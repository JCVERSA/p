import { describe, it, expect } from "vitest";
import {
  decodeNakanimeResponse,
  deriveNakanimeKey,
  parseNakanimeSeasonsScript,
  isNakanimeUrl
} from "../src/bot/services/nakanimeClient.js";

/**
 * Fixed vectors computed with the reference (Python) implementation of
 * nakanime's XOR scheme: key = derive("nkapiv1" + path), body[i] ^ key[i%32].
 */
const SEARCH_PATH = "/api/catalog/search?q=geass&sort=relevance&page=1&per_page=32";
const SEARCH_HEX =
  "4b52d491441192ca6b0b929954528ac102439cd2431cc597124a92935f14d5dd5715d18343529cd24419c49c55528ad2731fd4951037d5914303928d6d0d";
const SOURCES_HEX =
  "2af03bbb26a275e27ba932be33bb2bc24dc00f850b8112890e8f49d64fb820a3059401f679f422aa35f861fe35aa2b9012d84ccb138f038d06c418850f800a847fa026fb23f936ba3af436ac69fc22";

describe("nakanime XOR codec", () => {
  it("derives the documented key", () => {
    const key = deriveNakanimeKey(SEARCH_PATH);
    expect(Array.from(key.slice(0, 8))).toEqual([48, 112, 176, 240, 48, 112, 176, 240]);
    expect(key.length).toBe(32);
  });

  it("decodes a captured search payload", () => {
    const out = decodeNakanimeResponse(Buffer.from(SEARCH_HEX, "hex"), SEARCH_PATH);
    expect(out).not.toBeNull();
    expect(JSON.parse(out!)).toEqual({
      data: [{ id: 123, slug: "code-geass", title: "Code Geass" }]
    });
  });

  it("decodes a captured sources payload", () => {
    const out = decodeNakanimeResponse(Buffer.from(SOURCES_HEX, "hex"), "/api/sources/anime");
    expect(JSON.parse(out!)).toEqual({
      host: "sibnet",
      language: "VOSTFR",
      url: "https://video.sibnet.ru/v/abc.mp4"
    });
  });

  it("round-trips arbitrary payloads", () => {
    const key = deriveNakanimeKey("/api/anime/42/episodes");
    const payload = Buffer.from(JSON.stringify({ data: [{ seasonNumber: 2, number: 7 }] }), "utf8");
    const enc = Buffer.alloc(payload.length);
    for (let i = 0; i < payload.length; i++) enc[i] = payload[i] ^ key[i % key.length];
    expect(decodeNakanimeResponse(enc, "/api/anime/42/episodes")).toBe(payload.toString("utf8"));
  });
});

describe("nakanime seasons script parser", () => {
  it("extracts seasons and episode numbers from the embedded JSON script", () => {
    const html = `
      <html><body>
      <script>console.log("noise")</script>
      <script>{"animeId":123,"seasons":[
        {"number":1,"episodes":[{"number":1,"id":11},{"number":2,"id":12}]},
        {"number":2,"episodes":[{"number":1,"id":21}]}
      ]}</script>
      </body></html>`;
    const bySeason = parseNakanimeSeasonsScript(html);
    expect(bySeason.size).toBe(2);
    expect(bySeason.get(1)).toEqual([
      { number: 1, id: 11 },
      { number: 2, id: 12 }
    ]);
    expect(bySeason.get(2)).toEqual([{ number: 1, id: 21 }]);
  });

  it("returns an empty map when no seasons script exists", () => {
    expect(parseNakanimeSeasonsScript("<html><script>var x = 1;</script></html>").size).toBe(0);
  });
});

describe("isNakanimeUrl", () => {
  it("detects nakanime URLs and rejects others", () => {
    expect(isNakanimeUrl("https://nakanime.tv/anime/5/code-geass")).toBe(true);
    expect(isNakanimeUrl("https://anime-sama.to/catalogue/x/")).toBe(false);
    expect(isNakanimeUrl("")).toBe(false);
  });
});
