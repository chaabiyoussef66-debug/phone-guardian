package com.phoneguardian.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkQueryInspectorTest {
    private val redirectKeys = setOf("redirect", "redirect_url", "return", "returnurl",
        "next", "continue", "destination", "dest", "target", "url")

    @Test
    fun ordinaryDoubleEncodedTextDoesNotTriggerRedirectEncodingWarning() {
        val result = LinkQueryInspector.inspect("note=hello%2520world", redirectKeys)

        assertFalse(result.hasNestedRedirectEncoding)
        assertTrue(result.hosts.isEmpty())
    }

    @Test
    fun doubleEncodedRedirectIsDetectedAndHostExtracted() {
        val result = LinkQueryInspector.inspect(
            "url=https%253A%252F%252Fevil.example%252Flogin",
            redirectKeys
        )

        assertTrue(result.hasNestedRedirectEncoding)
        assertEquals(setOf("evil.example"), result.hosts)
    }

    @Test
    fun singlyEncodedRedirectDoesNotCountAsNestedEncoding() {
        val result = LinkQueryInspector.inspect(
            "url=https%3A%2F%2Fevil.example%2Flogin",
            redirectKeys
        )

        assertFalse(result.hasNestedRedirectEncoding)
        assertEquals(setOf("evil.example"), result.hosts)
    }

    @Test
    fun embeddedUrlsInJsonLikeValuesAreExtracted() {
        val result = LinkQueryInspector.inspect(
            "data=%7B%22redirect%22%3A%22https%3A%2F%2Fevil.example%2Flogin%22%7D",
            redirectKeys
        )

        assertEquals(setOf("evil.example"), result.hosts)
    }

    @Test
    fun duplicateHostsAreDeduplicatedAndMultipleHostsArePreserved() {
        val result = LinkQueryInspector.inspect(
            "url=https%3A%2F%2Fevil.example%2Flogin&next=https%3A%2F%2Fevil.example%2Fverify&return=https%3A%2F%2Fother.example%2F",
            redirectKeys
        )

        assertEquals(setOf("evil.example", "other.example"), result.hosts)
    }

    @Test
    fun plusSignIsPreservedDuringQueryDecoding() {
        val result = LinkQueryInspector.inspect(
            "url=https%3A%2F%2Fevil.example%2Fa+b",
            redirectKeys
        )

        assertEquals(setOf("evil.example"), result.hosts)
    }
}
