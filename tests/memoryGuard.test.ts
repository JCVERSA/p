import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import fs from "fs";
import {
  RSS_CRITICAL_MB,
  RSS_PAUSE_THRESHOLD_MB,
  enforceMemoryHeadroom,
  memoryPressure,
  rssMb
} from "../src/bot/services/memoryGuard.js";

/**
 * Audit 8.50 — the bot was OOM-killed (kernel "Killed", no stack) mid-batch
 * inside its ~954 MB cgroup even though --max-old-space-size=384 was set:
 * that flag caps the V8 heap only, while external Buffers + glibc arena
 * fragmentation kept growing the RSS. These tests pin the guard contract:
 * classification thresholds, bounded back-off, and the actual wiring in the
 * batch worker + manage.sh startup env.
 */

const MB = 1048576;

const fakeRss = (mb: number) => ({ rss: mb * MB }) as NodeJS.MemoryUsage;

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  delete (globalThis as { gc?: unknown }).gc;
});

describe("memoryPressure (pure classification)", () => {
  it("classifies ok below the pause threshold", () => {
    expect(memoryPressure(0)).toBe("ok");
    expect(memoryPressure(RSS_PAUSE_THRESHOLD_MB - 1)).toBe("ok");
  });

  it("classifies high from the pause threshold up to critical", () => {
    expect(memoryPressure(RSS_PAUSE_THRESHOLD_MB)).toBe("high");
    expect(memoryPressure(RSS_CRITICAL_MB - 1)).toBe("high");
  });

  it("classifies critical at and beyond the critical threshold", () => {
    expect(memoryPressure(RSS_CRITICAL_MB)).toBe("critical");
    expect(memoryPressure(5000)).toBe("critical");
  });

  it("keeps thresholds below the ~954 MB cgroup ceiling with headroom", () => {
    // Pause must trigger early enough that two pauses still fit the budget;
    // critical must leave the kernel room before the hard kill at ~954 MB.
    expect(RSS_PAUSE_THRESHOLD_MB).toBeLessThan(750);
    expect(RSS_CRITICAL_MB).toBeGreaterThan(RSS_PAUSE_THRESHOLD_MB);
    expect(RSS_CRITICAL_MB).toBeLessThan(900);
  });
});

describe("rssMb", () => {
  it("reports the live RSS as a positive integer in MB", () => {
    const value = rssMb();
    expect(Number.isInteger(value)).toBe(true);
    expect(value).toBeGreaterThan(20); // any running Node process
  });
});

describe("enforceMemoryHeadroom", () => {
  it("returns ok immediately without pausing when pressure is low", async () => {
    const memSpy = vi.spyOn(process, "memoryUsage").mockReturnValue(fakeRss(300));
    const logSpy = vi.spyOn(console, "log").mockImplementation(() => {});
    const gc = vi.fn();
    (globalThis as { gc?: unknown }).gc = gc;

    const result = await enforceMemoryHeadroom("unit-ok");

    expect(result).toBe("ok");
    expect(gc).not.toHaveBeenCalled();
    expect(memSpy).toHaveBeenCalled();
    // Signature log: [MEM] label: rss=X MB — level
    expect(logSpy).toHaveBeenCalledWith(expect.stringMatching(/\[MEM\] unit-ok: rss=300 MB — ok$/));
  });

  it("pauses, forces GC and reports ok once RSS drops back", async () => {
    const gc = vi.fn();
    (globalThis as { gc?: unknown }).gc = gc;
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    // High on entry, healthy again after the first 5 s pause.
    vi.spyOn(process, "memoryUsage")
      .mockReturnValueOnce(fakeRss(750))
      .mockReturnValueOnce(fakeRss(400));

    const pending = enforceMemoryHeadroom("unit-recover");
    await vi.advanceTimersByTimeAsync(5100);
    const result = await pending;

    expect(result).toBe("ok");
    expect(gc).toHaveBeenCalledTimes(1); // single recovery attempt
    expect(warnSpy).toHaveBeenCalledWith(
      expect.stringContaining("rss=750 MB — high, forcing GC and pausing")
    );
  });

  it("keeps pausing but stays bounded (max 2 attempts) while pressure persists", async () => {
    const gc = vi.fn();
    (globalThis as { gc?: unknown }).gc = gc;
    vi.spyOn(console, "warn").mockImplementation(() => {});
    vi.spyOn(process, "memoryUsage").mockReturnValue(fakeRss(900));

    const pending = enforceMemoryHeadroom("unit-stuck");
    await vi.advanceTimersByTimeAsync(30_000); // far beyond the guard's budget
    const result = await pending;

    expect(result).toBe("critical");
    expect(gc).toHaveBeenCalledTimes(2); // bounded — no infinite retry loop
  });
});

describe("8.50 wiring (the 8.49b lesson: green locally ≠ wired in production)", () => {
  it("the novabox batch worker calls enforceMemoryHeadroom between episodes", () => {
    const source = fs.readFileSync("src/bot/commands/novabox.ts", "utf8");
    expect(source).toContain('from "../services/memoryGuard.js"');
    // Called inside the worker loop, after each episode completes.
    const workerLoop = source.slice(
      source.indexOf("const worker = async () => {"),
      source.indexOf("const worker = async () => {") + 1200
    );
    expect(workerLoop).toContain("await enforceMemoryHeadroom(");
  });

  it("manage.sh caps glibc arenas at startup and ships a watchdog command", () => {
    const script = fs.readFileSync("manage.sh", "utf8");
    expect(script).toContain('MALLOC_ARENA_MAX="${NEBULA_MALLOC_ARENA_MAX:-2}"');
    expect(script).toContain("cmd_watchdog() {");
    // Registered in the dispatch case + documented in help.
    expect(script).toMatch(/\bwatchdog\) cmd_watchdog "\$@" ;;/);
    expect(script).toContain("watchdog${C_RESET}");
  });
});
