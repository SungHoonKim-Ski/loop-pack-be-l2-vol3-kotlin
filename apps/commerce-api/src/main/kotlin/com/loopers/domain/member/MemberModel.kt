package com.loopers.domain.member

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Email
import com.loopers.domain.member.vo.LoginId
import com.loopers.domain.member.vo.MemberName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "member")
class MemberModel(
    loginId: LoginId,
    encodedPassword: String,
    name: MemberName,
    birthday: LocalDate,
    email: Email,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, unique = true)
    var loginId: String = loginId.value
        protected set

    @Column(name = "password", nullable = false)
    var password: String = encodedPassword
        protected set

    @Column(name = "name", nullable = false)
    var name: String = name.value
        protected set

    @Column(name = "birthday", nullable = false)
    var birthday: LocalDate = birthday
        protected set

    @Column(name = "email", nullable = false, unique = true)
    var email: String = email.value
        protected set

    fun changePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    fun getMaskedName(): String = MemberName(name).masked()
}
