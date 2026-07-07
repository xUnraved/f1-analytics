package de.htw.f1analytics.service;

import de.htw.f1analytics.domain.Tipp;
import de.htw.f1analytics.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BettingService {

    @Inject
    SeasonService seasonService;

    public record TippDto(Long id, String username, String userColor,
                          int year, int round, String category, String pick,
                          Integer points, Instant createdAt, Instant settledAt) {}

    public record LeaderboardEntry(String username, String color,
                                   int totalPoints, int settledTipps, int openTipps) {}

    public record CrowdPick(String category, String pick, int count) {}

    public record RaceInfo(int year, int round, String gp, String country, String date,
                           String countryFlag, String circuitImage, boolean tipsOpen) {}

    public static final String CAT_WINNER = "WINNER";
    public static final String CAT_POLE = "POLE";
    public static final String CAT_PODIUM = "PODIUM";
    public static final String CAT_FASTEST_LAP = "FASTEST_LAP";
    public static final String CAT_H2H = "H2H";

    private static final List<String> CATEGORIES = List.of(CAT_WINNER, CAT_POLE, CAT_PODIUM, CAT_FASTEST_LAP, CAT_H2H);

    @Transactional
    public TippDto submit(User user, int year, int round, String category, String pick) {
        validateCategory(category);
        validatePick(category, pick);

        SeasonService.Race race = findRace(year, round);
        if (race == null) throw new NotFoundException("Rennen nicht gefunden: " + year + "/R" + round);
        if (race.completed()) throw new BadRequestException("Tipps für dieses Rennen sind bereits geschlossen.");

        Tipp existing = Tipp.findOne(user.id, year, round, category);
        if (existing != null) {
            existing.pick = pick;
            existing.createdAt = Instant.now();
            existing.points = null;
            existing.settledAt = null;
            return toDto(existing);
        }

        Tipp t = new Tipp();
        t.user = user;
        t.year = year;
        t.round = round;
        t.category = category;
        t.pick = pick;
        t.createdAt = Instant.now();
        t.persist();
        return toDto(t);
    }

    @Transactional
    public void delete(User user, int year, int round, String category) {
        SeasonService.Race race = findRace(year, round);
        if (race != null && race.completed()) {
            throw new BadRequestException("Tipps für ein abgeschlossenes Rennen können nicht gelöscht werden.");
        }
        Tipp existing = Tipp.findOne(user.id, year, round, category);
        if (existing != null) existing.delete();
    }

    @Transactional
    public List<TippDto> tippsForRace(int year, int round) {
        SeasonService.Race race = findRace(year, round);
        List<Tipp> tipps = Tipp.findByRace(year, round);
        if (race != null && race.completed()) {
            for (Tipp t : tipps) settleIfNeeded(t, race);
        }
        return tipps.stream().map(this::toDto).toList();
    }

    @Transactional
    public List<TippDto> myTipps(User user, int year) {
        List<Tipp> all = Tipp.findByUserYear(user.id, year);
        for (Tipp t : all) {
            SeasonService.Race race = findRace(t.year, t.round);
            if (race != null && race.completed()) settleIfNeeded(t, race);
        }
        return all.stream().map(this::toDto).toList();
    }

    @Transactional
    public List<LeaderboardEntry> leaderboard(int year) {
        SeasonService.SeasonStats stats = seasonService.seasonStats(year);
        Map<Long, SeasonService.Race> raceByRound = new HashMap<>();
        for (SeasonService.Race r : stats.races()) raceByRound.put((long) r.round(), r);

        for (Tipp t : Tipp.<Tipp>list("year", year)) {
            SeasonService.Race race = raceByRound.get((long) t.round);
            if (race != null && race.completed()) settleIfNeeded(t, race);
        }

        Map<Long, LeaderboardAcc> acc = new LinkedHashMap<>();
        for (Tipp t : Tipp.<Tipp>list("year", year)) {
            LeaderboardAcc a = acc.computeIfAbsent(t.user.id, k -> new LeaderboardAcc(t.user.username, t.user.color));
            if (t.points != null) {
                a.total += t.points;
                a.settled++;
            } else {
                a.open++;
            }
        }
        List<LeaderboardEntry> result = new ArrayList<>();
        for (LeaderboardAcc a : acc.values()) {
            result.add(new LeaderboardEntry(a.username, a.color, a.total, a.settled, a.open));
        }
        result.sort(Comparator.comparingInt(LeaderboardEntry::totalPoints).reversed()
                .thenComparing(Comparator.comparingInt(LeaderboardEntry::settledTipps).reversed()));
        return result;
    }

    public List<CrowdPick> crowdPicks(int year, int round) {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (String c : CATEGORIES) counts.put(c, new LinkedHashMap<>());
        for (Tipp t : Tipp.findByRace(year, round)) {
            Map<String, Integer> m = counts.get(t.category);
            if (m != null) m.merge(t.pick, 1, Integer::sum);
        }
        List<CrowdPick> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : counts.entrySet()) {
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(e.getValue().entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            for (Map.Entry<String, Integer> pe : sorted) {
                result.add(new CrowdPick(e.getKey(), pe.getKey(), pe.getValue()));
            }
        }
        return result;
    }

    public List<RaceInfo> upcomingRaces(int year) {
        SeasonService.SeasonStats stats = seasonService.seasonStats(year);
        List<RaceInfo> result = new ArrayList<>();
        for (SeasonService.Race r : stats.races()) {
            if (!r.completed()) {
                result.add(new RaceInfo(year, r.round(), r.gp(), r.country(), r.date(),
                        r.countryFlag(), r.circuitImage(), true));
            }
        }
        return result;
    }

    private void settleIfNeeded(Tipp t, SeasonService.Race race) {
        if (t.points != null) return;
        if (!race.completed()) return;
        t.points = computePoints(race, t.category, t.pick);
        t.settledAt = Instant.now();
    }

    int computePoints(SeasonService.Race race, String category, String pick) {
        switch (category) {
            case CAT_WINNER -> {
                if (race.result() == null || race.result().isEmpty()) return 0;
                return matches(pick, race.result().get(0).abbr()) ? 10 : 0;
            }
            case CAT_POLE -> {
                if (race.qualifyingResult() == null || race.qualifyingResult().isEmpty()) return 0;
                return matches(pick, race.qualifyingResult().get(0).abbr()) ? 5 : 0;
            }
            case CAT_PODIUM -> {
                String[] picks = pick.split("\\|");
                if (picks.length != 3) return 0;
                int pts = 0;
                for (int i = 0; i < 3; i++) {
                    if (race.result() != null && race.result().size() > i &&
                            matches(picks[i], race.result().get(i).abbr())) pts += 5;
                }
                return pts;
            }
            case CAT_FASTEST_LAP -> {
                if (race.fastestLap() == null) return 0;
                return matches(pick, race.fastestLap().abbr()) ? 3 : 0;
            }
            case CAT_H2H -> {
                String[] parts = pick.split("\\|");
                if (parts.length != 2) return 0;
                Integer posA = finishPosition(race, parts[0]);
                Integer posB = finishPosition(race, parts[1]);
                if (posA == null || posB == null) return 0;
                return posA < posB ? 4 : 0;
            }
            default -> {
                return 0;
            }
        }
    }

    private static Integer finishPosition(SeasonService.Race race, String abbr) {
        if (race.result() == null || abbr == null) return null;
        for (SeasonService.ResultRow row : race.result()) {
            if (row.abbr() != null && row.abbr().equalsIgnoreCase(abbr.trim())) return row.pos();
        }
        return null;
    }

    private static boolean matches(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private SeasonService.Race findRace(int year, int round) {
        SeasonService.SeasonStats stats = seasonService.seasonStats(year);
        for (SeasonService.Race r : stats.races()) {
            if (r.round() == round) return r;
        }
        return null;
    }

    private void validateCategory(String category) {
        if (!CATEGORIES.contains(category)) throw new BadRequestException("Unbekannte Kategorie: " + category);
    }

    void validatePick(String category, String pick) {
        if (pick == null || pick.trim().isEmpty()) throw new BadRequestException("Tipp fehlt.");
        if (CAT_PODIUM.equals(category)) {
            String[] parts = pick.split("\\|");
            if (parts.length != 3) throw new BadRequestException("Podium-Tipp braucht 3 Fahrer (Format: ABBR|ABBR|ABBR).");
            for (String p : parts) {
                if (p == null || p.trim().isEmpty()) throw new BadRequestException("Podium-Tipp unvollständig.");
            }
            if (parts[0].equalsIgnoreCase(parts[1]) || parts[1].equalsIgnoreCase(parts[2])
                    || parts[0].equalsIgnoreCase(parts[2])) {
                throw new BadRequestException("Podium-Tipp: alle 3 Fahrer müssen unterschiedlich sein.");
            }
        }
        if (CAT_H2H.equals(category)) {
            String[] parts = pick.split("\\|");
            if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                throw new BadRequestException("Head-to-Head braucht 2 Fahrer (Format: ABBR|ABBR).");
            }
            if (parts[0].trim().equalsIgnoreCase(parts[1].trim())) {
                throw new BadRequestException("Head-to-Head: beide Fahrer müssen unterschiedlich sein.");
            }
        }
    }

    private TippDto toDto(Tipp t) {
        return new TippDto(t.id, t.user.username, t.user.color,
                t.year, t.round, t.category, t.pick, t.points, t.createdAt, t.settledAt);
    }

    private static final class LeaderboardAcc {
        final String username;
        final String color;
        int total;
        int settled;
        int open;

        LeaderboardAcc(String username, String color) {
            this.username = username;
            this.color = color;
        }
    }
}