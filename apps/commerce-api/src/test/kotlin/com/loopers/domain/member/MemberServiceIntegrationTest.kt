package com.loopers.domain.member

import com.loopers.IntegrationTestBase
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

class MemberServiceIntegrationTest @Autowired constructor(
    private val memberService: MemberService,
    private val memberJpaRepository: MemberJpaRepository,
    private val passwordEncoder: PasswordEncoder,
) : IntegrationTestBase() {

    @DisplayName("회원을 등록할 때,")
    @Nested
    inner class Register {
        @DisplayName("유효한 회원정보로 등록하면, 암호화된 비밀번호로 저장된다.")
        @Test
        fun savesWithEncryptedPassword_whenValidCommandIsProvided() {
            // arrange
            val command = MemberRegisterCommand(
                loginId = "testuser01",
                password = "TestPass123!",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )

            // act
            val result = memberService.register(command)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result.loginId).isEqualTo(command.loginId) },
                { assertThat(result.name).isEqualTo(command.name) },
                { assertThat(result.email).isEqualTo(command.email) },
                { assertThat(result.birthDate).isEqualTo(command.birthDate) },
                { assertThat(result.password).isNotEqualTo(command.password) },
                { assertThat(passwordEncoder.matches(command.password, result.password)).isTrue() },
            )
        }

        @DisplayName("중복된 loginId로 등록하면, DataIntegrityViolationException이 발생한다.")
        @Test
        fun throwsDataIntegrityViolationException_whenDuplicateLoginIdIsProvided() {
            // arrange
            val existingMember = MemberModel(
                loginId = "testuser01",
                password = "TestPass123!",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            existingMember.encryptPassword(passwordEncoder.encode(existingMember.password))
            memberJpaRepository.save(existingMember)

            val command = MemberRegisterCommand(
                loginId = "testuser01",
                password = "TestPass456!",
                name = "김철수",
                email = "another@example.com",
                birthDate = LocalDate.of(1995, 5, 5),
            )

            // act & assert
            assertThrows<DataIntegrityViolationException> {
                memberService.register(command)
            }
        }

        @DisplayName("중복된 email로 등록하면, DataIntegrityViolationException이 발생한다.")
        @Test
        fun throwsDataIntegrityViolationException_whenDuplicateEmailIsProvided() {
            // arrange
            val existingMember = MemberModel(
                loginId = "testuser01",
                password = "TestPass123!",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            existingMember.encryptPassword(passwordEncoder.encode(existingMember.password))
            memberJpaRepository.save(existingMember)

            val command = MemberRegisterCommand(
                loginId = "testuser02",
                password = "TestPass456!",
                name = "김철수",
                email = "test@example.com",
                birthDate = LocalDate.of(1995, 5, 5),
            )

            // act & assert
            assertThrows<DataIntegrityViolationException> {
                memberService.register(command)
            }
        }
    }

    @DisplayName("회원을 조회할 때,")
    @Nested
    inner class GetMember {
        @DisplayName("존재하는 ID로 조회하면, 회원 정보를 반환한다.")
        @Test
        fun returnsMemberInfo_whenValidIdIsProvided() {
            // arrange
            val member = MemberModel(
                loginId = "testuser01",
                password = "TestPass123!",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            member.encryptPassword(passwordEncoder.encode(member.password))
            val savedMember = memberJpaRepository.save(member)

            // act
            val result = memberService.getMember(savedMember.id)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result.id).isEqualTo(savedMember.id) },
                { assertThat(result.loginId).isEqualTo(savedMember.loginId) },
                { assertThat(result.name).isEqualTo(savedMember.name) },
                { assertThat(result.email).isEqualTo(savedMember.email) },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundException_whenInvalidIdIsProvided() {
            // arrange
            val invalidId = 999L

            // act
            val exception = assertThrows<CoreException> {
                memberService.getMember(invalidId)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("회원을 인증할 때,")
    @Nested
    inner class Authenticate {
        @DisplayName("올바른 loginId와 password로 인증하면, 회원 정보를 반환한다.")
        @Test
        fun returnsMemberInfo_whenCorrectCredentialsAreProvided() {
            // arrange
            val rawPassword = "TestPass123!"
            val member = MemberModel(
                loginId = "testuser01",
                password = rawPassword,
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            member.encryptPassword(passwordEncoder.encode(rawPassword))
            val savedMember = memberJpaRepository.save(member)

            // act
            val result = memberService.authenticate(savedMember.loginId, rawPassword)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result.id).isEqualTo(savedMember.id) },
                { assertThat(result.loginId).isEqualTo(savedMember.loginId) },
            )
        }

        @DisplayName("존재하지 않는 loginId로 인증하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenInvalidLoginIdIsProvided() {
            // arrange
            val invalidLoginId = "nonexistent"
            val password = "TestPass123!"

            // act
            val exception = assertThrows<CoreException> {
                memberService.authenticate(invalidLoginId, password)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("잘못된 password로 인증하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenIncorrectPasswordIsProvided() {
            // arrange
            val rawPassword = "TestPass123!"
            val member = MemberModel(
                loginId = "testuser01",
                password = rawPassword,
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            member.encryptPassword(passwordEncoder.encode(rawPassword))
            val savedMember = memberJpaRepository.save(member)

            val wrongPassword = "WrongPass456!"

            // act
            val exception = assertThrows<CoreException> {
                memberService.authenticate(savedMember.loginId, wrongPassword)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    inner class ChangePassword {
        @DisplayName("올바른 현재 비밀번호와 새 비밀번호로 변경하면, 성공한다.")
        @Test
        fun changesPasswordSuccessfully_whenValidCurrentPasswordAndNewPasswordAreProvided() {
            // arrange
            val currentPassword = "TestPass123!"
            val member = MemberModel(
                loginId = "testuser01",
                password = currentPassword,
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            member.encryptPassword(passwordEncoder.encode(currentPassword))
            val savedMember = memberJpaRepository.save(member)

            val newPassword = "NewPass456!"

            // act
            memberService.changePassword(savedMember.id, currentPassword, newPassword)

            // assert
            val updatedMember = memberJpaRepository.findById(savedMember.id).get()
            assertAll(
                { assertThat(passwordEncoder.matches(currentPassword, updatedMember.password)).isFalse() },
                { assertThat(passwordEncoder.matches(newPassword, updatedMember.password)).isTrue() },
            )
        }

        @DisplayName("잘못된 현재 비밀번호로 변경하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorizedException_whenIncorrectCurrentPasswordIsProvided() {
            // arrange
            val currentPassword = "TestPass123!"
            val member = MemberModel(
                loginId = "testuser01",
                password = currentPassword,
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            member.encryptPassword(passwordEncoder.encode(currentPassword))
            val savedMember = memberJpaRepository.save(member)

            val wrongCurrentPassword = "WrongPass456!"
            val newPassword = "NewPass789!"

            // act
            val exception = assertThrows<CoreException> {
                memberService.changePassword(savedMember.id, wrongCurrentPassword, newPassword)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }
}
