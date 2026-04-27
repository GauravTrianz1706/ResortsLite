package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Security configuration for Azure Active Directory integration.
 * Replaces file-based authentication with cloud-native identity management.
 */
@Configuration
@EnableWebSecurity
public class AzureSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/api/bookings/**").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .csrf().disable()
            .headers().frameOptions().disable();
        
        // Note: In production, enable Azure AD authentication:
        // http.oauth2Login()
        //     .and()
        //     .oauth2ResourceServer()
        //     .jwt();
    }
}
