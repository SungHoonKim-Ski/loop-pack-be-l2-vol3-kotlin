package com.loopers.config.cache

import com.loopers.config.auth.AuthenticatedMember
import java.security.MessageDigest

data class CachedAuth(
    val memberId: Long,
    val loginId: String,
    val passwordDigest: String,
) {
    fun matchesPassword(rawPassword: String): Boolean {
        return passwordDigest == sha256(rawPassword)
    }

    fun toAuthenticatedMember(): AuthenticatedMember {
        return AuthenticatedMember(id = memberId, loginId = loginId)
    }

    companion object {
        fun of(authenticatedMember: AuthenticatedMember, rawPassword: String): CachedAuth {
            return CachedAuth(
                memberId = authenticatedMember.id,
                loginId = authenticatedMember.loginId,
                passwordDigest = sha256(rawPassword),
            )
        }

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
