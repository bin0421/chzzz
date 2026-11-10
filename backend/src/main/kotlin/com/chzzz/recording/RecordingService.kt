package com.chzzz.recording

import com.chzzz.common.BroadcastDeletedEvent
import com.chzzz.common.BroadcastType
import com.chzzz.common.FieldValidationException
import com.chzzz.common.NotFoundException
import com.chzzz.common.Ownership
import com.chzzz.common.UnauthenticatedException
import com.chzzz.member.LoginMember
import com.chzzz.member.Member
import com.chzzz.member.MemberRepository
import com.chzzz.recording.storage.UploadedFileStorage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.InputStream
import java.time.Instant
import java.util.UUID

@Service
class RecordingService(
    private val recordingRepository: RecordingRepository,
    private val uploadedFileStorage: UploadedFileStorage,
    private val memberRepository: MemberRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 영상 출처는 업로드 파일 또는 외부 URL 중 정확히 하나다.
     * 업로드 파일은 입력 검증 → 파일 저장 → 메타데이터 저장 순서로 등록하고, 메타데이터 저장이 실패하면 파일을 지운다.
     * 등록한 회원이 소유자가 된다.
     */
    fun register(title: String?, url: String?, file: UploadedFileInput?, member: LoginMember): Recording {
        val owner = memberRepository.findByIdOrNull(member.id) ?: throw UnauthenticatedException()
        val externalUrl = url?.takeIf { it.isNotBlank() }
        VideoSource.requireExactlyOne(hasFile = file != null, hasUrl = externalUrl != null)
        return if (file != null) registerUploadedFile(title, file, owner) else registerExternalUrl(title, externalUrl!!, owner)
    }

    private fun registerExternalUrl(title: String?, url: String, owner: Member): Recording =
        recordingRepository.save(
            Recording.register(
                title = title,
                videoSource = VideoSource.externalUrl(url),
                owner = owner,
                now = Instant.now(),
            )
        )

    private fun registerUploadedFile(title: String?, upload: UploadedFileInput, owner: Member): Recording {
        val extension = upload.originalFilename.orEmpty().substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            throw FieldValidationException("file", "mp4 또는 webm 파일만 올릴 수 있습니다.")
        }
        val storedName = "${UUID.randomUUID()}.$extension"
        val recording = Recording.register(
            title = title,
            videoSource = VideoSource.uploadedFile(storedName),
            owner = owner,
            now = Instant.now(),
        )
        uploadedFileStorage.save(storedName, upload.content)
        try {
            return recordingRepository.save(recording)
        } catch (e: RuntimeException) {
            uploadedFileStorage.delete(storedName)
            throw e
        }
    }

    private companion object {
        val ALLOWED_EXTENSIONS = setOf("mp4", "webm")
    }

    /** 소유자만 제목을 바꾼다. 없으면 404, 소유자가 아니면 403. */
    @Transactional
    fun rename(id: Long, title: String?, member: LoginMember): Recording {
        val recording = recordingRepository.findByIdOrNull(id) ?: throw NotFoundException("녹화방송을 찾을 수 없습니다.")
        Ownership.requireOwner(recording.owner?.id, member)
        recording.rename(title)
        return recording
    }

    /**
     * 소유자만 삭제한다. 없으면 404, 소유자가 아니면 403.
     * 같은 트랜잭션에서 "방송 삭제됨" 이벤트를 발행해 댓글도 함께 지워지게 하고,
     * 업로드 파일은 DB 삭제가 커밋된 뒤에 지운다(롤백되면 파일이 남아 재생할 수 있어야 하므로).
     */
    @Transactional
    fun delete(id: Long, member: LoginMember) {
        val recording = recordingRepository.findByIdOrNull(id) ?: throw NotFoundException("녹화방송을 찾을 수 없습니다.")
        Ownership.requireOwner(recording.owner?.id, member)
        recordingRepository.delete(recording)
        eventPublisher.publishEvent(BroadcastDeletedEvent(BroadcastType.RECORDING, id))
        if (recording.videoSource.type == VideoSourceType.UPLOADED_FILE) {
            val storedName = recording.videoSource.value
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = uploadedFileStorage.delete(storedName)
            })
        }
    }

    @Transactional(readOnly = true)
    fun list(): List<Recording> = recordingRepository.findAllByOrderByCreatedAtDescIdDesc()

    @Transactional(readOnly = true)
    fun find(id: Long): Recording? = recordingRepository.findByIdOrNull(id)

    /** 업로드 파일 녹화방송의 영상. 녹화방송이 없거나, 업로드 파일 출처가 아니거나, 파일이 디스크에 없으면 null. */
    @Transactional(readOnly = true)
    fun findVideo(id: Long): RecordingVideo? {
        val source = recordingRepository.findByIdOrNull(id)?.videoSource ?: return null
        if (source.type != VideoSourceType.UPLOADED_FILE) return null
        val storedName = source.value
        val resource = uploadedFileStorage.load(storedName) ?: return null
        val mediaType = MediaType.parseMediaType("video/" + storedName.substringAfterLast('.'))
        return RecordingVideo(resource, mediaType)
    }
}

class RecordingVideo(
    val resource: Resource,
    val mediaType: MediaType,
)

/** 등록할 때 사용자가 올린 파일. 웹 계층의 타입에 묶이지 않도록 이름과 내용만 받는다. */
class UploadedFileInput(
    val originalFilename: String?,
    val content: InputStream,
)
