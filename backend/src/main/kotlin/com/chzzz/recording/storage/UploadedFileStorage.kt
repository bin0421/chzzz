package com.chzzz.recording.storage

import org.springframework.core.io.Resource
import java.io.InputStream

/**
 * 업로드 파일 저장소. 저장 이름은 호출하는 쪽(녹화방송 모듈)이 정한다.
 * 1단계 구현은 로컬 디스크다(ADR-0001). 2단계에서 교체할 수 있도록 녹화방송 모듈은 이 인터페이스에만 의존한다.
 */
interface UploadedFileStorage {
    fun save(name: String, content: InputStream)

    /** 저장된 파일. 바이트 범위로 읽을 수 있다. 없으면 null. */
    fun load(name: String): Resource?

    fun delete(name: String)
}
