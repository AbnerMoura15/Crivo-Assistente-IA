package com.ragbot.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração central de segurança.
 *
 * Regras de acesso:
 * ┌─────────────────────────────────────┬──────────────────────────────┐
 * │ Endpoint                            │ Quem pode                    │
 * ├─────────────────────────────────────┼──────────────────────────────┤
 * │ POST /api/auth/**                   │ Todos (login/registro)       │
 * │ GET  /api/documents                 │ VIEWER, EDITOR, ADMIN        │
 * │ POST /api/documents                 │ EDITOR, ADMIN                │
 * │ DELETE /api/documents/{id}          │ ADMIN                        │
 * │ POST /api/chat                      │ VIEWER, EDITOR, ADMIN        │
 * │ GET|PUT|DELETE /api/users/**        │ ADMIN                        │
 * │ GET / (frontend estático)           │ Todos                        │
 * │ /actuator/health                    │ Todos                        │
 * └─────────────────────────────────────┴──────────────────────────────┘
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // API REST não usa CSRF (stateless via JWT)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless: sem sessão HTTP — o JWT é o estado
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Regras de acesso por endpoint e método HTTP
            .authorizeHttpRequests(auth -> auth

                // Endpoints públicos
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/", "/index.html", "/**.css", "/**.js").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // Documentos
                .requestMatchers(HttpMethod.GET, "/api/documents/**").hasAnyRole("VIEWER", "EDITOR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/documents/**").hasAnyRole("EDITOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/documents/**").hasRole("ADMIN")

                // Chat — todos os perfis autenticados
                .requestMatchers("/api/chat/**").hasAnyRole("VIEWER", "EDITOR", "ADMIN")
                .requestMatchers("/api/feedback/**").hasAnyRole("VIEWER", "EDITOR", "ADMIN")

                // Gestão de usuários — apenas ADMIN
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                // Qualquer outra rota exige autenticação
                .anyRequest().authenticated()
            )

            // Injeta o filtro JWT antes do filtro padrão de username/password
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt com custo 12 — bom equilíbrio segurança/performance
        return new BCryptPasswordEncoder(12);
    }
}
