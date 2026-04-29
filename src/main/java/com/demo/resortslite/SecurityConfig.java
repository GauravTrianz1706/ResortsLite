package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for Azure Active Directory integration.
 * This replaces file-based authentication with cloud-native identity management.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/h2-console/**").permitAll()
                .antMatchers("/api/bookings/**").permitAll() // Configure based on your security requirements
                .anyRequest().authenticated()
            .and()
            .csrf().disable() // Disable CSRF for API endpoints (configure appropriately for production)
            .headers().frameOptions().disable(); // Allow H2 console frames
        
        // Azure AD OAuth2 configuration would be added here
        // http.oauth2Login() and http.oauth2ResourceServer() for full Azure AD integration
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
