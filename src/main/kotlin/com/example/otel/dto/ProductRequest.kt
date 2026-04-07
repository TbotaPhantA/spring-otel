package com.example.otel.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class ProductRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    val description: String? = null,

    @field:NotBlank(message = "Price is required")
    val price: String,

    @field:Min(value = 0, message = "Quantity must be non-negative")
    val quantity: Int = 0
)
