package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1DriverDto;
import de.htw.f1analytics.domain.QuizDriverEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizService {

    private static final Map<String, Integer> BIRTH_YEARS = Map.ofEntries(
            Map.entry("VER", 1997), Map.entry("NOR", 2000), Map.entry("LEC", 1997),
            Map.entry("HAM", 1985), Map.entry("SAI", 1994), Map.entry("RUS", 1998),
            Map.entry("PIA", 2001), Map.entry("ALO", 1981), Map.entry("STR", 1999),
            Map.entry("PER", 1990), Map.entry("GAS", 1996), Map.entry("OCO", 1996),
            Map.entry("ALB", 1996), Map.entry("ZHO", 1999), Map.entry("BOT", 1989),
            Map.entry("MAG", 1992), Map.entry("HUL", 1987), Map.entry("TSU", 2000),
            Map.entry("LAW", 2002), Map.entry("BEA", 2000), Map.entry("ANT", 2006),
            Map.entry("HAD", 2004), Map.entry("BOR", 2004), Map.entry("DOO", 2004),
            Map.entry("RIC", 1989), Map.entry("DEV", 1995), Map.entry("SAR", 1999),
            Map.entry("COL", 2003), Map.entry("VET", 1987), Map.entry("RAI", 1979)
    );

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

    public record CircuitQuizItem(String name, String imageUrl, String country) {}

    public record DriverQuizItem(String abbr, String name, String headshotUrl,
                                 String countryCode, String countryName, Integer birthYear) {}

    public record QuizData(List<CircuitQuizItem> circuits, List<DriverQuizItem> drivers) {}

    @Inject
    EntityManager em;

    public QuizData getQuizData() {
        return new QuizData(getCircuits(), getDrivers());
    }

    @SuppressWarnings("unchecked")
    private List<CircuitQuizItem> getCircuits() {
        List<Object[]> rows = em.createQuery(
                        "SELECT DISTINCT s.location, s.circuitImage, s.countryName FROM F1SessionEntity s " +
                                "WHERE s.sessionName = 'Race' AND s.circuitImage IS NOT NULL AND s.location IS NOT NULL")
                .getResultList();
        return rows.stream()
                .map(r -> new CircuitQuizItem((String) r[0], (String) r[1], (String) r[2]))
                .toList();
    }

    private List<DriverQuizItem> getDrivers() {
        return QuizDriverEntity.withHeadshotAndCountry().stream()
                .map(d -> new DriverQuizItem(d.abbr, d.name, d.headshotUrl, d.countryCode, d.countryName, d.birthYear))
                .toList();
    }

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        for (QuizDriverEntity d : QuizDriverEntity.<QuizDriverEntity>listAll()) {
            if (d.abbr == null) continue;
            Integer by = BIRTH_YEARS.get(d.abbr.toUpperCase());
            if (by != null && !by.equals(d.birthYear)) d.birthYear = by;
        }
    }

    @Transactional
    public void saveDriverIfAbsent(OpenF1DriverDto dto) {
        if (dto.driverNumber() == null || dto.headshotUrl() == null || dto.countryCode() == null) return;
        String countryName = COUNTRY_NAMES.get(dto.countryCode().toUpperCase());
        if (countryName == null) return;

        String abbr = dto.nameAcronym() != null ? dto.nameAcronym().toUpperCase() : "";
        Integer birthYear = BIRTH_YEARS.get(abbr);

        QuizDriverEntity existing = QuizDriverEntity.findById(dto.driverNumber());
        if (existing != null) {
            if (dto.headshotUrl() != null) existing.headshotUrl = dto.headshotUrl();
            if (countryName != null)       existing.countryName = countryName;
            if (birthYear != null)         existing.birthYear   = birthYear;
            return;
        }
        QuizDriverEntity e = new QuizDriverEntity();
        e.driverNumber = dto.driverNumber();
        e.abbr         = abbr.isEmpty() ? "#" + dto.driverNumber() : abbr;
        e.name         = dto.fullName()  != null ? dto.fullName()  : e.abbr;
        e.headshotUrl  = dto.headshotUrl();
        e.countryCode  = dto.countryCode().toUpperCase();
        e.countryName  = countryName;
        e.birthYear    = birthYear;
        e.persist();
    }
}