package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "f1_result", indexes = {
        @Index(name = "idx_result_session", columnList = "session_key"),
        @Index(name = "idx_result_session_driver", columnList = "session_key, driver_number")
})
public class F1ResultEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "f1_result_seq")
    @SequenceGenerator(name = "f1_result_seq", sequenceName = "f1_result_seq", allocationSize = 50)
    public Long id;

    @Column(name = "session_key")
    public int sessionKey;

    @Column(name = "driver_number")
    public int driverNumber;

    public String abbr;
    public String name;
    public String team;
    public String color;
    public int pos;

    // Nur bei Race-Ergebnis
    public Integer pts;

    @Column(name = "gap_text")
    public String gapText;

    // Nur bei Quali/Training
    @Column(name = "best_lap")
    public String bestLap;

    public boolean dnf;
    public boolean dns;
    public boolean dsq;
    public Integer laps;

    public static List<F1ResultEntity> findBySession(int sessionKey) {
        return list("sessionKey = ?1 order by pos", sessionKey);
    }

    public static boolean existsForSession(int sessionKey) {
        return count("sessionKey", sessionKey) > 0;
    }

    public static void deleteBySession(int sessionKey) {
        delete("sessionKey", sessionKey);
    }
}
