package ma.wwtrading.orders.api;

import java.net.URI;

import ma.wwtrading.orders.application.OrderNotFoundException;
import ma.wwtrading.orders.application.OrderNotCancellableException;
import ma.wwtrading.orders.application.PaymentConfirmationException;
import ma.wwtrading.orders.application.OrderConfirmationException;
import ma.wwtrading.orders.application.OrderProcessingException;
import ma.wwtrading.orders.application.OrderFulfillmentException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", exception.getMessage());
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    ProblemDetail handleNotCancellable(OrderNotCancellableException exception) {
        return problem(HttpStatus.CONFLICT, "Order cannot be cancelled", "This order is no longer cancellable");
    }

    @ExceptionHandler(PaymentConfirmationException.class)
    ProblemDetail handlePaymentConfirmation(PaymentConfirmationException exception) {
        return problem(HttpStatus.CONFLICT, "Payment cannot be confirmed",
                "The order payment is already confirmed or the order is cancelled");
    }

    @ExceptionHandler(OrderConfirmationException.class)
    ProblemDetail handleOrderConfirmation(OrderConfirmationException exception) {
        return problem(HttpStatus.CONFLICT, "Order cannot be confirmed",
                "The order is no longer in a confirmable state");
    }

    @ExceptionHandler(OrderProcessingException.class)
    ProblemDetail handleOrderProcessing(OrderProcessingException exception) {
        return problem(HttpStatus.CONFLICT, "Order cannot start processing", "The order is not confirmed");
    }

    @ExceptionHandler(OrderFulfillmentException.class)
    ProblemDetail handleOrderFulfillment(OrderFulfillmentException exception) {
        return problem(HttpStatus.CONFLICT, "Order fulfilment transition rejected",
                "The order is not in the required state");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request contains invalid fields");
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
        LOGGER.warn("Order request rejected because a data constraint was violated");
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing data");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleRequestStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, status.getReasonPhrase(), exception.getReason());
    }

    @ExceptionHandler({ IllegalArgumentException.class, MaxUploadSizeExceededException.class })
    ProblemDetail handleInvalidUpload(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid upload", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled order API error type={}", exception.getClass().getSimpleName());
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
