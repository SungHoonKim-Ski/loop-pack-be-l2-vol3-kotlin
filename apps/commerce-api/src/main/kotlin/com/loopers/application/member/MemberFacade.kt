package com.loopers.application.member

import com.loopers.config.cache.CacheConfig
import com.loopers.domain.member.MemberService
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class MemberFacade(
    private val memberService: MemberService,
    private val cacheManager: CacheManager,
) {
    fun register(
        loginId: String,
        password: String,
        name: String,
        birthday: LocalDate,
        email: String,
    ) {
        memberService.register(
            loginId = loginId,
            password = password,
            name = name,
            birthday = birthday,
            email = email,
        )
    }

    fun getMyInfo(loginId: String): MemberInfo {
        val member = memberService.getMemberByLoginId(loginId)
        return MemberInfo.from(member)
    }

    fun changePassword(loginId: String, newPassword: String) {
        memberService.changePassword(loginId, newPassword)
        cacheManager.getCache(CacheConfig.AUTH_CACHE)?.evict(loginId)
    }
}
