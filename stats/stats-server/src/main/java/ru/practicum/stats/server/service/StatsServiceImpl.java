package ru.practicum.stats.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;
import ru.practicum.stats.server.model.EndpointHit;
import ru.practicum.stats.server.repository.EndpointHitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private final EndpointHitRepository repository;

    @Override
    @Transactional
    public void saveHit(EndpointHitDto hitDto) {
        log.info("Сохранение информации о запросе: app={}, uri={}, ip={}", hitDto.getApp(), hitDto.getUri(), hitDto.getIp());

        EndpointHit hit = EndpointHit.builder()
                .app(hitDto.getApp())
                .uri(hitDto.getUri())
                .ip(hitDto.getIp())
                .timestamp(hitDto.getTimestamp())
                .build();

        repository.save(hit);
    }

    @Override
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        log.info("Получение статистики с {} по {}. Фильтр uris={}, уникальные IP={}", start, end, uris, unique);

        if (uris == null || uris.isEmpty()) {
            if (unique) {
                return repository.getAllStatsUniqueIp(start, end);
            } else {
                return repository.getAllStats(start, end);
            }
        } else {
            if (unique) {
                return repository.getStatsWithUrisUniqueIp(start, end, uris);
            } else {
                return repository.getStatsWithUris(start, end, uris);
            }
        }
    }
}

