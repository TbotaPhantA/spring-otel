package com.example.otel.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI().info(
        Info()
            .title("Product API")
            .version("1.0")
            .description("REST API for managing products")
            .contact(Contact().name("API Support"))
    )
}
