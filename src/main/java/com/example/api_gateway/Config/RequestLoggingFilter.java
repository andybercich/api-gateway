package com.example.api_gateway.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String method =
                exchange.getRequest().getMethod().name();

        String path =
                exchange.getRequest().getPath().value();

        log.info("Request received: method={}, path={}", method, path);

        return chain.filter(exchange)
                .doFinally(signal ->
                        log.info(
                                "Request completed: method={}, path={}, status={}",
                                method,
                                path,
                                exchange.getResponse().getStatusCode()
                        )
                );
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
