package com.dropboxclone.backend.config;

import com.dropboxclone.backend.auth.security.JwtProperties;
import com.dropboxclone.backend.auth.security.RefreshTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, JwtProperties.class, RefreshTokenProperties.class})
public class ApplicationConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
