package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MemberModelTest {
    @DisplayName("회원 모델을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        fun createsMemberModel_whenAllFieldsAreValid() {
            // arrange
            val loginId = "testuser123"
            val password = "Test1234!@#"
            val name = "홍길동"
            val email = "test@example.com"
            val birthDate = LocalDate.of(1990, 1, 1)

            // act
            val member = MemberModel(
                loginId = loginId,
                password = password,
                name = name,
                email = email,
                birthDate = birthDate,
            )

            // assert
            assertAll(
                { assertThat(member.id).isEqualTo(0L) },
                { assertThat(member.loginId).isEqualTo(loginId) },
                { assertThat(member.password).isEqualTo(password) },
                { assertThat(member.name).isEqualTo(name) },
                { assertThat(member.email).isEqualTo(email) },
                { assertThat(member.birthDate).isEqualTo(birthDate) },
            )
        }

        @DisplayName("loginId가 영문/숫자가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenLoginIdContainsSpecialCharacters() {
            // arrange
            val loginId = "test@user"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = loginId,
                    password = "Test1234!@#",
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("loginId가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenLoginIdIsBlank() {
            // arrange
            val loginId = "   "

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = loginId,
                    password = "Test1234!@#",
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("email 형식이 잘못되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenEmailFormatIsInvalid() {
            // arrange
            val email = "invalid-email"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = "Test1234!@#",
                    name = "홍길동",
                    email = email,
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordIsTooShort() {
            // arrange
            val password = "Test1!@"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자 초과면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordIsTooLong() {
            // arrange
            val password = "Test1234!@#Test1234"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 대문자가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordHasNoUppercase() {
            // arrange
            val password = "test1234!@#"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 소문자가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordHasNoLowercase() {
            // arrange
            val password = "TEST1234!@#"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 숫자가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordHasNoDigit() {
            // arrange
            val password = "TestTest!@#"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 특수문자가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordHasNoSpecialChar() {
            // arrange
            val password = "Test12345678"

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            val password = "Test19900101!@#"
            val birthDate = LocalDate.of(1990, 1, 1)

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = password,
                    name = "홍길동",
                    email = "test@example.com",
                    birthDate = birthDate,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNameIsBlank() {
            // arrange
            val name = "   "

            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "testuser123",
                    password = "Test1234!@#",
                    name = name,
                    email = "test@example.com",
                    birthDate = LocalDate.of(1990, 1, 1),
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름 마스킹 기능을 사용할 때, ")
    @Nested
    inner class GetMaskedName {
        @DisplayName("2글자 이름은 '홍길' → '홍*' 형식으로 마스킹된다.")
        @Test
        fun masksNameWithTwoCharacters() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@#",
                name = "홍길",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )

            // act
            val maskedName = member.getMaskedName()

            // assert
            assertThat(maskedName).isEqualTo("홍*")
        }

        @DisplayName("3글자 이름은 '홍길동' → '홍*동' 형식으로 마스킹된다.")
        @Test
        fun masksNameWithThreeCharacters() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@#",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )

            // act
            val maskedName = member.getMaskedName()

            // assert
            assertThat(maskedName).isEqualTo("홍*동")
        }

        @DisplayName("4글자 이상 이름은 '남궁선생' → '남**생' 형식으로 마스킹된다.")
        @Test
        fun masksNameWithFourOrMoreCharacters() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@#",
                name = "남궁선생",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )

            // act
            val maskedName = member.getMaskedName()

            // assert
            assertThat(maskedName).isEqualTo("남**생")
        }
    }

    @DisplayName("비밀번호 변경 기능을 사용할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 새 비밀번호로 변경이 성공한다.")
        @Test
        fun changesPasswordSuccessfully_whenNewPasswordIsValid() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@#",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            val newPassword = "NewPass123!@#"

            // act
            member.changePassword(newPassword)

            // assert
            assertThat(member.password).isEqualTo(newPassword)
        }

        @DisplayName("유효하지 않은 새 비밀번호면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenNewPasswordIsInvalid() {
            // arrange
            val member = MemberModel(
                loginId = "testuser123",
                password = "Test1234!@#",
                name = "홍길동",
                email = "test@example.com",
                birthDate = LocalDate.of(1990, 1, 1),
            )
            val newPassword = "short"

            // act
            val result = assertThrows<CoreException> {
                member.changePassword(newPassword)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
