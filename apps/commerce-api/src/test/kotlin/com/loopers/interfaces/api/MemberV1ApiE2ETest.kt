package com.loopers.interfaces.api

import com.loopers.domain.member.MemberModel
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.interfaces.api.member.MemberV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_REGISTER = "/api/v1/members"
        private const val ENDPOINT_ME = "/api/v1/members/me"
        private const val ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password"
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }

    private val validPassword = "Password1!"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun registerRequest(
        loginId: String = "user01",
        password: String = validPassword,
        name: String = "홍길동",
        birthday: String = "2000-01-01",
        email: String = "user@example.com",
    ): MemberV1Dto.RegisterRequest {
        return MemberV1Dto.RegisterRequest(
            loginId = loginId,
            password = password,
            name = name,
            birthday = LocalDate.parse(birthday),
            email = email,
        )
    }

    private fun authHeaders(loginId: String = "user01", password: String = validPassword): HttpHeaders {
        return HttpHeaders().apply {
            set(HEADER_LOGIN_ID, loginId)
            set(HEADER_LOGIN_PW, password)
        }
    }

    @DisplayName("POST /api/v1/members (회원가입)")
    @Nested
    inner class Register {
        @DisplayName("유효한 정보로 가입하면, 200 OK 응답을 받는다.")
        @Test
        fun returns200_whenValidInfoIsProvided() {
            // arrange
            val request = registerRequest()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("중복된 loginId로 가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returns409_whenLoginIdAlreadyExists() {
            // arrange
            val request = registerRequest()
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                object : ParameterizedTypeReference<ApiResponse<Void>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("비밀번호 규칙에 맞지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returns400_whenPasswordIsInvalid() {
            // arrange
            val request = registerRequest(password = "short")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }

    @DisplayName("GET /api/v1/members/me (내 정보 조회)")
    @Nested
    inner class GetMe {
        @DisplayName("인증에 성공하면, 마스킹된 이름과 함께 회원 정보를 반환한다.")
        @Test
        fun returns200WithMaskedName_whenAuthenticated() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(registerRequest()),
                object : ParameterizedTypeReference<ApiResponse<Void>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo("user01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.email).isEqualTo("user@example.com") },
            )
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returns401_whenNoAuthHeaders() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                HttpEntity<Any>(HttpHeaders()),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returns401_whenPasswordIsWrong() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(registerRequest()),
                object : ParameterizedTypeReference<ApiResponse<Void>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders(password = "WrongPass1!")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password (비밀번호 변경)")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 새 비밀번호로 변경하면, 200 OK 응답을 받는다.")
        @Test
        fun returns200_whenNewPasswordIsValid() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(registerRequest()),
                object : ParameterizedTypeReference<ApiResponse<Void>>() {},
            )
            val changeRequest = MemberV1Dto.ChangePasswordRequest(newPassword = "NewPass1!")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(changeRequest, authHeaders()),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @DisplayName("비밀번호 규칙에 맞지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returns400_whenNewPasswordViolatesRules() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(registerRequest()),
                object : ParameterizedTypeReference<ApiResponse<Void>>() {},
            )
            val changeRequest = MemberV1Dto.ChangePasswordRequest(newPassword = "short")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(changeRequest, authHeaders()),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returns400_whenNewPasswordIsSameAsCurrent() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(registerRequest()),
                object : ParameterizedTypeReference<ApiResponse<Void>>() {},
            )
            val changeRequest = MemberV1Dto.ChangePasswordRequest(newPassword = validPassword)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(changeRequest, authHeaders()),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("인증에 실패하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returns401_whenAuthenticationFails() {
            // arrange
            val changeRequest = MemberV1Dto.ChangePasswordRequest(newPassword = "NewPass1!")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Void>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(changeRequest, authHeaders(password = "WrongPass1!")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
