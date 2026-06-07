package de.htw.f1analytics.service;

import de.htw.f1analytics.client.OpenF1Client;
import de.htw.f1analytics.client.OpenF1SessionDto;
import de.htw.f1analytics.domain.SessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class SessionService {

    @RestClient
    OpenF1Client openF1Client;

    @Transactional
    public List<SessionEntity> getSessionsByYear(int year) {

        List<SessionEntity> cached = SessionEntity.list("year", year);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<OpenF1SessionDto> dtos = openF1Client.getSessions(year);

        List<SessionEntity> entities = dtos.stream()
                .filter(dto -> dto.sessionKey() != null)
                .map(SessionService::toEntity)
                .toList();

        SessionEntity.persist(entities);
        return entities;
    }

    private static SessionEntity toEntity(OpenF1SessionDto dto) {
        SessionEntity e = new SessionEntity();
        e.sessionKey = dto.sessionKey();
        e.sessionName = dto.sessionName();
        e.sessionType = dto.sessionType();
        e.countryName = dto.countryName();
        e.circuitShortName = dto.circuitShortName();
        e.dateStart = dto.dateStart();
        e.year = dto.year();
        return e;
    }
}