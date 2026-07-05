package de.htw.f1analytics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class F1alyticsScore {

    private F1alyticsScore() {
    }

    public record ScoreCard(
            double score,
            double q,
            double r,
            double delta,
            double base,
            double modifiers,
            boolean dnf,
            String note
    ) {
    }

    public record Entry(
            int driverNumber,
            String team,
            int finish,
            Integer grid,
            Double qualiLap,
            boolean dnf,
            boolean dns,
            boolean dsq
    ) {
    }

    private static final double W_Q = 0.15;
    private static final double W_R = 0.20;
    private static final double W_D = 0.20;

    private static final double MOD_POLE = 0.3;
    private static final double MOD_WIN = 1.0;
    private static final double MOD_PODIUM = 0.3;

    private static final double CAP_DNF = 7.5;

    public static Map<Integer, ScoreCard> scoreRace(List<Entry> entries) {
        Map<Integer, ScoreCard> out = new HashMap<>();
        if (entries == null || entries.isEmpty()) {
            return out;
        }

        int nStarters = (int) entries.stream().filter(e -> e.grid() != null).count();
        if (nStarters < 2) {
            nStarters = entries.size();
        }
        int nClassified = entries.size();

        for (Entry e : entries) {
            if (e.dns()) {
                continue;
            }

            int grid = e.grid() != null ? e.grid() : nStarters;
            int finish = e.finish();

            double r = score10(nClassified, finish);

            double posGain = grid - finish;
            double delta = clamp(5.0 + 0.6 * posGain, 0, 10);

            double gridScore = score10(nStarters, grid);
            Double tmLap = bestTeammateLap(entries, e);
            double q;
            if (e.qualiLap() != null && tmLap != null && tmLap > 0) {
                double tmScore = clamp(5.0 + 300.0 * (tmLap - e.qualiLap()) / tmLap, 0, 10);
                q = 0.6 * gridScore + 0.4 * tmScore;
            } else {
                q = gridScore;
            }

            double wSum = W_Q + W_R + W_D;
            double base = (W_Q * q + W_R * r + W_D * delta) / wSum;

            double mods = 0;
            StringBuilder note = new StringBuilder();
            if (grid == 1) {
                mods += MOD_POLE;
                note.append("Pole +0,3. ");
            }
            boolean dnf = e.dnf() || e.dsq();
            if (!dnf) {
                if (finish == 1) {
                    mods += MOD_WIN;
                    note.append("Sieg +1,0. ");
                } else if (finish <= 3) {
                    mods += MOD_PODIUM;
                    note.append("Podium +0,3. ");
                }
            }

            double score = base + mods;
            if (dnf) {
                score = Math.min(score, CAP_DNF);
                note.append(e.dsq() ? "DSQ — gedeckelt. " : "DNF — gedeckelt. ");
            }
            score = clamp(score, 1, 10);

            out.put(e.driverNumber(), new ScoreCard(
                    round1(score), round1(q), round1(r), round1(delta),
                    round1(base), round1(mods), dnf, note.toString().trim()));
        }
        return out;
    }

    private static double score10(int n, int pos) {
        if (n <= 1) {
            return 5.0;
        }
        double t = (double) (n - pos) / (n - 1);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return clamp(10.0 * t * t, 0, 10);
    }

    private static Double bestTeammateLap(List<Entry> entries, Entry self) {
        Double best = null;
        for (Entry o : entries) {
            if (o.driverNumber() == self.driverNumber()) {
                continue;
            }
            if (o.team() == null || !o.team().equals(self.team())) {
                continue;
            }
            if (o.qualiLap() == null) {
                continue;
            }
            if (best == null || o.qualiLap() < best) {
                best = o.qualiLap();
            }
        }
        return best;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}