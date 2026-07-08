package de.htw.f1analytics.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Berechnet statistische Rennprognosen für alle Fahrer einer Saison.
 *
 * Rating-Formel (4 gewichtete Faktoren):
 *   35 % WM-Punkte-Anteil      (Gesamtleistung der Saison)
 *   30 % Aktuelle Form          (F1alytics-Durchschnitt der letzten 3 Rennen)
 *   20 % Sieg-Rate              (Siege / abgeschlossene Rennen)
 *   15 % Strecken-Performance   (historischer F1alytics-Score auf dieser Strecke/Land)
 *
 * Aus den Ratings wird per Softmax (Temperatur 1,15) eine normierte
 * Gewinnwahrscheinlichkeit berechnet. Höhere Temperatur = weniger extremes Ergebnis.
 *
 * Podium-Wahrscheinlichkeit: 60 % Hochrechnung aus Win-Prob + 40 % historische Podium-Rate.
 */
@ApplicationScoped
public class ForecastService {

    private static final double SOFTMAX_TEMPERATURE = 1.15;
    private static final double W_POINTS = 0.35;
    private static final double W_FORM = 0.30;
    private static final double W_WINS = 0.20;
    private static final double W_TRACK = 0.15;
    private static final double TRACK_NEUTRAL = 5.0;

    @Inject
    SeasonService seasonService;

    public record DriverForecast(String abbr, String name, String team, String color,
                                 double winProb, double podiumProb, double form,
                                 Double track, int trackRaces,
                                 String trend, double rating) {}

    public record Forecast(int year, int completedRaces, String nextGp, Integer nextRound,
                           String trackName, String trackBasis, List<DriverForecast> drivers) {}

    private record TrackStats(double avg, int races) {}

