package ma.wwtrading.products.application;

import ma.wwtrading.products.api.CreateProductRequest;
import ma.wwtrading.products.api.ProductResponse;
import ma.wwtrading.products.api.ProductImageResponse;
import ma.wwtrading.products.domain.ProductImageEntity;
import ma.wwtrading.products.domain.ProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {

    @Mapping(target = "reference", source = "productReference")
    @Mapping(target = "availableQuantity", ignore = true)
    ProductResponse toResponse(ProductEntity product);

    @Mapping(target = "imageUrl", expression = "java(\"/api/products/\" + image.getProduct().getId() + \"/images/\" + image.getId() + \"/content\")")
    ProductImageResponse toImageResponse(ProductImageEntity image);

    default ProductEntity toEntity(CreateProductRequest request) {
        return new ProductEntity(
            request.reference().trim(),
            request.name().trim(),
            request.description(),
            request.price(),
            request.type(),
            request.brand(),
            request.model(),
            request.machineCategory(),
            request.colorMode(),
            request.warrantyMonths(),
            request.warrantyCopies()
        );
    }
}
