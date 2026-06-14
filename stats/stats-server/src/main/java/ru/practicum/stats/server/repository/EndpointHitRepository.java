package ru.practicum.stats.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.stats.dto.ViewStatsDto;
import ru.practicum.stats.server.model.EndpointHit;

import java.time.LocalDateTime;
import java.util.List;

public interface EndpointHitRepository extends JpaRepository<EndpointHit, Long> {

    @Query("SELECT new ru.practicum.stats.dto.ViewStatsDto(h.app, h.uri, COUNT(h.ip)) " +
            "FROM EndpointHit h " +
            "WHERE h.timestamp BETWEEN :start AND :end " +
            "AND h.uri IN :uris " +
            "GROUP BY h.app, h.uri " +
            "ORDER BY COUNT(h.ip) DESC")
    List<ViewStatsDto> getStatsWithUris(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("uris") List<String> uris);

    @Query("SELECT new ru.practicum.stats.dto.ViewStatsDto(h.app, h.uri, COUNT(DISTINCT h.ip)) " +
            "FROM EndpointHit h " +
            "WHERE h.timestamp BETWEEN :start AND :end " +
            "AND h.uri IN :uris " +
            "GROUP BY h.app, h.uri " +
            "ORDER BY COUNT(DISTINCT h.ip) DESC")
    List<ViewStatsDto> getStatsWithUrisUniqueIp(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("uris") List<String> uris);

    @Query("SELECT new ru.practicum.stats.dto.ViewStatsDto(h.app, h.uri, COUNT(h.ip)) " +
            "FROM EndpointHit h " +
            "WHERE h.timestamp BETWEEN :start AND :end " +
            "GROUP BY h.app, h.uri " +
            "ORDER BY COUNT(h.ip) DESC")
    List<ViewStatsDto> getAllStats(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    @Query("SELECT new ru.practicum.stats.dto.ViewStatsDto(h.app, h.uri, COUNT(DISTINCT h.ip)) " +
            "FROM EndpointHit h " +
            "WHERE h.timestamp BETWEEN :start AND :end " +
            "GROUP BY h.app, h.uri " +
            "ORDER BY COUNT(DISTINCT h.ip) DESC")
    List<ViewStatsDto> getAllStatsUniqueIp(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);
}

