package com.hdn.adsmodule.ads

import android.util.Log
import com.google.gson.Gson

object AdUnitParser {
    private const val TAG = "AdUnitParser"
    private val gson = Gson()

    /**
     * [raw]: 1 ID hoặc JSON array ID (waterfall). Rỗng/không hợp lệ -> [fallback].
     * ID trống bị bỏ: MAX không nhận ad unit rỗng, list rỗng = slot chưa cấu hình (load fail ngay, không crash).
     */
    fun parse(raw: String, fallback: String): List<String> {
        Log.d(TAG, "parse: $raw")
        val source = raw.trim()
        val fallbackList = listOf(fallback.trim()).filter { it.isNotEmpty() }
        if (source.isEmpty()) return fallbackList

        val values = runCatching {
            gson.fromJson(source, Array<String?>::class.java)
                ?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty()
        }.getOrDefault(emptyList())

        if (values.isNotEmpty()) return values
        // Không phải JSON array -> coi cả chuỗi là 1 ID
        return if (source.startsWith("[")) fallbackList else listOf(source)
    }
}
