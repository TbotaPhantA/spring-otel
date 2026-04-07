package com.example.otel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OtelApplication

fun main(args: Array<String>) {
    runApplication<OtelApplication>(*args)
}
