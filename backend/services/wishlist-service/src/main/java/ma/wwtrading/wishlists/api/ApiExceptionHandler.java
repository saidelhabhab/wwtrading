package ma.wwtrading.wishlists.api;

import java.net.URI;

import ma.wwtrading.wishlists.application.WishlistItemNotFoundException;
import ma.wwtrading.wishlists.application.WishlistNotFoundException;
import ma.wwtrading.wishlists.application.WishlistProductAlreadyExistsException;
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

    @ExceptionHandler(WishlistNotFoundException.class)
    ProblemDetail handleWishlistNotFound(WishlistNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Wishlist not found", "The wishlist does not exist");
    }

    @ExceptionHandler(WishlistItemNotFoundException.class)
    ProblemDetail handleItemNotFound(WishlistItemNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Wishlist item not found", "The wishlist item does not exist");
    }

    @ExceptionHandler(WishlistProductAlreadyExistsException.class)
    ProblemDetail handleItemConflict(WishlistProductAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "Product already in wishlist", "The product is already in the wishlist");
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
    ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        LOGGER.warn("Wishlist request rejected because a data constraint was violated");
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing wishlist data");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, "Request rejected", exception.getReason() == null ? "The request was rejected" : exception.getReason());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled wishlist API error type={}", exception.getClass().getSimpleName());
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
