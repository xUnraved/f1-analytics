package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1Client;
import de.htw.f1analytics.client.OpenF1DriverDto;
import de.htw.f1analytics.client.OpenF1GridDto;
import de.htw.f1analytics.client.OpenF1LapDto;
import de.htw.f1analytics.client.OpenF1MeetingDto;
import de.htw.f1analytics.client.OpenF1ResultDto;
import de.htw.f1analytics.client.OpenF1SessionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
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

/**
 * Aggregiert alle F1-Saisondaten aus der OpenF1-API und verwaltet einen zweistufigen Cache.
 *
 * Architektur:
 *   1. RAM-Cache (ConcurrentHashMap)  – schnellster Pfad, verloren bei Neustart
 *   2. DB-Cache (SeasonCacheStore)    – JSON-Blob in PostgreSQL, überlebt Neustarts
 *   3. OpenF1-API                     – Quelle der Wahrheit, langsam (Rate-Limit)
 *
 * Beim ersten Aufruf für ein Jahr wird ein virtueller Thread gestartet, der die API
 * asynchron abfragt und den Cache befüllt. Solange geladen wird, liefert seasonStats()
 * eine "loading: true"-Antwort.
 *
 * API-Throttle: 2500 ms zwischen allen Anfragen + Semaphore (ein gleichzeitiger Build).
 * Rate-Limit-Antwort (HTTP 429): 65 s Pause, bis zu 4 Versuche.
 * Live-Session-Sperre (HTTP 401 "Live F1 session"): 2 min Cooldown.
 */
@ApplicationScoped
public class SeasonService {

    @RestClient
    OpenF1Client openF1Client;

    @Inject
    SeasonCacheStore cacheStore;

    @Inject
    RaceCacheStore raceCacheStore;

    @Inject
    F1DataStore dataStore;

    @Inject
    QuizService quizService;

