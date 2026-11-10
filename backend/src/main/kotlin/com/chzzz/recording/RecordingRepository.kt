package com.chzzz.recording

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface RecordingRepository : JpaRepository<Recording, Long> {
    @EntityGraph(attributePaths = ["owner"])
    fun findAllByOrderByCreatedAtDescIdDesc(): List<Recording>
}
