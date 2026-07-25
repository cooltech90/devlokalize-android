package com.devlokalize.sdk

/**
 * A tiny, dependency-free JSON reader/writer scoped to exactly what the
 * DevLokalize API needs: objects, strings, and null. Keeping this hand-rolled
 * instead of pulling in Gson/Moshi/kotlinx.serialization keeps the SDK at
 * zero external dependencies, so it can't version-conflict with whatever
 * JSON library the host app already uses.
 */
internal object MiniJson {

    /** Parses a JSON object into a `Map<String, Any?>` (values are `String`, `Map<String, Any?>`, or `null`). */
    fun parseObject(input: String): Map<String, Any?> {
        val parser = Parser(input)
        parser.skipWhitespace()
        val result = parser.parseValue()
        @Suppress("UNCHECKED_CAST")
        return result as? Map<String, Any?> ?: emptyMap()
    }

    /** Serializes a `Map<String, Map<String, String>>` (the on-disk cache shape) back to JSON. */
    fun stringify(components: Map<String, Map<String, String>>): String {
        val sb = StringBuilder()
        sb.append('{')
        components.entries.forEachIndexed { i, (component, strings) ->
            if (i > 0) sb.append(',')
            sb.append(quote(component)).append(':').append('{')
            strings.entries.forEachIndexed { j, (key, value) ->
                if (j > 0) sb.append(',')
                sb.append(quote(key)).append(':').append(quote(value))
            }
            sb.append('}')
        }
        sb.append('}')
        return sb.toString()
    }

    /** Parses the flat on-disk cache format back into a nested map. */
    fun parseComponentsCache(input: String): Map<String, Map<String, String>> {
        val raw = parseObject(input)
        val result = LinkedHashMap<String, Map<String, String>>()
        for ((component, value) in raw) {
            @Suppress("UNCHECKED_CAST")
            val inner = value as? Map<String, Any?> ?: continue
            val strings = LinkedHashMap<String, String>()
            for ((k, v) in inner) {
                if (v is String) strings[k] = v
            }
            result[component] = strings
        }
        return result
    }

    private fun quote(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            return when {
                pos >= s.length -> null
                s[pos] == '{' -> parseObject()
                s[pos] == '"' -> parseString()
                s.startsWith("null", pos) -> { pos += 4; null }
                s.startsWith("true", pos) -> { pos += 4; true }
                s.startsWith("false", pos) -> { pos += 5; false }
                else -> parseNumberOrBail()
            }
        }

        private fun parseNumberOrBail(): Any? {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '-' || s[pos] == '+' || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E')) {
                pos++
            }
            if (pos == start) { pos++; return null }
            return s.substring(start, pos)
        }

        fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            expect('{')
            skipWhitespace()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; break }
                    else -> break
                }
            }
            return map
        }

        fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < s.length && s[pos] != '"') {
                val c = s[pos]
                if (c == '\\' && pos + 1 < s.length) {
                    pos++
                    when (s[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = s.substring(pos + 1, pos + 5)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(s[pos])
                    }
                } else {
                    sb.append(c)
                }
                pos++
            }
            expect('"')
            return sb.toString()
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        private fun expect(c: Char) {
            if (pos < s.length && s[pos] == c) {
                pos++
            } else {
                error("Expected '$c' at position $pos in: $s")
            }
        }
    }
}
