package com.card.Yugioh.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password-hash:{noop}adminpass}")
    private String adminPasswordHash;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.withUsername(adminUsername)
            .password(adminPasswordHash)
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/queue/**", "/api/admin/queue/**").hasRole("ADMIN")
                .requestMatchers("/images/**").permitAll()
                .anyRequest().permitAll())
            .headers(headers ->
                headers.permissionsPolicy(policy ->
                    policy.policy("accelerometer=(self), " +
                        "gyroscope=(self), " +
                        "orientation-sensor=(self)")))
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
