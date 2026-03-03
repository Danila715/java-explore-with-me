package ru.practicum.ewm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для создания нового пользователя
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewUserRequest {

    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 2, max = 250, message = "Длина имени должна быть от 2 до 250 символов")
    private String name;

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Email должен быть корректным")
    @Size(min = 6, max = 254, message = "Длина email должна быть от 6 до 254 символов")
    private String email;
}