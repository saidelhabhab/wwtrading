export const PRODUCT_TYPES = ['PRINTER', 'TONER', 'CONSUMABLE', 'SPARE_PART'] as const;
export type ProductType = typeof PRODUCT_TYPES[number];

export const MACHINE_CATEGORIES = [
  'GRAND_FORMAT_ECOSOLVENT',
  'DIGITAL_PRESS',
  'FINISHING_MACHINE'
] as const;
export type MachineCategory = typeof MACHINE_CATEGORIES[number];

export const PRODUCT_COLOR_MODES = [
  'COLOR',
  'MONOCHROME'
] as const;
export type ProductColorMode = typeof PRODUCT_COLOR_MODES[number];

export const PRODUCT_TYPE_LABELS: Readonly<Record<ProductType, string>> = {
  PRINTER: 'Imprimante',
  TONER: 'Toner',
  CONSUMABLE: 'Consommable',
  SPARE_PART: 'Pièce détachée'
};

export const MACHINE_CATEGORY_LABELS: Readonly<Record<MachineCategory, string>> = {
  GRAND_FORMAT_ECOSOLVENT: 'Machine grand format / Ecosolver',
  DIGITAL_PRESS: 'Machine de production / Presse numérique',
  FINISHING_MACHINE: 'Matériel de finition'
};

export const PRODUCT_COLOR_MODE_LABELS: Readonly<Record<ProductColorMode, string>> = {
  COLOR: 'Couleur',
  MONOCHROME: 'Noir & Blanc'
};

export interface Product {
  readonly id: string;
  readonly reference: string;
  readonly name: string;
  readonly description: string | null;
  readonly price: number;
  readonly type: ProductType;
  readonly brand: string | null;
  readonly model: string | null;
  readonly machineCategory: MachineCategory | null;
  readonly colorMode: ProductColorMode | null;
  readonly warrantyMonths: number | null;
  readonly warrantyCopies: number | null;
  readonly availableQuantity?: number | null;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly images?: readonly ProductImage[];
}

export interface ProductImage {
  readonly id: string;
  readonly imageUrl: string;
  readonly main: boolean;
  readonly displayOrder: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreateProductRequest {
  readonly reference: string;
  readonly name: string;
  readonly description: string | null;
  readonly price: number;
  readonly type: ProductType;
  readonly brand: string | null;
  readonly model: string | null;
  readonly machineCategory: MachineCategory | null;
  readonly colorMode: ProductColorMode | null;
  readonly warrantyMonths: number | null;
  readonly warrantyCopies: number | null;
  readonly availableQuantity?: number | null;
}

export interface UpdateProductRequest {
  readonly reference: string;
  readonly name: string;
  readonly description: string | null;
  readonly price: number;
  readonly type: ProductType;
  readonly brand: string | null;
  readonly model: string | null;
  readonly machineCategory: MachineCategory | null;
  readonly colorMode: ProductColorMode | null;
  readonly warrantyMonths: number | null;
  readonly warrantyCopies: number | null;
  readonly availableQuantity?: number | null;
}

/** Optional catalogue filters. Omitted properties are not sent to the API. */
export interface ProductFilters {
  readonly search?: string;
  readonly type?: ProductType;
  readonly machineCategory?: MachineCategory;
  readonly colorMode?: ProductColorMode;
}

export interface ProductPage {
  readonly content: readonly Product[];
  readonly number: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly numberOfElements: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly empty: boolean;
}
