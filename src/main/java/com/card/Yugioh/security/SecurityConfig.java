package com.card.Yugioh.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf->csrf.disable())  // CSRF 보호 비활성화 (테스트 목적으로만)
            .authorizeHttpRequests(auth->auth.anyRequest().permitAll())
            .formLogin(form->form.disable());  // 기본 로그인 폼 비활성화
        return http.build();
    }
}
