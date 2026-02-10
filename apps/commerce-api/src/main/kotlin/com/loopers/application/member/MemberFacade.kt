package com.loopers.application.member

import com.loopers.config.cache.CacheConfig
import com.loopers.domain.member.MemberRegisterCommand
import com.loopers.domain.member.MemberService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.cache.CacheManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class MemberFacade(
    private val memberService: MemberService,
    private val cacheManager: CacheManager,
) {
    fun register(command: MemberRegisterCommand): MemberInfo {
        return try {
            memberService.register(command)
                .let { MemberInfo.from(it) }
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 회원 정보입니다.")
        }
    }

    fun getMember(memberId: Long): MemberInfo {
        return memberService.getMember(memberId)
            .let { MemberInfo.from(it) }
    }

    fun changePassword(
        memberId: Long,
        currentPassword: String,
        newPassword: String,
    ): MemberInfo {
        memberService.changePassword(memberId, currentPassword, newPassword)
        val member = memberService.getMember(memberId)
        cacheManager.getCache(CacheConfig.AUTH_CACHE)?.evict(member.loginId)
        return MemberInfo.from(member)
    }
}
