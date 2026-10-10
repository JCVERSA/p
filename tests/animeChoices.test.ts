/**
 * 8.97 — mémoire des choix interactifs anime.
 *
 * 1. Service pur (animeChoices.ts) : enregistrement, TTL, oubli, format du
 *    bloc injecté dans le prompt de l'agent.
 * 2. Wiring : novabox enregistre au point de convergence, l'agent et le
 *    chat DM injectent le bloc, `.ai forget` efface tout.
 */
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { mkdtempSync, rmSync } from "fs";
import { join } from "path";
import { tmpdir } from "os";
import { readFileSync } from "fs";
import {
  recordAnimeChoice,
  getAnimeChoice,
  getAnimeChoiceContext,
  forgetAnimeChoices,
  getChoicesTtlMs
} from "../src/bot/services/animeChoices.js";

let dir: string;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "anime-choices-"));
  process.env.NEBULA_DATA_DIR = dir;
  delete process.env.NEBULA_ANIME_CHOICES_TTL_HOURS;
});

afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
  delete process.env.NEBULA_DATA_DIR;
});

const REC = {
  title: "Tokyo Ghoul",
  source: "va" as const,
  language: "VOSTFR" as const,
  seasonName: "Saison 1",
  quality: "1080P",
  episodesSpec: "e5-7",
  lastEpisode: 7
};

describe("animeChoices — service (8.97)", () => {
  it("enregistre puis relit le dernier téléchargement du chat", () => {
    const now = Date.now();
    recordAnimeChoice("chat-1", REC, now);
    const rec = getAnimeChoice("chat-1", now + 1000)!;
    expect(rec.title).toBe("Tokyo Ghoul");
    expect(rec.language).toBe("VOSTFR");
    expect(rec.lastEpisode).toBe(7);
    expect(rec.ts).toBe(now);
  });

  it("un nouvel enregistrement REMPLACE le précédent (le dernier gagne)", () => {
    const now = Date.now();
    recordAnimeChoice("chat-1", REC, now);
    recordAnimeChoice("chat-1", { ...REC, title: "Naruto", lastEpisode: 3 }, now + 5000);
    expect(getAnimeChoice("chat-1", now + 6000)?.title).toBe("Naruto");
  });

  it("les chats sont indépendants", () => {
    const now = Date.now();
    recordAnimeChoice("chat-1", REC, now);
    expect(getAnimeChoice("chat-2", now)).toBeNull();
  });

  it("TTL expiré → null (défaut 7 jours, réglable)", () => {
    const now = Date.now();
    recordAnimeChoice("chat-1", REC, now);
    expect(getAnimeChoice("chat-1", now + getChoicesTtlMs() - 1)).not.toBeNull();
    expect(getAnimeChoice("chat-1", now + getChoicesTtlMs() + 1)).toBeNull();
  });

  it("NEBULA_ANIME_CHOICES_TTL_HOURS=0 désactive tout (record no-op)", () => {
    process.env.NEBULA_ANIME_CHOICES_TTL_HOURS = "0";
    recordAnimeChoice("chat-1", REC);
    expect(getAnimeChoice("chat-1")).toBeNull();
    expect(getAnimeChoiceContext("chat-1")).toBeNull();
  });

  it("titre plafonné et espaces normalisés (données catalogue, pas de texte libre)", () => {
    recordAnimeChoice("chat-1", { ...REC, title: "A".repeat(300) });
    expect(getAnimeChoice("chat-1")!.title.length).toBeLessThanOrEqual(120);
  });

  it("forgetAnimeChoices efface et signale", () => {
    recordAnimeChoice("chat-1", REC);
    expect(forgetAnimeChoices("chat-1")).toBe(true);
    expect(getAnimeChoice("chat-1")).toBeNull();
    expect(forgetAnimeChoices("chat-1")).toBe(false);
  });
});

describe("animeChoices — bloc injecté dans le prompt (8.97)", () => {
  it("format compact et factuel : titre, langue, saison, qualité, épisodes", () => {
    const now = Date.now();
    recordAnimeChoice("chat-1", REC, now);
    const block = getAnimeChoiceContext("chat-1", now + 3 * 60 * 60 * 1000)!;
    expect(block).toContain("[Historique anime — dernier téléchargement de ce chat, il y a 3 h]");
    expect(block).toContain("Titre : Tokyo Ghoul");
    expect(block).toContain("Langue : VOSTFR");
    expect(block).toContain("Saison : Saison 1");
    expect(block).toContain("Qualité : 1080P");
    expect(block).toContain("Épisodes : e5-7");
    expect(block).toContain("dernier épisode : 7");
    expect(block).toContain("Catalogue : va");
  });

  it("aucun bloc sans enregistrement (pas de bruit dans le prompt)", () => {
    expect(getAnimeChoiceContext("chat-inconnu")).toBeNull();
  });
});

describe("animeChoices — wiring (8.97)", () => {
  const read = (p: string) => readFileSync(join(__dirname, p), "utf-8");

  it("novabox enregistre au point de convergence (sendFinalEpisode)", () => {
    const src = read("../src/bot/commands/novabox.ts");
    expect(src).toContain("recordAnimeChoice(context.sender");
  });

  it("l'agent ET le chat DM injectent le bloc", () => {
    expect(read("../src/bot/services/agentRunner.ts")).toContain("getAnimeChoiceContext(info.senderJid)");
    expect(read("../src/bot/botEngine.ts")).toContain("getAnimeChoiceContext(senderJid)");
  });

  it("`.ai forget` efface aussi la mémoire des choix", () => {
    expect(read("../src/bot/commands/ai.ts")).toContain("forgetAnimeChoices(msg.key.remoteJid");
  });

  it("la fiche agent documente l'usage de l'historique", () => {
    const knowledge = read("../src/bot/commandKnowledge.ts");
    expect(knowledge).toContain("Historique anime");
    expect(knowledge).toContain("dernier épisode + 1");
  });
});
