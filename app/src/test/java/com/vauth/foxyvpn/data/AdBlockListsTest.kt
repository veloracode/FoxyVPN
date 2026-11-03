package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Block ads (B2): the generated-asset parser, incl. fail-open cases. */
class AdBlockListsTest {

    @Test
    fun parse_validAsset() {
        val lists = AdBlockLists.parse(
            """
            {
              "domain_suffix": ["doubleclick.net", "ads.example.com"],
              "domain": ["tracker.example.org"],
              "domain_regex": ["^ads[0-9]+\\."],
              "domain_keyword": []
            }
            """.trimIndent(),
        )
        assertNotNull(lists)
        assertEquals(listOf("doubleclick.net", "ads.example.com"), lists!!.domainSuffix)
        assertEquals(listOf("tracker.example.org"), lists.domain)
        assertEquals(listOf("^ads[0-9]+\\."), lists.domainRegex)
        assertEquals(emptyList<String>(), lists.domainKeyword)
    }

    @Test
    fun parse_brokenJson_failsOpen() {
        assertNull(AdBlockLists.parse("not json"))
        assertNull(AdBlockLists.parse(""))
    }

    @Test
    fun parse_allEmpty_isEmpty() {
        val lists = AdBlockLists.parse(
            """{"domain_suffix": [], "domain": [], "domain_regex": [], "domain_keyword": []}""",
        )
        assertNotNull(lists)
        assertEquals(true, lists!!.isEmpty)
    }
}
