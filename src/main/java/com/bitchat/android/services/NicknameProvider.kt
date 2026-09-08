package com.bitchat.android.services

object NicknameProvider {
    @Volatile
    private var customNickname: String? = null

    fun setNickname(nickname: String) {
        customNickname = nickname
    }

    fun getNickname(peerId: String): String = customNickname ?: "Unknown"
}

