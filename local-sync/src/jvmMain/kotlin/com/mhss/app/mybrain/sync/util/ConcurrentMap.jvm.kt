package com.mhss.app.mybrain.sync.util

actual inline fun <K, V> concurrentMutableMap(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()
