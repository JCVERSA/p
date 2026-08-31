import { describe, it, expect } from "vitest";
import { canonicalResolutionForChoice } from "../src/bot/utils/quickAnimeParser.js";
import { pickOptimalStream } from "../src/bot/services/animeStreamExtractor.js";
import { isNakanimeVfLabel, splitMirrorsByLanguage } from "../src/bot/commands/novabox.js";

describe("canonicalResolutionForChoice (quick mode rN semantics)", () => {
  it("maps r1..r4 to the canonical menu qualities", () => {
    expect(canonicalResolutionForChoice("r1")).toBe("480P");
    expect(canonicalResolutionForChoice("r2")).toBe("360P");
    expect(canonicalResolutionForChoice("r3")).toBe("720P");
    expect(canonicalResolutionForChoice("r4")).toBe("1080P");
  });
  it("clamps out-of-range indices instead of returning garbage", () => {
    expect(canonicalResolutionForChoice("r9")).toBe("1080P");
    expect(canonicalResolutionForChoice("r0")).toBe("480P");
  });
  it("accepts explicit and sloppy forms", () => {
    expect(canonicalResolutionForChoice("480p")).toBe("480P");
    expect(canonicalResolutionForChoice("720P")).toBe("720P");
    expect(canonicalResolutionForChoice("")).toBe("480P");
  });
});

describe("pickOptimalStream nearest-quality fallback", () => {
  const t = (resolution: string, url = "https://x/" + resolution) =>
    ({ resolution, url, type: "hls" as const });

  it("returns the exact match when present", () => {
    const tracks = [t("720P"), t("360P"), t("1080P")];
    expect(pickOptimalStream(tracks, "360P").resolution).toBe("360P");
  });

  it("prefers the tallest track not taller than the request", () => {
    const tracks = [t("360P"), t("480P"), t("720P")];
    expect(pickOptimalStream(tracks, "480P").resolution).toBe("480P");
    // 360P requested, only 360 absent: tallest <= 360 none -> smallest (720P)
    const only720 = [t("720P"), t("1080P")];
    expect(pickOptimalStream(only720, "360P").resolution).toBe("720P");
  });

  it("never silently upgrades to a bigger quality than requested when smaller exists", () => {
    const tracks = [t("480P"), t("1080P")];
    expect(pickOptimalStream(tracks, "360P").resolution).toBe("480P");
  });

  it("keeps the legacy 480>360>720 preference when nothing requested", () => {
    const tracks = [t("1080P"), t("360P"), t("720P")];
    expect(pickOptimalStream(tracks).resolution).toBe("360P");
  });
});

describe("isNakanimeVfLabel", () => {
  it("detects French dub labels and nothing else", () => {
    expect(isNakanimeVfLabel("VF")).toBe(true);
    expect(isNakanimeVfLabel("vf")).toBe(true);
    expect(isNakanimeVfLabel("VOSTFR")).toBe(false);
    expect(isNakanimeVfLabel("vostfr")).toBe(false);
    expect(isNakanimeVfLabel("VOST")).toBe(false);
    expect(isNakanimeVfLabel("")).toBe(false);
  });
});

describe("splitMirrorsByLanguage", () => {
  const eps = {
    1: ["https://vf.example/e1", "https://vf.example/e2"],
    2: ["https://vost.example/e1", "https://vost.example/e2"],
    3: ["https://vf2.example/e1", "https://vf2.example/e2"]
  };
  const labels = {
    1: { host: "P1", language: "VF" },
    2: { host: "P2", language: "VOSTFR" },
    3: { host: "P3", language: "VF" }
  };

  it("puts VF lists first when VF is requested (VF-by-default)", () => {
    const { primary, secondary } = splitMirrorsByLanguage(eps, labels, 0, "VF");
    expect(primary).toEqual(["https://vf.example/e1", "https://vf2.example/e1"]);
    expect(secondary).toEqual(["https://vost.example/e1"]);
  });

  it("puts VOSTFR lists first when VOSTFR is requested", () => {
    const { primary, secondary } = splitMirrorsByLanguage(eps, labels, 1, "VOSTFR");
    expect(primary).toEqual(["https://vost.example/e2"]);
    expect(secondary).toEqual(["https://vf.example/e2", "https://vf2.example/e2"]);
  });

  it("keeps every list primary when no labels exist (anime-sama path)", () => {
    const { primary, secondary } = splitMirrorsByLanguage(eps, undefined, 0, "VF");
    expect(primary.length).toBe(3);
    expect(secondary.length).toBe(0);
  });

  it("falls back to all mirrors when the requested language has no list", () => {
    const onlyVost = { 1: eps[2] };
    const onlyVostLabels = { 1: labels[2] };
    const { primary } = splitMirrorsByLanguage(onlyVost, onlyVostLabels, 0, "VF");
    expect(primary).toEqual(["https://vost.example/e1"]);
  });
});
