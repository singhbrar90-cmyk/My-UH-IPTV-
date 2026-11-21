package com.example.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import java.util.Locale

data class ExternalLaunchResult(
    val launched: Boolean,
    val message: String
)

object ExternalPlayerHelper {

    fun guessVideoMimeType(url: String): String {
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            cleanPath.endsWith(".m3u8") || cleanPath.endsWith(".m3u") -> "application/x-mpegURL"
            cleanPath.endsWith(".mpd") -> "application/dash+xml"
            cleanPath.endsWith(".ts") -> "video/mp2t"
            cleanPath.endsWith(".mkv") -> "video/x-matroska"
            cleanPath.endsWith(".mp4") || cleanPath.endsWith(".m4v") -> "video/mp4"
            cleanPath.endsWith(".avi") -> "video/x-msvideo"
            cleanPath.endsWith(".webm") -> "video/webm"
            else -> {
                val ext = MimeTypeMap.getFileExtensionFromUrl(cleanPath)
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/*"
            }
        }
    }

    fun launchExternalPlayer(
        context: Context,
        streamUrl: String,
        title: String
    ): ExternalLaunchResult {
        if (streamUrl.isBlank()) {
            return ExternalLaunchResult(
                launched = false,
                message = "No stream URL is available to send to an external player."
            )
        }
        val uri = Uri.parse(streamUrl)
        val mime = guessVideoMimeType(streamUrl)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val fallbackVideoIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            val chooser = Intent.createChooser(viewIntent, "Open stream with external player").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ExternalLaunchResult(
                launched = true,
                message = "Chooser opened for $title ($mime). Playback control is handled by the selected app."
            )
        }.recoverCatching {
            val fallbackChooser = Intent.createChooser(fallbackVideoIntent, "Open stream with external player").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackChooser)
            ExternalLaunchResult(
                launched = true,
                message = "Chooser opened for $title (video/*)."
            )
        }.getOrElse { error ->
            ExternalLaunchResult(
                launched = false,
                message = "No compatible external video player was found on this device (${error.localizedMessage ?: error.javaClass.simpleName})."
            )
        }
    }
}
