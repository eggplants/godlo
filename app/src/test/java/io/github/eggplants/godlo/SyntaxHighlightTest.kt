package io.github.eggplants.godlo

import io.github.eggplants.godlo.core.ConfigFormat
import io.github.eggplants.godlo.ui.settings.TokenKind
import io.github.eggplants.godlo.ui.settings.TokenKind.COMMENT
import io.github.eggplants.godlo.ui.settings.TokenKind.KEY
import io.github.eggplants.godlo.ui.settings.TokenKind.KEYWORD
import io.github.eggplants.godlo.ui.settings.TokenKind.NUMBER
import io.github.eggplants.godlo.ui.settings.TokenKind.OPTION
import io.github.eggplants.godlo.ui.settings.TokenKind.SECTION
import io.github.eggplants.godlo.ui.settings.TokenKind.STRING
import io.github.eggplants.godlo.ui.settings.highlight
import io.github.eggplants.godlo.ui.settings.lineStarts
import org.junit.Assert.assertEquals
import org.junit.Test

class SyntaxHighlightTest {
    /** The tokens of [text] as (what they cover, kind). */
    private fun tokens(text: String, format: ConfigFormat): List<Pair<String, TokenKind>> =
        highlight(text, format).map { text.substring(it.start, it.end) to it.kind }

    @Test
    fun json() {
        assertEquals(
            listOf(
                "\"extractor\"" to KEY,
                "\"skip\"" to KEY,
                "\"abort:3\"" to STRING,
                "\"n\"" to KEY,
                "-1.5e3" to NUMBER,
                "true" to KEYWORD,
                "null" to KEYWORD
            ),
            tokens(
                """{"extractor": {"skip": "abort:3", "n": [-1.5e3, true, null]}}""",
                ConfigFormat.JSON
            )
        )
    }

    @Test
    fun jsonStringWithEscapesAndAnOpenOne() {
        assertEquals(
            listOf("\"a\\\"b\"" to STRING, "\"open" to STRING, "1" to NUMBER),
            tokens("[\"a\\\"b\", \"open\n1]", ConfigFormat.JSON)
        )
    }

    @Test
    fun toml() {
        val text = """
            # comment
            format = "jpg"  # pages
            cbz = false
            when = 1979-05-27T07:32:00Z
            patrol = [
              { url = "https://a", title = 'A' },
            ]

            [site."x.com"]
            username = "me"

            [[works]]
            n = 0x1F
        """.trimIndent()
        assertEquals(
            listOf(
                "# comment" to COMMENT,
                "format" to KEY,
                "\"jpg\"" to STRING,
                "# pages" to COMMENT,
                "cbz" to KEY,
                "false" to KEYWORD,
                "when" to KEY,
                "1979-05-27T07:32:00Z" to NUMBER,
                "patrol" to KEY,
                "url" to KEY,
                "\"https://a\"" to STRING,
                "title" to KEY,
                "'A'" to STRING,
                "[site.\"x.com\"]" to SECTION,
                "username" to KEY,
                "\"me\"" to STRING,
                "[[works]]" to SECTION,
                "n" to KEY,
                "0x1F" to NUMBER
            ),
            tokens(text, ConfigFormat.TOML)
        )
    }

    @Test
    fun tomlDottedKeysAndMultilineStrings() {
        assertEquals(
            listOf(
                "site" to KEY,
                "\"x.com\"" to KEY,
                "user" to KEY,
                "\"\"\"a\nb\"\"\"" to STRING,
                "k" to KEY,
                "1.5" to NUMBER
            ),
            tokens("site.\"x.com\".user = \"\"\"a\nb\"\"\"\nk = 1.5", ConfigFormat.TOML)
        )
    }

    @Test
    fun ytdlpOptions() {
        assertEquals(
            listOf(
                "# subs" to COMMENT,
                "--embed-subs" to OPTION,
                "-f" to OPTION,
                "\"bv*+ba\"" to STRING,
                "--sub-langs" to OPTION
            ),
            tokens("# subs\n--embed-subs\n-f \"bv*+ba\" --sub-langs=ja,en", ConfigFormat.ARGS)
        )
    }

    @Test
    fun cookies() {
        val text = "# Netscape HTTP Cookie File\n" +
            ".example.com\tTRUE\t/\tFALSE\t0\tname\tvalue\n" +
            "#HttpOnly_.x.com\tFALSE\t/\tTRUE\t1700000000\tsid\tabc"
        assertEquals(
            listOf(
                "# Netscape HTTP Cookie File" to COMMENT,
                ".example.com" to KEY,
                "TRUE" to KEYWORD,
                "FALSE" to KEYWORD,
                "0" to NUMBER,
                "name" to KEY,
                "value" to STRING,
                "#HttpOnly_.x.com" to KEY,
                "FALSE" to KEYWORD,
                "TRUE" to KEYWORD,
                "1700000000" to NUMBER,
                "sid" to KEY,
                "abc" to STRING
            ),
            tokens(text, ConfigFormat.COOKIES)
        )
    }

    @Test
    fun lineStartsCountAnEmptyLastLine() {
        assertEquals(listOf(0, 2, 3), lineStarts("a\n\n"))
    }
}
