package de.htw.f1analytics.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CacheWarmer {

    private static final Logger LOG = Logger.getLogger(CacheWarmer.class);

    @Inject
    SeasonService seasonService;

    void onStart(@Observes StartupEvent ev) {
        int cur = Year.now().getValue();
        List<Integer> years = new ArrayList<>();
        years.add(cur);
        if (cur - 1 >= SeasonService.START_YEAR) years.add(cur - 1);

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