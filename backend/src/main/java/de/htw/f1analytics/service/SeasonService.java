package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1Client;
import de.htw.f1analytics.client.OpenF1DriverDto;
import de.htw.f1analytics.client.OpenF1GridDto;
import de.htw.f1analytics.client.OpenF1LapDto;
import de.htw.f1analytics.client.OpenF1MeetingDto;
import de.htw.f1analytics.client.OpenF1ResultDto;
import de.htw.f1analytics.client.OpenF1SessionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@ApplicationScoped
public class SeasonService {

    @RestClient
    OpenF1Client openF1Client;

    @Inject
    SeasonCacheStore cacheStore;

    public record ResultRow(String abbr, String name, String team, String color,
                            int pos, int pts, String gapText, boolean dnf,
                            boolean dns, boolean dsq, Integer laps,
                            F1alyticsScore.ScoreCard score) {}

    public record SessionResultRow(String abbr, String name, String team, String color,
                                   int pos, String bestLap, String gap,
                                   boolean dnf, boolean dns, boolean dsq) {}

    public record PracticeSession(String name, List<SessionResultRow> result) {}

    public record Race(String gp, String country, String circuit, double lat, double lon,
                       String date, int round, boolean completed,
                       List<ResultRow> result, ResultRow fastestLap,
                       String circuitImage, String countryFlag,
                       List<SessionResultRow> qualifyingResult,
                       List<PracticeSession> practiceResults) {}

    public record DriverStanding(String abbr, String name, String team, int num, String color,
                                 int points, int wins, int podiums, int dnf,
                                 List<Integer> finishes, List<Integer> cum,
                                 Double avgFinish, Integer bestFinish,
                                 Double avgScore, List<Double> scoreHistory,
                                 String headshot, Integer maxTopSpeed, Double avgTopSpeed) {}

    public record TeamStanding(String team, String color, int points, int wins,
                               List<DriverStanding> drivers) {}

    public record SeasonStats(List<Race> races, List<DriverStanding> drivers,
                              List<TeamStanding> teams, boolean loading, int totalRaces) {}

