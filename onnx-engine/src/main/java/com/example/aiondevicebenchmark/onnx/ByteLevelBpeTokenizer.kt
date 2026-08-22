package com.example.aiondevicebenchmark.onnx

import org.json.JSONObject
import java.io.File

internal class ByteLevelBpeTokenizer private constructor(
    private val vocab: Map<String, Long>,
    private val idToToken: Map<Long, String>,
    private val mergeRanks: Map<Pair<String, String>, Int>,
) {
    fun encode(text: String): List<Long> {
        return text.toPieces().flatMap { piece ->
            val byteToken = piece.toByteLevelToken()
            bpe(byteToken).mapNotNull { vocab[it] }
        }
    }

    fun decode(ids: List<Long>): String {
        val bytes = ids.flatMap { id ->
            val token = idToToken[id] ?: return@flatMap emptyList()
            token.map { char -> byteDecoder[char] ?: char.code.toByte() }
        }.toByteArray()
        return bytes.toString(Charsets.UTF_8)
    }

    private fun bpe(token: String): List<String> {
        if (token.length <= 1 || vocab.containsKey(token)) return listOf(token)
        var parts = token.map { it.toString() }
        while (parts.size > 1) {
            var best: MergeCandidate? = null
            for (index in 0 until parts.lastIndex) {
                val rank = mergeRanks[parts[index] to parts[index + 1]] ?: continue
                val candidate = MergeCandidate(index = index, rank = rank)
                if (best == null || candidate.rank < best.rank) {
                    best = candidate
                }
            }
            val selected = best ?: break
            val next = mutableListOf<String>()
            var index = 0
            while (index < parts.size) {
                if (index == selected.index) {
                    next += parts[index] + parts[index + 1]
                    index += 2
                } else {
                    next += parts[index]
                    index += 1
                }
            }
            parts = next
        }
        return parts
    }

    private data class MergeCandidate(val index: Int, val rank: Int)

    companion object {
        fun fromFile(file: File): ByteLevelBpeTokenizer {
            val root = JSONObject(file.readText())
            val model = root.getJSONObject("model")
            val vocabObject = model.getJSONObject("vocab")
            val vocab = buildMap {
                vocabObject.keys().forEach { token ->
                    put(token, vocabObject.getLong(token))
                }
            }
            val idToToken = vocab.entries.associate { (token, id) -> id to token }
            val merges = model.getJSONArray("merges")
            val mergeRanks = buildMap {
                for (index in 0 until merges.length()) {
                    val merge = merges.get(index)
                    val pair = when (merge) {
                        is String -> merge.split(" ", limit = 2)
                        else -> {
                            val array = merges.getJSONArray(index)
                            listOf(array.getString(0), array.getString(1))
                        }
                    }
                    if (pair.size == 2) put(pair[0] to pair[1], index)
                }
            }
            return ByteLevelBpeTokenizer(vocab = vocab, idToToken = idToToken, mergeRanks = mergeRanks)
        }

        private val byteEncoder: Map<Byte, Char>
        private val byteDecoder: Map<Char, Byte>

        init {
            val bytes = mutableListOf<Int>()
            bytes += ('!'.code..'~'.code)
            bytes += ('¡'.code..'¬'.code)
            bytes += ('®'.code..'ÿ'.code)

            val chars = bytes.toMutableList()
            var next = 0
            for (byte in 0..255) {
                if (byte !in bytes) {
                    bytes += byte
                    chars += 256 + next
                    next += 1
                }
            }
            byteEncoder = bytes.mapIndexed { index, byte -> byte.toByte() to chars[index].toChar() }.toMap()
            byteDecoder = byteEncoder.entries.associate { (byte, char) -> char to byte }
        }

        private fun String.toPieces(): List<String> {
            return Regex("""\s+|\S+""").findAll(this).map { it.value }.toList()
        }

        private fun String.toByteLevelToken(): String {
            return toByteArray(Charsets.UTF_8).map { byte -> byteEncoder.getValue(byte) }.joinToString("")
        }
    }
}
