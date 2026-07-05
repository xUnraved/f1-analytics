package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1SessionDto;
import de.htw.f1analytics.domain.F1LocationEntity;
import de.htw.f1analytics.domain.F1ResultEntity;
import de.htw.f1analytics.domain.F1SessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Zentraler Datenbankzugriff für alle F1-Rohdaten.
 * Trennt Persistenz klar vom Aggregations-Service.
 */
@ApplicationScoped
public class F1DataStore {

    private static final Logger LOG = Logger.getLogger(F1DataStore.class);

    @Inject
    EntityManager em;

    @Transactional
    public void saveSession(F1SessionEntity e) {
        F1SessionEntity existing = F1SessionEntity.findById(e.sessionKey);
        if (existing == null) em.persist(e);
        else {
            existing.circuitImage = e.circuitImage;
            existing.countryFlag  = e.countryFlag;
            existing.lat          = e.lat;
            existing.lon          = e.lon;
        }
    }

    public boolean sessionsExistForYear(int year) {
        return F1SessionEntity.existsForYear(year);
    }

    public List<F1SessionEntity> getSessionsByYearAndName(int year, String name) {
        return F1SessionEntity.findByYearAndName(year, name);
    }

    public F1SessionEntity getSession(int sessionKey) {
        return F1SessionEntity.findByKey(sessionKey);
    }

    @Transactional
    public void saveResults(List<F1ResultEntity> rows) {
        for (F1ResultEntity r : rows) em.persist(r);
    }

    public boolean resultsExistForSession(int sessionKey) {
        return F1ResultEntity.existsForSession(sessionKey);
    }

    public List<F1ResultEntity> getResults(int sessionKey) {
        return F1ResultEntity.findBySession(sessionKey);
    }

    @Transactional
    public void deleteResults(int sessionKey) {
        F1ResultEntity.deleteBySession(sessionKey);
    }

    @Transactional
    public void saveLocations(List<F1LocationEntity> rows) {
        if (rows.isEmpty()) return;
        int batchSize = 500;
        for (int i = 0; i < rows.size(); i++) {
            em.persist(rows.get(i));
            if ((i + 1) % batchSize == 0) {
                em.flush();
                em.clear();
            }
        }
        LOG.infof("Replay: %d Positionen für Session %d gespeichert",
                rows.size(), rows.get(0).sessionKey);
    }

    public boolean locationsExistForSession(int sessionKey) {
        return F1LocationEntity.existsForSession(sessionKey);
    }

    public List<F1LocationEntity> getLocations(int sessionKey) {
        return F1LocationEntity.findBySession(sessionKey);
    }

    @Transactional
    public void deleteLocations(int sessionKey) {
        F1LocationEntity.deleteBySession(sessionKey);
    }

    @Transactional
    public void deleteYear(int year) {
        List<F1SessionEntity> sessions = F1SessionEntity.list("year", year);
        for (F1SessionEntity s : sessions) {
            F1ResultEntity.deleteBySession(s.sessionKey);
            F1LocationEntity.deleteBySession(s.sessionKey);
        }
        F1SessionEntity.delete("year", year);
    }

    public static F1SessionEntity fromDto(OpenF1SessionDto dto, int year,
                                           String circuitImage, String countryFlag,
                                           double lat, double lon) {
        F1SessionEntity e = new F1SessionEntity();
        e.sessionKey       = dto.sessionKey();
        e.year             = year;
        e.meetingKey       = dto.meetingKey();
        e.sessionName      = dto.sessionName();
        e.location         = dto.location();
        e.countryName      = dto.countryName();
        e.circuitShortName = dto.circuitShortName();
        e.dateStart        = dto.dateStart();
        e.circuitImage     = circuitImage;
        e.countryFlag      = countryFlag;
        e.lat              = lat;
        e.lon              = lon;
        return e;
    }

    public static F1ResultEntity raceResult(int sessionKey, int driverNumber,
            String abbr, String name, String team, String color,
            int pos, int pts, String gapText,
            boolean dnf, boolean dns, boolean dsq, Integer laps) {
        F1ResultEntity e = new F1ResultEntity();
        e.sessionKey    = sessionKey;
        e.driverNumber  = driverNumber;
        e.abbr          = abbr;
        e.name          = name;
        e.team          = team;
        e.color         = color;
        e.pos           = pos;
        e.pts           = pts;
        e.gapText       = gapText;
        e.dnf           = dnf;
        e.dns           = dns;
        e.dsq           = dsq;
        e.laps          = laps;
        return e;
    }

    public static F1ResultEntity sessionResult(int sessionKey, int driverNumber,
            String abbr, String name, String team, String color,
            int pos, String bestLap, String gap,
            boolean dnf, boolean dns, boolean dsq) {
        F1ResultEntity e = new F1ResultEntity();
        e.sessionKey   = sessionKey;
        e.driverNumber = driverNumber;
        e.abbr         = abbr;
        e.name         = name;
        e.team         = team;
        e.color        = color;
        e.pos          = pos;
        e.bestLap      = bestLap;
        e.gapText      = gap;
        e.dnf          = dnf;
        e.dns          = dns;
        e.dsq          = dsq;
        return e;
    }

    public static F1LocationEntity location(int sessionKey, int driverNumber,
                                             int tSeconds, double x, double y) {
        F1LocationEntity e = new F1LocationEntity();
        e.sessionKey    = sessionKey;
        e.driverNumber  = driverNumber;
        e.tSeconds      = tSeconds;
        e.x             = x;
        e.y             = y;
        return e;
    }
}
