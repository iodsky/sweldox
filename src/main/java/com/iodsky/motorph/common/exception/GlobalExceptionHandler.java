package com.iodsky.motorph.common.exception;

import com.iodsky.motorph.common.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(), 404, ex.getMessage(), null);
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateFieldException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateFieldException(DuplicateFieldException ex) {
        ErrorResponse error = new ErrorResponse(LocalDateTime.now(), 409, ex.getMessage(), null);
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
}
