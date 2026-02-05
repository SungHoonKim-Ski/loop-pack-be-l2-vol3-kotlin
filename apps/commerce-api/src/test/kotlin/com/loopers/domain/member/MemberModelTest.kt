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
    private val validLoginId = "user01"
    private val validPassword = "Password1!"
    private val validName = "홍길동"
    private val validBirthday = LocalDate.of(2000, 1, 1)
    private val validEmail = "user@example.com"

    @DisplayName("회원 모델을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        fun createsMemberModel_whenAllFieldsAreValid() {
            // arrange & act
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // assert
            assertAll(
                { assertThat(member.loginId).isEqualTo(validLoginId) },
                { assertThat(member.name).isEqualTo(validName) },
                { assertThat(member.birthday).isEqualTo(validBirthday) },
                { assertThat(member.email).isEqualTo(validEmail) },
            )
        }

        @DisplayName("loginId에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "user@01",
                    password = validPassword,
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("loginId에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsKorean() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "유저01",
                    password = validPassword,
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("loginId가 빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsBlank() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = "",
                    password = validPassword,
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("name이 빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = validPassword,
                    name = "   ",
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("email 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailFormatIsInvalid() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = validPassword,
                    name = validName,
                    birthday = validBirthday,
                    email = "invalid-email",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("email에 @가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailHasNoAtSign() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = validPassword,
                    name = validName,
                    birthday = validBirthday,
                    email = "userexample.com",
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    inner class PasswordValidation {
        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = "Pass1!",
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooLong() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = "Password1!Extra12",
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = "Pass word1!",
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthdayYyyyMMdd() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = "20000101Ab!",
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthdayYyMMdd() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = "Ab000101cd!",
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(MMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthdayMMdd() {
            // act
            val result = assertThrows<CoreException> {
                MemberModel(
                    loginId = validLoginId,
                    password = "Abcd0101ef!",
                    name = validName,
                    birthday = validBirthday,
                    email = validEmail,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    inner class MaskName {
        @DisplayName("3글자 이름이면, 마지막 글자를 *로 대체한다.")
        @Test
        fun masksLastCharacter_whenNameHasThreeCharacters() {
            // arrange
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = "홍길동",
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val maskedName = member.getMaskedName()

            // assert
            assertThat(maskedName).isEqualTo("홍길*")
        }

        @DisplayName("2글자 이름이면, 마지막 글자를 *로 대체한다.")
        @Test
        fun masksLastCharacter_whenNameHasTwoCharacters() {
            // arrange
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = "AB",
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val maskedName = member.getMaskedName()

            // assert
            assertThat(maskedName).isEqualTo("A*")
        }

        @DisplayName("1글자 이름이면, *로 대체한다.")
        @Test
        fun masksEntireName_whenNameHasOneCharacter() {
            // arrange
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = "김",
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val maskedName = member.getMaskedName()

            // assert
            assertThat(maskedName).isEqualTo("*")
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    inner class ChangePassword {
        @DisplayName("유효한 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        fun changesPassword_whenNewPasswordIsValid() {
            // arrange
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            member.changePassword("NewPass1!", validBirthday)

            // assert
            assertThat(member.password).isNotEqualTo(validPassword)
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = assertThrows<CoreException> {
                member.changePassword(validPassword, validBirthday)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("새 비밀번호가 규칙에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNewPasswordIsInvalid() {
            // arrange
            val member = MemberModel(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthday = validBirthday,
                email = validEmail,
            )

            // act
            val result = assertThrows<CoreException> {
                member.changePassword("short", validBirthday)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
