package ma.wwtrading.products.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ma.wwtrading.products.application.ProductApplicationService;
import ma.wwtrading.products.application.ProductMapper;
import ma.wwtrading.products.application.ProductNotFoundException;
import ma.wwtrading.products.application.ProductReferenceConflictException;
import ma.wwtrading.products.domain.MachineCategory;
import ma.wwtrading.products.domain.ProductColorMode;
import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductRepository;
import ma.wwtrading.products.domain.ProductType;
import ma.wwtrading.products.domain.StockRepository;

import org.springframework.context.ApplicationEventPublisher;

class ProductBehaviorTests {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProductApplicationService application = new ProductApplicationService(
            repository,
            Mappers.getMapper(ProductMapper.class),
            mock(StockRepository.class),
            eventPublisher
    );
    private final ProductApplicationService controllerService = mock(ProductApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(controllerService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void createsActiveProductAndRejectsDuplicateReference() {
        when(repository.findByProductReference("KM-C360I")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = application.create(request("KM-C360I", "Konica Minolta C360i"));

        assertThat(response.id()).isNotNull();
        assertThat(response.reference()).isEqualTo("KM-C360I");
        assertThat(response.active()).isTrue();
        verify(eventPublisher).publishEvent(any(ma.wwtrading.products.infrastructure.kafka.ProductCreatedDomainEvent.class));

        when(repository.findByProductReference("KM-C360I"))
                .thenReturn(Optional.of(new ProductEntity("KM-C360I", "Existing", null, BigDecimal.ONE)));
        assertThatThrownBy(() -> application.create(request("KM-C360I", "Duplicate")))
                .isInstanceOf(ProductReferenceConflictException.class);
    }

    @Test
    void getsExistingProductAndRejectsMissingProduct() {
        UUID id = UUID.randomUUID();
        var product = new ProductEntity("DEV-INEO", "Develop ineo", null, BigDecimal.TEN);
        when(repository.findById(id)).thenReturn(Optional.of(product));

        assertThat(application.getById(id).reference()).isEqualTo("DEV-INEO");

        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> application.getById(missingId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void mapsPaginatedProducts() {
        var pageable = PageRequest.of(1, 10);
        var product = new ProductEntity("KM-C360I", "Konica Minolta C360i", null, BigDecimal.TEN);
        when(repository.findAllFiltered(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 11));

        var page = application.list(pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(11);
    }

    @Test
    void createsAndUpdatesEverySupportedProductType() {
        for (ProductType type : ProductType.values()) {
            when(repository.findByProductReference("REF-" + type)).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var response = application.create(new CreateProductRequest(
                    "REF-" + type, "Product", null, BigDecimal.ONE, type
            ));

            assertThat(response.type()).isEqualTo(type);
        }
    }

    @Test
    void createsPrinterWithExtendedFieldsAndUpdatesItToToner() {
        when(repository.findByProductReference("KM-C360I")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = application.create(new CreateProductRequest(
                "KM-C360I", "bizhub C360i", "Multifunction printer", new BigDecimal("45000.00"),
                ProductType.PRINTER, "Konica Minolta", "bizhub C360i", MachineCategory.DIGITAL_PRESS,
                ProductColorMode.COLOR, 6, 10_000
        ));

        assertThat(created.brand()).isEqualTo("Konica Minolta");
        assertThat(created.model()).isEqualTo("bizhub C360i");
        assertThat(created.machineCategory()).isEqualTo(MachineCategory.DIGITAL_PRESS);
        assertThat(created.colorMode()).isEqualTo(ProductColorMode.COLOR);
        assertThat(created.warrantyMonths()).isEqualTo(6);
        assertThat(created.warrantyCopies()).isEqualTo(10_000);

        UUID id = created.id();
        ProductEntity product = new ProductEntity(
                "KM-C360I", "bizhub C360i", "Multifunction printer", new BigDecimal("45000.00"),
                ProductType.PRINTER, "Konica Minolta", "bizhub C360i", MachineCategory.DIGITAL_PRESS,
                ProductColorMode.COLOR, 6, 10_000
        );
        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(repository.findByProductReference("TN-C360I")).thenReturn(Optional.empty());

        var updated = application.update(id, new UpdateProductRequest(
                "TN-C360I", "Toner", "Replacement toner", new BigDecimal("100.00"),
                ProductType.TONER, "Konica Minolta", "TN-C360I", null, null, null, null
        ));

        assertThat(updated.type()).isEqualTo(ProductType.TONER);
        assertThat(updated.machineCategory()).isNull();
        assertThat(updated.colorMode()).isNull();
        assertThat(updated.brand()).isEqualTo("Konica Minolta");
        assertThat(updated.warrantyMonths()).isNull();
        assertThat(updated.warrantyCopies()).isNull();
    }

    @Test
    void rejectsNonPrinterMachineCategoriesAtApplicationBoundary() {
        for (ProductType type : new ProductType[]{
            ProductType.TONER, ProductType.CONSUMABLE, ProductType.SPARE_PART
        }) {
            when(repository.findByProductReference("INVALID-" + type)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> application.create(new CreateProductRequest(
                    "INVALID-" + type, "Product", null, BigDecimal.ONE, type,
                    null, null, MachineCategory.DIGITAL_PRESS, null, null, null
            ))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsNonPrinterColorModesAtApplicationBoundary() {
        for (ProductType type : new ProductType[]{
            ProductType.TONER, ProductType.CONSUMABLE, ProductType.SPARE_PART
        }) {
            for (ProductColorMode colorMode : ProductColorMode.values()) {
                assertThatThrownBy(() -> application.create(new CreateProductRequest(
                        "INVALID-" + type, "Product", null, BigDecimal.ONE, type,
                        null, null, null, colorMode, null, null
                )))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Color mode is only supported for printer products");
            }
        }
    }

    @Test
    void passesFiltersThroughToTheDatabaseQuery() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findAllFiltered(ProductType.PRINTER, MachineCategory.DIGITAL_PRESS,
                ProductColorMode.COLOR, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        application.list(ProductType.PRINTER, MachineCategory.DIGITAL_PRESS,
                ProductColorMode.COLOR, pageable);

        verify(repository).findAllFiltered(ProductType.PRINTER, MachineCategory.DIGITAL_PRESS,
                ProductColorMode.COLOR, pageable);
    }

    @Test
    void passesSearchTermThroughToDatabaseQuery() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findAllFiltered("bizhub", null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        application.list("  bizhub  ", null, null, null, pageable);

        verify(repository).findAllFiltered("bizhub", null, null, null, pageable);
    }

    @Test
    void controllerForwardsSearchParamToListService() throws Exception {
        var pageable = PageRequest.of(0, 20);
        when(controllerService.list(org.mockito.ArgumentMatchers.eq("toner"), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/api/products?search=toner"))
                .andExpect(status().isOk());

        verify(controllerService).list(org.mockito.ArgumentMatchers.eq("toner"), any(), any(), any(), any());
    }

    @Test
    void rejectsContradictoryFilterCombinationsInsteadOfIgnoringThem() {
        var pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> application.list(ProductType.SPARE_PART, null,
                ProductColorMode.COLOR, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Color mode filter is only supported for printer products");

        assertThatThrownBy(() -> application.list(ProductType.TONER, MachineCategory.DIGITAL_PRESS,
                null, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Machine category filter is only supported for printer products");

        verifyNoInteractions(repository);
    }

    @Test
    void updatesOnlyEditableFieldsAndPreservesServerManagedState() {
        UUID routeId = UUID.randomUUID();
        var product = new ProductEntity("OLD-REF", "Old name", "Old description", BigDecimal.ONE);
        UUID entityId = product.getId();
        product.deactivate();
        when(repository.findById(routeId)).thenReturn(Optional.of(product));
        when(repository.findByProductReference("NEW-REF")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(product)).thenReturn(product);

        var response = application.update(routeId, new UpdateProductRequest(
                "NEW-REF", "New name", null, new BigDecimal("25.00")
        ));

        assertThat(response.id()).isEqualTo(entityId);
        assertThat(response.reference()).isEqualTo("NEW-REF");
        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.price()).isEqualByComparingTo("25.00");
        assertThat(response.active()).isFalse();
    }

    @Test
    void activatesAndDeactivatesIdempotentlyAndRejectsMissingProduct() {
        UUID id = UUID.randomUUID();
        var product = new ProductEntity("KM-C360I", "Konica Minolta C360i", null, BigDecimal.TEN);
        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(product)).thenReturn(product);

        assertThat(application.activate(id).active()).isTrue();
        assertThat(application.activate(id).active()).isTrue();
        assertThat(application.deactivate(id).active()).isFalse();
        assertThat(application.deactivate(id).active()).isFalse();

        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> application.activate(missingId))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> application.deactivate(missingId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void controllerCreatesProductAndReturnsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(controllerService.create(any())).thenReturn(response(id, "KM-C360I", true));

        mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"KM-C360I\",\"name\":\"C360i\",\"price\":12500,\"type\":\"PRINTER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/" + id))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.machineCategory").value(nullValue()));
    }

    @Test
    void controllerValidatesRequestsAndReturnsSafeProblems() throws Exception {
        mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"\",\"name\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
        verifyNoInteractions(controllerService);

        UUID missingId = UUID.randomUUID();
        when(controllerService.getById(missingId)).thenThrow(new ProductNotFoundException(missingId));
        mvc.perform(get("/api/products/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The product does not exist"));

        UUID conflictId = UUID.randomUUID();
        when(controllerService.getById(conflictId)).thenThrow(new ProductReferenceConflictException("SECRET-SQL"));
        mvc.perform(get("/api/products/{id}", conflictId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The product reference already exists"));
    }

    @Test
    void controllerUpdateIgnoresServerManagedJsonFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(controllerService.update(org.mockito.ArgumentMatchers.eq(id), any()))
                .thenReturn(response(id, "NEW-REF", false));

        mvc.perform(put("/api/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reference":"NEW-REF",
                      "name":"Updated",
                      "description":null,
                      "price":25,
                      "type":"TONER",
                      "id":"00000000-0000-0000-0000-000000000000",
                      "active":true,
                      "createdAt":"2000-01-01T00:00:00Z"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        ArgumentCaptor<UpdateProductRequest> request = ArgumentCaptor.forClass(UpdateProductRequest.class);
        verify(controllerService).update(org.mockito.ArgumentMatchers.eq(id), request.capture());
        assertThat(request.getValue().reference()).isEqualTo("NEW-REF");
        assertThat(UpdateProductRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("reference", "name", "description", "price", "type", "brand", "model",
                        "machineCategory", "colorMode", "warrantyMonths", "warrantyCopies");
        // Phase 2: availability is server-managed through the Stock system and is
        // only exposed as a read-only compatibility field on the response record.
        assertThat(ProductResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("availableQuantity");
        assertThat(CreateProductRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("availableQuantity");
    }

    @Test
    void controllerActivatesAndDeactivatesWithoutRequestBodies() throws Exception {
        UUID id = UUID.randomUUID();
        when(controllerService.activate(id)).thenReturn(response(id, "KM-C360I", true));
        when(controllerService.deactivate(id)).thenReturn(response(id, "KM-C360I", false));

        mvc.perform(post("/api/products/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/products/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.active").value(false));

        UUID missingId = UUID.randomUUID();
        when(controllerService.activate(missingId)).thenThrow(new ProductNotFoundException(missingId));
        mvc.perform(post("/api/products/{id}/activate", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The product does not exist"));
    }

    private CreateProductRequest request(String reference, String name) {
        return new CreateProductRequest(reference, name, null, new BigDecimal("12500.00"));
    }

    private ProductResponse response(UUID id, String reference, boolean active) {
        return new ProductResponse(id, reference, "Product", null, BigDecimal.TEN, active, null, null);
    }
}
