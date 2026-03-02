package ru.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для получения статистики по посещениям
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewStatsDto {

    private String app;

    private String uri;

    private Long hits;
}
