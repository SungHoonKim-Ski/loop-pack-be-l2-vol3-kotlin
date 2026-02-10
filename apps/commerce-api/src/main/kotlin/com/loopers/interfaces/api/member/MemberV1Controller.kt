package com.loopers.interfaces.api.member

import com.loopers.application.member.MemberFacade
import com.loopers.config.auth.Authenticated
import com.loopers.config.auth.AuthenticatedMember
import com.loopers.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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

    @Authenticated
    @GetMapping("/me")
    override fun getMe(
        authenticatedMember: AuthenticatedMember,
    ): ApiResponse<MemberV1Dto.MemberResponse> {
        return memberFacade.getMember(authenticatedMember.id)
            .let { MemberV1Dto.MemberResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @Authenticated
    @PatchMapping("/me/password")
    override fun changePassword(
        authenticatedMember: AuthenticatedMember,
        @RequestBody request: MemberV1Dto.ChangePasswordRequest,
    ): ApiResponse<MemberV1Dto.MemberResponse> {
        return memberFacade.changePassword(
            memberId = authenticatedMember.id,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword,
        )
            .let { MemberV1Dto.MemberResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
