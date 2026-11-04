export interface WishlistItem {
  readonly id: string;
  readonly productReference: string;
}

export interface Wishlist {
  readonly id: string;
  readonly items: readonly WishlistItem[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AddWishlistItemRequest {
  readonly productReference: string;
}
