package com.loopers.domain.member

import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

@SpringBootTest
class MemberServiceIntegrationTest @Autowired constructor(
    private val memberService: MemberService,
    private val memberJpaRepository: MemberJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val validLoginId = "user01"
    private val validPassword = "Password1!"
    private val validName = "홍길동"
    private val validBirthday = LocalDate.of(2000, 1, 1)
    private val validEmail = "user@example.com"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    inner class Register {
        @DisplayName("유효한 정보가 주어지면, 정상적으로 가입된다.")
        @Test
        fun registersMember_whenValidInfoIsProvided() {
            // act
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // assert
            val savedMember = memberJpaRepository.findByLoginId(validLoginId)
            assertAll(
                { assertThat(savedMember).isNotNull() },
                { assertThat(savedMember!!.loginId).isEqualTo(validLoginId) },
                { assertThat(savedMember!!.name).isEqualTo(validName) },
                { assertThat(savedMember!!.birthday).isEqualTo(validBirthday) },
                { assertThat(savedMember!!.email).isEqualTo(validEmail) },
            )
        }

        @DisplayName("이미 존재하는 loginId로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = assertThrows<CoreException> {
                memberService.register(
                    loginId = validLoginId,
                    password = validPassword,
                    name = "다른이름",
                    birthday = validBirthday,
                    email = "other@example.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("회원을 조회할 때,")
    @Nested
    inner class GetMember {
        @DisplayName("존재하는 loginId로 조회하면, 회원 정보를 반환한다.")
        @Test
        fun returnsMember_whenValidLoginIdIsProvided() {
            // arrange
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = memberService.getMemberByLoginId(validLoginId)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result.loginId).isEqualTo(validLoginId) },
                { assertThat(result.name).isEqualTo(validName) },
            )
        }

        @DisplayName("존재하지 않는 loginId로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            // act
            val result = assertThrows<CoreException> {
                memberService.getMemberByLoginId("nonexistent")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("인증을 할 때,")
    @Nested
    inner class Authenticate {
        @DisplayName("올바른 loginId와 password가 주어지면, 회원 정보를 반환한다.")
        @Test
        fun returnsMember_whenCredentialsAreValid() {
            // arrange
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = memberService.authenticate(validLoginId, validPassword)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result.loginId).isEqualTo(validLoginId) },
            )
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = assertThrows<CoreException> {
                memberService.authenticate(validLoginId, "WrongPass1!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 loginId이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdDoesNotExist() {
            // act
            val result = assertThrows<CoreException> {
                memberService.authenticate("nonexistent", validPassword)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        fun changesPassword_whenNewPasswordIsValid() {
            // arrange
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )
            val newPassword = "NewPass1!"

            // act
            memberService.changePassword(validLoginId, newPassword)

            // assert
            val member = memberJpaRepository.findByLoginId(validLoginId)!!
            assertThat(passwordEncoder.matches(newPassword, member.password)).isTrue()
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            memberService.register(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = assertThrows<CoreException> {
                memberService.changePassword(validLoginId, validPassword)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 회원의 비밀번호를 변경하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMemberDoesNotExist() {
            // act
            val result = assertThrows<CoreException> {
                memberService.changePassword("nonexistent", "NewPass1!")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
