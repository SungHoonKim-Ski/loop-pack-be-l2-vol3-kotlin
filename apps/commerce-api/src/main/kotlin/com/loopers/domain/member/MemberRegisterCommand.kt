package com.loopers.domain.member

import java.time.LocalDate

data class MemberRegisterCommand(
    val loginId: String,
    val password: String,
    val name: String,
    val email: String,
    val birthDate: LocalDate,
)
