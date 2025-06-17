package com.sarinah.peoplecounter.configuration;

import com.sarinah.peoplecounter.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Objects;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String paramName = ex.getName();         // e.g. "page"
        String paramValue = Objects.toString(ex.getValue());
        String expectedType = ex.getRequiredType().getSimpleName();

        ErrorResponse body = new ErrorResponse();
        body.setStatus(HttpStatus.BAD_REQUEST.value());
        body.setError("Bad Request");
        body.setMessage(
                String.format(
                        "Parameter '%s' harus bertipe %s, tapi nilainya '%s' tidak valid",
                        paramName, expectedType, paramValue
                )
        );
        body.setPath(request.getRequestURI());

        return ResponseEntity
                .badRequest()
                .body(body);
    }

    // (Opsional) tangani exception lain, misal missing params
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse();
        body.setStatus(HttpStatus.BAD_REQUEST.value());
        body.setError("Bad Request");
        body.setMessage(
                String.format("Parameter '%s' wajib diisi", ex.getParameterName())
        );
        body.setPath(request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }
}
