package com.example.otel.repository

import com.example.otel.entity.Product
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import org.springframework.context.annotation.Lazy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProductRepository : JpaRepository<Product, Int>, TracedProductRepository

interface TracedProductRepository {
    fun tracedFindAll(): List<Product>
    fun tracedFindById(id: Int): Optional<Product>
    fun tracedSave(product: Product): Product
    fun tracedDeleteById(id: Int)
    fun tracedExistsById(id: Int): Boolean
}

@Repository
class TracedProductRepositoryImpl(
    @Lazy private val delegate: JpaRepository<Product, Int>,
    private val tracer: Tracer
) : TracedProductRepository {

    override fun tracedFindAll(): List<Product> {
        val span = tracer.spanBuilder("Repository.findAll")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            val result = delegate.findAll()
            span.setAttribute("db.product.count", result.size.toLong())
            return result
        } finally {
            span.end()
            scope.close()
        }
    }

    override fun tracedFindById(id: Int): Optional<Product> {
        val span = tracer.spanBuilder("Repository.findById")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("productId", id.toLong())
            span.setAttribute("db.operation", "SELECT")
            
            val result = delegate.findById(id)
            
            span.setAttribute("db.result.found", result.isPresent)
            return result
        } finally {
            span.end()
            scope.close()
        }
    }

    override fun tracedSave(product: Product): Product {
        val span = tracer.spanBuilder("Repository.save")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            product.id?.let { span.setAttribute("productId", it.toLong()) }
            span.setAttribute("db.operation", if (product.id == null) "INSERT" else "UPDATE")
            span.setAttribute("product.name", product.name)
            
            val result = delegate.save(product)
            
            result.id?.let { span.setAttribute("productId", it.toLong()) }
            return result
        } finally {
            span.end()
            scope.close()
        }
    }

    override fun tracedDeleteById(id: Int) {
        val span = tracer.spanBuilder("Repository.deleteById")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("productId", id.toLong())
            span.setAttribute("db.operation", "DELETE")
            
            delegate.deleteById(id)
        } finally {
            span.end()
            scope.close()
        }
    }

    override fun tracedExistsById(id: Int): Boolean {
        val span = tracer.spanBuilder("Repository.existsById")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()
        
        val scope = span.makeCurrent()
        try {
            span.setAttribute("productId", id.toLong())
            span.setAttribute("db.operation", "SELECT")
            
            val result = delegate.existsById(id)
            span.setAttribute("db.result.exists", result)
            return result
        } finally {
            span.end()
            scope.close()
        }
    }
}
