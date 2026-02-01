package com.loopers.domain.member

interface MemberRepository {
    fun find(id: Long): MemberModel?
    fun findByLoginId(loginId: String): MemberModel?
    fun findByEmail(email: String): MemberModel?
    fun save(member: MemberModel): MemberModel
}
