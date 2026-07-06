package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1Client;
import de.htw.f1analytics.client.OpenF1GridDto;
import de.htw.f1analytics.client.OpenF1IntervalDto;
import de.htw.f1analytics.client.OpenF1LapDto;
import de.htw.f1analytics.client.OpenF1PositionDto;
import de.htw.f1analytics.client.OpenF1RaceControlDto;
import de.htw.f1analytics.client.OpenF1StintDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Liefert die Zusatzdaten für die Live-Timing-Anzeige neben dem Replay (Reifen, Rundenzeiten,
 *  Sektorzeiten, Positionsverlauf, Flaggen). Im Gegensatz zu {@link ReplayService} pro Session
 *  nur RAM-gecacht — die zugrundeliegenden OpenF1-Endpunkte liefern jeweils die GESAMTE Session
 *  in einem Aufruf (kein Pro-Fahrer-Loop wie bei /location), das Neuladen ist also günstig. */
@ApplicationScoped
public class ReplayTimingService {

    private static final Logger LOG = Logger.getLogger(ReplayTimingService.class);

    @RestClient
    OpenF1Client openF1Client;

    private static final long THROTTLE_MS = 2500;
    private static final int MAX_RETRY = 3;

    private final Map<Integer, TimingData> ramCache = new ConcurrentHashMap<>();
    private final Map<Integer, Object> sessionLocks = new ConcurrentHashMap<>();

    /** t = Sekunden seit Rennstart, wie ReplayService.ReplayFrame.t. */
    public record LapInfo(int driverNumber, int lapNumber, double t, Double duration,
                           Double sector1, Double sector2, Double sector3) {}

    public record StintInfo(int driverNumber, String compound, int lapStart, int lapEnd) {}

    public record PositionPoint(int driverNumber, int position, double t) {}

    public record FlagEvent(double t, String flag, String scope) {}

    /** gapSec = Abstand zum Vordermann in Sekunden, oder null (Führender / überrundet —
     *  OpenF1 liefert dafür Text wie "+1 LAP" statt einer Zahl, siehe parseGap). */
    public record IntervalPoint(int driverNumber, Double gapSec, double t) {}

    public record TimingData(List<LapInfo> laps, List<StintInfo> stints,
                              List<PositionPoint> positions, List<FlagEvent> flags,
                              List<IntervalPoint> intervals, Map<Integer, Integer> gridPosition) {}

    public void clearTiming(int sessionKey) {
        ramCache.remove(sessionKey);
    }

    public TimingData getTiming(int sessionKey, String sessionDateStart) {
        TimingData cached = ramCache.get(sessionKey);
        if (cached != null) return cached;

        Object lock = sessionLocks.computeIfAbsent(sessionKey, k -> new Object());
        synchronized (lock) {
            cached = ramCache.get(sessionKey);
            if (cached != null) return cached;

            long raceStartMs = (sessionDateStart != null && !sessionDateStart.isBlank())
                    ? ReplayService.parseIso(sessionDateStart) : 0;

            List<OpenF1LapDto> rawLaps = fetch(() -> openF1Client.getLaps(sessionKey));
            List<OpenF1StintDto> rawStints = fetch(() -> openF1Client.getStints(sessionKey));
            List<OpenF1PositionDto> rawPositions = fetch(() -> openF1Client.getPosition(sessionKey));
            List<OpenF1RaceControlDto> rawFlags = fetch(() -> openF1Client.getRaceControl(sessionKey));
            List<OpenF1GridDto> rawGrid = fetch(() -> openF1Client.getStartingGrid(sessionKey));
            List<OpenF1IntervalDto> rawIntervals = fetch(() -> openF1Client.getIntervals(sessionKey));

            List<LapInfo> laps = new ArrayList<>();
            for (OpenF1LapDto lap : rawLaps) {
                if (lap.driverNumber() == null || lap.lapNumber() == null || lap.dateStart() == null) continue;
                long ts = ReplayService.parseIso(lap.dateStart());
                if (ts <= 0) continue;
                double t = raceStartMs > 0 ? (ts - raceStartMs) / 1000.0 : 0;
                laps.add(new LapInfo(lap.driverNumber(), lap.lapNumber(), t, lap.lapDuration(),
                        lap.durationSector1(), lap.durationSector2(), lap.durationSector3()));
            }
            laps.sort(Comparator.comparingDouble(LapInfo::t));

            List<StintInfo> stints = new ArrayList<>();
            for (OpenF1StintDto s : rawStints) {
                if (s.driverNumber() == null || s.compound() == null) continue;
                stints.add(new StintInfo(s.driverNumber(), s.compound(),
                        s.lapStart() != null ? s.lapStart() : 1,
                        s.lapEnd() != null ? s.lapEnd() : Integer.MAX_VALUE));
            }

            List<PositionPoint> positions = new ArrayList<>();
            for (OpenF1PositionDto p : rawPositions) {
                if (p.driverNumber() == null || p.position() == null || p.date() == null) continue;
                long ts = ReplayService.parseIso(p.date());
                if (ts <= 0) continue;
                double t = raceStartMs > 0 ? (ts - raceStartMs) / 1000.0 : 0;
                positions.add(new PositionPoint(p.driverNumber(), p.position(), t));
            }
            positions.sort(Comparator.comparingDouble(PositionPoint::t));

            List<FlagEvent> flags = new ArrayList<>();
            for (OpenF1RaceControlDto rc : rawFlags) {
                if (rc.flag() == null || rc.date() == null) continue;
                long ts = ReplayService.parseIso(rc.date());
                if (ts <= 0) continue;
                double t = raceStartMs > 0 ? (ts - raceStartMs) / 1000.0 : 0;
                flags.add(new FlagEvent(t, rc.flag(), rc.scope()));
            }
            flags.sort(Comparator.comparingDouble(FlagEvent::t));

            List<IntervalPoint> intervals = new ArrayList<>();
            for (OpenF1IntervalDto iv : rawIntervals) {
                if (iv.driverNumber() == null || iv.date() == null) continue;
                long ts = ReplayService.parseIso(iv.date());
                if (ts <= 0) continue;
                double t = raceStartMs > 0 ? (ts - raceStartMs) / 1000.0 : 0;
                intervals.add(new IntervalPoint(iv.driverNumber(), parseGap(iv.interval()), t));
            }
            intervals.sort(Comparator.comparingDouble(IntervalPoint::t));

            Map<Integer, Integer> gridPosition = new HashMap<>();
            for (OpenF1GridDto g : rawGrid) {
                if (g.driverNumber() != null && g.position() != null) {
                    gridPosition.put(g.driverNumber(), g.position());
                }
            }
            // Fallback falls Startaufstellung fehlt (z.B. Sprint-Session ohne eigenes Grid):
            // erste bekannte Live-Position als Grid-Ersatz verwenden.
            if (gridPosition.isEmpty()) {
                for (PositionPoint p : positions) {
                    gridPosition.putIfAbsent(p.driverNumber(), p.position());
                }
            }

            TimingData data = new TimingData(laps, stints, positions, flags, intervals, gridPosition);
            ramCache.put(sessionKey, data);
            LOG.infof("Timing %d: %d Runden, %d Stints, %d Positionswechsel, %d Flaggen, %d Intervalle geladen",
                    sessionKey, laps.size(), stints.size(), positions.size(), flags.size(), intervals.size());
            return data;
        }
    }

    /** OpenF1 liefert interval/gap_to_leader als Zahl (Sekunden), Text ("+1 LAP") oder null
     *  (Führender). Nur der Zahlenfall ergibt einen sinnvollen Sekundenabstand. */
    private static Double parseGap(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
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
