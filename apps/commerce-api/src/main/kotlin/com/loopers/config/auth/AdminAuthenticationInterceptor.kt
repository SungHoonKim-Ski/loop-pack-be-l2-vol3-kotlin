package com.loopers.config.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminAuthenticationInterceptor : HandlerInterceptor {

    companion object {
        const val HEADER_LDAP = "X-Loopers-Ldap"
        const val LDAP_VALUE = "loopers.admin"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        val hasAnnotation = handler.getMethodAnnotation(AdminAuthenticated::class.java) != null ||
            handler.beanType.getAnnotation(AdminAuthenticated::class.java) != null
        if (!hasAnnotation) return true

        val ldapHeader = request.getHeader(HEADER_LDAP)
        if (ldapHeader != LDAP_VALUE) {
            throw CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 정보가 없습니다.")
        }
        return true
    }
}
