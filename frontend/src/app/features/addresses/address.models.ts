export interface Address {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly company: string | null;
  readonly phone: string | null;
  readonly addressLine1: string;
  readonly addressLine2: string | null;
  readonly city: string;
  readonly postalCode: string | null;
  readonly country: string;
  readonly defaultAddress: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AddressRequest {
  readonly firstName: string;
  readonly lastName: string;
  readonly company: string | null;
  readonly phone: string | null;
  readonly addressLine1: string;
  readonly addressLine2: string | null;
  readonly city: string;
  readonly postalCode: string | null;
  readonly country: string;
}
