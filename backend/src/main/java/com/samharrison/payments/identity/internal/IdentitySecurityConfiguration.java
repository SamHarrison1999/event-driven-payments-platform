package com.samharrison.payments.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http
    ) throws Exception {
        http
            .csrf(Customizer.withDefaults())
            .sessionManagement(
                session ->
                    session.sessionCreationPolicy(
                        SessionCreationPolicy.IF_REQUIRED
                    )
            )
            .requestCache(
                AbstractHttpConfigurer::disable
            )
            .httpBasic(
                AbstractHttpConfigurer::disable
            )
            .formLogin(
                AbstractHttpConfigurer::disable
            )
            .logout(
                AbstractHttpConfigurer::disable
            )
            .exceptionHandling(
                exceptions ->
                    exceptions
                        .authenticationEntryPoint(
                            (
                                request,
                                response,
                                exception
                            ) ->
                                response.sendError(
                                    HttpStatus.UNAUTHORIZED.value()
                                )
                        )
                        .accessDeniedHandler(
                            (
                                request,
                                response,
                                exception
                            ) ->
                                response.sendError(
                                    HttpStatus.FORBIDDEN.value()
                                )
                        )
            )
            .authorizeHttpRequests(
                authorize ->
                    authorize
                        .requestMatchers(
                            HttpMethod.POST,
                            "/api/v1/identity/registrations"
                        )
                        .permitAll()
                        .requestMatchers(
                            HttpMethod.GET,
                            "/api/v1/identity/csrf",
                            "/api/v1/system/info",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info",
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/swagger-ui.html",
                            "/swagger-ui/**"
                        )
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
            );

        return http.build();
    }
}
