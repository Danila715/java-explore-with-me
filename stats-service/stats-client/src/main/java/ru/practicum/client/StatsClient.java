package ru.practicum.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP-клиент для взаимодействия с сервисом статистики
 */
@Slf4j
@Service
public class StatsClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RestClient restClient;

    public StatsClient(@Value("${stats-server.url}") String serverUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(serverUrl)
                .build();
    }

    /**
     * Отправка информации о запросе в сервис статистики
     *
     * @param app       название приложения
     * @param uri       URI эндпоинта
     * @param ip        IP-адрес пользователя
     * @param timestamp время запроса
     */
    public void saveHit(String app, String uri, String ip, LocalDateTime timestamp) {
        EndpointHitDto hitDto = EndpointHitDto.builder()
                .app(app)
                .uri(uri)
                .ip(ip)
                .timestamp(timestamp)
                .build();

        try {
            restClient.post()
                    .uri("/hit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(hitDto)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Hit saved successfully: app={}, uri={}, ip={}", app, uri, ip);
        } catch (Exception e) {
            log.error("Error while saving hit: {}", e.getMessage(), e);
        }
    }

    /**
     * Получение статистики по посещениям
     *
     * @param start  начало периода
     * @param end    конец периода
     * @param uris   список URI (может быть null)
     * @param unique учитывать только уникальные IP
     * @return список статистики
     */
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        try {
            // Форматирование и кодирование дат
            String encodedStart = URLEncoder.encode(start.format(FORMATTER), StandardCharsets.UTF_8);
            String encodedEnd = URLEncoder.encode(end.format(FORMATTER), StandardCharsets.UTF_8);

            // Построение URL
            StringBuilder uriBuilder = new StringBuilder("/stats")
                    .append("?start=").append(encodedStart)
                    .append("&end=").append(encodedEnd)
                    .append("&unique=").append(unique != null ? unique : false);

            if (uris != null && !uris.isEmpty()) {
                String urisParam = String.join(",", uris);
                uriBuilder.append("&uris=").append(URLEncoder.encode(urisParam, StandardCharsets.UTF_8));
            }

            String uri = uriBuilder.toString();
            log.info("Getting stats from: {}", uri);

            List<ViewStatsDto> stats = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            log.info("Received {} stats records", stats != null ? stats.size() : 0);
            return stats;
        } catch (Exception e) {
            log.error("Error while getting stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get stats", e);
        }
    }

    /**
     * Получение статистики для одного URI
     */
    public Long getViewsForUri(String uri, LocalDateTime start, LocalDateTime end) {
        List<ViewStatsDto> stats = getStats(start, end, List.of(uri), false);

        if (stats != null && !stats.isEmpty()) {
            return stats.get(0).getHits();
        }

        return 0L;
    }

    /**
     * Получение статистики для нескольких URI в виде Map
     */
    public Map<String, Long> getViewsForUris(List<String> uris, LocalDateTime start, LocalDateTime end) {
        List<ViewStatsDto> stats = getStats(start, end, uris, false);

        if (stats == null) {
            return Map.of();
        }

        return stats.stream()
                .collect(Collectors.toMap(
                        ViewStatsDto::getUri,
                        ViewStatsDto::getHits
                ));
    }
}