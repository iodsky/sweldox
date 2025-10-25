package com.iodsky.motorph.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FieldValidationError> fieldErrors;

    @Data
    @AllArgsConstructor
    public static class FieldValidationError {
        private String field;
        private String message;
    }
}
