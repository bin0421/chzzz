package com.chzzz.common

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** `@Scheduled` 주기 작업을 켠다. */
@Configuration
@EnableScheduling
class SchedulingConfig