    @Inject
    ReplayService replayService;

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
                       List<PracticeSession> practiceResults,
                       int sessionKey, String sessionDateStart) {}

    public record DriverStanding(String abbr, String name, String team, int num, String color,
                                 int points, int wins, int podiums, int dnf,
                                 List<Integer> finishes, List<Integer> cum,
                                 Double avgFinish, Integer bestFinish,
                                 Double avgScore, List<Double> scoreHistory,
                                 String headshot, Integer maxTopSpeed, Double avgTopSpeed) {}

    public record TeamStanding(String team, String color, int points, int wins,
                               List<DriverStanding> drivers) {}

    public record SeasonStats(List<Race> races, List<DriverStanding> drivers,
                              List<TeamStanding> teams, boolean loading, int totalRaces,
                              boolean liveSessionBlocked) {}

    static final int START_YEAR = 2023;
    private static final int[] POINTS = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};

    private static final long THROTTLE_MS = 2500;
    private static final int MAX_RETRY = 4;

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(SeasonService.class);

    private static final long API_COOLDOWN_MS = 2 * 60 * 1000L; // 2 Minuten Pause nach 401/leerem API-Ergebnis

    private final Map<Integer, SeasonStats> cache = new ConcurrentHashMap<>();
    private final Map<Integer, Long> apiBlockedUntil = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> liveBlockedYears = new ConcurrentHashMap<>();
    private volatile boolean lastFetchWas401 = false;

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

    private static final Map<String, String> TEAM_COLOR_FALLBACK = Map.ofEntries(
            Map.entry("mclaren", "#FF8000"),
            Map.entry("ferrari", "#E8002D"),
            Map.entry("red bull", "#3671C6"),
            Map.entry("mercedes", "#27F4D2"),
            Map.entry("aston martin", "#229971"),
            Map.entry("alpine", "#0093CC"),
            Map.entry("williams", "#64C4FF"),
            Map.entry("racing bulls", "#6692FF"),
            Map.entry("sauber", "#52E252"),
            Map.entry("audi", "#52E252"),
            Map.entry("haas", "#B6BABD"),
            Map.entry("cadillac", "#D4AF37")
    );

    private String resolveColor(String team, String color) {
        boolean valid = color != null && !color.isBlank() && !"#888888".equals(color);
        if (valid) return color;
        if (team != null) {
            String t = team.toLowerCase(java.util.Locale.ROOT);
            for (Map.Entry<String, String> e : TEAM_COLOR_FALLBACK.entrySet()) {
                if (t.contains(e.getKey())) return e.getValue();
            }
        }
        return color != null ? color : "#888888";
    }

    private String resolveHeadshot(String headshot, int num, String abbr) {
        if (headshot != null && !headshot.isBlank()) return headshot;
        de.htw.f1analytics.domain.QuizDriverEntity byNum = de.htw.f1analytics.domain.QuizDriverEntity.findById(num);
        if (byNum != null && byNum.headshotUrl != null && !byNum.headshotUrl.isBlank()) return byNum.headshotUrl;
        if (abbr != null) {
            de.htw.f1analytics.domain.QuizDriverEntity byAbbr =
                    de.htw.f1analytics.domain.QuizDriverEntity.find("abbr", abbr.toUpperCase(java.util.Locale.ROOT)).firstResult();
            if (byAbbr != null && byAbbr.headshotUrl != null && !byAbbr.headshotUrl.isBlank()) return byAbbr.headshotUrl;
        }
        return headshot;
    }

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

    /** Gibt alle Saison-Jahre ab START_YEAR bis heute zurück. */
    public List<Integer> years() {
        int cur = Year.now().getValue();
        List<Integer> ys = new ArrayList<>();
        for (int y = START_YEAR; y <= cur; y++) ys.add(y);
        return ys;
    }

    /**
     * Gibt die aggregierten Saison-Statistiken zurück.
     * Cache-Hierarchie: RAM → DB → API (asynchroner Hintergrundthread).
     * Gibt bei Cache-Miss sofort loading=true zurück, damit das Frontend pollen kann.
     */
    public SeasonStats seasonStats(int year) {

        SeasonStats cached = cache.get(year);
        if (cached != null) return cached;

        SeasonStats fromDb = cacheStore.load(year);
        if (fromDb != null) {
            cache.put(year, fromDb);
            return fromDb;
        }

        // API-Cooldown: nicht alle 6s erneut versuchen wenn API geblockt ist
        Long blockedUntil = apiBlockedUntil.get(year);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) {
            boolean liveBlocked = Boolean.TRUE.equals(liveBlockedYears.get(year));
            return new SeasonStats(List.of(), List.of(), List.of(), true, 0, liveBlocked);
        }

        if (fetching.add(year)) {
            Thread.ofVirtual().name("season-fetch-" + year).start(() -> {
                try {
                    io.quarkus.arc.Arc.container().requestContext().activate();
                    try {
                        buildAndCache(year);
                    } finally {
                        try { io.quarkus.arc.Arc.container().requestContext().terminate(); } catch (Exception ignored) {}
                    }
                } finally {
                    fetching.remove(year);  // immer ausführen, auch bei Arc-Fehler
                }
            });
        }

        return new SeasonStats(List.of(), List.of(), List.of(), true, 0, false);
    }

    /** Stellt sicher, dass der Cache für ein Jahr befüllt ist (synchron, z. B. für CacheWarmer). */
    @ActivateRequestContext
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

    /** Löscht RAM, DB-Cache und alle rohen DB-Zeilen einer Saison vollständig. */
    public void clearCache(int year) {
        cache.remove(year);
        fetching.remove(year);
        cacheStore.delete(year);
        raceCacheStore.deleteForYear(year);
        dataStore.deleteYear(year);
    }

    /** Löscht nur das Ergebnis einer einzelnen Session und invalidiert den Saison-Cache. */
    public void clearRace(int sessionKey, int year) {
        dataStore.deleteResults(sessionKey);
        raceCacheStore.delete(sessionKey);
        cache.remove(year);
        fetching.remove(year);
        cacheStore.delete(year);
    }

    /**
     * Löscht nur diese eine Session aus der DB, holt sie synchron von der OpenF1-API neu
     * und gibt die vollständig aktualisierte SeasonStats zurück — kein Polling nötig.
     */
    @ActivateRequestContext
    public SeasonStats refreshSingleRace(int sessionKey, int year) {
        dataStore.deleteResults(sessionKey);
        raceCacheStore.delete(sessionKey);
        cache.remove(year);
        fetching.remove(year);
        cacheStore.delete(year);
        try {
            API_SLOT.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new SeasonStats(List.of(), List.of(), List.of(), false, 0, false);
        }
        try {
            buildAndCacheInternal(year);
        } finally {
            API_SLOT.release();
        }
        SeasonStats result = cache.get(year);
        return result != null ? result : new SeasonStats(List.of(), List.of(), List.of(), false, 0, false);
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

        List<de.htw.f1analytics.domain.F1SessionEntity> dbSessions =
                de.htw.f1analytics.domain.F1SessionEntity.list("year = ?1 order by dateStart", year);

        Map<String, String> circuitImages = new LinkedHashMap<>();
        Map<String, String> countryFlags  = new LinkedHashMap<>();

        if (dbSessions.isEmpty()) {
            for (OpenF1MeetingDto m : fetch(() -> openF1Client.getMeetings(year))) {
                if (m.location() == null) continue;
                if (m.circuitImage() != null) circuitImages.put(m.location(), m.circuitImage());
                if (m.countryFlag()  != null) countryFlags.put(m.location(), m.countryFlag());
            }
            List<OpenF1SessionDto> apiSessions = fetch(() -> openF1Client.getSessions(year));
            if (apiSessions.isEmpty()) {
                if (lastFetchWas401) {
                    LOG.warnf("Season %d: Live-Session läuft – API-Zugriff gesperrt. Nächster Versuch in %.0f Minuten.", year, API_COOLDOWN_MS / 60000.0);
                    liveBlockedYears.put(year, true);
                } else {
                    LOG.warnf("Season %d: API lieferte keine Sessions. Nächster Versuch in %.0f Minuten.", year, API_COOLDOWN_MS / 60000.0);
                }
                apiBlockedUntil.put(year, System.currentTimeMillis() + API_COOLDOWN_MS);
                return;
            }
            apiBlockedUntil.remove(year);
            liveBlockedYears.remove(year);
            for (OpenF1SessionDto s : apiSessions) {
                if (s.sessionKey() == null) continue;
                double[] cc = COORDS.getOrDefault(orElse(s.location(), orElse(s.circuitShortName(), "")), new double[]{0, 0});
                dataStore.saveSession(F1DataStore.fromDto(s, year,
                        circuitImages.getOrDefault(s.location(), null),
                        countryFlags.getOrDefault(s.location(), null),
                        cc[0], cc[1]));
            }
            dbSessions = de.htw.f1analytics.domain.F1SessionEntity.list("year = ?1 order by dateStart", year);
        }

        for (de.htw.f1analytics.domain.F1SessionEntity s : dbSessions) {
            if (s.location == null) continue;
            if (s.circuitImage != null) circuitImages.put(s.location, s.circuitImage);
            if (s.countryFlag  != null) countryFlags.put(s.location, s.countryFlag);
        }

        Map<Integer, Integer> qualiByMeeting = new LinkedHashMap<>();
        Map<Integer, List<de.htw.f1analytics.domain.F1SessionEntity>> practicesByMeeting = new LinkedHashMap<>();
        for (de.htw.f1analytics.domain.F1SessionEntity s : dbSessions) {
            if (s.meetingKey == null) continue;
            String name = s.sessionName;
            if ("Qualifying".equals(name) || "Sprint Qualifying".equals(name) || "Sprint Shootout".equals(name)) {
                qualiByMeeting.put(s.meetingKey, s.sessionKey);
            } else if (name != null && name.startsWith("Practice")) {
                practicesByMeeting.computeIfAbsent(s.meetingKey, k -> new ArrayList<>()).add(s);
            }
        }

        List<de.htw.f1analytics.domain.F1SessionEntity> raceSessions = dbSessions.stream()
                .filter(s -> "Race".equals(s.sessionName))
                .sorted(Comparator.comparing(s -> s.dateStart == null ? "" : s.dateStart))
                .toList();

        Map<Integer, Accum> byNum = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();
        List<Race> races = new ArrayList<>();
        int round = 0;
        int totalRaceSessions = raceSessions.size();

        for (de.htw.f1analytics.domain.F1SessionEntity rs : raceSessions) {

            List<ResultRow> result;
            ResultRow fastest = null;
            boolean fromApi;

            // ── 1. Einzelrennen-Cache (race_cache) prüfen ──────────────────────────
            java.util.Optional<Race> cachedRace = raceCacheStore.load(rs.sessionKey);
            if (cachedRace.isPresent()) {
                Race cr = cachedRace.get();
                // byNum aus f1_result aufbauen (für korrekte Fahrerwertung)
                if (dataStore.resultsExistForSession(rs.sessionKey)) {
                    // Scores aus dem Race-Cache für byNum merken
                    Map<String, Double> scoreByAbbr = new HashMap<>();
                    for (ResultRow rr : cr.result()) {
                        if (rr.score() != null) scoreByAbbr.put(rr.abbr(), rr.score().score());
                    }
                    for (de.htw.f1analytics.domain.F1ResultEntity r : dataStore.getResults(rs.sessionKey)) {
                        Accum a = byNum.computeIfAbsent(r.driverNumber, k -> {
                            Accum x = new Accum(); x.num = r.driverNumber;
                            x.abbr = r.abbr; x.name = r.name; x.team = r.team; x.color = r.color;
                            return x;
                        });
                        a.abbr = r.abbr; a.name = r.name; a.team = r.team; a.color = r.color;
                        boolean out = r.dnf || r.dns || r.dsq;
                        int pts = r.pts != null ? r.pts : 0;
                        if (r.dnf) a.dnf++;
                        if (!out) {
                            a.points += pts;
                            if (r.pos == 1) a.wins++;
                            if (r.pos <= 3) a.podiums++;
                            a.finishes.add(r.pos);
                        }
                        Double s = scoreByAbbr.get(r.abbr);
                        if (s != null) a.scores.add(s);
                    }
                }
                round++;
                for (Accum a : byNum.values()) a.cum.add(a.points);
                races.add(cr);
                cache.put(year, new SeasonStats(List.copyOf(races), List.of(), List.of(), true, totalRaceSessions, false));
                continue;
            }

            if (dataStore.resultsExistForSession(rs.sessionKey)) {
                fromApi = false;
                List<de.htw.f1analytics.domain.F1ResultEntity> dbRows = dataStore.getResults(rs.sessionKey);
                List<F1alyticsScore.Entry> dbEntries = new ArrayList<>();
                for (de.htw.f1analytics.domain.F1ResultEntity r : dbRows) {
                    dbEntries.add(new F1alyticsScore.Entry(r.driverNumber,
                            r.team != null ? r.team : "—", r.pos, null, null,
                            r.dnf, r.dns, r.dsq));
                }
                Map<Integer, F1alyticsScore.ScoreCard> dbScores = F1alyticsScore.scoreRace(dbEntries);
                result = new ArrayList<>();
                for (de.htw.f1analytics.domain.F1ResultEntity r : dbRows) {
                    Accum a = byNum.computeIfAbsent(r.driverNumber, k -> {
                        Accum x = new Accum();
                        x.num = r.driverNumber;
                        x.abbr = r.abbr; x.name = r.name;
                        x.team = r.team; x.color = r.color;
                        return x;
                    });
                    a.abbr = r.abbr; a.name = r.name; a.team = r.team; a.color = r.color;
                    int pos = r.pos;
                    int pts = r.pts != null ? r.pts : 0;
                    boolean out = r.dnf || r.dns || r.dsq;

                    if (r.dnf) a.dnf++;
                    if (!out) {
                        a.points += pts;
                        if (pos == 1) a.wins++;
                        if (pos <= 3) a.podiums++;
                        a.finishes.add(pos);
                    }
                    F1alyticsScore.ScoreCard dbCard = dbScores.get(r.driverNumber);
                    if (dbCard != null) a.scores.add(dbCard.score());
                    result.add(new ResultRow(r.abbr, r.name, r.team, r.color,
                            pos, pts, r.gapText != null ? r.gapText : "—",
                            r.dnf, r.dns, r.dsq, r.laps, dbCard));
                }

            } else {
                fromApi = true;
                List<OpenF1ResultDto> rows = fetch(() -> openF1Client.getResults(rs.sessionKey)).stream()
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
                    for (OpenF1DriverDto d : fetch(() -> openF1Client.getDrivers(rs.sessionKey))) {
                        if (d.driverNumber() == null || !unknownNums.contains(d.driverNumber())) continue;
                        Accum a = byNum.computeIfAbsent(d.driverNumber(), k -> new Accum());
                        a.num = d.driverNumber();
                        a.abbr = orElse(d.nameAcronym(), "#" + d.driverNumber());
                        a.name = orElse(d.fullName(), a.abbr);
                        a.team = orElse(d.teamName(), "—");
                        a.color = d.teamColour() != null ? "#" + d.teamColour() : "#888888";
                        a.headshot = d.headshotUrl();
                        quizService.saveDriverIfAbsent(d);
                    }
                }

                Map<Integer, F1alyticsScore.ScoreCard> scoreByNum = Map.of();
                Map<Integer, Integer> maxSpeedByNum = new HashMap<>();
                Map<Integer, Double> minLapByNum = new HashMap<>();
                if (!rows.isEmpty()) {
                    Map<Integer, Integer> gridPos = new HashMap<>();
                    Map<Integer, Double> qualiLap = new HashMap<>();

                    Integer qKey = rs.meetingKey != null ? qualiByMeeting.get(rs.meetingKey) : null;
                    if (qKey != null) {
                        for (OpenF1ResultDto q : fetch(() -> openF1Client.getResults(qKey))) {
                            if (q.driverNumber() != null && q.position() != null) {
                                gridPos.put(q.driverNumber(), q.position());
                            }
                        }
                    }
                    if (gridPos.isEmpty()) {
                        for (OpenF1GridDto g : fetch(() -> openF1Client.getStartingGrid(rs.sessionKey))) {
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

                    for (OpenF1LapDto lap : fetch(() -> openF1Client.getLaps(rs.sessionKey))) {
                        if (lap.driverNumber() == null) continue;
                        if (lap.stSpeed() != null) {
                            maxSpeedByNum.merge(lap.driverNumber(), lap.stSpeed(), Math::max);
                        }
                        if (lap.lapDuration() != null && !Boolean.TRUE.equals(lap.isPitOutLap())) {
                            minLapByNum.merge(lap.driverNumber(), lap.lapDuration(), Math::min);
                        }
                    }
                }

                result = new ArrayList<>();
                List<de.htw.f1analytics.domain.F1ResultEntity> toSave = new ArrayList<>();
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

                    toSave.add(F1DataStore.raceResult(rs.sessionKey, num,
                            a.abbr, a.name, a.team, a.color,
                            pos, pts, gapText(pos, out, r),
                            dnf, dns, dsq, r.numberOfLaps()));
                }
                if (!toSave.isEmpty()) dataStore.saveResults(toSave);

                // Fastest lap
                if (!minLapByNum.isEmpty()) {
                    Integer fastestNum = null;
                    double fastestTime = Double.MAX_VALUE;
                    for (Map.Entry<Integer, Double> e : minLapByNum.entrySet()) {
                        if (e.getValue() < fastestTime) { fastestTime = e.getValue(); fastestNum = e.getKey(); }
                    }
                    if (fastestNum != null) {
                        Accum fa = byNum.get(fastestNum);
                        if (fa != null) {
                            for (ResultRow rr : result) {
                                if (rr.abbr().equals(fa.abbr)) { fastest = rr; break; }
                            }
                        }
                    }
                }
            }

            boolean completed = !result.isEmpty();
            LocalDate d = parseDate(rs.dateStart);
            boolean future = d == null || !d.isBefore(today);

            if (!completed && !future) continue;

            round++;
            for (Accum a : byNum.values()) a.cum.add(a.points);

            double[] cc = new double[]{rs.lat, rs.lon};
            String imgUrl  = rs.circuitImage;
            String flagUrl = rs.countryFlag;

            // Qualifying and practice: only fetched from API (not stored in DB)
            List<SessionResultRow> qualifyingResult = List.of();
            List<PracticeSession> practiceResults   = List.of();
            if (completed && fromApi && rs.meetingKey != null) {
                Integer qualiKey = qualiByMeeting.get(rs.meetingKey);
                if (qualiKey != null) qualifyingResult = buildSessionRows(qualiKey, byNum, true);
                List<de.htw.f1analytics.domain.F1SessionEntity> practices = practicesByMeeting.getOrDefault(rs.meetingKey, List.of());
                practiceResults = new ArrayList<>();
                for (de.htw.f1analytics.domain.F1SessionEntity p : practices) {
                    practiceResults.add(new PracticeSession(p.sessionName, buildSessionRows(p.sessionKey, byNum, false)));
                }
            }

            Race newRace = new Race(
                    orElse(rs.location, orElse(rs.circuitShortName, "GP")),
                    orElse(rs.countryName, "—"),
                    orElse(rs.circuitShortName, "—"),
                    cc[0], cc[1],
                    rs.dateStart == null ? "" : rs.dateStart.substring(0, Math.min(10, rs.dateStart.length())),
                    round, completed, result, fastest, imgUrl, flagUrl,
                    qualifyingResult, practiceResults,
                    rs.sessionKey,
                    rs.dateStart != null ? rs.dateStart : "");
            races.add(newRace);

            // Rennergebnis pro Session in race_cache persistieren
            if (completed) {
                raceCacheStore.save(rs.sessionKey, year, newRace);
            }

            cache.put(year, new SeasonStats(List.copyOf(races), List.of(), List.of(), true, totalRaceSessions, false));
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
            drivers.add(new DriverStanding(a.abbr, a.name, a.team, a.num, resolveColor(a.team, a.color),
                    a.points, a.wins, a.podiums, a.dnf,
                    List.copyOf(a.finishes), List.copyOf(a.cum), avg, best,
                    avgScore, List.copyOf(a.scores),
                    resolveHeadshot(a.headshot, a.num, a.abbr), maxTop, avgTop));
        }
        drivers.sort(Comparator.comparingInt(DriverStanding::points).reversed());

        Map<String, List<DriverStanding>> byTeam = new LinkedHashMap<>();
        for (DriverStanding d : drivers) byTeam.computeIfAbsent(d.team(), k -> new ArrayList<>()).add(d);

        List<TeamStanding> teams = new ArrayList<>();
        for (Map.Entry<String, List<DriverStanding>> e : byTeam.entrySet()) {
            int pts = e.getValue().stream().mapToInt(DriverStanding::points).sum();
            int wins = e.getValue().stream().mapToInt(DriverStanding::wins).sum();
            String color = resolveColor(e.getKey(),
                    e.getValue().isEmpty() ? "#888888" : e.getValue().get(0).color());
            teams.add(new TeamStanding(e.getKey(), color, pts, wins, e.getValue()));
        }
        teams.sort(Comparator.comparingInt(TeamStanding::points).reversed());

        SeasonStats stats = new SeasonStats(races, drivers, teams, false, races.size(), false);
        if (!races.isEmpty()) {
            cache.put(year, stats);
            cacheStore.save(year, stats);
            prefetchAllReplays(year, races);
        }
    }

    /** Lädt GPS-Positionen aller abgeschlossenen Rennen im Hintergrund nach dem Season-Build. */
    private void prefetchAllReplays(int year, List<Race> races) {
        Thread.ofVirtual().name("replay-prefetch-" + year).start(() -> {
            io.quarkus.arc.Arc.container().requestContext().activate();
            try {
                for (Race r : races) {
                    if (!r.completed() || r.sessionKey() == 0 || r.sessionDateStart() == null) continue;
                    if (dataStore.locationsExistForSession(r.sessionKey())) continue;
                    LOG.infof("Replay-Prefetch %d: lade GPS für %s (Session %d) …",
                            year, r.gp(), r.sessionKey());
                    try {
                        replayService.getReplay(r.sessionKey(), r.sessionDateStart());
                    } catch (Exception e) {
                        LOG.warnf("Replay-Prefetch %d: Fehler für Session %d: %s",
                                year, r.sessionKey(), e.getMessage());
                    }
                }
                LOG.infof("Replay-Prefetch %d: alle Sessions abgeschlossen", year);
            } finally {
                try { io.quarkus.arc.Arc.container().requestContext().terminate(); } catch (Exception ignored) {}
            }
        });
    }

    private <T> List<T> fetch(Supplier<List<T>> call) {
        int attempts = 0;
        while (true) {
            try {
                Thread.sleep(THROTTLE_MS);
                List<T> result = call.get();
                lastFetchWas401 = false;
                return result;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return List.of();
            } catch (WebApplicationException e) {
                int s = e.getResponse() != null ? e.getResponse().getStatus() : 0;
                if (s == 401) {
                    String body = "";
                    try { body = e.getResponse().readEntity(String.class); } catch (Exception ignored) {}
                    if (body.contains("Live F1 session")) {
                        lastFetchWas401 = true;
                    }
                    return List.of();
                }
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