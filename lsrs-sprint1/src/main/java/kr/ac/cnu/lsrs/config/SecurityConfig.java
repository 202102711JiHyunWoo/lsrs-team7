package kr.ac.cnu.lsrs.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.cnu.lsrs.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.*;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    @Bean JwtEncoder jwtEncoder(KeyMaterial key) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key.bytes()));
    }
    @Bean JwtDecoder jwtDecoder(KeyMaterial key, Clock clock, @Value("${app.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key.bytes(), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        OAuth2TokenValidator<Jwt> strictExpiry = jwt -> jwt.getExpiresAt() != null && clock.instant().isBefore(jwt.getExpiresAt())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token is expired or has no expiry", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(issuer), strictExpiry));
        return decoder;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, AppUserRepository users) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users", "/api/email-verifications", "/api/email-verifications/resend", "/api/auth/tokens", "/api/app-info", "/api/dev/mailbox").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> securityError(response, 401, "UNAUTHENTICATED", "로그인이 필요한 기능입니다."))
                        .accessDeniedHandler((request, response, exception) -> securityError(response, 403, "FORBIDDEN", "이 기능을 이용할 권한이 없습니다.")))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))
                .addFilterBefore(new JwtAuthenticationFilter(decoder, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    static void securityError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message +
                "\",\"fieldErrors\":{},\"details\":{},\"requestId\":\"" + UUID.randomUUID() + "\"}");
    }
}
