package com.minemods.bettertwink.client.bot;

import java.util.Random;

/**
 * Human-like behaviour profiles for the sorting bot.
 *
 * <p>Each profile defines click and between-chest delay distributions modelled as
 * <strong>log-normal</strong> distributions with a mean (μ) and standard deviation (σ)
 * in milliseconds.  Log-normal is chosen because human reaction times follow this
 * distribution empirically (long-tail on the right, bounded below zero).
 *
 * <p>Profiles also govern micro-pauses (random short stalls) and the tiny probability
 * of a misclick-then-correction (see §4.2 of the design doc).
 */
public enum HumanProfile {
    //              clickMu  clickSig  chestMu  chestSig
    CASUAL          (210,    85,       420,     130),
    GRINDER         (115,    32,       210,     65),
    SPEEDRUNNER     ( 78,    22,       115,     38),
    AFK_LIKE        (460,    185,      920,     360),
    /** Fastest possible — for test environments or single-player. */
    INSTANT         ( 55,     6,        65,      12);

    public final double clickMu;
    public final double clickSig;
    public final double chestMu;
    public final double chestSig;

    private static final Random RNG = new Random();

    HumanProfile(double cmu, double csig, double bmu, double bsig) {
        this.clickMu  = cmu;  this.clickSig  = csig;
        this.chestMu  = bmu;  this.chestSig  = bsig;
    }

    // ── Delay samplers ─────────────────────────────────────────────────────

    /**
     * Sample the next inter-click delay in <strong>milliseconds</strong>.
     * Clamped to [50 ms, 2000 ms].
     */
    public long nextClickDelayMs() {
        return clamp(logNormal(clickMu, clickSig), 50, 2000);
    }

    /**
     * Sample the delay between opening two chests in <strong>milliseconds</strong>.
     * Clamped to [80 ms, 6000 ms].
     */
    public long nextChestDelayMs() {
        return clamp(logNormal(chestMu, chestSig), 80, 6000);
    }

    /**
     * Sample the next inter-click delay converted to <strong>ticks</strong> (50 ms/tick).
     * Returns at least 1.
     */
    public int nextClickDelayTicks() {
        return (int) Math.max(1, nextClickDelayMs() / 50);
    }

    /**
     * Sample the between-chests delay converted to <strong>ticks</strong>.
     * Returns at least 2.
     */
    public int nextChestDelayTicks() {
        return (int) Math.max(2, nextChestDelayMs() / 50);
    }

    /**
     * Returns {@code true} with profile-specific probability — simulates the human
     * tendency to glance around / pause briefly between actions.
     */
    public boolean shouldMicroPause() {
        return switch (this) {
            case AFK_LIKE    -> RNG.nextDouble() < 0.015;
            case CASUAL      -> RNG.nextDouble() < 0.004;
            case GRINDER     -> RNG.nextDouble() < 0.001;
            default          -> false;
        };
    }

    /**
     * Sample micro-pause duration in ticks (used when {@link #shouldMicroPause()} is true).
     * Range: 20–120 ticks (1–6 seconds).
     */
    public int microPauseTicks() {
        return switch (this) {
            case AFK_LIKE -> 60 + RNG.nextInt(600);   // 3–33 s
            case CASUAL   -> 20 + RNG.nextInt(80);    // 1–5 s
            default       -> 20 + RNG.nextInt(40);    // 1–3 s
        };
    }

    /**
     * Returns {@code true} approximately 1/200 times — simulates a misclick
     * (only in CASUAL and AFK_LIKE profiles; an immediate correction follows).
     */
    public boolean shouldMisclick() {
        return (this == CASUAL || this == AFK_LIKE) && RNG.nextInt(200) == 0;
    }

    // ── Log-normal helper ──────────────────────────────────────────────────

    /**
     * Samples from a log-normal distribution parameterised by the <em>mean</em> (μ)
     * and <em>standard deviation</em> (σ) of the resulting distribution.
     *
     * <p>Conversion: ln-mean  = ln(μ / √(1 + (σ/μ)²)), ln-sigma = √(ln(1 + (σ/μ)²)).
     */
    private static long logNormal(double mu, double sigma) {
        double cv    = sigma / mu;
        double lnMu  = Math.log(mu / Math.sqrt(1.0 + cv * cv));
        double lnSig = Math.sqrt(Math.log(1.0 + cv * cv));
        double z     = RNG.nextGaussian();
        return Math.round(Math.exp(lnMu + lnSig * z));
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Parse from config string, defaults to CASUAL on parse failure. */
    public static HumanProfile fromString(String s) {
        if (s == null) return CASUAL;
        try { return valueOf(s.toUpperCase()); } catch (Exception e) { return CASUAL; }
    }
}
