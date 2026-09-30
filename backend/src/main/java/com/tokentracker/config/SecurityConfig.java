package com.tokentracker.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless security: this service issues HS256 JWTs ({@code /api/auth/login}) and validates them as a resource server.
 * <ul>
 * <li>visitors read the token summaries and the hourly prices;</li>
 * <li>authenticated users also read quantities, holders and holder statistics;</li>
 * <li>admins also add and remove tokens and trigger synchronisations.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    /** JWT claim holding the account role. */
    public static final String ROLE_CLAIM = "role";

    private static final String ADMIN = "ADMIN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> writeError(objectMapper, response,
                HttpStatus.UNAUTHORIZED, "Authentication is required, or your session has expired.", "unauthorized");
        http
                .csrf(csrf -> csrf.disable()) // bearer tokens only, no cookie to protect
                .cors(withDefaults()) // uses the Spring MVC mappings of CorsConfig
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        // Visitors: token summaries and hourly prices
                        .requestMatchers(HttpMethod.GET, "/api/tokens", "/api/tokens/*", "/api/tokens/*/prices").permitAll()
                        // Admin: token management and synchronisations
                        .requestMatchers(HttpMethod.POST, "/api/tokens", "/api/tokens/sync", "/api/tokens/*/sync").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/tokens/*").hasRole(ADMIN)
                        // Authenticated users: everything else under /api (quantities, holders, holder stats, account)
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/management/health", "/management/health/**", "/management/info").permitAll()
                        .requestMatchers("/management/**").hasRole(ADMIN)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler((request, response, ex) -> writeError(objectMapper,
                        response, HttpStatus.FORBIDDEN, "You are not allowed to perform this action.", "forbidden")));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(TokenTrackerProperties properties) {
        String secret = properties.security().jwtSecret();
        byte[] key;
        if (secret == null || secret.isBlank()) {
            LOG.warn("token-tracker.security.jwt-secret is not set: using a random key, "
                    + "every session will be invalidated on restart. Set JWT_SECRET in production.");
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = Base64.getDecoder().decode(secret.trim());
            if (key.length < 32) {
                throw new IllegalStateException("token-tracker.security.jwt-secret must be at least 256 bits (32 bytes)");
            }
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Same body as {@code GlobalExceptionHandler}, so the frontend reads every error the same way. */
    private static void writeError(ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status,
            String message, String errorKey) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("entityName", "auth");
        body.put("errorKey", errorKey);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
