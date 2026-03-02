package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.stats.exception.ValidationException;
import ru.practicum.stats.mapper.StatsMapper;
import ru.practicum.stats.model.EndpointHit;
import ru.practicum.stats.model.ViewStats;
import ru.practicum.stats.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы со статистикой
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository statsRepository;

    /**
     * Сохранение информации о запросе
     */
    @Transactional
    public EndpointHitDto saveHit(EndpointHitDto endpointHitDto) {
        log.info("Saving hit: app={}, uri={}, ip={}",
                endpointHitDto.getApp(), endpointHitDto.getUri(), endpointHitDto.getIp());

        EndpointHit endpointHit = StatsMapper.toEndpointHit(endpointHitDto);
        EndpointHit saved = statsRepository.save(endpointHit);

        log.info("Hit saved with id={}", saved.getId());
        return StatsMapper.toEndpointHitDto(saved);
    }

    /**
     * Получение статистики по посещениям
     */
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        log.info("Getting stats: start={}, end={}, uris={}, unique={}", start, end, uris, unique);

        // Валидация дат
        if (start.isAfter(end)) {
            throw new ValidationException("Дата начала не может быть позже даты окончания");
        }

        List<ViewStats> stats;

        // Выбор запроса в зависимости от параметров
        if (uris == null || uris.isEmpty()) {
            // Статистика по всем URI
            if (Boolean.TRUE.equals(unique)) {
                stats = statsRepository.findAllUniqueStats(start, end);
            } else {
                stats = statsRepository.findAllStats(start, end);
            }
        } else {
            // Статистика по конкретным URI
            if (Boolean.TRUE.equals(unique)) {
                stats = statsRepository.findUniqueStatsByUris(start, end, uris);
            } else {
                stats = statsRepository.findStatsByUris(start, end, uris);
            }
        }

        log.info("Found {} stats records", stats.size());
        return stats.stream()
                .map(StatsMapper::toViewStatsDto)
                .collect(Collectors.toList());
    }
}
