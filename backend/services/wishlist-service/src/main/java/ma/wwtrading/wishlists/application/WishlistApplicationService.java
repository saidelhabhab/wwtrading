package ma.wwtrading.wishlists.application;

import java.util.UUID;

import ma.wwtrading.wishlists.api.AddWishlistItemRequest;
import ma.wwtrading.wishlists.api.WishlistResponse;
import ma.wwtrading.wishlists.domain.WishlistEntity;
import ma.wwtrading.wishlists.domain.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistApplicationService {

    private final WishlistRepository repository;
    private final WishlistMapper mapper;

    public WishlistApplicationService(WishlistRepository repository, WishlistMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public WishlistResponse getOrCreate(String customerReference) {
        return mapper.toResponse(findOrCreate(customerReference));
    }

    @Transactional(readOnly = true)
    public WishlistResponse getCurrent(String customerReference) {
        return mapper.toResponse(repository.findByCustomerReference(customerReference)
            .orElseThrow(() -> new WishlistNotFoundException(customerReference)));
    }

    @Transactional
    public WishlistResponse addItem(String customerReference, AddWishlistItemRequest request) {
        WishlistEntity wishlist = findOrCreate(customerReference);
        try {
            wishlist.addItem(request.productReference());
        } catch (IllegalStateException exception) {
            throw new WishlistProductAlreadyExistsException(request.productReference());
        }
        return mapper.toResponse(repository.save(wishlist));
    }

    @Transactional
    public void removeItem(String customerReference, UUID itemId) {
        WishlistEntity wishlist = findOwnedWishlist(customerReference, itemId);
        if (!wishlist.removeItem(itemId)) {
            throw new WishlistItemNotFoundException(itemId);
        }
        repository.save(wishlist);
    }

    @Transactional
    public void clear(String customerReference) {
        repository.findByCustomerReference(customerReference).ifPresent(wishlist -> {
            wishlist.clearItems();
            repository.save(wishlist);
        });
    }

    private WishlistEntity findOrCreate(String customerReference) {
        return repository.findByCustomerReference(customerReference)
            .orElseGet(() -> repository.save(new WishlistEntity(customerReference)));
    }

    private WishlistEntity findOwnedWishlist(String customerReference, UUID itemId) {
        return repository.findByCustomerReference(customerReference)
            .orElseThrow(() -> new WishlistItemNotFoundException(itemId));
    }
}
