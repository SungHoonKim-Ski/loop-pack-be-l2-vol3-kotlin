package com.loopers.domain.common.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class Email(val value: String) {
    companion object {
        private val REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()

        fun of(value: String): Email {
            if (!REGEX.matches(value)) {
                throw CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.")
            }
            return Email(value)
        }
    }
}
