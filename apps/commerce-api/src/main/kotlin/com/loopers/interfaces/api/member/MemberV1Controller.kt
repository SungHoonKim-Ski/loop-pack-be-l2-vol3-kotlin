package com.loopers.interfaces.api.member

import com.loopers.application.member.MemberFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberV1Controller(
    private val memberFacade: MemberFacade,
) : MemberV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun registerMember(
        @RequestBody request: MemberV1Dto.RegisterRequest,
    ): ApiResponse<MemberV1Dto.MemberResponse> {
        return memberFacade.register(request.toCommand())
            .let { MemberV1Dto.MemberResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMe(
        @RequestHeader(value = "X-LOGIN-ID", required = false) loginId: String?,
        @RequestHeader(value = "X-PASSWORD", required = false) password: String?,
    ): ApiResponse<MemberV1Dto.MemberResponse> {
        if (loginId == null || password == null) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 없습니다.")
        }

        return memberFacade.getMember(loginId, password)
            .let { MemberV1Dto.MemberResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/me/password")
    override fun changePassword(
        @RequestHeader(value = "X-LOGIN-ID", required = false) loginId: String?,
        @RequestHeader(value = "X-PASSWORD", required = false) password: String?,
        @RequestBody request: MemberV1Dto.ChangePasswordRequest,
    ): ApiResponse<MemberV1Dto.MemberResponse> {
        if (loginId == null || password == null) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 없습니다.")
        }

        return memberFacade.changePassword(
            loginId = loginId,
            authPassword = password,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword,
        )
            .let { MemberV1Dto.MemberResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
