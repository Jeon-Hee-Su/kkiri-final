package com.kkiri.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.kkiri.security.JwtFilter;
import com.kkiri.security.OAuth2SuccessHandler;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	private OAuth2SuccessHandler oAuth2SuccessHandler;

	@Autowired
	private JwtFilter jwtFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(request -> {
				var config = new org.springframework.web.cors.CorsConfiguration();
				config.setAllowedOrigins(java.util.List.of("http://localhost:8566", "https://dorothy-untumultuous-hygrometrically.ngrok-free.dev ")); 
				config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
				config.setAllowedHeaders(java.util.List.of("*"));
				config.setAllowCredentials(true); 
				return config;
			}))
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/session-stream").authenticated()
				.requestMatchers(
							"/",
							"/index",
					        "/auth/**",
					        "/login",       // 💡 중요: /login/** 과 별개로 정확히 /login 을 허용해야 합니다.
					        "/login/**",
					        "/error",       // 💡 중요: 에러 페이지 접근을 허용해야 404 무한루프를 막습니다.
					        "/api/auth/**",
					        "/oauth2/**",
					        "/css/**",
					        "/js/**", 
					        "/img/**", 
					        "/favicon.ico",
					        "/posscanner",
					        "/join",
					        "/api/groups/by-code/**",
					        "/api/payment/qr/pay",
					        "/firebase-messaging-sw.js",
					        "/api/fcm/**",
					        "/api/admin/auto-transfer/**"
						).permitAll(	)
					.anyRequest().authenticated()
					)
				.logout(logout -> logout.logoutUrl("/api/user/logout").logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true).deleteCookies("JSESSIONID","accessToken","refreshToken").permitAll())
				.oauth2Login(oauth2 -> oauth2
					.loginPage("/auth/login")
					.successHandler(oAuth2SuccessHandler)
				)
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, authException) -> {
							String path = request.getRequestURI();
							// API 요청은 JSON 401 반환 (fetch가 HTML을 받아 파싱 오류 나는 것 방지)
							if (path.startsWith("/api/")) {
								response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
								response.setContentType("application/json;charset=UTF-8");
								response.getWriter().write("{\"error\":\"인증이 필요합니다.\",\"status\":401}");
							} else {
								// 일반 페이지 요청은 로그인 페이지로 리다이렉트
								response.sendRedirect("/auth/login?needLogin=true");
							}
						})
					);

		// JWT 필터 추가
		http.addFilterBefore(jwtFilter,
				org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}