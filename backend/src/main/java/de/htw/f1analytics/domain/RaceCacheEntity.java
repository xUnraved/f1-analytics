package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "race_cache")
public class RaceCacheEntity extends PanacheEntityBase {

    @Id
    @Column(name = "session_key")
    public int sessionKey;

    @Column(name = "year", nullable = false)
    public int year;

    @Column(name = "race_json", columnDefinition = "TEXT", nullable = false)
    public String raceJson;

    @Column(name = "cached_at")
    public Instant cachedAt;
}
