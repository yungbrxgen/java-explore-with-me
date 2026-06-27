package ru.practicum.ewm.exception;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ApiError {
    private String status;     
    private String reason;
    private String message;
    private String timestamp;
    private List<String> errors;
}
