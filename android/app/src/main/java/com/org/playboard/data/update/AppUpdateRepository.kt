package com.org.playboard.data.update

import android.content.Context
import com.org.playboard.BuildConfig
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.AppUpdateDto
import com.org.playboard.di.AuthApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
)

/** Fetches public release metadata and stores at most one installer APK. */
@Singleton
class AppUpdateRepository @Inject constructor(
    @AuthApi private val api: PlayboardApi,
    @ApplicationContext private val context: Context,
) {
    companion object {
        fun isNewerThanCurrent(remoteVersionCode: Int): Boolean = remoteVersionCode > BuildConfig.VERSION_CODE
    }

    suspend fun findUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        api.getAppUpdate().toUpdate()?.takeIf { isNewerThanCurrent(it.versionCode) }
    }

    suspend fun download(update: AppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val target = File(directory, "Playboard-${update.versionCode}.apk")
        try {
            api.downloadApk(update.downloadUrl).use { body ->
                val total = body.contentLength()
                body.byteStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                        }
                        read = input.read(buffer)
                    }
                } }
            }
            target
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    fun delete(file: File) { file.delete() }
}

private fun AppUpdateDto.toUpdate(): AppUpdate? {
    if (!available || versionCode == null || versionName.isNullOrBlank() || downloadUrl.isNullOrBlank()) return null
    return AppUpdate(versionCode, versionName, downloadUrl)
}
