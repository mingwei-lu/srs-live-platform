package com.srs.live.config;

import com.srs.live.interceptor.JwtAuthInterceptor;
import com.srs.live.interceptor.ThirdPartyAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final ThirdPartyAuthInterceptor thirdPartyAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor,
                         ThirdPartyAuthInterceptor thirdPartyAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.thirdPartyAuthInterceptor = thirdPartyAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 鉴权拦截器：拦截所有 API，排除无需鉴权的路径
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/users/login",
                        "/api/v1/users/register",
                        "/api/v1/srs/callback/**"
                );

        // 第三方 API 鉴权拦截器
        registry.addInterceptor(thirdPartyAuthInterceptor)
                .addPathPatterns("/api/v1/third-party/**");
    }
}