package com.karadesk.internal.net

import com.karadesk.KaraDeskException
import com.karadesk.internal.model.KaraDeskJson
import com.karadesk.internal.model.UploadTicket
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class HttpMethod { GET, POST, PUT, DELETE }

internal class ApiRequest(
    val method: HttpMethod,
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val body: JsonElement? = null,
    val requiresSession: Boolean = true,
)

internal class ApiClient(
    val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    val userAgent: String,
    val http: OkHttpClient = defaultHttpClient(),
) {
    suspend fun send(request: ApiRequest, token: String?): JsonElement {
        val url = baseUrl.newBuilder()
            .encodedPath(request.path)
            .apply { request.query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()

        val body: RequestBody? = when {
            request.body != null -> request.body.toString().toRequestBody(JSON)
            request.method == HttpMethod.POST || request.method == HttpMethod.PUT -> "{}".toRequestBody(JSON)
            else -> null
        }

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .method(request.method.name, body)

        if (token != null && request.requiresSession) {
            builder.header("Authorization", "Bearer $token")
        }

        val (code, text) = execute(builder.build())

        if (code !in 200..299) {
            throw errorFor(code, text)
        }

        return try {
            KaraDeskJson.parseToJsonElement(text.ifEmpty { "{}" })
        } catch (_: Exception) {
            throw KaraDeskException.UnexpectedResponse()
        }
    }

    suspend fun sendObject(request: ApiRequest, token: String?): JsonObject =
        send(request, token) as? JsonObject ?: throw KaraDeskException.UnexpectedResponse()

    suspend fun upload(bytes: ByteArray, filename: String, contentType: String, ticket: UploadTicket) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            ticket.fields.toSortedMap().forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart("file", filename, bytes.toRequestBody(contentType.toMediaType()))
        }.build()

        val request = Request.Builder()
            .url(ticket.url)
            .method(ticket.method.uppercase(), form)
            .build()

        val (code, _) = execute(request)

        if (code !in 200..299) {
            throw KaraDeskException.Server(code, UPLOAD_FAILED)
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)

        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(KaraDeskException.Network(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { it.code to it.body.string() }
                } catch (e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(KaraDeskException.Network(e))
                    }
                    return
                }

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        })
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.karadesk.com"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val UPLOAD_FAILED = "The upload failed. Please try again."

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        fun errorFor(status: Int, body: String): KaraDeskException {
            val message = runCatching {
                (KaraDeskJson.parseToJsonElement(body).jsonObject["message"] as? JsonPrimitive)?.content
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Request failed ($status)"

            val lowered = message.lowercase()

            return when {
                status == 401 && ("identity" in lowered || "hash" in lowered) -> KaraDeskException.IdentityVerificationFailed(message)
                status == 403 || status == 404 -> KaraDeskException.Unavailable(message)
                else -> KaraDeskException.Server(status, message)
            }
        }
    }
}

internal val KaraDeskException.isExpiredSession: Boolean
    get() = this is KaraDeskException.Server && statusCode == 401
