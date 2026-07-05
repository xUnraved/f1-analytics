package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "f1_session", indexes = {
        @Index(name = "idx_session_year", columnList = "year"),
        @Index(name = "idx_session_meeting", columnList = "meeting_key")
})
public class F1SessionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "session_key")
    public int sessionKey;

    public int year;

    @Column(name = "meeting_key")
    public Integer meetingKey;

    @Column(name = "session_name")
    public String sessionName;   // "Race", "Qualifying", "Practice 1" …

    public String location;

    @Column(name = "country_name")
    public String countryName;

    @Column(name = "circuit_short_name")
    public String circuitShortName;

    @Column(name = "date_start")
    public String dateStart;

    @Column(name = "circuit_image", columnDefinition = "text")
    public String circuitImage;

    @Column(name = "country_flag", columnDefinition = "text")
    public String countryFlag;

    public double lat;
    public double lon;

    public static F1SessionEntity findByKey(int key) {
        return findById(key);
    }

    public static List<F1SessionEntity> findByYearAndName(int year, String sessionName) {
        return list("year = ?1 and sessionName = ?2", year, sessionName);
    }

    public static boolean existsForYear(int year) {
        return count("year", year) > 0;
    }
}
