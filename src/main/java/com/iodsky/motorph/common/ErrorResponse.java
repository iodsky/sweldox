package com.iodsky.motorph.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String message;
    private String path;
    private List<FieldValidationError> fieldErrors;

    @Data
    @AllArgsConstructor
    public static class FieldValidationError {
        private String field;
        private String message;
    }
}
