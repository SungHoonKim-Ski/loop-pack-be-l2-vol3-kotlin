package com.loopers.interfaces.api.member

import com.loopers.config.auth.AuthenticatedMember
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Member V1 API", description = "회원 API 입니다.")
interface MemberV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "새로운 회원을 등록합니다.",
    )
    fun register(
        request: MemberV1Dto.RegisterRequest,
    ): ApiResponse<Any>

    @Operation(
        summary = "내 정보 조회",
        description = "인증된 회원의 정보를 조회합니다.",
    )
    fun getMe(
        authenticatedMember: AuthenticatedMember,
    ): ApiResponse<MemberV1Dto.MemberResponse>

    @Operation(
        summary = "비밀번호 변경",
        description = "인증된 회원의 비밀번호를 변경합니다.",
    )
    fun changePassword(
        authenticatedMember: AuthenticatedMember,
        request: MemberV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any>
}
