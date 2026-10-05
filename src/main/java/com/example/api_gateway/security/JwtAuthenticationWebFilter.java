package com.example.api_gateway.security;

import io.jsonwebtoken.Claims;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationWebFilter.class);

    private static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/actuator/health");

    private final JwtService jwtService;

    public JwtAuthenticationWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String path = exchange.getRequest().getPath().value();

        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            log.warn("Authentication failed: JWT missing, path={}", path);

            return unauthorized(exchange);
        }

        try {

            Claims claims = jwtService.parseToken(authorization.substring(7));

            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            Object userId = claims.get("userId");

            if (username == null || username.isBlank() || role == null || role.isBlank()
                    || !(userId instanceof Number)) {

                log.warn("Authentication failed: invalid JWT claims, path={}", path);

                return unauthorized(exchange);
            }

            log.info("Authentication successful: username={}, path={}", username, path);

            var authentication = new UsernamePasswordAuthenticationToken(username, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            return chain.filter(exchange)

                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (RuntimeException exception) {

            log.warn("Authentication failed: invalid JWT, path={}", path);

            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body = "{\"code\":\"UNAUTHORIZED\",\"message\":\"JWT inválido o ausente\"}"
                .getBytes(StandardCharsets.UTF_8);

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(body)
                        )
                );
    }
}