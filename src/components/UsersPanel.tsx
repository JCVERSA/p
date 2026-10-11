import { useCallback, useEffect, useRef, useState } from "react";
import {
  Users,
  RefreshCw,
  Search,
  Ban,
  ShieldCheck,
  RotateCcw,
  MessageSquare,
  UserPlus,
  Activity,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useBotUrl } from "../lib/botContext";

/**
 * 9.16/9.17 — Gestionnaire d'utilisateurs (décision owner : niveau « complet »).
 *
 * Répertoire construit sur les interactions réelles. Actions : bannir/
 * réhabiliter (un banni est ignoré par tout le moteur — l'owner ne peut
 * jamais l'être : badge 👑 + double garde), reset quotas IA/web/vocal du
 * jour, DM rapide.
 *
 * 9.17 (audit J-3) : recherche + segment + pagination CÔTÉ SERVEUR —
 * le polling ne transporte plus que la page affichée.
 */

interface KnownUser {
  jid: string;
  name: string;
  firstSeen: number;
  lastSeen: number;
  messageCount: number;
  commandCount: number;
  lastCommand: string | null;
  banned: boolean;
  isOwner?: boolean;
}

interface KnownGroup {
  jid: string;
  name: string;
  firstSeen: number;
  lastSeen: number;
  messageCount: number;
}

type Segment = "all" | "active7d" | "new30d" | "banned";

const PAGE_SIZE = 200;

function relTime(ts: number): string {
  const s = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (s < 60) return "à l'instant";
  if (s < 3600) return `il y a ${Math.floor(s / 60)} min`;
  if (s < 86400) return `il y a ${Math.floor(s / 3600)} h`;
  return `il y a ${Math.floor(s / 86400)} j`;
}

