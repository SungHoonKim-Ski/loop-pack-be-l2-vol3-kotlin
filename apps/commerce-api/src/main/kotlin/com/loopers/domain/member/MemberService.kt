package com.loopers.domain.member

import com.loopers.domain.common.vo.Email
import com.loopers.domain.member.vo.LoginId
import com.loopers.domain.member.vo.MemberName
import com.loopers.domain.member.vo.RawPassword
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun register(
        loginId: String,
        password: String,
        name: String,
        birthday: LocalDate,
        email: String,
    ) {
        RawPassword.validate(password, birthday)

        val member = MemberModel(
            loginId = LoginId.of(loginId),
            encodedPassword = passwordEncoder.encode(password),
            name = MemberName.of(name),
            birthday = birthday,
            email = Email.of(email),
        )
        try {
            memberRepository.save(member)
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.")
        }
    }

    @Transactional(readOnly = true)
    fun getMemberByLoginId(loginId: String): MemberModel {
        return memberRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다.")
    }

    @Transactional(readOnly = true)
    fun authenticate(loginId: String, password: String): MemberModel {
        val member = memberRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.")

        if (!passwordEncoder.matches(password, member.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.")
        }

        return member
    }

    @Transactional
    fun changePassword(loginId: String, currentPassword: String, newPassword: String) {
        val member = memberRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.")

        if (!passwordEncoder.matches(currentPassword, member.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.")
        }

        if (passwordEncoder.matches(newPassword, member.password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.")
        }

        RawPassword.validate(newPassword, member.birthday)
        member.changePassword(passwordEncoder.encode(newPassword))
    }
}
