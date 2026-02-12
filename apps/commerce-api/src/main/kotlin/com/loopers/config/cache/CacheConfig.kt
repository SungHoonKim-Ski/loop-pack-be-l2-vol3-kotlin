package com.loopers.config.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    companion object {
        const val AUTH_CACHE = "auth-cache"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val authCache = CaffeineCache(
            AUTH_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build(),
        )

        return SimpleCacheManager().apply {
            setCaches(listOf(authCache))
        }
    }
}
