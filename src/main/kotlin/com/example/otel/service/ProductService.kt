package com.example.otel.service

import com.example.otel.dto.ProductRequest
import com.example.otel.dto.ProductResponse
import com.example.otel.entity.Product
import com.example.otel.exception.EntityNotFoundException
import com.example.otel.repository.ProductRepository
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val tracer: Tracer
) {

    fun getAllProducts(): List<ProductResponse> {
        val span = tracer.spanBuilder("ProductService.getAllProducts")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            val result = productRepository.tracedFindAll().map { ProductResponse.fromEntity(it) }
            span.setAttribute("product.count", result.size.toLong())
            return result
        } finally {
            span.end()
            scope.close()
        }
    }

    fun getProductById(id: Int): ProductResponse {
        val span = tracer.spanBuilder("ProductService.getProductById")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("productId", id.toLong())
            val product = productRepository.tracedFindById(id)
                .orElseThrow { EntityNotFoundException("Product not found with id: $id") }
            return ProductResponse.fromEntity(product)
        } finally {
            span.end()
            scope.close()
        }
    }

    fun createProduct(request: ProductRequest): ProductResponse {
        val span = tracer.spanBuilder("ProductService.createProduct")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("product.name", request.name)
            span.setAttribute("product.price", request.price)
            
            val product = Product(
                name = request.name,
                description = request.description,
                price = request.price,
                quantity = request.quantity
            )
            val saved = productRepository.tracedSave(product)
            
            span.setAttribute("productId", saved.id?.toLong() ?: 0L)
            return ProductResponse.fromEntity(saved)
        } finally {
            span.end()
            scope.close()
        }
    }

    fun updateProduct(id: Int, request: ProductRequest): ProductResponse {
        val span = tracer.spanBuilder("ProductService.updateProduct")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("productId", id.toLong())
            span.setAttribute("product.name", request.name)
            span.setAttribute("product.price", request.price)
            
            val existing = productRepository.tracedFindById(id)
                .orElseThrow { EntityNotFoundException("Product not found with id: $id") }

            val updated = existing.copy(
                name = request.name,
                description = request.description,
                price = request.price,
                quantity = request.quantity
            )
            val saved = productRepository.tracedSave(updated)
            return ProductResponse.fromEntity(saved)
        } finally {
            span.end()
            scope.close()
        }
    }

    fun deleteProduct(id: Int) {
        val span = tracer.spanBuilder("ProductService.deleteProduct")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("productId", id.toLong())
            
            if (!productRepository.tracedExistsById(id)) {
                throw EntityNotFoundException("Product not found with id: $id")
            }
            productRepository.tracedDeleteById(id)
        } finally {
            span.end()
            scope.close()
        }
    }
}
