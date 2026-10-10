import fs from "fs";
import path from "path";

/**
 * 8.97 — mémoire des choix interactifs anime (owner request 2026-10-10).
 *
 * Le bot se souvient du DERNIER téléchargement lancé dans chaque chat
 * (anime, catalogue, langue, saison, qualité, épisodes) — juste assez pour
 * que l'agent résolve « le même anime », « l'épisode suivant », « le même
 * mais en 720p » en commande exacte, sans rien deviner.
 *
 * Décisions :
 *  - UN enregistrement par chat (le dernier) : « le même qu'hier » désigne
 *    le dernier téléchargement, pas un historique complet ;
 *  - TTL fixe depuis l'enregistrement — défaut 7 jours,
 *    NEBULA_ANIME_CHOICES_TTL_HOURS ("0" = désactivé) ;
 *  - JSON à écriture atomique comme les autres stores, plafond de chats ;
 *  - effacé par `.ai forget` (avec la mémoire de conversation) ;
 *  - le titre vient du catalogue (pas du texte libre utilisateur) et est
 *    plafonné ; aucun autre champ libre.
 */

export interface AnimeChoiceRecord {
  title: string;
  source: "va" | "as";
  language: "VF" | "VOSTFR";
  seasonName: string;
  quality: string;
  episodesSpec: string;
  lastEpisode: number | null;
  ts: number;
}

type ChoiceStore = Record<string, AnimeChoiceRecord>;

const DEFAULT_TTL_HOURS = 168; // 7 jours
const MAX_TITLE_CHARS = 120;
const MAX_FIELD_CHARS = 60;
const MAX_STORED_CHATS = 500;

function dataDir(): string {
  return process.env.NEBULA_DATA_DIR || path.join(process.cwd(), "database");
}

function storePath(): string {
  return path.join(dataDir(), "anime_choices.json");
}

export function getChoicesTtlMs(): number {
  const raw = Number(process.env.NEBULA_ANIME_CHOICES_TTL_HOURS);
  const hours = Number.isFinite(raw) && raw >= 0 ? raw : DEFAULT_TTL_HOURS;
  return hours * 60 * 60 * 1000;
}

function isDisabled(): boolean {
  return getChoicesTtlMs() === 0;
}

function loadStore(): ChoiceStore {
  try {
    const raw = fs.readFileSync(storePath(), "utf-8");
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? (parsed as ChoiceStore) : {};
  } catch {
    return {};
  }
}

function saveStore(store: ChoiceStore): void {
  try {
    fs.mkdirSync(dataDir(), { recursive: true });
    const tmp = `${storePath()}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(store), "utf-8");
    fs.renameSync(tmp, storePath());
  } catch {
    // best-effort : la mémoire est un confort, jamais un blocker.
  }
}

function cap(value: string | undefined | null, max = MAX_FIELD_CHARS): string {
  const v = (value || "").replace(/\s+/g, " ").trim();
  return v.length > max ? v.slice(0, max - 1) + "…" : v;
}

/** Enregistre le DERNIER téléchargement du chat (remplace le précédent). */
export function recordAnimeChoice(
  chatJid: string,
  rec: {
    title: string;
    source: "va" | "as";
    language: "VF" | "VOSTFR";
    seasonName?: string;
    quality?: string;
    episodesSpec?: string;
    lastEpisode?: number | null;
  },
  now = Date.now()
): void {
  if (!chatJid || !rec?.title || isDisabled()) return;
  const store = loadStore();
  store[chatJid] = {
    title: cap(rec.title, MAX_TITLE_CHARS),
    source: rec.source === "as" ? "as" : "va",
    language: rec.language === "VF" ? "VF" : "VOSTFR",
    seasonName: cap(rec.seasonName),
    quality: cap(rec.quality),
    episodesSpec: cap(rec.episodesSpec),
    lastEpisode:
      typeof rec.lastEpisode === "number" && Number.isFinite(rec.lastEpisode) && rec.lastEpisode > 0
        ? Math.floor(rec.lastEpisode)
        : null,
    ts: now
  };
  // Plafond : éviction des plus anciens (lazy, sans scan externe).
  const keys = Object.keys(store);
  if (keys.length > MAX_STORED_CHATS) {
    keys
      .sort((a, b) => store[a].ts - store[b].ts)
      .slice(0, keys.length - MAX_STORED_CHATS)
      .forEach((k) => delete store[k]);
  }
  saveStore(store);
}

/** Dernier téléchargement du chat, ou null (absent / expiré / désactivé). */
export function getAnimeChoice(chatJid: string, now = Date.now()): AnimeChoiceRecord | null {
  if (!chatJid || isDisabled()) return null;
  const rec = loadStore()[chatJid];
  if (!rec || typeof rec.ts !== "number") return null;
  if (now - rec.ts > getChoicesTtlMs()) return null;
  return rec;
}

function relativeFr(ms: number): string {
  if (ms < 60_000) return "à l'instant";
  const min = Math.floor(ms / 60_000);
  if (min < 60) return `il y a ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `il y a ${h} h`;
  return `il y a ${Math.floor(h / 24)} j`;
}

/**
 * Bloc injecté dans le prompt système de l'agent (et du chat DM) : factuel,
 * compact, en français — les règles d'USAGE vivent dans la fiche agent
 * (commandKnowledge), pas ici.
 */
export function getAnimeChoiceContext(chatJid: string, now = Date.now()): string | null {
  const rec = getAnimeChoice(chatJid, now);
  if (!rec) return null;
  const parts = [
    `Titre : ${rec.title}`,
    `Catalogue : ${rec.source}`,
    `Langue : ${rec.language}`,
    rec.seasonName ? `Saison : ${rec.seasonName}` : "",
    rec.quality ? `Qualité : ${rec.quality}` : "",
    rec.episodesSpec ? `Épisodes : ${rec.episodesSpec}` : "",
    rec.lastEpisode ? `dernier épisode : ${rec.lastEpisode}` : ""
  ].filter(Boolean);
  return (
    `[Historique anime — dernier téléchargement de ce chat, ${relativeFr(now - rec.ts)}]\n` +
    parts.join(" · ")
  );
}

/** `.ai forget` efface aussi cette mémoire. Retourne true si quelque chose existait. */
export function forgetAnimeChoices(chatJid: string): boolean {
  if (!chatJid) return false;
  const store = loadStore();
  if (!(chatJid in store)) return false;
  delete store[chatJid];
  saveStore(store);
  return true;
}
