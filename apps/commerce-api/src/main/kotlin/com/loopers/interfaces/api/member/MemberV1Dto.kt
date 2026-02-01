package com.loopers.interfaces.api.member

import com.fasterxml.jackson.annotation.JsonFormat
import com.loopers.application.member.MemberInfo
import com.loopers.domain.member.MemberRegisterCommand
import java.time.LocalDate

class MemberV1Dto {
    data class RegisterRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val email: String,
        val birthDate: String,
    ) {
        fun toCommand(): MemberRegisterCommand {
            return MemberRegisterCommand(
                loginId = loginId,
                password = password,
                name = name,
                email = email,
                birthDate = LocalDate.parse(birthDate),
            )
        }
    }

    data class MemberResponse(
        val id: Long,
        val loginId: String,
        val maskedName: String,
        val email: String,
        @JsonFormat(pattern = "yyyy-MM-dd")
        val birthDate: LocalDate,
    ) {
        companion object {
            fun from(info: MemberInfo): MemberResponse {
                return MemberResponse(
                    id = info.id,
                    loginId = info.loginId,
                    maskedName = info.maskedName,
                    email = info.email,
                    birthDate = info.birthDate,
                )
            }
        }
    }

    data class ChangePasswordRequest(
        val currentPassword: String,
        val newPassword: String,
    )
}