    static final int START_YEAR = 2023;
    private static final int[] POINTS = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};

    private static final long THROTTLE_MS = 2500;
    private static final int MAX_RETRY = 4;

    private final Map<Integer, SeasonStats> cache = new ConcurrentHashMap<>();

    private final java.util.Set<Integer> fetching = ConcurrentHashMap.newKeySet();

    private static final Semaphore API_SLOT = new Semaphore(1);

    private static final Map<String, double[]> COORDS = Map.ofEntries(
            Map.entry("Sakhir", new double[]{26.03, 50.51}),
            Map.entry("Jeddah", new double[]{21.63, 39.1}),
            Map.entry("Melbourne", new double[]{-37.85, 144.97}),
            Map.entry("Suzuka", new double[]{34.84, 136.54}),
            Map.entry("Shanghai", new double[]{31.34, 121.22}),
            Map.entry("Miami", new double[]{25.96, -80.24}),
            Map.entry("Imola", new double[]{44.34, 11.72}),
            Map.entry("Monte Carlo", new double[]{43.73, 7.42}),
            Map.entry("Catalunya", new double[]{41.57, 2.26}),
            Map.entry("Barcelona", new double[]{41.57, 2.26}),
            Map.entry("Montreal", new double[]{45.5, -73.52}),
            Map.entry("Spielberg", new double[]{47.22, 14.76}),
            Map.entry("Silverstone", new double[]{52.08, -1.02}),
            Map.entry("Hungaroring", new double[]{47.58, 19.25}),
            Map.entry("Budapest", new double[]{47.58, 19.25}),
            Map.entry("Spa-Francorchamps", new double[]{50.44, 5.97}),
            Map.entry("Zandvoort", new double[]{52.39, 4.54}),
            Map.entry("Monza", new double[]{45.62, 9.28}),
            Map.entry("Baku", new double[]{40.37, 49.85}),
            Map.entry("Marina Bay", new double[]{1.29, 103.86}),
            Map.entry("Singapore", new double[]{1.29, 103.86}),
            Map.entry("Austin", new double[]{30.13, -97.64}),
            Map.entry("Mexico City", new double[]{19.4, -99.09}),
            Map.entry("São Paulo", new double[]{-23.7, -46.7}),
            Map.entry("Sao Paulo", new double[]{-23.7, -46.7}),
            Map.entry("Interlagos", new double[]{-23.7, -46.7}),
            Map.entry("Las Vegas", new double[]{36.11, -115.17}),
            Map.entry("Lusail", new double[]{25.49, 51.45}),
            Map.entry("Yas Island", new double[]{24.47, 54.6}),
            Map.entry("Yas Marina", new double[]{24.47, 54.6}),
            Map.entry("Yas Marina Circuit", new double[]{24.47, 54.6})
    );

    private static final class Accum {
        String abbr;
        String name;
        String team;
        String color;
        String headshot;
        int num;
        int points;
        int wins;
        int podiums;
        int dnf;
        final List<Integer> finishes = new ArrayList<>();
        final List<Integer> cum = new ArrayList<>();
        final List<Double> scores = new ArrayList<>();
        final List<Integer> topSpeeds = new ArrayList<>();
    }

    public void clearCache(int year) {
        cache.remove(year);
        cacheStore.delete(year);
    }

    public List<Integer> years() {
        int cur = Year.now().getValue();
        List<Integer> ys = new ArrayList<>();
        for (int y = START_YEAR; y <= cur; y++) ys.add(y);
        return ys;
    }

    public SeasonStats seasonStats(int year) {

        SeasonStats cached = cache.get(year);
        if (cached != null) return cached;

        SeasonStats fromDb = cacheStore.load(year);
        if (fromDb != null) {
            cache.put(year, fromDb);
            return fromDb;
        }

        if (fetching.add(year)) {
            Thread.ofVirtual().name("season-fetch-" + year).start(() -> {
                try {
                    buildAndCache(year);
                } finally {
                    fetching.remove(year);
                }
            });
        }

        return new SeasonStats(List.of(), List.of(), List.of(), true, 0);
    }

    public void ensureCached(int year) {
        if (cache.containsKey(year)) return;
        SeasonStats fromDb = cacheStore.load(year);
        if (fromDb != null) {
            cache.put(year, fromDb);
            return;
        }
        if (fetching.add(year)) {
            try {
                buildAndCache(year);
            } finally {
                fetching.remove(year);
            }
        }
    }

    private void buildAndCache(int year) {
        try {
            API_SLOT.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            buildAndCacheInternal(year);
        } finally {
            API_SLOT.release();
        }
    }

    private void buildAndCacheInternal(int year) {

        Map<String, String> circuitImages = new LinkedHashMap<>();
        Map<String, String> countryFlags = new LinkedHashMap<>();
        for (OpenF1MeetingDto m : fetch(() -> openF1Client.getMeetings(year))) {
            if (m.location() == null) continue;
            if (m.circuitImage() != null) circuitImages.put(m.location(), m.circuitImage());
            if (m.countryFlag() != null) countryFlags.put(m.location(), m.countryFlag());
        }

        List<OpenF1SessionDto> sessions = fetch(() -> openF1Client.getSessions(year));

        Map<Integer, Integer> qualiByMeeting = new LinkedHashMap<>();
        Map<Integer, List<OpenF1SessionDto>> practicesByMeeting = new LinkedHashMap<>();
        for (OpenF1SessionDto s : sessions) {
            if (s.sessionKey() == null || s.meetingKey() == null) continue;
            String name = s.sessionName();
            if ("Qualifying".equals(name) || "Sprint Qualifying".equals(name) || "Sprint Shootout".equals(name)) {
                qualiByMeeting.put(s.meetingKey(), s.sessionKey());
            } else if (name != null && name.startsWith("Practice")) {
                practicesByMeeting.computeIfAbsent(s.meetingKey(), k -> new ArrayList<>()).add(s);
            }
        }

        List<OpenF1SessionDto> raceSessions = sessions.stream()
                .filter(s -> s.sessionKey() != null && "Race".equals(s.sessionName()))
                .sorted(Comparator.comparing(s -> s.dateStart() == null ? "" : s.dateStart()))
                .toList();

        Map<Integer, Accum> byNum = new LinkedHashMap<>();

        if (!raceSessions.isEmpty()) {
            OpenF1SessionDto refSession = raceSessions.get(raceSessions.size() - 1);
            for (OpenF1DriverDto d : fetch(() -> openF1Client.getDrivers(refSession.sessionKey()))) {
                if (d.driverNumber() == null) continue;
                Accum a = byNum.computeIfAbsent(d.driverNumber(), k -> new Accum());
                a.num = d.driverNumber();
                a.abbr = orElse(d.nameAcronym(), "#" + d.driverNumber());
                a.name = orElse(d.fullName(), a.abbr);
                a.team = orElse(d.teamName(), "—");
                a.color = d.teamColour() != null ? "#" + d.teamColour() : "#888888";
                a.headshot = d.headshotUrl();
            }
        }

        LocalDate today = LocalDate.now();
        List<Race> races = new ArrayList<>();
        int round = 0;

        for (OpenF1SessionDto rs : raceSessions) {
            List<OpenF1ResultDto> rows = fetch(() -> openF1Client.getResults(rs.sessionKey())).stream()
                    .filter(r -> r.position() != null && r.driverNumber() != null)
                    .sorted(Comparator.comparingInt(OpenF1ResultDto::position))
                    .toList();

            java.util.Set<Integer> unknownNums = new java.util.HashSet<>();
            for (OpenF1ResultDto r : rows) {
                if (r.driverNumber() != null && !byNum.containsKey(r.driverNumber())) {
                    unknownNums.add(r.driverNumber());
                }
            }
            if (!unknownNums.isEmpty()) {
                for (OpenF1DriverDto d : fetch(() -> openF1Client.getDrivers(rs.sessionKey()))) {
                    if (d.driverNumber() == null || !unknownNums.contains(d.driverNumber())) continue;
                    Accum a = byNum.computeIfAbsent(d.driverNumber(), k -> new Accum());
                    a.num = d.driverNumber();
                    a.abbr = orElse(d.nameAcronym(), "#" + d.driverNumber());
                    a.name = orElse(d.fullName(), a.abbr);
                    a.team = orElse(d.teamName(), "—");
                    a.color = d.teamColour() != null ? "#" + d.teamColour() : "#888888";
                    a.headshot = d.headshotUrl();
                }
            }

            Map<Integer, F1alyticsScore.ScoreCard> scoreByNum = Map.of();
            Map<Integer, Integer> maxSpeedByNum = new HashMap<>();
            Map<Integer, Double> minLapByNum = new HashMap<>();
            if (!rows.isEmpty()) {
                Map<Integer, Integer> gridPos = new HashMap<>();
                Map<Integer, Double> qualiLap = new HashMap<>();

                Integer qKey = rs.meetingKey() != null ? qualiByMeeting.get(rs.meetingKey()) : null;
                if (qKey != null) {
                    for (OpenF1ResultDto q : fetch(() -> openF1Client.getResults(qKey))) {
                        if (q.driverNumber() != null && q.position() != null) {
                            gridPos.put(q.driverNumber(), q.position());
                        }
                    }
                }
                if (gridPos.isEmpty()) {
                    for (OpenF1GridDto g : fetch(() -> openF1Client.getStartingGrid(rs.sessionKey()))) {
                        if (g.driverNumber() == null) continue;
                        if (g.position() != null) gridPos.put(g.driverNumber(), g.position());
                        if (g.lapDuration() != null) qualiLap.put(g.driverNumber(), g.lapDuration());
                    }
                }

                List<F1alyticsScore.Entry> scoreEntries = new ArrayList<>();
                for (OpenF1ResultDto sr : rows) {
                    int num = sr.driverNumber();
                    Accum acc = byNum.get(num);
                    scoreEntries.add(new F1alyticsScore.Entry(
                            num, acc != null ? acc.team : "—", sr.position(),
                            gridPos.get(num), qualiLap.get(num),
                            Boolean.TRUE.equals(sr.dnf()),
                            Boolean.TRUE.equals(sr.dns()),
                            Boolean.TRUE.equals(sr.dsq())));
                }
                scoreByNum = F1alyticsScore.scoreRace(scoreEntries);

                for (OpenF1LapDto lap : fetch(() -> openF1Client.getLaps(rs.sessionKey()))) {
                    if (lap.driverNumber() == null) continue;
                    if (lap.stSpeed() != null) {
                        maxSpeedByNum.merge(lap.driverNumber(), lap.stSpeed(), Math::max);
                    }
                    if (lap.lapDuration() != null && !Boolean.TRUE.equals(lap.isPitOutLap())) {
                        minLapByNum.merge(lap.driverNumber(), lap.lapDuration(), Math::min);
                    }
                }
            }

            List<ResultRow> result = new ArrayList<>();
            for (OpenF1ResultDto r : rows) {
                int num = r.driverNumber();
                Accum a = byNum.computeIfAbsent(num, k -> {
                    Accum x = new Accum();
                    x.num = num; x.abbr = "#" + num; x.name = "#" + num;
                    x.team = "—"; x.color = "#888888";
                    return x;
                });
                int pos = r.position();
                boolean dnf = Boolean.TRUE.equals(r.dnf());
                boolean dns = Boolean.TRUE.equals(r.dns());
                boolean dsq = Boolean.TRUE.equals(r.dsq());
                boolean out = dnf || dns || dsq;
                int pts = (!out && pos >= 1 && pos <= POINTS.length) ? POINTS[pos - 1] : 0;

                if (dnf) a.dnf++;
                if (!out) {
                    a.points += pts;
                    if (pos == 1) a.wins++;
                    if (pos <= 3) a.podiums++;
                    a.finishes.add(pos);
                }
                F1alyticsScore.ScoreCard card = scoreByNum.get(num);
                if (card != null) a.scores.add(card.score());
                Integer ts = maxSpeedByNum.get(num);
                if (ts != null) a.topSpeeds.add(ts);
                result.add(new ResultRow(a.abbr, a.name, a.team, a.color, pos, pts,
                        gapText(pos, out, r), dnf, dns, dsq, r.numberOfLaps(), card));
            }

            boolean completed = !result.isEmpty();
            LocalDate d = parseDate(rs.dateStart());
            boolean future = d == null || !d.isBefore(today);

            if (!completed && !future) continue;

            round++;

            for (Accum a : byNum.values()) a.cum.add(a.points);

            double[] cc = COORDS.getOrDefault(orElse(rs.location(), orElse(rs.circuitShortName(), "")), new double[]{0, 0});
            String imgUrl = circuitImages.getOrDefault(rs.location(), null);
            String flagUrl = countryFlags.getOrDefault(rs.location(), null);

            List<SessionResultRow> qualifyingResult = List.of();
            List<PracticeSession> practiceResults = List.of();
            if (completed) {
                Integer qualiKey = rs.meetingKey() != null ? qualiByMeeting.get(rs.meetingKey()) : null;
                if (qualiKey != null) qualifyingResult = buildSessionRows(qualiKey, byNum, true);

                practiceResults = new ArrayList<>();
                for (OpenF1SessionDto p : practicesByMeeting.getOrDefault(rs.meetingKey(), List.of())) {
                    practiceResults.add(new PracticeSession(p.sessionName(), buildSessionRows(p.sessionKey(), byNum, false)));
                }
            }

            ResultRow fastest = null;
            if (!minLapByNum.isEmpty()) {
                Integer fastestNum = null;
                double fastestTime = Double.MAX_VALUE;
                for (Map.Entry<Integer, Double> e : minLapByNum.entrySet()) {
                    if (e.getValue() < fastestTime) {
                        fastestTime = e.getValue();
                        fastestNum = e.getKey();
                    }
                }
                if (fastestNum != null) {
                    Accum fa = byNum.get(fastestNum);
                    if (fa != null) {
                        for (ResultRow rr : result) {
                            if (rr.abbr().equals(fa.abbr)) {
                                fastest = rr;
                                break;
                            }
                        }
                    }
                }
            }

            races.add(new Race(
                    orElse(rs.location(), orElse(rs.circuitShortName(), "GP")),
                    orElse(rs.countryName(), "—"),
                    orElse(rs.circuitShortName(), "—"),
                    cc[0], cc[1],
                    rs.dateStart() == null ? "" : rs.dateStart().substring(0, Math.min(10, rs.dateStart().length())),
                    round, completed, result, fastest, imgUrl, flagUrl, qualifyingResult, practiceResults));

            cache.put(year, new SeasonStats(List.copyOf(races), List.of(), List.of(), true, raceSessions.size()));
        }

        List<DriverStanding> drivers = new ArrayList<>();
        for (Accum a : byNum.values()) {
            Double avg = a.finishes.isEmpty() ? null
                    : a.finishes.stream().mapToInt(Integer::intValue).average().orElse(0);
            Integer best = a.finishes.isEmpty() ? null
                    : a.finishes.stream().mapToInt(Integer::intValue).min().getAsInt();
            Double avgScore = a.scores.isEmpty() ? null
                    : Math.round(a.scores.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10.0) / 10.0;
            Integer maxTop = a.topSpeeds.isEmpty() ? null
                    : a.topSpeeds.stream().mapToInt(Integer::intValue).max().getAsInt();
            Double avgTop = a.topSpeeds.isEmpty() ? null
                    : Math.round(a.topSpeeds.stream().mapToInt(Integer::intValue).average().orElse(0) * 10.0) / 10.0;
            drivers.add(new DriverStanding(a.abbr, a.name, a.team, a.num, a.color,
                    a.points, a.wins, a.podiums, a.dnf,
                    List.copyOf(a.finishes), List.copyOf(a.cum), avg, best,
                    avgScore, List.copyOf(a.scores),
                    a.headshot, maxTop, avgTop));
        }
        drivers.sort(Comparator.comparingInt(DriverStanding::points).reversed());

        Map<String, List<DriverStanding>> byTeam = new LinkedHashMap<>();
        for (DriverStanding d : drivers) byTeam.computeIfAbsent(d.team(), k -> new ArrayList<>()).add(d);

        List<TeamStanding> teams = new ArrayList<>();
        for (Map.Entry<String, List<DriverStanding>> e : byTeam.entrySet()) {
            int pts = e.getValue().stream().mapToInt(DriverStanding::points).sum();
            int wins = e.getValue().stream().mapToInt(DriverStanding::wins).sum();
            String color = e.getValue().isEmpty() ? "#888888" : e.getValue().get(0).color();
            teams.add(new TeamStanding(e.getKey(), color, pts, wins, e.getValue()));
        }
        teams.sort(Comparator.comparingInt(TeamStanding::points).reversed());

        SeasonStats stats = new SeasonStats(races, drivers, teams, false, races.size());
        if (!races.isEmpty()) {
            cache.put(year, stats);
            cacheStore.save(year, stats);
        }
    }

    private <T> List<T> fetch(Supplier<List<T>> call) {
        int attempts = 0;
        while (true) {
            try {
                Thread.sleep(THROTTLE_MS);
                return call.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return List.of();
            } catch (WebApplicationException e) {
                int s = e.getResponse() != null ? e.getResponse().getStatus() : 0;
                if (s == 429 && attempts < MAX_RETRY) {
                    attempts++;
                    sleepQuiet(65_000L);
                    continue;
                }
                if (s >= 500 && attempts < MAX_RETRY) {
                    attempts++;
                    sleepQuiet(2000L * attempts);
                    continue;
                }
                return List.of();
            } catch (ProcessingException e) {
                if (attempts < MAX_RETRY) {
                    attempts++;
                    sleepQuiet(700L * attempts);
                    continue;
                }
                return List.of();
            }
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static LocalDate parseDate(String dateStart) {
        if (dateStart == null || dateStart.length() < 10) return null;
        try {
            return LocalDate.parse(dateStart.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String gapText(int pos, boolean out, OpenF1ResultDto r) {
        if (out) return "—";
        if (pos == 1) {
            Double t = extractLastDouble(r.duration());
            if (t == null) return "—";
            int h = (int) (t / 3600);
            int m = (int) ((t % 3600) / 60);
            String sec = String.format(Locale.US, "%06.3f", t % 60);
            return h + ":" + String.format("%02d", m) + ":" + sec;
        }
        Object g = r.gapToLeader();
        if (g instanceof Number num) return "+" + String.format(Locale.US, "%.3f", num.doubleValue()) + "s";
        if (g instanceof String s && !s.isBlank()) return s;
        return "—";
    }

    private static Double extractLastDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            if (last instanceof Number n) return n.doubleValue();
        }
        return null;
    }

    private static String formatLapTime(Double sec) {
        if (sec == null) return "—";
        int m = (int) (sec / 60);
        return m + ":" + String.format(Locale.US, "%06.3f", sec % 60);
    }

    private List<SessionResultRow> buildSessionRows(int sessionKey, Map<Integer, Accum> byNum, boolean isQualifying) {
        List<SessionResultRow> rows = new ArrayList<>();
        for (OpenF1ResultDto r : fetch(() -> openF1Client.getResults(sessionKey))) {
            if (r.position() == null || r.driverNumber() == null) continue;
            Accum a = byNum.get(r.driverNumber());
            String abbr  = a != null ? a.abbr  : "#" + r.driverNumber();
            String name  = a != null ? a.name  : "#" + r.driverNumber();
            String team  = a != null ? a.team  : "—";
            String color = a != null ? a.color : "#888888";

            boolean dnf = Boolean.TRUE.equals(r.dnf());
            boolean dns = Boolean.TRUE.equals(r.dns());
            boolean dsq = Boolean.TRUE.equals(r.dsq());
            boolean out = dnf || dns || dsq;

            String bestLap = out ? "—" : formatLapTime(extractLastDouble(r.duration()));
            String gap = "—";
            if (!out) {
                if (r.position() == 1) {
                    gap = isQualifying ? "POLE" : "—";
                } else {
                    Double g = extractLastDouble(r.gapToLeader());
                    if (g != null && g > 0) gap = "+" + String.format(Locale.US, "%.3f", g) + "s";
                }
            }
            rows.add(new SessionResultRow(abbr, name, team, color, r.position(), bestLap, gap, dnf, dns, dsq));
        }
        rows.sort(Comparator.comparingInt(SessionResultRow::pos));
        return rows;
    }

    private static String orElse(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}