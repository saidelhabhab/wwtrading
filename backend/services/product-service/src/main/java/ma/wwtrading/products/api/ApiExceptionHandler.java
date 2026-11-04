package ma.wwtrading.products.api;

import java.net.URI;

import ma.wwtrading.products.application.ProductNotFoundException;
import ma.wwtrading.products.application.ProductImageException;
import ma.wwtrading.products.application.ProductReferenceConflictException;
import ma.wwtrading.products.application.StockValidationException;
import ma.wwtrading.products.domain.InsufficientStockException;
import ma.wwtrading.products.domain.StockInvariantViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleNotFound(ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", "The product does not exist");
    }

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail handleInsufficientStock(InsufficientStockException exception) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Insufficient stock", exception.getMessage());
        detail.setProperty("availableQuantity", extractAvailableQuantity(exception.getMessage()));
        return detail;
    }

    @ExceptionHandler(StockInvariantViolationException.class)
    ProblemDetail handleStockInvariant(StockInvariantViolationException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Stock invariant violated", exception.getMessage());
    }

    @ExceptionHandler(StockValidationException.class)
    ProblemDetail handleStockValidation(StockValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Stock validation failed", exception.getMessage());
    }

    private Integer extractAvailableQuantity(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("available (-?\\d+)")
            .matcher(message);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    @ExceptionHandler(ProductReferenceConflictException.class)
    ProblemDetail handleReferenceConflict(ProductReferenceConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Product reference conflict", "The product reference already exists");
    }

    @ExceptionHandler(ProductImageException.class)
    ProblemDetail handleProductImage(ProductImageException exception) {
        return problem(exception.status(), exception.title(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMaximumUploadSize(MaxUploadSizeExceededException exception) {
        return problem(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Product image too large",
            "The image exceeds the configured maximum size"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "The request contains invalid fields");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList());
        return detail;
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ProblemDetail handleInvalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request contains an invalid value");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataConflict(DataIntegrityViolationException exception) {
        LOGGER.warn("Product request rejected because a data constraint was violated");
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing product data");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled product API error type={}", exception.getClass().getSimpleName());
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
