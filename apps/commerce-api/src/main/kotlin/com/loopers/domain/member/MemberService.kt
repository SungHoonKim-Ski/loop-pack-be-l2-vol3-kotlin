package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun register(command: MemberRegisterCommand): MemberModel {
        val member = MemberModel(
            loginId = command.loginId,
            password = command.password,
            name = command.name,
            email = command.email,
            birthDate = command.birthDate,
        )

        val encodedPassword = passwordEncoder.encode(command.password)
        member.encryptPassword(encodedPassword)

        return memberRepository.save(member)
    }

    @Transactional(readOnly = true)
    fun getMember(id: Long): MemberModel {
        return memberRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 회원을 찾을 수 없습니다.")
    }

    @Transactional(readOnly = true)
    fun getMemberByLoginId(loginId: String): MemberModel? {
        return memberRepository.findByLoginId(loginId)
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
    fun changePassword(memberId: Long, currentPassword: String, newPassword: String) {
        val member = memberRepository.find(memberId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $memberId] 회원을 찾을 수 없습니다.")

        if (!passwordEncoder.matches(currentPassword, member.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.")
        }

        member.changePassword(newPassword)
        val encodedPassword = passwordEncoder.encode(newPassword)
        member.encryptPassword(encodedPassword)

        memberRepository.save(member)
    }
}
