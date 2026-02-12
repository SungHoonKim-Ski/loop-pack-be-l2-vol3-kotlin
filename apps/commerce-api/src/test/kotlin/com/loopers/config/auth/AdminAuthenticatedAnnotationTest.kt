package com.loopers.config.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RestController

@DisplayName("@AdminAuthenticated 어노테이션 안전장치 테스트")
class AdminAuthenticatedAnnotationTest {

    @DisplayName("interfaces.api.admin 패키지의 모든 @RestController에 @AdminAuthenticated가 적용되어 있다.")
    @Test
    fun allAdminControllersHaveAdminAuthenticatedAnnotation() {
        // arrange
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        val adminControllers = scanner.findCandidateComponents("com.loopers.interfaces.api.admin")

        // act & assert
        adminControllers.forEach { beanDef ->
            val controllerClass = Class.forName(beanDef.beanClassName)
            assertThat(controllerClass.isAnnotationPresent(AdminAuthenticated::class.java))
                .withFailMessage("${controllerClass.simpleName}에 @AdminAuthenticated 어노테이션이 누락되었습니다.")
                .isTrue()
        }
    }
}
