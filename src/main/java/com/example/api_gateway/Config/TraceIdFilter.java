package com.example.api_gateway.Config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String traceId = exchange.getRequest()
                .getHeaders()
                .getFirst(TRACE_ID);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        String finalTraceId = traceId;

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(TRACE_ID, finalTraceId)
                .build();

        ServerWebExchange mutatedExchange = exchange
                .mutate()
                .request(request)
                .build();

        exchange.getResponse()
                .getHeaders()
                .add(TRACE_ID, finalTraceId);

        return chain.filter(mutatedExchange)
                .contextWrite(context ->
                        context.put(TRACE_ID, finalTraceId)
                );
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
