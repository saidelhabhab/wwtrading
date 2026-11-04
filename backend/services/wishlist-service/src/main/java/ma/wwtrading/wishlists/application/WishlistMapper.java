package ma.wwtrading.wishlists.application;

import ma.wwtrading.wishlists.api.AddWishlistItemRequest;
import ma.wwtrading.wishlists.api.WishlistItemResponse;
import ma.wwtrading.wishlists.api.WishlistResponse;
import ma.wwtrading.wishlists.domain.WishlistEntity;
import ma.wwtrading.wishlists.domain.WishlistItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WishlistMapper {

    WishlistResponse toResponse(WishlistEntity wishlist);

    WishlistItemResponse toResponse(WishlistItemEntity item);

    default WishlistItemEntity toEntity(AddWishlistItemRequest request, WishlistEntity wishlist) {
        return wishlist.addItem(request.productReference());
    }
}
