package com.bitchat.android.sync

object GossipSyncManager {
    interface ConfigProvider {
        fun seenCapacity(): Int
        fun gcsMaxBytes(): Int
        fun gcsTargetFpr(): Double
    }
}
