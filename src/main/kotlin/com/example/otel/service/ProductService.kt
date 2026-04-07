package com.example.otel.service

import com.example.otel.dto.ProductRequest
import com.example.otel.dto.ProductResponse
import com.example.otel.entity.Product
import com.example.otel.exception.EntityNotFoundException
import com.example.otel.repository.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductService(private val productRepository: ProductRepository) {

    fun getAllProducts(): List<ProductResponse> =
        productRepository.findAll().map { ProductResponse.fromEntity(it) }

    fun getProductById(id: Int): ProductResponse {
        val product = productRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Product not found with id: $id") }
        return ProductResponse.fromEntity(product)
    }

    fun createProduct(request: ProductRequest): ProductResponse {
        val product = Product(
            name = request.name,
            description = request.description,
            price = request.price,
            quantity = request.quantity
        )
        val saved = productRepository.save(product)
        return ProductResponse.fromEntity(saved)
    }

    fun updateProduct(id: Int, request: ProductRequest): ProductResponse {
        val existing = productRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Product not found with id: $id") }

        val updated = existing.copy(
            name = request.name,
            description = request.description,
            price = request.price,
            quantity = request.quantity
        )
        val saved = productRepository.save(updated)
        return ProductResponse.fromEntity(saved)
    }

    fun deleteProduct(id: Int) {
        if (!productRepository.existsById(id)) {
            throw EntityNotFoundException("Product not found with id: $id")
        }
        productRepository.deleteById(id)
    }
}