    public Forecast forecast(int year) {
        SeasonService.SeasonStats stats = seasonService.seasonStats(year);
        List<SeasonService.Race> races = stats.races() != null ? stats.races() : List.of();
        List<SeasonService.DriverStanding> standings = stats.drivers() != null ? stats.drivers() : List.of();

        int completed = (int) races.stream().filter(SeasonService.Race::completed).count();
        SeasonService.Race next = races.stream().filter(r -> !r.completed()).findFirst().orElse(null);
        String nextGp = next != null ? next.gp() : null;
        Integer nextRound = next != null ? next.round() : null;
        String trackName = next != null ? next.circuit() : null;

        Map<String, TrackStats> history = new HashMap<>();
        String trackBasis = next != null ? collectTrackHistory(next, history) : null;

        int n = standings.size();
        if (n == 0) {
            return new Forecast(year, completed, nextGp, nextRound, trackName, trackBasis, List.of());
        }

        double totalPoints = 0;
        for (SeasonService.DriverStanding d : standings) totalPoints += Math.max(0, d.points());

        double[] shares = new double[n];
        double[] forms = new double[n];
        double[] winRates = new double[n];
        double[] podiumRates = new double[n];
        double[] trackScores = new double[n];
        double maxShare = 0;

        for (int i = 0; i < n; i++) {
            SeasonService.DriverStanding d = standings.get(i);
            shares[i] = totalPoints > 0 ? Math.max(0, d.points()) / totalPoints : 1.0 / n;
            maxShare = Math.max(maxShare, shares[i]);
            forms[i] = recentForm(d);
            winRates[i] = completed > 0 ? (double) d.wins() / completed : 0;
            podiumRates[i] = completed > 0 ? (double) d.podiums() / completed : 0;
            TrackStats ts = history.get(d.abbr());
            trackScores[i] = ts != null ? clamp(ts.avg(), 0, 10) : TRACK_NEUTRAL;
        }

        double sumExp = 0;
        double[] exps = new double[n];
        double[] ratings = new double[n];
        for (int i = 0; i < n; i++) {
            double pointsScore = maxShare > 0 ? shares[i] / maxShare * 10.0 : 5.0;
            double winScore = Math.min(10.0, winRates[i] * 12.0);
            ratings[i] = W_POINTS * pointsScore + W_FORM * forms[i] + W_WINS * winScore + W_TRACK * trackScores[i];
            exps[i] = Math.exp(ratings[i] / SOFTMAX_TEMPERATURE);
            sumExp += exps[i];
        }

        List<DriverForecast> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            SeasonService.DriverStanding d = standings.get(i);
            double winProb = sumExp > 0 ? exps[i] / sumExp : 1.0 / n;
            double podiumProb = clamp(0.60 * Math.min(1.0, winProb * 3.0) + 0.40 * podiumRates[i], 0, 0.97);
            TrackStats ts = history.get(d.abbr());
            result.add(new DriverForecast(
                    d.abbr(), d.name(), d.team(), d.color(),
                    round4(winProb), round4(podiumProb),
                    round1(forms[i]),
                    ts != null ? round1(ts.avg()) : null,
                    ts != null ? ts.races() : 0,
                    trend(d), round1(ratings[i])));
        }
        result.sort(Comparator.comparingDouble(DriverForecast::winProb).reversed());
        return new Forecast(year, completed, nextGp, nextRound, trackName, trackBasis, result);
    }

    private String collectTrackHistory(SeasonService.Race next, Map<String, TrackStats> out) {
        Map<String, double[]> acc = new HashMap<>();
        String circuit = norm(next.circuit());
        String country = norm(next.country());

        String basis = null;
        if (circuit != null && accumulate(acc, circuit, null)) {
            basis = "circuit";
        } else if (country != null && accumulate(acc, null, country)) {
            basis = "country";
        }
        if (basis == null) return null;

        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            double[] v = e.getValue();
            if (v[1] > 0) out.put(e.getKey(), new TrackStats(v[0] / v[1], (int) v[1]));
        }
        return basis;
    }

    private boolean accumulate(Map<String, double[]> acc, String circuit, String country) {
        boolean any = false;
        List<Integer> years;
        try {
            years = seasonService.years();
        } catch (Exception e) {
            return false;
        }
        for (Integer y : years) {
            try {
                SeasonService.SeasonStats s = seasonService.seasonStats(y);
                if (s.races() == null) continue;
                for (SeasonService.Race r : s.races()) {
                    if (!r.completed() || r.result() == null || r.result().isEmpty()) continue;
                    boolean match = circuit != null
                            ? circuit.equals(norm(r.circuit()))
                            : country != null && country.equals(norm(r.country()));
                    if (!match) continue;
                    any = true;
                    int fieldSize = r.result().size();
                    for (SeasonService.ResultRow row : r.result()) {
                        if (row.dns() || row.abbr() == null) continue;
                        double score = row.score() != null ? row.score().score() : posScore(row.pos(), fieldSize);
                        double[] v = acc.computeIfAbsent(row.abbr(), k -> new double[2]);
                        v[0] += score;
                        v[1] += 1;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return any;
    }

    private static double posScore(int pos, int fieldSize) {
        if (fieldSize <= 1) return 5.0;
        return clamp(1.0 + 9.0 * (fieldSize - pos) / (fieldSize - 1.0), 1, 10);
    }

    private static String norm(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? null : v;
    }

    private double recentForm(SeasonService.DriverStanding d) {
        List<Double> history = d.scoreHistory();
        if (history != null && !history.isEmpty()) {
            List<Double> recent = new ArrayList<>();
            for (int i = history.size() - 1; i >= 0 && recent.size() < 3; i--) {
                Double v = history.get(i);
                if (v != null) recent.add(v);
            }
            if (!recent.isEmpty()) {
                double sum = 0;
                for (double v : recent) sum += v;
                return clamp(sum / recent.size(), 0, 10);
            }
        }
        if (d.avgScore() != null) return clamp(d.avgScore(), 0, 10);
        return 5.0;
    }

    private String trend(SeasonService.DriverStanding d) {
        List<Integer> finishes = d.finishes();
        Double avg = d.avgFinish();
        if (finishes == null || finishes.isEmpty() || avg == null) return "flat";
        List<Integer> recent = new ArrayList<>();
        for (int i = finishes.size() - 1; i >= 0 && recent.size() < 3; i--) {
            Integer f = finishes.get(i);
            if (f != null && f > 0) recent.add(f);
        }
        if (recent.isEmpty()) return "flat";
        double sum = 0;
        for (int f : recent) sum += f;
        double recentAvg = sum / recent.size();
        if (recentAvg <= avg - 1.0) return "up";
        if (recentAvg >= avg + 1.0) return "down";
        return "flat";
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}