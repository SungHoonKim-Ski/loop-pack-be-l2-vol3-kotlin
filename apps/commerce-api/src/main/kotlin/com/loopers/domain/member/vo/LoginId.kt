package com.loopers.domain.member.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class LoginId(val value: String) {
    companion object {
        private val REGEX = "^[a-zA-Z0-9]+$".toRegex()

        fun of(value: String): LoginId {
            if (value.isBlank() || !REGEX.matches(value)) {
                throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.")
            }
            return LoginId(value)
        }
    }
}
