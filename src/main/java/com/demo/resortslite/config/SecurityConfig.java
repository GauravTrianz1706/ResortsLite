package com.demo.resortslite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "azure.ad.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        // Default configuration when Azure AD is not enabled
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/api/**").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                .antMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .headers().frameOptions().disable(); // For H2 console

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "azure.ad.enabled", havingValue = "true")
    public SecurityFilterChain azureAdSecurityFilterChain(HttpSecurity http) throws Exception {
        // Azure AD OAuth2 configuration
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            .and()
            .oauth2Login()
            .and()
            .oauth2ResourceServer()
                .jwt();

        return http.build();
    }
}
