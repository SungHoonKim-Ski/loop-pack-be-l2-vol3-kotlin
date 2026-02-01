package com.loopers.application.member

import com.loopers.domain.member.MemberModel
import java.time.LocalDate

data class MemberInfo(
    val id: Long,
    val loginId: String,
    val maskedName: String,
    val email: String,
    val birthDate: LocalDate,
) {
    companion object {
        fun from(model: MemberModel): MemberInfo {
            return MemberInfo(
                id = model.id,
                loginId = model.loginId,
                maskedName = model.getMaskedName(),
                email = model.email,
                birthDate = model.birthDate,
            )
        }
    }
}
