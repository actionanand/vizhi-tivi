package com.actionanand.vizhitivi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDisplayTest {
    @Test fun readableNamesReplaceIsoCodes() {
        assertEquals("Tamil", displayLanguage("ta"))
        assertEquals("English", displayLanguage("en"))
        assertEquals("Hindi", displayLanguage("hi"))
        assertEquals("Malayalam", displayLanguage("ml"))
        assertEquals("Telugu", displayLanguage("te"))
        assertEquals("Kannada", displayLanguage("kn"))
        assertEquals("Sinhala", displayLanguage("si"))
    }
    @Test fun unknownCodesGetReadableFallback() {
        assertEquals("Unknown language", displayLanguage("und"))
        assertEquals("Unknown language", displayLanguage(""))
    }
    @Test fun respectsTamilUiLanguage() {
        assertEquals("தமிழ்", displayLanguage("ta", "ta"))
    }
}
