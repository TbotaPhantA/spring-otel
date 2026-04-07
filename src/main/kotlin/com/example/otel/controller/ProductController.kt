package com.example.otel.controller

import com.example.otel.dto.ProductRequest
import com.example.otel.dto.ProductResponse
import com.example.otel.service.ProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product management API")
class ProductController(private val productService: ProductService) {

    @GetMapping
    @Operation(summary = "Get all products")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of all products")
    )
    fun getAllProducts(): ResponseEntity<List<ProductResponse>> =
        ResponseEntity.ok(productService.getAllProducts())

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Product found"),
        ApiResponse(responseCode = "404", description = "Product not found")
    )
    fun getProductById(@PathVariable id: Int): ResponseEntity<ProductResponse> =
        ResponseEntity.ok(productService.getProductById(id))

    @PostMapping
    @Operation(summary = "Create a new product")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Product created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid input")
    )
    fun createProduct(@Valid @RequestBody request: ProductRequest): ResponseEntity<ProductResponse> {
        val created = productService.createProduct(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Product updated successfully"),
        ApiResponse(responseCode = "404", description = "Product not found"),
        ApiResponse(responseCode = "400", description = "Invalid input")
    )
    fun updateProduct(
        @PathVariable id: Int,
        @Valid @RequestBody request: ProductRequest
    ): ResponseEntity<ProductResponse> =
        ResponseEntity.ok(productService.updateProduct(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Product deleted successfully"),
        ApiResponse(responseCode = "404", description = "Product not found")
    )
    fun deleteProduct(@PathVariable id: Int): ResponseEntity<Void> {
        productService.deleteProduct(id)
        return ResponseEntity.noContent().build()
    }
}
