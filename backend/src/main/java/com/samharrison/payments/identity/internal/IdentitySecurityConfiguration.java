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
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository()
        );
    }

    @Bean
    SessionAuthenticationStrategy
    sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    LogoutHandler logoutHandler(
        SecurityContextRepository
            securityContextRepository
    ) {
        SecurityContextLogoutHandler logoutHandler =
            new SecurityContextLogoutHandler();

        logoutHandler.setSecurityContextRepository(
            securityContextRepository
        );

        return logoutHandler;
    }

    @Bean
    DefaultCookieSerializer identitySessionCookieSerializer(
        ServerProperties serverProperties
    ) {
        Cookie cookie =
            serverProperties
                .getServlet()
                .getSession()
                .getCookie();

        DefaultCookieSerializer serializer =
            new DefaultCookieSerializer();

        if (cookie.getName() != null) {
            serializer.setCookieName(
                cookie.getName()
            );
        }

        if (cookie.getDomain() != null) {
            serializer.setDomainName(
                cookie.getDomain()
            );
        }

        if (cookie.getPath() != null) {
            serializer.setCookiePath(
                cookie.getPath()
            );
        }

        if (cookie.getHttpOnly() != null) {
            serializer.setUseHttpOnlyCookie(
                cookie.getHttpOnly()
            );
        }

        if (cookie.getSecure() != null) {
            serializer.setUseSecureCookie(
                cookie.getSecure()
            );
        }

        if (cookie.getMaxAge() != null) {
            serializer.setCookieMaxAge(
                Math.toIntExact(
                    cookie.getMaxAge().toSeconds()
                )
            );
        }

        if (cookie.getSameSite() != null) {
            serializer.setSameSite(
                cookie.getSameSite()
                    .attributeValue()
            );
        }

        if (cookie.getPartitioned() != null) {
            serializer.setPartitioned(
                cookie.getPartitioned()
            );
        }

        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityContextRepository
            securityContextRepository,
        SessionAuthenticationStrategy
            sessionAuthenticationStrategy
    ) throws Exception {
        http
            .csrf(Customizer.withDefaults())
            .securityContext(
                securityContext ->
                    securityContext
                        .securityContextRepository(
                            securityContextRepository
                        )
                        .requireExplicitSave(true)
            )
            .sessionManagement(
                session ->
                    session
                        .sessionCreationPolicy(
                            SessionCreationPolicy.IF_REQUIRED
                        )
                        .sessionAuthenticationStrategy(
                            sessionAuthenticationStrategy
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
                            "/api/v1/identity/registrations",
                            "/api/v1/identity/session"
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
