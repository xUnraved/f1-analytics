package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1Client;
import de.htw.f1analytics.client.OpenF1DriverDto;
import de.htw.f1analytics.client.OpenF1LocationDto;
import de.htw.f1analytics.domain.F1LocationEntity;
import de.htw.f1analytics.domain.F1SessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@ApplicationScoped
public class ReplayService {

    private static final Logger LOG = Logger.getLogger(ReplayService.class);

    @RestClient
    OpenF1Client openF1Client;

    @Inject
    F1DataStore dataStore;

    private static final long THROTTLE_MS = 2500;
    private static final int MAX_RETRY = 3;

    /** RAM-Cache für bereits gebaute ReplayData (ergänzt den DB-Cache) */
    private final Map<Integer, ReplayData> ramCache = new ConcurrentHashMap<>();

    public record ReplayDriver(int num, String abbr, String name, String team, String color) {}

    public record ReplayFrame(int t, Map<Integer, double[]> p) {}

    public record ReplayData(List<ReplayDriver> drivers, List<ReplayFrame> frames, int duration) {}

    public void clearReplay(int sessionKey) {
        ramCache.remove(sessionKey);
        dataStore.deleteLocations(sessionKey);
        LOG.infof("Replay %d: Cache und DB-Daten gelöscht", sessionKey);
    }

    public ReplayData getReplay(int sessionKey, String sessionDateStart) {
        // 1. RAM-Cache
        ReplayData cached = ramCache.get(sessionKey);
        if (cached != null) return cached;

        // 2. PostgreSQL — Positionen bereits gespeichert?
        if (dataStore.locationsExistForSession(sessionKey)) {
            LOG.infof("Replay %d: aus PostgreSQL geladen", sessionKey);
            ReplayData fromDb = buildFromDb(sessionKey);
            ramCache.put(sessionKey, fromDb);
            return fromDb;
        }

        // 3. OpenF1 API abrufen, in DB speichern, zurückgeben
        LOG.infof("Replay %d: von OpenF1 API laden ...", sessionKey);
        ReplayData fresh = buildFromApi(sessionKey, sessionDateStart);
        // Leere Ergebnisse NICHT cachen — nächste Anfrage soll es erneut versuchen (z.B. nach einer Live-Session)
        if (!fresh.frames().isEmpty()) {
            ramCache.put(sessionKey, fresh);
        }
        return fresh;
    }

    private ReplayData buildFromDb(int sessionKey) {
        List<F1LocationEntity> rows = dataStore.getLocations(sessionKey);
        if (rows.isEmpty()) return empty();

        Map<Integer, ReplayDriver> driverMap = loadDriversFromSession(sessionKey);
        TreeMap<Integer, Map<Integer, double[]>> bySecond = new TreeMap<>();
        for (F1LocationEntity loc : rows) {
            bySecond.computeIfAbsent(loc.tSeconds, k -> new HashMap<>())
                    .put(loc.driverNumber, new double[]{loc.x, loc.y});
        }
        return buildFrames(bySecond, driverMap);
    }

    private Map<Integer, ReplayDriver> loadDriversFromSession(int sessionKey) {
        Map<Integer, ReplayDriver> map = new LinkedHashMap<>();
        for (var r : dataStore.getResults(sessionKey)) {
            map.put(r.driverNumber, new ReplayDriver(r.driverNumber, r.abbr, r.name, r.team, r.color));
        }
        return map;
    }

