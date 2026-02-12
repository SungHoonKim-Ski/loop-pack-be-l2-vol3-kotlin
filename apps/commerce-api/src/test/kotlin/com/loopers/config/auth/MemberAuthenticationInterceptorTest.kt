package com.loopers.config.auth

import com.loopers.config.cache.CacheConfig
import com.loopers.config.cache.CachedAuth
import com.loopers.domain.common.vo.Email
import com.loopers.domain.member.MemberModel
import com.loopers.domain.member.MemberService
import com.loopers.domain.member.vo.LoginId
import com.loopers.domain.member.vo.MemberName
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.CacheManager
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import java.time.LocalDate

class MemberAuthenticationInterceptorTest {

    private lateinit var interceptor: MemberAuthenticationInterceptor
    private lateinit var memberService: MemberService
    private lateinit var cacheManager: CacheManager

    private val testMember = MemberModel(
        loginId = LoginId.of("testuser01"),
        encodedPassword = "TestPass123!",
        name = MemberName.of("홍길동"),
        email = Email.of("test@example.com"),
        birthday = LocalDate.of(1990, 1, 1),
    ).apply {
        // ID를 리플렉션으로 설정 (테스트용)
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, 1L)
    }

    @BeforeEach
    fun setUp() {
        memberService = io.mockk.mockk()
        cacheManager = buildTestCacheManager()
        interceptor = MemberAuthenticationInterceptor(memberService, cacheManager)
    }

    private fun buildTestCacheManager(): CacheManager {
        val config = CacheConfig()
        val manager = config.cacheManager() as org.springframework.cache.support.SimpleCacheManager
        manager.afterPropertiesSet()
        return manager
    }

    private fun createAuthenticatedHandlerMethod(): HandlerMethod {
        val method = TestController::class.java.getMethod("authenticatedEndpoint")
        return HandlerMethod(TestController(), method)
    }

    private fun createRequest(loginId: String?, password: String?): MockHttpServletRequest {
        return MockHttpServletRequest().apply {
            loginId?.let { addHeader(MemberAuthenticationInterceptor.HEADER_LOGIN_ID, it) }
            password?.let { addHeader(MemberAuthenticationInterceptor.HEADER_LOGIN_PW, it) }
        }
    }

    @DisplayName("인증 캐싱 동작 검증")
    @Nested
    inner class AuthCaching {

        @DisplayName("첫 번째 인증 요청 시, memberService.authenticate()를 호출하고 결과를 캐싱한다.")
        @Test
        fun callsAuthenticateAndCachesResult_onFirstRequest() {
            // arrange
            every { memberService.authenticate("testuser01", "TestPass123!") } returns testMember
            val request = createRequest("testuser01", "TestPass123!")
            val response = MockHttpServletResponse()
            val handler = createAuthenticatedHandlerMethod()

            // act
            interceptor.preHandle(request, response, handler)

            // assert
            verify(exactly = 1) { memberService.authenticate("testuser01", "TestPass123!") }
            val cached = cacheManager.getCache(CacheConfig.AUTH_CACHE)?.get("testuser01", CachedAuth::class.java)
            assertThat(cached).isNotNull
            assertThat(cached!!.matchesPassword("TestPass123!")).isTrue()
        }

        @DisplayName("동일한 자격증명으로 두 번째 요청 시, memberService.authenticate()를 호출하지 않고 캐시를 사용한다.")
        @Test
        fun usesCacheWithoutCallingAuthenticate_onSecondRequestWithSameCredentials() {
            // arrange
            every { memberService.authenticate("testuser01", "TestPass123!") } returns testMember
            val handler = createAuthenticatedHandlerMethod()

            // 첫 번째 요청 (캐시 적재)
            interceptor.preHandle(createRequest("testuser01", "TestPass123!"), MockHttpServletResponse(), handler)

            // act - 두 번째 요청
            val request2 = createRequest("testuser01", "TestPass123!")
            interceptor.preHandle(request2, MockHttpServletResponse(), handler)

            // assert
            verify(exactly = 1) { memberService.authenticate("testuser01", "TestPass123!") }
            val authenticatedMember = request2.getAttribute(MemberAuthenticationInterceptor.AUTHENTICATED_MEMBER_ATTRIBUTE) as AuthenticatedMember
            assertThat(authenticatedMember.id).isEqualTo(1L)
            assertThat(authenticatedMember.loginId).isEqualTo("testuser01")
        }

        @DisplayName("캐시된 loginId에 다른 비밀번호로 요청하면, memberService.authenticate()를 다시 호출한다.")
        @Test
        fun callsAuthenticateAgain_whenDifferentPasswordForCachedLoginId() {
            // arrange
            every { memberService.authenticate("testuser01", "TestPass123!") } returns testMember
            every { memberService.authenticate("testuser01", "WrongPass456!") } throws
                CoreException(com.loopers.support.error.ErrorType.UNAUTHORIZED, "인증에 실패했습니다.")
            val handler = createAuthenticatedHandlerMethod()

            // 첫 번째 요청 (캐시 적재)
            interceptor.preHandle(createRequest("testuser01", "TestPass123!"), MockHttpServletResponse(), handler)

            // act & assert - 다른 비밀번호로 요청
            assertThrows<CoreException> {
                interceptor.preHandle(createRequest("testuser01", "WrongPass456!"), MockHttpServletResponse(), handler)
            }
            verify(exactly = 1) { memberService.authenticate("testuser01", "WrongPass456!") }
        }

        @DisplayName("캐시 evict 후, memberService.authenticate()를 다시 호출한다.")
        @Test
        fun callsAuthenticateAgain_afterCacheEviction() {
            // arrange
            every { memberService.authenticate("testuser01", "TestPass123!") } returns testMember
            val handler = createAuthenticatedHandlerMethod()

            // 첫 번째 요청 (캐시 적재)
            interceptor.preHandle(createRequest("testuser01", "TestPass123!"), MockHttpServletResponse(), handler)

            // 캐시 evict (비밀번호 변경 시나리오)
            cacheManager.getCache(CacheConfig.AUTH_CACHE)?.evict("testuser01")

            // act - 재인증
            interceptor.preHandle(createRequest("testuser01", "TestPass123!"), MockHttpServletResponse(), handler)

            // assert
            verify(exactly = 2) { memberService.authenticate("testuser01", "TestPass123!") }
        }
    }

    // @MemberAuthenticated 어노테이션이 붙은 테스트용 컨트롤러
    class TestController {
        @MemberAuthenticated
        fun authenticatedEndpoint() {}
    }
}
