package ma.wwtrading.products.application;

import org.springframework.http.HttpStatus;

public final class ProductImageException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    private ProductImageException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }

    public static ProductImageException invalid(String message) {
        return new ProductImageException(HttpStatus.BAD_REQUEST, "Invalid product image", message);
    }

    public static ProductImageException unsupported(String message) {
        return new ProductImageException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported product image", message);
    }

    public static ProductImageException tooLarge(String message) {
        return new ProductImageException(HttpStatus.PAYLOAD_TOO_LARGE, "Product image too large", message);
    }

    public static ProductImageException notFound() {
        return new ProductImageException(HttpStatus.NOT_FOUND, "Product image not found", "The product image does not exist");
    }

    public static ProductImageException storageFailure() {
        return new ProductImageException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Product image storage error",
            "The product image operation could not be completed"
        );
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
