package com.card.Yugioh.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.withUsername("admin")
                .password("{noop}admin")
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // .csrf(AbstractHttpConfigurer::disable)  // CSRF 보호 비활성화 (테스트 목적으로만)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/queue/**", "/api/admin/queue/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .headers(headers ->
                headers.permissionsPolicy(policy ->
                    policy.policy("accelerometer=(self); " +
                                "gyroscope=(self); " +
                                "orientation-sensor=(self)")))
            .httpBasic(Customizer.withDefaults())
            .formLogin();  // 기본 로그인 폼 활성화
        return http.build();
    }
}
