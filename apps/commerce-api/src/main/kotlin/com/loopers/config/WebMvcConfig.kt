package com.loopers.config

import com.loopers.config.auth.AuthenticatedMemberArgumentResolver
import com.loopers.config.auth.AuthenticationInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val authenticationInterceptor: AuthenticationInterceptor,
    private val authenticatedMemberArgumentResolver: AuthenticatedMemberArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authenticationInterceptor)
            .addPathPatterns("/api/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedMemberArgumentResolver)
    }
}
