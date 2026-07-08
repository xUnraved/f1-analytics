package de.htw.f1analytics.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Wärmt den Saison-Cache beim Quarkus-Start asynchron auf.
 *
 * Beim Start wird für jede Saison (aktuelles Jahr bis START_YEAR, neueste zuerst)
 * ensureCached() aufgerufen. Falls die Daten bereits im DB-Cache liegen, ist dies
 * ein reiner DB-Lesezugriff. Sind sie nicht gecacht, werden sie von der OpenF1-API
 * geladen (kann mehrere Minuten dauern).
 *
 * Läuft in einem virtuellen Thread ("cache-warmer") damit der Quarkus-Start
 * nicht blockiert wird.
 */
@ApplicationScoped
public class CacheWarmer {

    private static final Logger LOG = Logger.getLogger(CacheWarmer.class);

    @Inject
    SeasonService seasonService;

    void onStart(@Observes StartupEvent ev) {
        int cur = Year.now().getValue();
        List<Integer> years = new ArrayList<>();
        for (int y = cur; y >= SeasonService.START_YEAR; y--) years.add(y);

        Thread.ofVirtual().name("cache-warmer").start(() -> {
            for (int y : years) {
                LOG.infof("Cache-Warming Saison %d ...", y);
                try {
                    seasonService.ensureCached(y);
                    SeasonService.SeasonStats s = seasonService.seasonStats(y);
                    LOG.infof("Cache-Warming %d fertig: %d Rennen, %d Fahrer", y, s.races().size(), s.drivers().size());
                } catch (Exception e) {
                    LOG.warnf("Cache-Warming %d fehlgeschlagen: %s", y, e.getMessage());
                }
            }
        });
    }
}