package com.loopers.domain.member

import com.loopers.domain.common.vo.Email
import com.loopers.domain.member.vo.LoginId
import com.loopers.domain.member.vo.MemberName
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MemberModelTest {
    private val validLoginId = LoginId.of("user01")
    private val validEncodedPassword = "encodedPassword"
    private val validName = MemberName.of("홍길동")
    private val validBirthday = LocalDate.of(2000, 1, 1)
    private val validEmail = Email.of("user@example.com")

    private fun createMember(
        loginId: LoginId = validLoginId,
        encodedPassword: String = validEncodedPassword,
        name: MemberName = validName,
        birthday: LocalDate = validBirthday,
        email: Email = validEmail,
    ) = MemberModel(
        loginId = loginId,
        encodedPassword = encodedPassword,
        name = name,
        birthday = birthday,
        email = email,
    )

    @DisplayName("회원 모델을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        fun createsMemberModel_whenAllFieldsAreValid() {
            // act
            val member = createMember()

            // assert
            assertAll(
                { assertThat(member.loginId).isEqualTo("user01") },
                { assertThat(member.password).isEqualTo(validEncodedPassword) },
                { assertThat(member.name).isEqualTo("홍길동") },
                { assertThat(member.birthday).isEqualTo(validBirthday) },
                { assertThat(member.email).isEqualTo("user@example.com") },
            )
        }

        @DisplayName("loginId에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            val result = assertThrows<CoreException> { LoginId.of("user@01") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("loginId에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdContainsKorean() {
            val result = assertThrows<CoreException> { LoginId.of("유저01") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("loginId가 빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLoginIdIsBlank() {
            val result = assertThrows<CoreException> { LoginId.of("") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("name이 빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            val result = assertThrows<CoreException> { MemberName.of("   ") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("email 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailFormatIsInvalid() {
            val result = assertThrows<CoreException> { Email.of("invalid-email") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("email에 @가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenEmailHasNoAtSign() {
            val result = assertThrows<CoreException> { Email.of("userexample.com") }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    inner class PasswordValidation {
        @DisplayName("유효한 비밀번호면, 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenPasswordIsValid() {
            assertDoesNotThrow {
                RawPassword.validate("Password1!", validBirthday)
            }
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooShort() {
            val result = assertThrows<CoreException> {
                RawPassword.validate("Pass1!", validBirthday)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordIsTooLong() {
            val result = assertThrows<CoreException> {
                RawPassword.validate("Password1!Extra12", validBirthday)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            val result = assertThrows<CoreException> {
                RawPassword.validate("Pass word1!", validBirthday)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthdayYyyyMMdd() {
            val result = assertThrows<CoreException> {
                RawPassword.validate("20000101Ab!", validBirthday)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthdayYyMMdd() {
            val result = assertThrows<CoreException> {
                RawPassword.validate("Ab000101cd!", validBirthday)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(MMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthdayMMdd() {
            val result = assertThrows<CoreException> {
                RawPassword.validate("Abcd0101ef!", validBirthday)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    inner class MaskName {
        @DisplayName("3글자 이름이면, 마지막 글자를 *로 대체한다.")
        @Test
        fun masksLastCharacter_whenNameHasThreeCharacters() {
            val member = createMember(name = MemberName.of("홍길동"))
            assertThat(member.getMaskedName()).isEqualTo("홍길*")
        }

        @DisplayName("2글자 이름이면, 마지막 글자를 *로 대체한다.")
        @Test
        fun masksLastCharacter_whenNameHasTwoCharacters() {
            val member = createMember(name = MemberName.of("AB"))
            assertThat(member.getMaskedName()).isEqualTo("A*")
        }

        @DisplayName("1글자 이름이면, *로 대체한다.")
        @Test
        fun masksEntireName_whenNameHasOneCharacter() {
            val member = createMember(name = MemberName.of("김"))
            assertThat(member.getMaskedName()).isEqualTo("*")
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    inner class ChangePassword {
        @DisplayName("새 암호화된 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        fun changesPassword_whenNewEncodedPasswordIsProvided() {
            // arrange
            val member = createMember()
            val newEncodedPassword = "newEncodedPassword"

            // act
            member.changePassword(newEncodedPassword)

            // assert
            assertThat(member.password).isEqualTo(newEncodedPassword)
        }
    }
}
