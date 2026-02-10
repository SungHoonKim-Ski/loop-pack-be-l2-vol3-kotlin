package com.loopers.domain.member

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity
@Table(name = "member")
class MemberModel(
    loginId: String,
    encodedPassword: String,
    name: String,
    birthday: LocalDate,
    email: String,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, unique = true)
    var loginId: String = loginId
        protected set

    @Column(name = "password", nullable = false)
    var password: String = encodedPassword
        protected set

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Column(name = "birthday", nullable = false)
    var birthday: LocalDate = birthday
        protected set

    @Column(name = "email", nullable = false, unique = true)
    var email: String = email
        protected set

    init {
        validateLoginId(this.loginId)
        validateName(this.name)
        validateEmail(this.email)
    }

    fun changePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    fun getMaskedName(): String {
        if (name.length <= 1) return "*"
        return name.substring(0, name.length - 1) + "*"
    }

    companion object {
        private val LOGIN_ID_REGEX = "^[a-zA-Z0-9]+$".toRegex()
        private val EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".toRegex()
        private val PASSWORD_CHAR_REGEX = "^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$".toRegex()

        private fun validateLoginId(loginId: String) {
            if (loginId.isBlank() || !LOGIN_ID_REGEX.matches(loginId)) {
                throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.")
            }
        }

        private fun validateName(name: String) {
            if (name.isBlank()) {
                throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
            }
        }

        private fun validateEmail(email: String) {
            if (!EMAIL_REGEX.matches(email)) {
                throw CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.")
            }
        }

        fun validatePassword(password: String, birthday: LocalDate) {
            if (password.length < 8 || password.length > 16) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.")
            }
            if (!PASSWORD_CHAR_REGEX.matches(password)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다.")
            }
            validatePasswordNotContainsBirthday(password, birthday)
        }

        private fun validatePasswordNotContainsBirthday(password: String, birthday: LocalDate) {
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
}
