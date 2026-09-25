package io.github.eggplants.godlo.ui.settings

import io.github.eggplants.godlo.core.ConfigFormat

/** What a stretch of a config file is, for the text editor to colour it by. */
enum class TokenKind { COMMENT, STRING, NUMBER, KEYWORD, KEY, SECTION, OPTION }

/** [kind] from [start] up to, not including, [end]. */
data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * The tokens of [text] written as [format], in order and not overlapping.
 *
 * A lexer good enough to colour a file being typed, not a parser: it never fails, and a string
 * left open stops at the end of its line rather than swallowing the rest of the file.
 */
fun highlight(text: String, format: ConfigFormat): List<Token> = when (format) {
    ConfigFormat.JSON -> JsonLexer(text).run()
    ConfigFormat.TOML -> TomlLexer(text).run()
    ConfigFormat.ARGS -> argsTokens(text)
    ConfigFormat.COOKIES -> cookieTokens(text)
}

/** Where each line of [text] starts, the first at 0. */
fun lineStarts(text: String): List<Int> = buildList {
    add(0)
    text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
}

private fun String.lineEnd(from: Int): Int = indexOf('\n', from).let { if (it < 0) length else it }

/** The end of the string opening at [start] with [quote]: past its closing quote, else its line. */
private fun String.stringEnd(start: Int, quote: String, escapes: Boolean): Int {
    var i = start + quote.length
    val multiline = quote.length == 3
    while (i < length) {
        if (escapes && this[i] == '\\') {
            i += 2
            continue
        }
        if (startsWith(quote, i)) return i + quote.length
        if (!multiline && this[i] == '\n') return i
        i++
    }
    return length
}

private fun String.nextNonBlank(from: Int): Char? {
    var i = from
    while (i < length && (this[i] == ' ' || this[i] == '\t')) i++
    return getOrNull(i)
}

private class JsonLexer(private val text: String) {
    private val tokens = mutableListOf<Token>()

    fun run(): List<Token> {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            i = when {
                c == '"' -> {
                    val end = text.stringEnd(i, "\"", escapes = true)
                    // A string before a colon is a key; the colon may be on the next line.
                    val kind = if (text.nextNonBlankOrNewline(end) == ':') {
                        TokenKind.KEY
                    } else {
                        TokenKind.STRING
                    }
                    tokens += Token(i, end, kind)
                    end
                }

                c == '-' || c.isDigit() -> word(i, TokenKind.NUMBER) {
                    it.isLetterOrDigit() ||
                        it in ".+-"
                }

                c.isLetter() -> {
                    val end = scan(i) { it.isLetter() }
                    if (text.substring(i, end) in
                        JSON_WORDS
                    ) {
                        tokens += Token(i, end, TokenKind.KEYWORD)
                    }
                    end
                }

                else -> i + 1
            }
        }
        return tokens
    }

    private fun String.nextNonBlankOrNewline(from: Int): Char? {
        var i = from
        while (i < length && this[i].isWhitespace()) i++
        return getOrNull(i)
    }

    private fun scan(from: Int, part: (Char) -> Boolean): Int {
        var i = from + 1
        while (i < text.length && part(text[i])) i++
        return i
    }

    private fun word(from: Int, kind: TokenKind, part: (Char) -> Boolean): Int =
        scan(from, part).also { tokens += Token(from, it, kind) }

    companion object {
        val JSON_WORDS = setOf("true", "false", "null")
    }
}

private class TomlLexer(private val text: String) {
    private val tokens = mutableListOf<Token>()

    /** The brackets and braces open around the current place: `[` for arrays, `{` for tables. */
    private val open = ArrayDeque<Char>()

    /** Whether a key is what comes next: at the start of a line, in `{ }`, and past a `.`. */
    private var expectKey = true

