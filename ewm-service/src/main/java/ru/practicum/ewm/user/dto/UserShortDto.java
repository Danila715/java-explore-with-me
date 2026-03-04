package ru.practicum.ewm.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Краткая информация о пользователе (для событий)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserShortDto {

    private Long id;

    private String name;
}
