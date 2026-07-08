package de.htw.f1analytics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Berechnet den proprietären F1alytics-Score (1–10) für jeden Fahrer in einem Rennen.
 *
 * Der Score setzt sich aus drei Komponenten zusammen:
 *   Q (Qualifying) – Startplatzbewertung, ggf. kombiniert mit Teamkollegen-Vergleich
 *   R (Race)       – Finale Positionsbewertung
 *   Δ (Delta)      – Positionsveränderung Start → Ziel
 *
 * Alle Komponenten werden mit score10() auf 0–10 normiert (quadratische Kurve: Top-Fahrer
 * werden überproportional belohnt). Das Ergebnis ist ein gewichtetes Mittel + Modifier.
 *
 * Modifier:
 *   +0,3 Pole Position
 *   +1,0 Rennsieg
 *   +0,3 Podium (P2/P3)
 *   Score gedeckelt auf 7,5 bei DNF/DSQ
 *
 * Finale Normierung: clamp(score, 1.0, 10.0)
 */
public final class F1alyticsScore {

    private F1alyticsScore() {
    }

    /**
     * Bewertungs-Ergebnis für einen Fahrer.
     * score    = Gesamtscore (1–10)
     * q/r/delta = Einzelkomponenten (0–10)
     * base     = gewichtetes Mittel vor Modifiern
     * modifiers = Summe der Modifier (Pole/Sieg/Podium)
     * dnf      = true wenn DNF oder DSQ
     * note     = menschenlesbare Erklärung der Modifier
     */
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

    /**
     * Eingabedaten für einen einzelnen Fahrer.
     * grid und qualiLap sind optional (können bei fehlender Startaufstellung null sein).
     */
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

    // Gewichte der drei Score-Komponenten (werden auf wSum normiert, sodass Summe = 1)
    private static final double W_Q = 0.15;
    private static final double W_R = 0.20;
    private static final double W_D = 0.20;

    // Score-Modifier für besondere Leistungen
    private static final double MOD_POLE   = 0.3;
    private static final double MOD_WIN    = 1.0;
    private static final double MOD_PODIUM = 0.3;

    /** Maximaler Score bei DNF/DSQ – schlechte Ergebnisse können Modifiergewinne nicht kompensieren. */
    private static final double CAP_DNF = 7.5;

    /**
     * Berechnet den F1alytics-Score für alle Fahrer eines Rennens.
     * DNS-Fahrer werden übersprungen (sind nicht gestartet und haben kein Ergebnis).
     *
     * @param entries Liste der Renn-Einträge (alle Fahrer außer DNS optional mit Grid/QualiLap)
     * @return Map von Fahrernummer → ScoreCard
     */
    public static Map<Integer, ScoreCard> scoreRace(List<Entry> entries) {
        Map<Integer, ScoreCard> out = new HashMap<>();
        if (entries == null || entries.isEmpty()) {
            return out;
        }

        // nStarters = Anzahl Fahrer mit bekanntem Startplatz (Basis für Q-Normierung)
        int nStarters = (int) entries.stream().filter(e -> e.grid() != null).count();
        if (nStarters < 2) {
            nStarters = entries.size();
        }
        int nClassified = entries.size();

        for (Entry e : entries) {
            if (e.dns()) {
                continue;
            }

            // Startplatz-Fallback: wenn kein Grid bekannt, letzter Platz angenommen
            int grid   = e.grid() != null ? e.grid() : nStarters;
            int finish = e.finish();

            // R-Komponente: Renn-Score (quadratische Normierung auf Feldgröße)
            double r = score10(nClassified, finish);

            // Δ-Komponente: Positionsveränderung Start → Ziel
            // 0,6 Punkte pro gewonnenem Platz, Basis 5,0 (keine Veränderung = Mitte)
            double posGain = grid - finish;
            double delta = clamp(5.0 + 0.6 * posGain, 0, 10);

            // Q-Komponente: Startplatz-Score mit optionalem Teamkollegen-Vergleich
            double gridScore = score10(nStarters, grid);
            Double tmLap = bestTeammateLap(entries, e);
            double q;
            if (e.qualiLap() != null && tmLap != null && tmLap > 0) {
                // Wenn Quali-Rundenzeiten bekannt: 60% Startplatz + 40% Zeitvergleich mit Teamkollege
                double tmScore = clamp(5.0 + 300.0 * (tmLap - e.qualiLap()) / tmLap, 0, 10);
                q = 0.6 * gridScore + 0.4 * tmScore;
            } else {
                q = gridScore;
            }

            // Basis-Score: gewichtetes Mittel der drei Komponenten (normiert auf 1)
            double wSum = W_Q + W_R + W_D;
            double base = (W_Q * q + W_R * r + W_D * delta) / wSum;

            // Modifier anwenden
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
            // DNF/DSQ: Score auf 7,5 begrenzen
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

    /**
     * Normiert eine Position auf 0–10 mit quadratischer Kurve.
     * Formel: 10 × ((n - pos) / (n - 1))²
     * → P1 bekommt 10, letzter Platz 0, quadratischer Abfall dazwischen.
     */
    private static double score10(int n, int pos) {
        if (n <= 1) {
            return 5.0;
        }
        double t = (double) (n - pos) / (n - 1);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return clamp(10.0 * t * t, 0, 10);
    }

    /**
     * Findet die beste Quali-Rundenzeit des Teamkollegen (für Q-Komponente).
     * Gibt null zurück, wenn kein Teamkollege mit bekannter Zeit vorhanden ist.
     */
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

    /** Rundet auf eine Nachkommastelle (für lesbare Score-Ausgaben). */
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
