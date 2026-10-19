package com.dmp.android.service

import android.os.Parcel
import android.os.Parcelable

/**
 * LLM 响应 Parcelable 实现
 */
data class LLMResponse(
    val content: String = "",
    val provider: String = "",
    val model: String = "",
    val finishReason: String = "stop",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "stop",
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(content)
        parcel.writeString(provider)
        parcel.writeString(model)
        parcel.writeString(finishReason)
        parcel.writeInt(promptTokens)
        parcel.writeInt(completionTokens)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LLMResponse> {
        override fun createFromParcel(parcel: Parcel): LLMResponse {
            return LLMResponse(parcel)
        }

        override fun newArray(size: Int): Array<LLMResponse?> {
            return arrayOfNulls(size)
        }
    }
}