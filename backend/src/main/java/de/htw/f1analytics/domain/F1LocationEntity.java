package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "f1_location", indexes = {
        @Index(name = "idx_location_session", columnList = "session_key"),
        @Index(name = "idx_location_session_driver", columnList = "session_key, driver_number")
})
public class F1LocationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "f1_location_seq")
    @SequenceGenerator(name = "f1_location_seq", sequenceName = "f1_location_seq", allocationSize = 5000)
    public Long id;

    @Column(name = "session_key")
    public int sessionKey;

    @Column(name = "driver_number")
    public int driverNumber;

    /** Millisekunden ab Fensterbeginn (volle GPS-Auflösung, nicht auf 1Hz gerundet) */
    @Column(name = "t_ms")
    public Long tMs;

    public double x;
    public double y;

    public static boolean existsForSession(int sessionKey) {
        return count("sessionKey", sessionKey) > 0;
    }

    public static List<F1LocationEntity> findBySession(int sessionKey) {
        return list("sessionKey = ?1 AND tMs IS NOT NULL order by tMs, driverNumber", sessionKey);
    }

    public static void deleteBySession(int sessionKey) {
        delete("sessionKey", sessionKey);
    }
}
