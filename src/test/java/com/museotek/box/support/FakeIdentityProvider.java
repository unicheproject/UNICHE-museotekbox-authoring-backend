package com.museotek.box.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * In-process fake Keycloak realm for tests: a JDK {@code HttpServer} serving real OIDC
 * discovery + JWKS documents, so {@code JwtDecoders.fromIssuerLocation} (used by
 * {@code SecurityConfig}) does a genuine HTTP round trip and real RS256 signature
 * verification against a real key pair — no mocking of Spring Security internals.
 *
 * <p>Any {@code @SpringBootTest} boots {@code SecurityConfig}, which eagerly performs
 * OIDC discovery against {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} at
 * context startup — so this must be running and wired in via
 * {@code @DynamicPropertySource} before the context refreshes, even for tests that never
 * make an authenticated request.
 */
public final class FakeIdentityProvider {

    private static final String KEY_ID = "test-key";

    private final HttpServer server;
    private final String issuerUri;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private FakeIdentityProvider(HttpServer server, String issuerUri, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.server = server;
        this.issuerUri = issuerUri;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static FakeIdentityProvider start() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String issuerUri = "http://localhost:" + server.getAddress().getPort() + "/realms/test";
            ObjectMapper mapper = new ObjectMapper();

            server.createContext("/realms/test/.well-known/openid-configuration", exchange -> {
                Map<String, Object> config = Map.of(
                        "issuer", issuerUri,
                        "authorization_endpoint", issuerUri + "/protocol/openid-connect/auth",
                        "token_endpoint", issuerUri + "/protocol/openid-connect/token",
                        "jwks_uri", issuerUri + "/protocol/openid-connect/certs",
                        "response_types_supported", List.of("code"),
                        "subject_types_supported", List.of("public"),
                        "id_token_signing_alg_values_supported", List.of("RS256")
                );
                byte[] body = mapper.writeValueAsBytes(config);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });

            server.createContext("/realms/test/protocol/openid-connect/certs", exchange -> {
                RSAKey jwk = new RSAKey.Builder(publicKey)
                        .keyID(KEY_ID)
                        .algorithm(JWSAlgorithm.RS256)
                        .keyUse(KeyUse.SIGNATURE)
                        .build();
                String body = "{\"keys\":[" + jwk.toJSONString() + "]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            server.start();
            return new FakeIdentityProvider(server, issuerUri, privateKey, publicKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start FakeIdentityProvider", e);
        }
    }

    public void stop() {
        server.stop(0);
    }

    public String issuerUri() {
        return issuerUri;
    }

    /** Signs a real RS256 JWT verifiable against this fake IdP's published JWKS. */
    public String signedJwt(String subject, String preferredUsername, String issuer,
                             List<String> audience, Date issuedAt, Date expiresAt) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(issuedAt)
                    .expirationTime(expiresAt);
            if (preferredUsername != null) {
                claims.claim("preferred_username", preferredUsername);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign test JWT", e);
        }
    }
}
