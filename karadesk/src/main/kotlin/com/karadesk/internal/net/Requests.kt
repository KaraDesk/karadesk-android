package com.karadesk.internal.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class DeviceInfo(
    val os: String,
    val osVersion: String,
    val deviceModel: String,
    val appVersion: String?,
    val sdkVersion: String,
    val language: String?,
    val timezone: String,
)

internal data class BootInput(
    val spaceId: String,
    val appId: String,
    val visitorId: String,
    val userId: String?,
    val email: String?,
    val name: String?,
    val phone: String?,
    val avatar: String?,
    val signedUpAt: Long?,
    val userHash: String?,
    val device: DeviceInfo,
)

internal object Requests {
    fun boot(input: BootInput) = ApiRequest(
        HttpMethod.POST,
        "/v1/messenger/boot",
        body = buildJsonObject {
            put("space_id", input.spaceId)
            put("app_id", input.appId)
            put("visitor_id", input.visitorId)
            input.userId?.let { put("user_id", it) }
            input.email?.let { put("email", it) }
            input.name?.let { put("name", it) }
            input.phone?.let { put("phone", it) }
            input.avatar?.let { put("avatar", it) }
            input.signedUpAt?.let { put("signed_up_at", it) }
            input.userHash?.let { put("user_hash", it) }
            put("device", buildJsonObject {
                put("os", input.device.os)
                put("os_version", input.device.osVersion)
                put("device_model", input.device.deviceModel)
                input.device.appVersion?.let { put("app_version", it) }
                put("sdk_version", input.device.sdkVersion)
                input.device.language?.let { put("language", it) }
                put("timezone", input.device.timezone)
            })
        },
        requiresSession = false,
    )

    fun conversations() = ApiRequest(HttpMethod.GET, "/v1/messenger/conversations")

    fun conversation(id: Long) = ApiRequest(HttpMethod.GET, "/v1/messenger/conversations/$id")

    private fun message(body: String, assetIds: List<String>): JsonObject = buildJsonObject {
        put("body", body)
        if (assetIds.isNotEmpty()) {
            put("attachments", buildJsonArray {
                assetIds.forEach { id ->
                    add(buildJsonObject {
                        put("type", "file")
                        put("asset_id", id)
                    })
                }
            })
        }
    }

    fun startConversation(body: String, assetIds: List<String>) =
        ApiRequest(HttpMethod.POST, "/v1/messenger/conversations", body = message(body, assetIds))

    fun send(conversationId: Long, body: String, assetIds: List<String>) =
        ApiRequest(HttpMethod.POST, "/v1/messenger/conversations/$conversationId/messages", body = message(body, assetIds))

    fun markRead(conversationId: Long) =
        ApiRequest(HttpMethod.POST, "/v1/messenger/conversations/$conversationId/read")

    fun rate(conversationId: Long, messageId: String, rating: Int?, remark: String?) = ApiRequest(
        HttpMethod.POST,
        "/v1/messenger/conversations/$conversationId/rating",
        body = buildJsonObject {
            put("message_id", messageId)
            rating?.let { put("rating", it) }
            remark?.let { put("remark", it) }
        },
    )

    fun startUpload(filename: String, contentType: String, byteSize: Int) = ApiRequest(
        HttpMethod.POST,
        "/v1/messenger/attachments",
        body = buildJsonObject {
            put("filename", filename)
            put("content_type", contentType)
            put("byte_size", byteSize)
        },
    )

    fun completeUpload(assetId: String) =
        ApiRequest(HttpMethod.POST, "/v1/messenger/attachments/$assetId/complete")

    fun react(articleId: Long, reaction: String) = ApiRequest(
        HttpMethod.POST,
        "/v1/messenger/articles/$articleId/reactions",
        body = buildJsonObject { put("reaction", reaction) },
    )

    fun collections(helpCenterId: Long) =
        ApiRequest(HttpMethod.GET, "/v1/public/help-centers/$helpCenterId/collections", requiresSession = false)

    fun articles(helpCenterId: Long, collectionId: Long) = ApiRequest(
        HttpMethod.GET,
        "/v1/public/help-centers/$helpCenterId/articles",
        query = listOf("collection_id" to collectionId.toString(), "limit" to "100", "offset" to "0"),
        requiresSession = false,
    )

    fun search(helpCenterId: Long, query: String) = ApiRequest(
        HttpMethod.GET,
        "/v1/public/help-centers/$helpCenterId/search",
        query = listOf("q" to query, "limit" to "20", "offset" to "0"),
        requiresSession = false,
    )

    fun article(helpCenterId: Long, articleId: Long) =
        ApiRequest(HttpMethod.GET, "/v1/public/help-centers/$helpCenterId/articles/$articleId", requiresSession = false)

    fun registerPushToken(token: String) = ApiRequest(
        HttpMethod.PUT,
        "/v1/messenger/push-token",
        body = buildJsonObject {
            put("token", token)
            put("environment", "production")
        },
    )

    fun removePushToken(token: String) =
        ApiRequest(HttpMethod.DELETE, "/v1/messenger/push-token/$token")
}
