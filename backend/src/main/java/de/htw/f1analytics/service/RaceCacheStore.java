package de.htw.f1analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htw.f1analytics.domain.RaceCacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RaceCacheStore {

    private static final Logger LOG = Logger.getLogger(RaceCacheStore.class);

    @Inject
    ObjectMapper mapper;

    @Transactional
    public Optional<SeasonService.Race> load(int sessionKey) {
        RaceCacheEntity e = RaceCacheEntity.findById(sessionKey);
        if (e == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(e.raceJson, SeasonService.Race.class));
        } catch (Exception ex) {
            LOG.warnf("race_cache für session %d konnte nicht gelesen werden: %s", sessionKey, ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void save(int sessionKey, int year, SeasonService.Race race) {
        try {
            String json = mapper.writeValueAsString(race);
            RaceCacheEntity e = RaceCacheEntity.findById(sessionKey);
            if (e == null) {
                e = new RaceCacheEntity();
                e.sessionKey = sessionKey;
                e.year = year;
                e.raceJson = json;
                e.cachedAt = Instant.now();
                e.persist();
            } else {
                e.raceJson = json;
                e.cachedAt = Instant.now();
            }
        } catch (Exception ex) {
            LOG.warnf("race_cache für session %d konnte nicht gespeichert werden: %s", sessionKey, ex.getMessage());
        }
    }

    @Transactional
    public void delete(int sessionKey) {
        RaceCacheEntity e = RaceCacheEntity.findById(sessionKey);
        if (e != null) e.delete();
    }

    @Transactional
    public void deleteForYear(int year) {
        RaceCacheEntity.delete("year", year);
    }

    public List<RaceCacheEntity> listForYear(int year) {
        return RaceCacheEntity.list("year", year);
    }
}
