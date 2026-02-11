package com.loopers.domain.member.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class MemberName(val value: String) {
    fun masked(): String {
        if (value.length <= 1) return "*"
        return value.substring(0, value.length - 1) + "*"
    }

    companion object {
        fun of(value: String): MemberName {
            if (value.isBlank()) {
                throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
            }
            return MemberName(value)
        }
    }
}
