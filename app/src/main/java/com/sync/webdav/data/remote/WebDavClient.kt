package com.sync.webdav.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavResource(
    val name: String,
    val href: String,
    val isDirectory: Boolean,
    val contentLength: Long = 0L
)

class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun sanitizeUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (path.isEmpty() || path == "/") cleanBase else "$cleanBase$cleanPath"
    }

    private fun applyAuth(builder: Request.Builder): Request.Builder {
        if (username.isNotEmpty()) {
            val credential = Credentials.basic(username, password)
            builder.header("Authorization", credential)
        }
        return builder
    }

    /**
     * Test connection to the WebDAV server
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val propfindXml = """<?xml version="1.0" encoding="utf-8" ?>
                |<D:propfind xmlns:D="DAV:">
                |  <D:prop><D:resourcetype/></D:prop>
                |</D:propfind>""".trimMargin()

            val request = applyAuth(
                Request.Builder()
                    .url(sanitizeUrl(""))
                    .method("PROPFIND", propfindXml.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
                    .header("Depth", "0")
            ).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List files in a WebDAV directory
     */
    suspend fun listRemoteFiles(remotePath: String): Result<List<WebDavResource>> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = sanitizeUrl(remotePath)
            val propfindXml = """<?xml version="1.0" encoding="utf-8" ?>
                |<D:propfind xmlns:D="DAV:">
                |  <D:prop>
                |    <D:displayname/>
                |    <D:resourcetype/>
                |    <D:getcontentlength/>
                |  </D:prop>
                |</D:propfind>""".trimMargin()

            val request = applyAuth(
                Request.Builder()
                    .url(targetUrl)
                    .method("PROPFIND", propfindXml.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
                    .header("Depth", "1")
            ).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(Exception("WebDAV Listing failed: ${response.code} ${response.message}"))
                }

                val bodyStream = response.body?.byteStream()
                    ?: return@withContext Result.success(emptyList())

                val resources = parsePropfindResponse(bodyStream, targetUrl)
                Result.success(resources)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePropfindResponse(inputStream: InputStream, requestUrl: String): List<WebDavResource> {
        val list = mutableListOf<WebDavResource>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)

            val responses = doc.getElementsByTagNameNS("*", "response")
            val requestPathNormalized = URI(requestUrl).path.trimEnd('/')

            for (i in 0 until responses.length) {
                val element = responses.item(i) as Element
                val hrefNode = element.getElementsByTagNameNS("*", "href").item(0) ?: continue
                val href = hrefNode.textContent.trim()
                val hrefPath = URI(href).path.trimEnd('/')

                // Skip the parent directory itself
                if (hrefPath.equals(requestPathNormalized, ignoreCase = true)) {
                    continue
                }

                val isDir = element.getElementsByTagNameNS("*", "collection").length > 0

                // Extract filename including extension
                val name = hrefPath.substringAfterLast('/')
                if (name.isNotEmpty()) {
                    val sizeStr = element.getElementsByTagNameNS("*", "getcontentlength").item(0)?.textContent ?: "0"
                    val size = sizeStr.toLongOrNull() ?: 0L
                    list.add(WebDavResource(name = name, href = href, isDirectory = isDir, contentLength = size))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Download a remote file to an output stream
     */
    suspend fun downloadFile(remotePath: String, outputStream: OutputStream): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fileUrl = sanitizeUrl(remotePath)
            val request = applyAuth(Request.Builder().url(fileUrl).get()).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }
                val inStream = response.body?.byteStream()
                    ?: return@withContext Result.failure(Exception("Empty body"))

                inStream.copyTo(outputStream)
                outputStream.flush()
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload a local stream to a remote WebDAV path
     */
    suspend fun uploadFile(remotePath: String, inputStream: InputStream, length: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fileUrl = sanitizeUrl(remotePath)
            val bytes = inputStream.readBytes()
            val body = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())

            val request = applyAuth(
                Request.Builder()
                    .url(fileUrl)
                    .put(body)
            ).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 204) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Upload failed: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a folder on WebDAV (MKCOL)
     */
    suspend fun createFolder(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val folderUrl = sanitizeUrl(remotePath)
            val request = applyAuth(
                Request.Builder()
                    .url(folderUrl)
                    .method("MKCOL", null)
            ).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 405) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("MKCOL failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
