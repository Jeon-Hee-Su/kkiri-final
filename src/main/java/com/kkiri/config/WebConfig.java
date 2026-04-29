package com.kkiri.config;

import com.kkiri.interceptor.GroupAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final GroupAccessInterceptor groupAccessInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:src/main/resources/static/uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(groupAccessInterceptor)
                .addPathPatterns(
                    "/group/detail/*",   // 그룹 메인
                    "/groupsettings",    // 그룹 설정
                    "/groupmembers",     // 멤버 관리
                    "/membermanage"      // 멤버 관리 (별칭)
                );
    }
}