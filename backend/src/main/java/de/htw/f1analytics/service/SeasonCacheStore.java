package de.htw.f1analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htw.f1analytics.domain.SeasonCacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class SeasonCacheStore {

    private static final Logger LOG = Logger.getLogger(SeasonCacheStore.class);

    public static final int CACHE_VERSION = 2;

    @Inject
    ObjectMapper mapper;

    @Transactional
    public SeasonService.SeasonStats load(int year) {
        SeasonCacheEntity e = SeasonCacheEntity.findById(year);
        if (e == null) return null;
        if (e.cacheVersion == null || e.cacheVersion != CACHE_VERSION) {
            LOG.infof("DB-Cache für %d ist veraltet (Version %s, erwartet %d) und wird neu aufgebaut.", year, e.cacheVersion, CACHE_VERSION);
            return null;
        }
        try {
            return mapper.readValue(e.statsJson, SeasonService.SeasonStats.class);
        } catch (Exception ex) {
            LOG.warnf("DB-Cache für %d konnte nicht gelesen werden: %s", year, ex.getMessage());
            return null;
        }
    }

    @Transactional
    public void delete(int year) {
        SeasonCacheEntity e = SeasonCacheEntity.findById(year);
        if (e != null) e.delete();
    }

    @Transactional
    public void save(int year, SeasonService.SeasonStats stats) {
        try {
            String json = mapper.writeValueAsString(stats);
            SeasonCacheEntity e = SeasonCacheEntity.findById(year);
            if (e == null) {
                e = new SeasonCacheEntity();
                e.year = year;
                e.statsJson = json;
                e.cachedAt = Instant.now();
                e.cacheVersion = CACHE_VERSION;
                e.persist();
            } else {
                e.statsJson = json;
                e.cachedAt = Instant.now();
                e.cacheVersion = CACHE_VERSION;
            }
        } catch (Exception ex) {
            LOG.warnf("DB-Cache für %d konnte nicht gespeichert werden: %s", year, ex.getMessage());
        }
    }
}