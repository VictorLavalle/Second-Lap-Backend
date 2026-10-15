package com.secondlap.backend.product;

import com.secondlap.backend.product.dto.AdminProductRequest;
import com.secondlap.backend.product.dto.ProductResponse;
import com.secondlap.backend.product.dto.UpdateProductStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody AdminProductRequest request) {
        return adminProductService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody AdminProductRequest request) {
        return adminProductService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminProductService.delete(id);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse updateStatus(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateProductStatusRequest request) {
        return adminProductService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/images")
    public ProductResponse addImages(@PathVariable UUID id,
                                     @RequestParam("files") List<MultipartFile> files) {
        return adminProductService.addImages(id, files);
    }
}
