package ru.practicum.ewm.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    private List<String> errors;  // Список стектрейсов или описания ошибок

    private String message;  // Сообщение об ошибке

    private String reason;  // Общее описание причины ошибки

    private String status;  // HTTP статус

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;  // Когда произошла ошибка
}