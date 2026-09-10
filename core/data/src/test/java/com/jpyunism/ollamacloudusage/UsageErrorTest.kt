package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageErrorTest {

    @Test
    fun `mapea CookieExpiredException a CookieExpired`() {
        assertEquals(UsageError.CookieExpired, UsageError.fromThrowable(CookieExpiredException()))
    }

    @Test
    fun `mapea InvalidApiKeyException a InvalidApiKey`() {
        assertEquals(UsageError.InvalidApiKey, UsageError.fromThrowable(InvalidApiKeyException()))
    }

    @Test
    fun `mapea cualquier otro throwable a Network con su mensaje`() {
        assertEquals(UsageError.Network("timeout"), UsageError.fromThrowable(RuntimeException("timeout")))
    }

    @Test
    fun `mapea throwable sin mensaje a Network con mensaje generico`() {
        assertEquals(UsageError.Network(""), UsageError.fromThrowable(RuntimeException()))
    }
}
