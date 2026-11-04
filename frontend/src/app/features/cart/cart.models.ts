export interface CartItem {
  readonly id: string;
  readonly productReference: string;
  readonly quantity: number;
  readonly availableQuantity?: number | null;
}

export interface Cart {
  readonly id: string;
  readonly items: readonly CartItem[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AddCartItemRequest {
  readonly productReference: string;
  readonly quantity: number;
}

export interface UpdateCartItemQuantityRequest {
  readonly quantity: number;
}
