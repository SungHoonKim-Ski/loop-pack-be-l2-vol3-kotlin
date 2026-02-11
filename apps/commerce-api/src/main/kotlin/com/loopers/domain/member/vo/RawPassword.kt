package com.loopers.domain.member.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object RawPassword {
    private val CHAR_REGEX = "^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$".toRegex()

    fun validate(password: String, birthday: LocalDate) {
        if (password.length < 8 || password.length > 16) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.")
        }
        if (!CHAR_REGEX.matches(password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.")
        }
        validateNotContainsBirthday(password, birthday)
    }

    private fun validateNotContainsBirthday(password: String, birthday: LocalDate) {
        val patterns = listOf(
            birthday.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            birthday.format(DateTimeFormatter.ofPattern("yyMMdd")),
            birthday.format(DateTimeFormatter.ofPattern("MMdd")),
        )
        patterns.forEach { pattern ->
            if (password.contains(pattern)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }
    }
}