    private ReplayData buildFromApi(int sessionKey, String sessionDateStart) {
        Map<Integer, ReplayDriver> driverMap = new LinkedHashMap<>();
        for (OpenF1DriverDto d : fetch(() -> openF1Client.getDrivers(sessionKey))) {
            if (d.driverNumber() == null) continue;
            String color = d.teamColour() != null ? "#" + d.teamColour() : "#888888";
            driverMap.put(d.driverNumber(), new ReplayDriver(
                    d.driverNumber(),
                    d.nameAcronym() != null ? d.nameAcronym() : "#" + d.driverNumber(),
                    d.fullName() != null ? d.fullName() : "#" + d.driverNumber(),
                    d.teamName() != null ? d.teamName() : "—",
                    color));
        }

        if (driverMap.isEmpty()) {
            LOG.warnf("Replay %d: keine Fahrer von API — abgebrochen", sessionKey);
            return empty();
        }

        // Zeitfenster berechnen (parseIso nimmt UTC an wenn kein Offset vorhanden)
        long raceStartMs = (sessionDateStart != null && !sessionDateStart.isBlank())
                ? parseIso(sessionDateStart) : 0;
        long from = raceStartMs > 0 ? raceStartMs - 30 * 60_000L : 0;
        long to   = raceStartMs > 0 ? raceStartMs + 4 * 3600_000L : Long.MAX_VALUE;
        LOG.infof("Replay %d: lade GPS pro Fahrer, Zeitfenster %s ± 30min/4h …",
                sessionKey, sessionDateStart);

        List<OpenF1LocationDto> locations = new ArrayList<>();
        for (int num : driverMap.keySet()) {
            int n = num;
            List<OpenF1LocationDto> raw = fetch(() -> openF1Client.getLocations(sessionKey, n));
            for (OpenF1LocationDto loc : raw) {
                if (loc.date() == null || loc.x() == null || loc.y() == null) continue;
                long ts = parseIso(loc.date());
                if (raceStartMs > 0 && (ts < from || ts > to)) continue;
                locations.add(loc);
            }
        }
        LOG.infof("Replay %d: %d Punkte nach Zeitfilter", sessionKey, locations.size());

        if (locations.isEmpty()) return empty();

        long startMs = locations.stream()
                .map(OpenF1LocationDto::date)
                .filter(Objects::nonNull)
                .mapToLong(ReplayService::parseIso)
                .filter(t -> t > 0)
                .min()
                .orElse(raceStartMs > 0 ? raceStartMs : 0);

        TreeMap<Integer, Map<Integer, double[]>> bySecond = new TreeMap<>();
        for (OpenF1LocationDto loc : locations) {
            if (loc.driverNumber() == null) continue;
            long ts = parseIso(loc.date());
            if (ts <= 0) continue;
            int sec = (int) ((ts - startMs) / 1000);
            if (sec < 0) continue;
            bySecond.computeIfAbsent(sec, k -> new HashMap<>())
                    .put(loc.driverNumber(), new double[]{loc.x(), loc.y()});
        }

        if (bySecond.isEmpty()) return empty();

        List<F1LocationEntity> toSave = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, double[]>> e : bySecond.entrySet()) {
            for (Map.Entry<Integer, double[]> d : e.getValue().entrySet()) {
                toSave.add(F1DataStore.location(sessionKey, d.getKey(), e.getKey(), d.getValue()[0], d.getValue()[1]));
            }
        }
        dataStore.saveLocations(toSave);
        LOG.infof("Replay %d: %d Positionen in DB gespeichert", sessionKey, toSave.size());

        return buildFrames(bySecond, driverMap);
    }

    private static ReplayData buildFrames(TreeMap<Integer, Map<Integer, double[]>> bySecond,
                                           Map<Integer, ReplayDriver> driverMap) {
        int maxSec = bySecond.lastKey();
        List<ReplayFrame> frames = new ArrayList<>(maxSec + 1);
        Map<Integer, double[]> lastPos = new HashMap<>();
        for (int t = 0; t <= maxSec; t++) {
            Map<Integer, double[]> snap = bySecond.get(t);
            if (snap != null) lastPos.putAll(snap);
            if (!lastPos.isEmpty()) frames.add(new ReplayFrame(t, new HashMap<>(lastPos)));
        }

        Set<Integer> active = new HashSet<>();
        for (ReplayFrame f : frames) active.addAll(f.p().keySet());
        List<ReplayDriver> drivers = new ArrayList<>();
        for (int num : active) {
            ReplayDriver d = driverMap.get(num);
            drivers.add(d != null ? d : new ReplayDriver(num, "#" + num, "#" + num, "—", "#888888"));
        }

        return new ReplayData(drivers, frames, maxSec);
    }

    private static ReplayData empty() {
        return new ReplayData(List.of(), List.of(), 0);
    }

    static long parseIso(String date) {
        if (date == null) return 0;
        // 1. Standard ISO with offset
        try {
            return OffsetDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {}
        // 2. Truncate sub-millisecond digits, then retry with offset
        String truncated = date.replaceAll("(\\.\\d{3})\\d+", "$1");
        try {
            return OffsetDateTime.parse(truncated, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {}
        // 3. No timezone → assume UTC (OpenF1 DB dates without offset)
        try {
            return OffsetDateTime.parse(truncated + "+00:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
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
                if (s == 429 && attempts < MAX_RETRY) { attempts++; sleepQuiet(65_000L); continue; }
                if (s >= 500 && attempts < MAX_RETRY) { attempts++; sleepQuiet(2000L * attempts); continue; }
                return List.of();
            } catch (ProcessingException e) {
                if (attempts < MAX_RETRY) { attempts++; sleepQuiet(700L * attempts); continue; }
                return List.of();
            }
        }
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
