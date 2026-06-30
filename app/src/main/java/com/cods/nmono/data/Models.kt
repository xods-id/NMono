package com.cods.nmono.data

import com.google.gson.annotations.SerializedName

data class Note(
    @SerializedName("id") val id: String,
    @SerializedName("title") var title: String,
    @SerializedName("content") var content: String,
    @SerializedName("hashtags") var hashtags: List<String>,
    @SerializedName("imageBase64") var imageBase64: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") var updatedAt: Long,
    @SerializedName("audioBase64") var audioBase64: String? = null,
    @SerializedName("deletedAt") var deletedAt: Long? = null
)

data class TagHistory(
    @SerializedName("name") val name: String,
    @SerializedName("count") var count: Int,
    @SerializedName("lastUsed") var lastUsed: Long
)

data class ExportData(
    @SerializedName("version") val version: Int = 3,
    @SerializedName("exportedAt") val exportedAt: String,
    @SerializedName("notes") val notes: List<Note>,
    @SerializedName("tagHistory") val tagHistory: List<TagHistory>,
    @SerializedName("customOrder") val customOrder: List<String>
)

enum class SortType { UPDATED, CREATED, TITLE_ASC, TITLE_DESC, CUSTOM }
enum class ToastType { SUCCESS, ERROR, INFO }

data class ToastData(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 5000L,
    val undoAction: (() -> Unit)? = null
)