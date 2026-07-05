package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1DriverDto;
import de.htw.f1analytics.domain.QuizDriverEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizService {

    private static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
            Map.entry("AUS", "Australien"),
            Map.entry("AUT", "Österreich"),
            Map.entry("BEL", "Belgien"),
            Map.entry("BRA", "Brasilien"),
            Map.entry("CAN", "Kanada"),
            Map.entry("CHN", "China"),
            Map.entry("DEN", "Dänemark"),
            Map.entry("DNK", "Dänemark"),
            Map.entry("FIN", "Finnland"),
            Map.entry("FRA", "Frankreich"),
            Map.entry("GBR", "Großbritannien"),
            Map.entry("GER", "Deutschland"),
            Map.entry("DEU", "Deutschland"),
            Map.entry("ITA", "Italien"),
            Map.entry("JPN", "Japan"),
            Map.entry("MEX", "Mexiko"),
            Map.entry("MON", "Monaco"),
            Map.entry("NED", "Niederlande"),
            Map.entry("NZL", "Neuseeland"),
            Map.entry("POL", "Polen"),
            Map.entry("RSA", "Südafrika"),
            Map.entry("SPA", "Spanien"),
            Map.entry("ESP", "Spanien"),
            Map.entry("THA", "Thailand"),
            Map.entry("USA", "USA"),
            Map.entry("CHE", "Schweiz"),
            Map.entry("ARG", "Argentinien"),
            Map.entry("IND", "Indien"),
            Map.entry("RUS", "Russland"),
            Map.entry("SGP", "Singapur")
    );

    public record CircuitQuizItem(String name, String imageUrl) {}

    public record DriverQuizItem(String abbr, String name, String headshotUrl,
                                 String countryCode, String countryName) {}

    public record QuizData(List<CircuitQuizItem> circuits, List<DriverQuizItem> drivers) {}

    @Inject
    EntityManager em;

    public QuizData getQuizData() {
        return new QuizData(getCircuits(), getDrivers());
    }

    @SuppressWarnings("unchecked")
    private List<CircuitQuizItem> getCircuits() {
        List<Object[]> rows = em.createQuery(
                        "SELECT DISTINCT s.location, s.circuitImage FROM F1SessionEntity s " +
                        "WHERE s.sessionName = 'Race' AND s.circuitImage IS NOT NULL AND s.location IS NOT NULL")
                .getResultList();
        return rows.stream()
                .map(r -> new CircuitQuizItem((String) r[0], (String) r[1]))
                .toList();
    }

    private List<DriverQuizItem> getDrivers() {
        return QuizDriverEntity.withHeadshotAndCountry().stream()
                .map(d -> new DriverQuizItem(d.abbr, d.name, d.headshotUrl, d.countryCode, d.countryName))
                .toList();
    }

    @Transactional
    public void saveDriverIfAbsent(OpenF1DriverDto dto) {
        if (dto.driverNumber() == null || dto.headshotUrl() == null || dto.countryCode() == null) return;
        String countryName = COUNTRY_NAMES.get(dto.countryCode().toUpperCase());
        if (countryName == null) return;

        QuizDriverEntity existing = QuizDriverEntity.findById(dto.driverNumber());
        if (existing != null) {
            // update headshot/country if richer data arrived
            if (dto.headshotUrl() != null) existing.headshotUrl = dto.headshotUrl();
            if (countryName != null)        existing.countryName = countryName;
            return;
        }
        QuizDriverEntity e = new QuizDriverEntity();
        e.driverNumber = dto.driverNumber();
        e.abbr         = dto.nameAcronym() != null ? dto.nameAcronym() : "#" + dto.driverNumber();
        e.name         = dto.fullName()    != null ? dto.fullName()    : e.abbr;
        e.headshotUrl  = dto.headshotUrl();
        e.countryCode  = dto.countryCode().toUpperCase();
        e.countryName  = countryName;
        e.persist();
    }
}
