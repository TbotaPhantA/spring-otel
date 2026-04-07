package com.example.otel.dto

import com.example.otel.entity.Product

data class ProductResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val price: String,
    val quantity: Int
) {
    companion object {
        fun fromEntity(product: Product): ProductResponse = ProductResponse(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            quantity = product.quantity
        )
    }
}
