package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class SeasonCacheEntity extends PanacheEntityBase {

    @Id
    @Column(name = "season_year")
    public int year;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String statsJson;

    public Instant cachedAt;

    public Integer cacheVersion;
}