    fun run(): List<Token> {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            i = when {
                c == '\n' -> {
                    if (open.isEmpty()) expectKey = true
                    i + 1
                }

                c == ' ' || c == '\t' || c == '\r' -> i + 1

                c == '#' -> add(i, text.lineEnd(i), TokenKind.COMMENT)

                c == '[' && expectKey && open.isEmpty() -> {
                    // A [table] or [[array of tables]] header, up to its closing bracket.
                    val close = text.indexOf(']', i).takeIf { it in i until text.lineEnd(i) }
                    val end = close?.let { if (text.startsWith("]]", it)) it + 2 else it + 1 }
                        ?: text.lineEnd(i)
                    add(i, end, TokenKind.SECTION)
                }

                c == '"' || c == '\'' -> {
                    val quote = if (text.startsWith("$c$c$c", i)) "$c$c$c" else "$c"
                    val end = text.stringEnd(i, quote, escapes = c == '"')
                    add(i, end, if (expectKey) TokenKind.KEY else TokenKind.STRING)
                }

                c == '=' -> {
                    expectKey = false
                    i + 1
                }

                c == '.' && expectKey -> i + 1

                c == '[' || c == '{' -> {
                    open.addLast(c)
                    expectKey = c == '{'
                    i + 1
                }

                c == ']' || c == '}' -> {
                    open.removeLastOrNull()
                    i + 1
                }

                c == ',' -> {
                    expectKey = open.lastOrNull() == '{'
                    i + 1
                }

                expectKey && isBare(c) -> add(i, scan(i, ::isBare), TokenKind.KEY)

                isValue(c) -> {
                    val end = scan(i, ::isValue)
                    val word = text.substring(i, end)
                    when {
                        word in TOML_WORDS -> add(i, end, TokenKind.KEYWORD)

                        word.first().isDigit() || word.first() in "+-" -> add(
                            i,
                            end,
                            TokenKind.NUMBER
                        )

                        else -> end
                    }
                }

                else -> i + 1
            }
        }
        return tokens
    }

    private fun add(start: Int, end: Int, kind: TokenKind): Int {
        tokens += Token(start, end, kind)
        return end
    }

    private fun scan(from: Int, part: (Char) -> Boolean): Int {
        var i = from + 1
        while (i < text.length && part(text[i])) i++
        return i
    }

    private fun isBare(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'

    /** Numbers, dates, times and words: `0x1F`, `1e-3`, `1979-05-27T07:32:00Z`, `inf`. */
    private fun isValue(c: Char) = c.isLetterOrDigit() || c in "_.:+-"

    companion object {
        val TOML_WORDS = setOf("true", "false", "inf", "nan", "+inf", "-inf", "+nan", "-nan")
    }
}

/** yt-dlp.conf: options, quoted values and `#` comments, split the way a shell would. */
private fun argsTokens(text: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        i = when {
            c.isWhitespace() -> i + 1

            c == '#' -> text.lineEnd(i).also { tokens += Token(i, it, TokenKind.COMMENT) }

            c == '"' || c == '\'' -> text.stringEnd(i, "$c", escapes = c == '"').also {
                tokens += Token(i, it, TokenKind.STRING)
            }

            else -> {
                var end = i
                while (end < text.length && !text[end].isWhitespace() && text[end] !in "\"'") end++
                if (c == '-') {
                    // --option=value: the value is not part of the option.
                    val eq = text.indexOf('=', i).takeIf { it in i until end } ?: end
                    tokens += Token(i, eq, TokenKind.OPTION)
                }
                end
            }
        }
    }
    return tokens
}

/**
 * cookies.txt: `#` comments, and seven tab-separated fields per cookie: domain, subdomains,
 * path, secure, expiry, name, value. `#HttpOnly_` in front of a domain is not a comment.
 */
private fun cookieTokens(text: String): List<Token> {
    val tokens = mutableListOf<Token>()
    lineStarts(text).forEach { start ->
        val end = text.lineEnd(start)
        if (start == end) return@forEach
        if (text[start] == '#' && !text.startsWith("#HttpOnly_", start)) {
            tokens += Token(start, end, TokenKind.COMMENT)
            return@forEach
        }
        var from = start
        for (field in 0 until 7) {
            val to = if (field ==
                6
            ) {
                end
            } else {
                text.indexOf('\t', from).takeIf { it in from until end } ?: end
            }
            val kind = when (field) {
                0, 5 -> TokenKind.KEY
                1, 3 -> TokenKind.KEYWORD
                4 -> TokenKind.NUMBER
                6 -> TokenKind.STRING
                else -> null
            }
            if (kind != null && to > from) tokens += Token(from, to, kind)
            if (to >= end) break
            from = to + 1
        }
    }
    return tokens
}
