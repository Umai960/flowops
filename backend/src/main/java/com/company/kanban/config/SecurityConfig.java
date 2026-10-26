package com.company.kanban.config;

import com.company.kanban.security.JwtAuthenticationFilter;
import com.company.kanban.security.DeviceAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_FRONTEND_PATHS = {
            "/",
            "/index.html",
            "/assets/**",
            "/favicon.ico",
            "/favicon/**",
            "/favicon.svg",
            "/manifest.json",
            "/manifest.webmanifest",
            "/robots.txt",
            "/icons/**",
            "/icons.svg",
            "/service-worker.js",
            "/sw.js",
            "/login",
            "/activate",
            "/dashboard",
            "/projects",
            "/requests",
            "/requests/**",
            "/reviews",
            "/reports",
            "/reports/**",
            "/ppc/**",
            "/chat",
            "/history",
            "/completed",
            "/settings/**",
            "/admin",
            "/admin/**",
            "/manager",
            "/staff"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DeviceAuthenticationFilter deviceAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DeviceAuthenticationFilter deviceAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
        this.deviceAuthenticationFilter = deviceAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> {})

                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                        )
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth ->
                        auth
                                // Live chat streams finish on an async dispatch; the original request was already authorized.
                                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

                                .requestMatchers(
                                        PathPatternRequestMatcher
                                                .withDefaults()
                                                .matcher(POST, "/api/auth/login")
                                ).permitAll()

                                .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(POST, "/api/devices/exchange")).permitAll()

                                .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(POST, "/api/auth/activate")).permitAll()

                                .requestMatchers(
                                        PathPatternRequestMatcher
                                                .withDefaults()
                                                .matcher(GET, "/api/health")
                                ).permitAll()

                                .requestMatchers(frontendMatchers(GET))
                                .permitAll()

                                .requestMatchers(frontendMatchers(HEAD))
                                .permitAll()

                                .requestMatchers(
                                        PathPatternRequestMatcher
                                                .withDefaults()
                                                .matcher("/error")
                                ).permitAll()

                                .requestMatchers(
                                        PathPatternRequestMatcher
                                                .withDefaults()
                                .matcher(HttpMethod.GET, "/api/users/assignable")
                                ).authenticated()

                                .requestMatchers(
                                        PathPatternRequestMatcher.withDefaults()
                                                .matcher(HttpMethod.GET, "/api/users/task-assignees")
                                ).authenticated()

                                .requestMatchers(
                                        PathPatternRequestMatcher
                                                .withDefaults()
                                                .matcher("/api/users/**")
                                ).hasRole("ADMIN")

                                .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        deviceAuthenticationFilter,
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }

    private static PathPatternRequestMatcher[] frontendMatchers(HttpMethod method) {
        PathPatternRequestMatcher.Builder builder =
                PathPatternRequestMatcher.withDefaults();

        return java.util.Arrays.stream(PUBLIC_FRONTEND_PATHS)
                .map(path -> builder.matcher(method, path))
                .toArray(PathPatternRequestMatcher[]::new);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
