package com.loopers.application.member

import com.loopers.domain.member.MemberRegisterCommand
import com.loopers.domain.member.MemberService
import org.springframework.stereotype.Component

@Component
class MemberFacade(
    private val memberService: MemberService,
) {
    fun register(command: MemberRegisterCommand): MemberInfo {
        return memberService.register(command)
            .let { MemberInfo.from(it) }
    }

    fun getMember(loginId: String, password: String): MemberInfo {
        return memberService.authenticate(loginId, password)
            .let { MemberInfo.from(it) }
    }

    fun changePassword(
        loginId: String,
        authPassword: String,
        currentPassword: String,
        newPassword: String,
    ): MemberInfo {
        val member = memberService.authenticate(loginId, authPassword)
        memberService.changePassword(member.id, currentPassword, newPassword)
        return memberService.getMember(member.id)
            .let { MemberInfo.from(it) }
    }
}
