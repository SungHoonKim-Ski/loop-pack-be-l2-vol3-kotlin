package com.loopers.interfaces.api

import com.loopers.E2ETestBase
import com.loopers.domain.member.MemberModel
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.interfaces.api.member.MemberV1Dto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

class MemberV1ApiE2ETest @Autowired constructor(
    private val memberJpaRepository: MemberJpaRepository,
    private val passwordEncoder: PasswordEncoder,
) : E2ETestBase() {
    companion object {
        private const val ENDPOINT_REGISTER = "/api/v1/members"
        private const val ENDPOINT_GET_ME = "/api/v1/members/me"
        private const val ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password"
    }

    @DisplayName("POST /api/v1/members (회원가입)")
    @Nested
    inner class Register {
        @DisplayName("유효한 회원정보로 가입하면, 201 CREATED와 회원 정보를 반환한다.")
        @Test
        fun returnsCreatedWithMemberInfo_whenValidRequest() {
            // arrange
            val request = MemberV1Dto.RegisterRequest(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "홍길동",
                email = "test@example.com",
                birthDate = "1990-01-01",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.loginId).isEqualTo(request.loginId) },
                { assertThat(response.body?.data?.maskedName).isEqualTo("홍*동") },
                { assertThat(response.body?.data?.email).isEqualTo(request.email) },
                { assertThat(response.body?.data?.birthDate).isEqualTo(LocalDate.parse(request.birthDate)) },
            )
        }

        @DisplayName("중복된 loginId로 가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun throwsConflict_whenDuplicateLoginId() {
            // arrange
            val existingMember = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "홍길동",
                email = "existing@example.com",
                birthDate = LocalDate.parse("1990-01-01"),
            ).apply {
                encryptPassword(passwordEncoder.encode("Test1234!@"))
            }
            memberJpaRepository.save(existingMember)

            val request = MemberV1Dto.RegisterRequest(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "김철수",
                email = "test@example.com",
                birthDate = "1990-01-01",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("잘못된 요청(빈 loginId)이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenInvalidRequest() {
            // arrange
            val request = MemberV1Dto.RegisterRequest(
                loginId = "",
                password = "Test1234!@",
                name = "홍길동",
                email = "test@example.com",
                birthDate = "1990-01-01",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/members/me (내 정보 조회)")
    @Nested
    inner class GetMe {
        @DisplayName("유효한 인증 헤더로 요청하면, 200 OK와 마스킹된 이름 포함 회원 정보를 반환한다.")
        @Test
        fun returnsMemberInfo_whenValidAuth() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.parse("1990-01-01"),
            ).apply {
                encryptPassword(passwordEncoder.encode("Test1234!@"))
            }
            memberJpaRepository.save(member)

            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", "testuser123")
                set("X-Loopers-LoginPw", "Test1234!@")
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_ME,
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo(member.loginId) },
                { assertThat(response.body?.data?.maskedName).isEqualTo("홍*동") },
                { assertThat(response.body?.data?.email).isEqualTo(member.email) },
            )
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenInvalidPassword() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.parse("1990-01-01"),
            ).apply {
                encryptPassword(passwordEncoder.encode("Test1234!@"))
            }
            memberJpaRepository.save(member)

            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", "testuser123")
                set("X-Loopers-LoginPw", "WrongPassword123!")
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_ME,
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenNoAuthHeader() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_ME,
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password (비밀번호 변경)")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 인증 헤더와 비밀번호로 요청하면, 200 OK와 회원 정보를 반환한다.")
        @Test
        fun returnsMemberInfo_whenValidRequest() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.parse("1990-01-01"),
            ).apply {
                encryptPassword(passwordEncoder.encode("Test1234!@"))
            }
            memberJpaRepository.save(member)

            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", "testuser123")
                set("X-Loopers-LoginPw", "Test1234!@")
            }

            val request = MemberV1Dto.ChangePasswordRequest(
                currentPassword = "Test1234!@",
                newPassword = "NewTest1234!@",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo(member.loginId) },
            )
        }

        @DisplayName("잘못된 현재 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun throwsUnauthorized_whenInvalidCurrentPassword() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.parse("1990-01-01"),
            ).apply {
                encryptPassword(passwordEncoder.encode("Test1234!@"))
            }
            memberJpaRepository.save(member)

            val headers = HttpHeaders().apply {
                set("X-Loopers-LoginId", "testuser123")
                set("X-Loopers-LoginPw", "Test1234!@")
            }

            val request = MemberV1Dto.ChangePasswordRequest(
                currentPassword = "WrongPassword123!",
                newPassword = "NewTest1234!@",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
