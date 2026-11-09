package com.chzzz.recording

import org.springframework.data.jpa.repository.JpaRepository

interface RecordingRepository : JpaRepository<Recording, Long> {
    fun findAllByOrderByCreatedAtDescIdDesc(): List<Recording>
}
