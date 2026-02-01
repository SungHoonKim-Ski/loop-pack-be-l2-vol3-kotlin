package com.loopers.domain.member

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "member")
class MemberModel(
    loginId: String,
    password: String,
    name: String,
    email: String,
    birthDate: LocalDate,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, unique = true)
    var loginId: String = loginId
        protected set

    @Column(name = "password", nullable = false)
    var password: String = password
        protected set

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Column(name = "email", nullable = false)
    var email: String = email
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    init {
        validateLoginId(loginId)
        validateEmail(email)
        validatePassword(password, birthDate)
        validateName(name)
    }

    private fun validateLoginId(loginId: String) {
        if (loginId.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.")
        }
        if (!loginId.matches(Regex("^[a-zA-Z0-9]+$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용할 수 있습니다.")
        }
    }

    private fun validateEmail(email: String) {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!email.matches(emailRegex)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
        }
    }

    private fun validatePassword(password: String, birthDate: LocalDate) {
        if (password.length < 8) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 최소 8자 이상이어야 합니다.")
        }
        if (password.length > 16) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 최대 16자까지 가능합니다.")
        }
        if (!password.any { it.isUpperCase() }) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 대문자를 포함해야 합니다.")
        }
        if (!password.any { it.isLowerCase() }) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 소문자를 포함해야 합니다.")
        }
        if (!password.any { it.isDigit() }) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 숫자를 포함해야 합니다.")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 특수문자를 포함해야 합니다.")
        }

        // 생년월일 포함 여부 검증 (YYYYMMDD, YYMMDD, MMDD 형식)
        val birthDateString = birthDate.toString().replace("-", "")
        val yyyymmdd = birthDateString
        val yymmdd = birthDateString.substring(2)
        val mmdd = birthDateString.substring(4)

        if (password.contains(yyyymmdd) || password.contains(yymmdd) || password.contains(mmdd)) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        }
    }

    fun getMaskedName(): String {
        return when (name.length) {
            2 -> "${name[0]}*"
            3 -> "${name[0]}*${name[2]}"
            else -> "${name[0]}${"*".repeat(name.length - 2)}${name[name.length - 1]}"
        }
    }

    fun changePassword(newPassword: String) {
        validatePassword(newPassword, birthDate)
        this.password = newPassword
    }

    fun encryptPassword(encodedPassword: String) {
        this.password = encodedPassword
    }
}
