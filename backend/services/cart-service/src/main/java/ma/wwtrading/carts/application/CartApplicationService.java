package ma.wwtrading.carts.application;

import java.util.UUID;
import java.util.List;

import ma.wwtrading.carts.api.AddCartItemRequest;
import ma.wwtrading.carts.api.CartResponse;
import ma.wwtrading.carts.api.UpdateCartItemQuantityRequest;
import ma.wwtrading.carts.domain.CartEntity;
import ma.wwtrading.carts.domain.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartApplicationService {

    private final CartRepository repository;
    private final CartMapper mapper;
    private final ProductAvailabilityClient productAvailabilityClient;

    @Autowired
    public CartApplicationService(
        CartRepository repository,
        CartMapper mapper,
        ProductAvailabilityClient productAvailabilityClient
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.productAvailabilityClient = productAvailabilityClient;
    }

    public CartApplicationService(CartRepository repository, CartMapper mapper) {
        this(repository, mapper, null);
    }

    @Transactional
    public CartResponse getOrCreate(String customerReference) {
        return toResponse(findOrCreate(customerReference));
    }

    @Transactional
    public CartResponse addItem(String customerReference, AddCartItemRequest request) {
        CartEntity cart = findOrCreate(customerReference);
        ProductAvailabilityClient.ProductAvailability availability = productAvailabilityClient == null
            ? null
            : productAvailabilityClient.getAvailability(request.productReference());
        int currentQuantity = cart.findByProductReference(request.productReference())
            .map(item -> item.getQuantity())
            .orElse(0);
        requireAvailableQuantity(request.productReference(), availability, (long) currentQuantity + request.quantity());
        cart.addOrMergeItem(request.productReference(), request.quantity());
        return toResponse(repository.save(cart));
    }

    @Transactional
    public CartResponse updateQuantity(
        String customerReference,
        UUID itemId,
        UpdateCartItemQuantityRequest request
    ) {
        CartEntity cart = findOwnedCart(customerReference, itemId);
        String productReference = cart.findItem(itemId)
            .orElseThrow(() -> new CartItemNotFoundException(itemId))
            .getProductReference();
        ProductAvailabilityClient.ProductAvailability availability = productAvailabilityClient == null
            ? null
            : productAvailabilityClient.getAvailability(productReference);
        requireAvailableQuantity(productReference, availability, request.quantity());
        cart.findItem(itemId)
            .orElseThrow(() -> new CartItemNotFoundException(itemId))
            .updateQuantity(request.quantity());
        return toResponse(repository.save(cart));
    }

    @Transactional
    public void removeItem(String customerReference, UUID itemId) {
        CartEntity cart = findOwnedCart(customerReference, itemId);
        if (!cart.removeItem(itemId)) {
            throw new CartItemNotFoundException(itemId);
        }
        repository.save(cart);
    }

    @Transactional
    public void clear(String customerReference) {
        repository.findByCustomerReference(customerReference).ifPresent(cart -> {
            cart.clearItems();
            repository.save(cart);
        });
    }

    private CartEntity findOrCreate(String customerReference) {
        return repository.findByCustomerReference(customerReference)
            .orElseGet(() -> repository.save(new CartEntity(customerReference)));
    }

    private CartEntity findOwnedCart(String customerReference, UUID itemId) {
        return repository.findByCustomerReference(customerReference)
            .orElseThrow(() -> new CartItemNotFoundException(itemId));
    }

    private CartResponse toResponse(CartEntity cart) {
        var mapped = mapper.toResponse(cart);
        List<ma.wwtrading.carts.api.CartItemResponse> items = mapped.items().stream()
            .map(item -> {
                Integer availableQuantity = productAvailabilityClient == null
                    ? null
                    : productAvailabilityClient.getAvailability(item.productReference()).availableQuantity();
                return new ma.wwtrading.carts.api.CartItemResponse(
                    item.id(), item.productReference(), item.quantity(), availableQuantity);
            })
            .toList();
        return new CartResponse(mapped.id(), items, mapped.createdAt(), mapped.updatedAt());
    }

    private void requireAvailableQuantity(
        String productReference,
        ProductAvailabilityClient.ProductAvailability availability,
        long requestedQuantity
    ) {
        Integer availableQuantity = availability == null ? null : availability.availableQuantity();
        if (availableQuantity != null && requestedQuantity > availableQuantity) {
            throw new StockLimitExceededException(productReference, availableQuantity);
        }
    }
}
