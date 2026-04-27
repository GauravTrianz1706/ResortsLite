package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                // Allow H2 console for development
                .antMatchers("/h2-console/**").permitAll()
                // Allow health check endpoints
                .antMatchers("/actuator/health").permitAll()
                // Require authentication for all other endpoints
                .anyRequest().authenticated()
            .and()
            .csrf().disable() // Disable CSRF for REST API (enable in production with proper token handling)
            .headers().frameOptions().disable(); // Allow H2 console frames

        // In production with Azure AD, uncomment the following:
        // http.oauth2Login()
        //     .and()
        //     .oauth2ResourceServer()
        //     .jwt();
    }
}
