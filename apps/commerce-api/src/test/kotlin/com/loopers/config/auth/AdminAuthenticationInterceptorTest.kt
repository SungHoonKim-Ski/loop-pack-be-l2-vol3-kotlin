package com.loopers.config.auth

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod

class AdminAuthenticationInterceptorTest {

    private lateinit var interceptor: AdminAuthenticationInterceptor

    @BeforeEach
    fun setUp() {
        interceptor = AdminAuthenticationInterceptor()
    }

    private fun createRequest(ldapHeader: String? = null): MockHttpServletRequest {
        return MockHttpServletRequest().apply {
            ldapHeader?.let { addHeader(AdminAuthenticationInterceptor.HEADER_LDAP, it) }
        }
    }

    private fun createAnnotatedHandlerMethod(): HandlerMethod {
        val method = AnnotatedTestController::class.java.getMethod("adminEndpoint")
        return HandlerMethod(AnnotatedTestController(), method)
    }

    private fun createNonAnnotatedHandlerMethod(): HandlerMethod {
        val method = NonAnnotatedTestController::class.java.getMethod("publicEndpoint")
        return HandlerMethod(NonAnnotatedTestController(), method)
    }

    private fun createClassLevelAnnotatedHandlerMethod(): HandlerMethod {
        val method = ClassLevelAnnotatedTestController::class.java.getMethod("adminEndpoint")
        return HandlerMethod(ClassLevelAnnotatedTestController(), method)
    }

    @DisplayName("어드민 인증 인터셉터 동작 검증")
    @Nested
    inner class AdminAuthentication {

        @DisplayName("LDAP 헤더가 정확할 때 요청을 통과시킨다.")
        @Test
        fun passesRequest_whenLdapHeaderIsCorrect() {
            // arrange
            val request = createRequest(AdminAuthenticationInterceptor.LDAP_VALUE)
            val response = MockHttpServletResponse()
            val handler = createAnnotatedHandlerMethod()

            // act
            val result = interceptor.preHandle(request, response, handler)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("LDAP 헤더가 없을 때 UNAUTHORIZED 예외를 발생시킨다.")
        @Test
        fun throwsUnauthorized_whenLdapHeaderIsMissing() {
            // arrange
            val request = createRequest()
            val response = MockHttpServletResponse()
            val handler = createAnnotatedHandlerMethod()

            // act & assert
            assertThrows<CoreException> {
                interceptor.preHandle(request, response, handler)
            }
        }

        @DisplayName("LDAP 헤더 값이 틀릴 때 UNAUTHORIZED 예외를 발생시킨다.")
        @Test
        fun throwsUnauthorized_whenLdapHeaderValueIsWrong() {
            // arrange
            val request = createRequest("wrong.value")
            val response = MockHttpServletResponse()
            val handler = createAnnotatedHandlerMethod()

            // act & assert
            assertThrows<CoreException> {
                interceptor.preHandle(request, response, handler)
            }
        }

        @DisplayName("@AdminAuthenticated 어노테이션이 없는 메서드는 인증 없이 통과시킨다.")
        @Test
        fun passesRequest_whenAnnotationIsMissing() {
            // arrange
            val request = createRequest()
            val response = MockHttpServletResponse()
            val handler = createNonAnnotatedHandlerMethod()

            // act
            val result = interceptor.preHandle(request, response, handler)

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("클래스 레벨 @AdminAuthenticated 어노테이션이 적용되면 메서드에서도 인증을 수행한다.")
        @Test
        fun performsAuthentication_whenClassLevelAnnotationIsPresent() {
            // arrange
            val request = createRequest()
            val response = MockHttpServletResponse()
            val handler = createClassLevelAnnotatedHandlerMethod()

            // act & assert
            assertThrows<CoreException> {
                interceptor.preHandle(request, response, handler)
            }
        }
    }

    // 메서드 레벨 @AdminAuthenticated 테스트용 컨트롤러
    class AnnotatedTestController {
        @AdminAuthenticated
        fun adminEndpoint() {}
    }

    // @AdminAuthenticated 없는 테스트용 컨트롤러
    class NonAnnotatedTestController {
        fun publicEndpoint() {}
    }

    // 클래스 레벨 @AdminAuthenticated 테스트용 컨트롤러
    @AdminAuthenticated
    class ClassLevelAnnotatedTestController {
        fun adminEndpoint() {}
    }
}