export default function UsersPanel() {
  const botUrl = useBotUrl();
  const [users, setUsers] = useState<KnownUser[]>([]);
  const [groups, setGroups] = useState<KnownGroup[]>([]);
  const [counts, setCounts] = useState({ all: 0, active7d: 0, new30d: 0, banned: 0 });
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [segment, setSegment] = useState<Segment>("all");
  const [busyJid, setBusyJid] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [confirmBan, setConfirmBan] = useState<string | null>(null);
  const [dmFor, setDmFor] = useState<string | null>(null);
  const [dmText, setDmText] = useState("");
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(
    async (opts: { q?: string; segment?: Segment; offset?: number } = {}) => {
      const q = opts.q ?? search;
      const seg = opts.segment ?? segment;
      const off = opts.offset ?? 0;
      const params = new URLSearchParams({ segment: seg, limit: String(PAGE_SIZE), offset: String(off) });
      if (q.trim()) params.set("q", q.trim());
      try {
        const res = await fetch(botUrl(`/api/bot/users?${params.toString()}`), { credentials: "same-origin" });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        setUsers(Array.isArray(data.users) ? data.users : []);
        setGroups(Array.isArray(data.groups) ? data.groups : []);
        setCounts(data.counts || { all: 0, active7d: 0, new30d: 0, banned: 0 });
        setTotal(typeof data.total === "number" ? data.total : 0);
        setError(null);
      } catch (e: any) {
        setError(e?.message || "impossible de charger le répertoire");
      } finally {
        setLoading(false);
      }
    },
    [botUrl, search, segment]
  );

  useEffect(() => {
    void load();
    const t = setInterval(() => void load({ offset }), 15_000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load, offset]);

  // Recherche : debouncée côté serveur (audit J-3)
  const onSearch = (value: string) => {
    setSearch(value);
    setOffset(0);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => void load({ q: value, offset: 0 }), 300);
  };

  const onSegment = (seg: Segment) => {
    setSegment(seg);
    setOffset(0);
    void load({ segment: seg, offset: 0 });
  };

  const act = useCallback(
    async (jid: string, action: string, text?: string) => {
      setBusyJid(jid);
      setNotice(null);
      try {
        const res = await fetch(botUrl("/api/bot/users/action"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "same-origin",
          body: JSON.stringify({ jid, action, text }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data?.error || `HTTP ${res.status}`);
        if (action === "ban") setNotice(`🚫 Utilisateur banni — il est ignoré par tout le bot.`);
        if (action === "unban") setNotice(`✅ Utilisateur réhabilité.`);
        if (action === "reset-quota") setNotice(`♻️ Quotas IA + web + vocal remis à zéro pour aujourd'hui.`);
        if (action === "dm") {
          setNotice(`📨 DM envoyé.`);
          setDmFor(null);
          setDmText("");
        }
        await load({ offset });
      } catch (e: any) {
        setNotice(`❌ ${e?.message || "action impossible"}`);
      } finally {
        setBusyJid(null);
        setConfirmBan(null);
      }
    },
    [botUrl, load, offset]
  );

  const hasPrev = offset > 0;
  const hasNext = offset + PAGE_SIZE < total;

  return (
    <div className="space-y-6">
      {/* En-tête statistiques */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-[#0b0b0c] border border-white/10 rounded-2xl p-5">
          <div className="flex items-center gap-2 text-zinc-400 text-xs font-bold uppercase tracking-wider">
            <Users className="w-4 h-4 text-amber-400" /> Utilisateurs connus
          </div>
          <div className="text-3xl font-extrabold text-white mt-2 tabular-nums">{counts.all}</div>
          <p className="text-[11px] text-zinc-500 mt-1">Chaque personne qui a écrit au bot au moins une fois.</p>
        </div>
        <div className="bg-[#0b0b0c] border border-white/10 rounded-2xl p-5">
          <div className="flex items-center gap-2 text-zinc-400 text-xs font-bold uppercase tracking-wider">
            <Activity className="w-4 h-4 text-emerald-400" /> Actifs (7 j)
          </div>
          <div className="text-3xl font-extrabold text-white mt-2 tabular-nums">{counts.active7d}</div>
          <p className="text-[11px] text-zinc-500 mt-1">{groups.length} groupe(s) connu(s) — visibles dans Annonces.</p>
        </div>
        <div className="bg-[#0b0b0c] border border-white/10 rounded-2xl p-5">
          <div className="flex items-center gap-2 text-zinc-400 text-xs font-bold uppercase tracking-wider">
            <Ban className="w-4 h-4 text-rose-400" /> Bannis
          </div>
          <div className="text-3xl font-extrabold text-white mt-2 tabular-nums">{counts.banned}</div>
          <p className="text-[11px] text-zinc-500 mt-1">Un banni est ignoré par tout le moteur.</p>
        </div>
      </div>

      {/* Barre de filtres */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center gap-3 bg-[#0b0b0c] border border-white/10 rounded-2xl p-4">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={(e) => onSearch(e.target.value)}
            placeholder="Rechercher un numéro ou un nom…"
            className="w-full pl-9 pr-3 py-2 bg-black border border-white/10 rounded-xl text-xs text-white placeholder-zinc-600 focus:outline-none focus:border-amber-400"
          />
        </div>
        <div className="flex gap-1.5 flex-wrap">
          {([
            ["all", "Tous"],
            ["active7d", "Actifs 7 j"],
            ["new30d", "Nouveaux 30 j"],
            ["banned", "Bannis"],
          ] as Array<[Segment, string]>).map(([id, label]) => (
            <button
              key={id}
              onClick={() => onSegment(id)}
              className={`px-3 py-2 rounded-xl text-xs font-bold transition cursor-pointer ${
                segment === id
                  ? "bg-amber-500 text-black"
                  : "bg-black border border-white/10 text-zinc-400 hover:text-white"
              }`}
            >
              {label}
            </button>
          ))}
          <button
            onClick={() => void load({ offset })}
            className="px-3 py-2 rounded-xl text-xs font-bold bg-black border border-white/10 text-zinc-400 hover:text-white transition cursor-pointer flex items-center gap-1.5"
            title="Rafraîchir"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3 bg-rose-950/40 border border-rose-900/60 rounded-xl text-xs text-rose-300 font-medium">
          ❌ {error} — le moteur doit être connecté pour remplir le répertoire.
        </div>
      )}
      {notice && (
        <div className="p-3 bg-amber-950/30 border border-amber-800/50 rounded-xl text-xs text-amber-300 font-medium">
          {notice}
        </div>
      )}

      {/* Tableau */}
      <div className="bg-[#0b0b0c] border border-white/10 rounded-2xl overflow-hidden">
        {loading && !users.length ? (
          <div className="p-8 text-center text-xs text-zinc-500">Chargement du répertoire…</div>
        ) : !users.length ? (
          <div className="p-8 text-center text-xs text-zinc-500">
            {counts.all
              ? "Aucun utilisateur ne correspond à ce filtre."
              : "Personne n'a encore écrit au bot — le répertoire se remplit dès le premier message."}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-zinc-500 border-b border-white/10 text-left">
                  <th className="px-4 py-3 font-bold">Utilisateur</th>
                  <th className="px-4 py-3 font-bold">Numéro</th>
                  <th className="px-4 py-3 font-bold">Dernière activité</th>
                  <th className="px-4 py-3 font-bold text-right">Messages</th>
                  <th className="px-4 py-3 font-bold text-right">Commandes</th>
                  <th className="px-4 py-3 font-bold">Dernière cmd</th>
                  <th className="px-4 py-3 font-bold text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.jid} className={`border-b border-white/5 hover:bg-white/[0.02] ${u.banned ? "opacity-60" : ""}`}>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-white truncate max-w-[160px]">{u.name || "Sans nom"}</span>
                        {u.isOwner && (
                          <span className="px-1.5 py-0.5 rounded bg-amber-500/15 border border-amber-500/40 text-amber-300 text-[10px] font-bold" title="Le propriétaire ne peut pas être banni">
                            👑 OWNER
                          </span>
                        )}
                        {u.banned && (
                          <span className="px-1.5 py-0.5 rounded bg-rose-950/60 border border-rose-900/60 text-rose-300 text-[10px] font-bold">
                            BANNI
                          </span>
                        )}
                      </div>
                      <span className="text-[10px] text-zinc-500">1er contact {relTime(u.firstSeen)}</span>
                    </td>
                    <td className="px-4 py-3 font-mono text-zinc-300">+{u.jid.split("@")[0]}</td>
                    <td className="px-4 py-3 text-zinc-400">{relTime(u.lastSeen)}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-zinc-300">{u.messageCount}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-zinc-300">{u.commandCount}</td>
                    <td className="px-4 py-3 font-mono text-zinc-500">{u.lastCommand ? `.${u.lastCommand}` : "—"}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => {
                            setDmFor(dmFor === u.jid ? null : u.jid);
                            setDmText("");
                          }}
                          disabled={busyJid === u.jid}
                          title="Envoyer un DM"
                          className="p-2 rounded-lg bg-black border border-white/10 text-zinc-400 hover:text-amber-400 hover:border-amber-500/40 transition cursor-pointer disabled:opacity-40"
                        >
                          <MessageSquare className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => void act(u.jid, "reset-quota")}
                          disabled={busyJid === u.jid}
                          title="Réinitialiser les quotas du jour (IA, web, vocal)"
                          className="p-2 rounded-lg bg-black border border-white/10 text-zinc-400 hover:text-amber-400 hover:border-amber-500/40 transition cursor-pointer disabled:opacity-40"
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                        </button>
                        {u.isOwner ? (
                          <button
                            disabled
                            title="Le propriétaire ne peut pas être banni"
                            className="p-2 rounded-lg bg-black border border-white/5 text-zinc-600 cursor-not-allowed"
                          >
                            <Ban className="w-3.5 h-3.5" />
                          </button>
                        ) : u.banned ? (
                          <button
                            onClick={() => void act(u.jid, "unban")}
                            disabled={busyJid === u.jid}
                            title="Réhabiliter"
                            className="px-2.5 py-2 rounded-lg bg-emerald-950/50 border border-emerald-800/60 text-emerald-400 text-[11px] font-bold hover:bg-emerald-900/50 transition cursor-pointer disabled:opacity-40 flex items-center gap-1.5"
                          >
                            <ShieldCheck className="w-3.5 h-3.5" /> Réhabiliter
                          </button>
                        ) : confirmBan === u.jid ? (
                          <button
                            onClick={() => void act(u.jid, "ban")}
                            disabled={busyJid === u.jid}
                            className="px-2.5 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-[11px] font-bold transition cursor-pointer disabled:opacity-40"
                          >
                            Confirmer le ban ?
                          </button>
                        ) : (
                          <button
                            onClick={() => setConfirmBan(u.jid)}
                            disabled={busyJid === u.jid}
                            title="Bannir (ignoré par tout le bot)"
                            className="p-2 rounded-lg bg-black border border-white/10 text-zinc-400 hover:text-rose-400 hover:border-rose-500/40 transition cursor-pointer disabled:opacity-40"
                          >
                            <Ban className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                      {dmFor === u.jid && (
                        <div className="mt-2 flex gap-2 justify-end">
                          <input
                            type="text"
                            value={dmText}
                            onChange={(e) => setDmText(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === "Enter" && dmText.trim()) void act(u.jid, "dm", dmText);
                            }}
                            placeholder="Ton message…"
                            autoFocus
                            className="flex-1 min-w-[180px] px-3 py-1.5 bg-black border border-white/10 rounded-lg text-xs text-white placeholder-zinc-600 focus:outline-none focus:border-amber-400"
                          />
                          <button
                            onClick={() => void act(u.jid, "dm", dmText)}
                            disabled={!dmText.trim() || busyJid === u.jid}
                            className="px-3 py-1.5 bg-amber-500 hover:bg-amber-400 disabled:bg-zinc-800 disabled:text-zinc-600 text-black font-bold text-xs rounded-lg transition cursor-pointer"
                          >
                            Envoyer
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="px-4 py-2.5 text-[11px] text-zinc-500 border-t border-white/10 flex items-center justify-between gap-2">
              <span>
                {users.length} affiché(s) sur {total}
                {segment === "banned" ? " (bannis)" : ""}
              </span>
              {(hasPrev || hasNext) && (
                <span className="flex items-center gap-1.5">
                  <button
                    disabled={!hasPrev}
                    onClick={() => {
                      const off = Math.max(0, offset - PAGE_SIZE);
                      setOffset(off);
                      void load({ offset: off });
                    }}
                    className="p-1.5 rounded-lg bg-black border border-white/10 text-zinc-400 hover:text-white disabled:opacity-30 transition cursor-pointer"
                    title="Page précédente"
                  >
                    <ChevronLeft className="w-3.5 h-3.5" />
                  </button>
                  <span className="tabular-nums">
                    {Math.floor(offset / PAGE_SIZE) + 1}/{Math.max(1, Math.ceil(total / PAGE_SIZE))}
                  </span>
                  <button
                    disabled={!hasNext}
                    onClick={() => {
                      const off = offset + PAGE_SIZE;
                      setOffset(off);
                      void load({ offset: off });
                    }}
                    className="p-1.5 rounded-lg bg-black border border-white/10 text-zinc-400 hover:text-white disabled:opacity-30 transition cursor-pointer"
                    title="Page suivante"
                  >
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </span>
              )}
            </div>
          </div>
        )}
      </div>

      <p className="text-[11px] text-zinc-500 flex items-center gap-1.5">
        <UserPlus className="w-3.5 h-3.5" />
        Le répertoire ne liste que les personnes qui ont déjà interagi avec le bot — impossible d'énumérer tous tes contacts WhatsApp.
      </p>
    </div>
  );
}
