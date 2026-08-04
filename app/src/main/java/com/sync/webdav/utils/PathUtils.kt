package com.sync.webdav.utils

import android.content.Context
import android.net.Uri
import java.net.URLDecoder

object PathUtils {

    /**
     * Converts a SAF tree Uri string to a human-readable Android absolute path.
     * Example: "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FMusic_dav"
     * Result: "/storage/emulated/0/Music/Music_dav"
     */
    fun formatDisplayPath(context: Context, uriString: String): String {
        if (uriString.isEmpty()) return "未选择目录"
        return try {
            val uri = Uri.parse(uriString)
            val path = uri.path ?: uriString

            // Extract document ID / tree ID
            val docId = when {
                path.contains("/tree/") -> path.substringAfter("/tree/")
                path.contains("/document/") -> path.substringAfter("/document/")
                else -> path
            }

            val decodedDocId = URLDecoder.decode(docId, "UTF-8")

            if (decodedDocId.contains(":")) {
                val parts = decodedDocId.split(":")
                val storageType = parts[0]
                val relativePath = parts.getOrNull(1)?.trimStart('/') ?: ""

                if (storageType.equals("primary", ignoreCase = true)) {
                    if (relativePath.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relativePath"
                } else {
                    if (relativePath.isEmpty()) "/storage/$storageType" else "/storage/$storageType/$relativePath"
                }
            } else {
                decodedDocId
            }
        } catch (e: Exception) {
            uriString
        }
    }
}
