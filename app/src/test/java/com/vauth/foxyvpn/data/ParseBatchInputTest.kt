package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The batch-import line parser, including the fix-round-1 separator
 * cases: "----", "," and ";" are separators in their own right, and a
 * password keeps the rest of the line (it may contain ":" or ",").
 */
class ParseBatchInputTest {

    private fun parse(text: String) = FxaAuthRepository.parseBatchInput(text)

    @Test
    fun colonSeparated() {
        val items = parse("user@example.com:secret")
        assertEquals(1, items.size)
        assertEquals("user@example.com", items[0].email)
        assertEquals("secret", items[0].password)
    }

    @Test
    fun dashSeparated() {
        val items = parse("user@example.com----secret")
        assertEquals(1, items.size)
        assertEquals("user@example.com", items[0].email)
        assertEquals("secret", items[0].password)
    }

    @Test
    fun commaSeparatedPasswordKeepsColons() {
        val items = parse("user@example.com,pass:with:colons")
        assertEquals(1, items.size)
        assertEquals("user@example.com", items[0].email)
        assertEquals("pass:with:colons", items[0].password)
    }

    @Test
    fun colonSeparatedPasswordKeepsCommas() {
        val items = parse("user@example.com:pass,word")
        assertEquals(1, items.size)
        assertEquals("pass,word", items[0].password)
    }

    @Test
    fun semicolonAndPipeAndWhitespaceSeparators() {
        assertEquals("pw1", parse("a@b.co;pw1")[0].password)
        assertEquals("pw2", parse("a@b.co|pw2")[0].password)
        assertEquals("pw3", parse("a@b.co pw3")[0].password)
    }

    @Test
    fun dashSeparatedPasswordKeepsColons() {
        assertEquals("pa:ss", parse("a@b.co----pa:ss")[0].password)
    }

    @Test
    fun emailOnlyLine() {
        val items = parse("only@example.com")
        assertEquals(1, items.size)
        assertNull(items[0].password)
    }

    @Test
    fun jsonObjectLine() {
        val items = parse("""{"email":"j@example.com","password":"jsonpw"}""")
        assertEquals(1, items.size)
        assertEquals("j@example.com", items[0].email)
        assertEquals("jsonpw", items[0].password)
    }

    @Test
    fun jsonArrayWithTokenKeys() {
        val items = parse(
            """[{"email":"t@example.com","access_token":"AT","refresh_token":"RT"},""" +
                """{"email":"u@example.com","password":"pw"}]"""
        )
        assertEquals(2, items.size)
        assertEquals("AT", items[0].accessToken)
        assertEquals("RT", items[0].refreshToken)
        assertEquals("pw", items[1].password)
    }

    @Test
    fun blankLinesAndGarbageSkipped() {
        val items = parse("\n  \nnot-an-email:secret\na@b.co:pw\n")
        assertEquals(1, items.size)
        assertEquals("a@b.co", items[0].email)
    }

    @Test
    fun emptyInput() {
        assertEquals(0, parse("   ").size)
    }
}
