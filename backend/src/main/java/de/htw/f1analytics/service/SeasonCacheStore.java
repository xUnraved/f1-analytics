package de.htw.f1analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htw.f1analytics.domain.SeasonCacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Persistiert und lädt die aggregierten Saisondaten (SeasonStats) als JSON-Blob in PostgreSQL.
 * Zweite Cache-Ebene hinter dem RAM-Cache – überlebt Quarkus-Neustarts.
 *
 * Bei Deserialisierungs-Fehlern (z. B. Formatänderung nach Code-Update) wird der
 * fehlerhafte Eintrag ignoriert und ein neuer API-Abruf ausgelöst.
 */
@ApplicationScoped
public class SeasonCacheStore {

    private static final Logger LOG = Logger.getLogger(SeasonCacheStore.class);

    @Inject
    ObjectMapper mapper;

    @Transactional
    public SeasonService.SeasonStats load(int year) {
        SeasonCacheEntity e = SeasonCacheEntity.findById(year);
        if (e == null) return null;
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
                e.persist();
            } else {
                e.statsJson = json;
                e.cachedAt = Instant.now();
            }
        } catch (Exception ex) {
            LOG.warnf("DB-Cache für %d konnte nicht gespeichert werden: %s", year, ex.getMessage());
        }
    }
}