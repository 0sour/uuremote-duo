package com.sour.duoremote.ui.streaming

import android.content.Context

/**
 * Compact offline pinyin candidate engine.
 *
 * The asset is intentionally small for v0.1; matching is prefix-based and
 * frequency-ranked. It never calls a network service and never logs input.
 */
class PinyinEngine private constructor(
    private val entries: List<Entry>,
) {
    data class Entry(val pinyin: String, val word: String, val score: Int)

    fun candidates(input: String, limit: Int = 7): List<String> {
        val query = normalize(input)
        if (query.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { it.pinyin.startsWith(query) }
            .sortedWith(
                compareBy<Entry> { if (it.pinyin == query) 0 else 1 }
                    .thenBy { it.pinyin.length }
                    .thenByDescending { it.score },
            )
            .map { it.word }
            .distinct()
            .take(limit.coerceIn(1, 9))
            .toList()
    }

    private fun normalize(value: String): String =
        value.lowercase().replace("'", "").replace(" ", "")

    companion object {
        @Volatile
        private var instance: PinyinEngine? = null

        fun get(context: Context): PinyinEngine {
            return instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }
        }

        private fun load(context: Context): PinyinEngine {
            val entries = runCatching {
                context.assets.open("pinyin_dict.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.mapNotNull(::parseLine).toList()
                }
            }.getOrDefault(emptyList())
            return PinyinEngine(entries)
        }

        internal fun parseLine(line: String): Entry? {
            val parts = line.trim().split('\t')
            if (parts.size < 3) return null
            val pinyin = parts[0].trim().lowercase()
            val word = parts[1].trim()
            val score = parts[2].trim().toIntOrNull() ?: return null
            if (pinyin.isEmpty() || word.isEmpty()) return null
            return Entry(pinyin, word, score)
        }
    }
}
