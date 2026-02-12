package com.loopers.config

import com.loopers.config.auth.AdminAuthenticationInterceptor
import com.loopers.config.auth.AuthenticatedMemberArgumentResolver
import com.loopers.config.auth.MemberAuthenticationInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val memberAuthenticationInterceptor: MemberAuthenticationInterceptor,
    private val adminAuthenticationInterceptor: AdminAuthenticationInterceptor,
    private val authenticatedMemberArgumentResolver: AuthenticatedMemberArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(memberAuthenticationInterceptor)
            .addPathPatterns("/api/**")
        registry.addInterceptor(adminAuthenticationInterceptor)
            .addPathPatterns("/api/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedMemberArgumentResolver)
    }
}
