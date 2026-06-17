package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SessionEntity extends PanacheEntityBase {

    @Id
    public Integer sessionKey;
    public String sessionName;
    public String sessionType;
    public String countryName;
    public String circuitShortName;
    public String dateStart;
    @Column(name = "session_year")
    public Integer year;
}