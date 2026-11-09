package com.chzzz.recording.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** `RECORDING_STORAGE_DIR` 아래 로컬 디스크에 업로드 파일을 둔다. 폴더가 없으면 시작할 때 만든다. */
@Component
class LocalDiskUploadedFileStorage(
    @Value("\${recording.storage-dir}") storageDir: Path,
) : UploadedFileStorage {

    private val root: Path = Files.createDirectories(storageDir).toAbsolutePath().normalize()

    override fun save(name: String, content: InputStream) {
        content.use { Files.copy(it, resolve(name), StandardCopyOption.REPLACE_EXISTING) }
    }

    override fun load(name: String): Resource? {
        val path = resolve(name)
        return if (Files.isRegularFile(path)) FileSystemResource(path) else null
    }

    override fun delete(name: String) {
        Files.deleteIfExists(resolve(name))
    }

    private fun resolve(name: String): Path {
        val path = root.resolve(name).normalize()
        require(path.parent == root) { "저장 폴더 밖의 경로는 쓸 수 없습니다: $name" }
        return path
    }
}
