package com.loopers.config.auth

import com.loopers.config.cache.CacheConfig
import com.loopers.config.cache.CachedAuth
import com.loopers.domain.member.MemberService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val memberService: MemberService,
    private val cacheManager: CacheManager,
) : HandlerInterceptor {

    companion object {
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
        const val AUTHENTICATED_MEMBER_ATTRIBUTE = "authenticatedMember"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        handler.getMethodAnnotation(Authenticated::class.java) ?: return true

        val loginId = request.getHeader(HEADER_LOGIN_ID)
        val password = request.getHeader(HEADER_LOGIN_PW)

        if (loginId.isNullOrBlank() || password.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 없습니다.")
        }

        val authenticatedMember = resolveFromCacheOrAuthenticate(loginId, password)
        request.setAttribute(AUTHENTICATED_MEMBER_ATTRIBUTE, authenticatedMember)

        return true
    }

    private fun resolveFromCacheOrAuthenticate(loginId: String, password: String): AuthenticatedMember {
        val cache = cacheManager.getCache(CacheConfig.AUTH_CACHE)
        val cachedAuth = cache?.get(loginId, CachedAuth::class.java)

        if (cachedAuth != null && cachedAuth.matchesPassword(password)) {
            return cachedAuth.toAuthenticatedMember()
        }

        val member = memberService.authenticate(loginId, password)
        val authenticatedMember = AuthenticatedMember(id = member.id, loginId = member.loginId)
        cache?.put(loginId, CachedAuth.of(authenticatedMember, password))

        return authenticatedMember
    }
}
