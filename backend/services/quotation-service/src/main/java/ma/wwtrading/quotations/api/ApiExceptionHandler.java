package ma.wwtrading.quotations.api;

import java.net.URI;

import ma.wwtrading.quotations.application.QuotationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(QuotationNotFoundException.class)
    ProblemDetail handleNotFound(QuotationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Quotation not found", exception.getMessage());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ProblemDetail handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "The request contains invalid fields");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList());
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "A request parameter has an invalid value");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        LOGGER.warn("Quotation request rejected because a data constraint was violated");
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing data");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleExpected(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        String reason = exception.getReason() == null ? "The request could not be completed" : exception.getReason();
        ProblemDetail problem = problem(status, status.getReasonPhrase(), reason);
        if ("IDEMPOTENCY_PAYLOAD_MISMATCH".equals(reason)) {
            problem.setProperty("code", "IDEMPOTENCY_PAYLOAD_MISMATCH");
        }
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled quotation API error type={}", exception.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "The request could not be completed");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:wwtrading:problem:" + status.value()));
        return problem;
    }

    private record FieldViolation(String field, String message) {
    }
}
