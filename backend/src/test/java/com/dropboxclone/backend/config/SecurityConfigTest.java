package com.dropboxclone.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dropboxclone.backend.auth.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig(
        mock(JwtAuthenticationFilter.class),
        mock(AuthenticationEntryPoint.class)
    );

    @Test
    void corsAllowsCredentialedLoginFromLocalFrontendOrigins() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource("http://localhost:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowPrivateNetwork()).isTrue();
        assertThat(cors.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(cors.checkOrigin("http://127.0.0.1:5173")).isEqualTo("http://127.0.0.1:5173");
        assertThat(cors.checkOrigin("http://[::1]:5173")).isEqualTo("http://[::1]:5173");
        assertThat(cors.checkOrigin("https://evil.example")).isNull();
    }
}
