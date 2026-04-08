package com.example.otel.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Configuration
class OtelConfig(
    @Value("\${spring.application.name:otel}")
    private val serviceName: String
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Bean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer(serviceName)
    }
    
    @Bean
    fun openTelemetry(): OpenTelemetry {
        logger.info("Configuring OpenTelemetry SDK with service name: $serviceName")
        
        val resource = Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.of(
                        ResourceAttributes.SERVICE_NAME, serviceName
                    )
                )
            )
        
        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:43170")
            .build()
        
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()
        
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal()
        
        logger.info("OpenTelemetry SDK configured successfully")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            tracerProvider.close()
        })
        
        return openTelemetry
    }
}

@Component
@Order(1)
class TracingFilter(
    private val tracer: Tracer
) : Filter {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val productIdPathPattern = Pattern.compile("^/api/products/(\\d+)$")
    private val test500Pattern = Pattern.compile("^/api/products/test-500$")
    
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        
        val requestPath = httpRequest.requestURI
        val isTest500Endpoint = test500Pattern.matcher(requestPath).matches()
        
        val wrappedResponse = ResponseCaptureWrapper(httpResponse)
        
        val spanName = "${httpRequest.method} ${httpRequest.requestURI}"
        
        val span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.SERVER)
            .startSpan()
        
        val scope = span.makeCurrent()
        
        MDC.put("trace_id", span.spanContext.traceId)
        MDC.put("span_id", span.spanContext.spanId)
        
        try {
            span.setAttribute("http.method", httpRequest.method)
            span.setAttribute("http.url", httpRequest.requestURL.toString())
            span.setAttribute("http.route", httpRequest.requestURI)
            span.setAttribute("http.host", httpRequest.remoteAddr ?: "unknown")
            
            if (!isTest500Endpoint) {
                extractProductIdFromPath(requestPath)?.let {
                    span.setAttribute(AttributeKey.stringKey("productId"), it)
                }
            }
            
            chain.doFilter(request, wrappedResponse)
            
            val statusCode = wrappedResponse.capturedStatus.toLong()
            span.setAttribute("http.status_code", statusCode)
            
            if (!isTest500Endpoint && httpRequest.method == "POST" && statusCode in 200..299) {
                wrappedResponse.capturedBody?.let { body ->
                    extractProductIdFromResponseBody(body)?.let { productId ->
                        span.setAttribute(AttributeKey.stringKey("productId"), productId)
                    }
                }
            }
            
            span.setStatus(if (statusCode >= 400) StatusCode.ERROR else StatusCode.OK)
        } catch (e: Exception) {
            span.setAttribute("http.status_code", 500L)
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.recordException(e)
            throw e
        } finally {
            MDC.remove("trace_id")
            MDC.remove("span_id")
            span.end()
            scope.close()
        }
    }
    
    private fun extractProductIdFromPath(path: String): String? {
        val matcher = productIdPathPattern.matcher(path)
        return if (matcher.matches()) matcher.group(1) else null
    }
    
    private fun extractProductIdFromResponseBody(body: String): String? {
        val idPattern = Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
        val matcher = idPattern.matcher(body)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    override fun init(filterConfig: jakarta.servlet.FilterConfig?) {
        logger.info("TracingFilter initialized")
    }
    
    override fun destroy() {
        logger.info("TracingFilter destroyed")
    }
}

class ResponseCaptureWrapper(delegate: HttpServletResponse) : HttpServletResponseWrapper(delegate) {
    
    private val capturedBodyBuilder = StringBuilder()
    var capturedStatus: Int = 0
        private set
    
    var capturedBody: String? = null
        private set
    
    override fun getWriter(): java.io.PrintWriter {
        return java.io.PrintWriter(object : java.io.Writer() {
            override fun write(c: CharArray, off: Int, len: Int) {
                capturedBodyBuilder.append(c, off, len)
            }
            
            override fun write(s: String, off: Int, len: Int) {
                capturedBodyBuilder.append(s, off, len)
            }
            
            override fun flush() {
                capturedBody = capturedBodyBuilder.toString()
            }
            
            override fun close() {}
        })
    }
    
    override fun setStatus(status: Int) {
        capturedStatus = status
        super.setStatus(status)
    }
    
    override fun sendError(sc: Int, msg: String?) {
        capturedStatus = sc
        super.sendError(sc, msg)
    }
    
    override fun sendError(sc: Int) {
        capturedStatus = sc
        super.sendError(sc)
    }
    
    override fun getStatus(): Int {
        return if (capturedStatus > 0) capturedStatus else super.getStatus()
    }
}
