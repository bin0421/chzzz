package com.chzzz.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    /** 현재 시각은 항상 이 시계로 얻는다. 테스트에서 바꿔 끼울 수 있다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
