package com.example.api_gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    private static final String SECRET = "test-secret-with-at-least-thirty-two-bytes-long";
    @Autowired private WebTestClient webTestClient;

    @Test
    void endpointProtegidoConJwtValidoEsPermitido() {
        webTestClient.get()
            .uri("/actuator/info")
            .header("Authorization", "Bearer " + token(SECRET, new Date(System.currentTimeMillis() + 60_000)))
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void endpointProtegidoSinJwtDevuelve401() {
        webTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void healthSinJwtEsPublico() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void jwtConFirmaInvalidaDevuelve401() {
        webTestClient.get()
            .uri("/actuator/info")
            .header("Authorization", "Bearer " + token("another-secret-with-at-least-thirty-two-bytes", new Date(System.currentTimeMillis() + 60_000)))
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void jwtExpiradoDevuelve401() {
        webTestClient.get()
            .uri("/actuator/info")
            .header("Authorization", "Bearer " + token(SECRET, new Date(System.currentTimeMillis() - 60_000)))
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void jwtManipuladoDevuelve401() {
        String jwt = token(SECRET, new Date(System.currentTimeMillis() + 60_000));
        webTestClient.get()
            .uri("/actuator/info")
            .header("Authorization", "Bearer " + jwt.substring(0, jwt.length() - 2) + "aa")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void loginSinJwtNoEsBloqueadoPorElGateway() {
        webTestClient.post()
            .uri("/auth/login")
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    private String token(String secret, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject("admin")
            .claim("userId", 1L)
            .claim("role", "ADMIN")
            .issuedAt(new Date())
            .expiration(expiration)
            .signWith(key)
            .compact();
    }
}