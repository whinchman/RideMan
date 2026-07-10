package com.two17industries.rideman.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadResponseParserTest {
    @Test fun activity_id_present_is_success() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":12345,"error":null}""")
        assertEquals(UploadResult.Success(12345), r)
    }

    @Test fun error_null_and_no_activity_is_pending() {
        val r = UploadResponseParser.parse(201, """{"id":99,"activity_id":null,"error":null,"status":"processing"}""")
        assertEquals(UploadResult.Pending(99), r)
    }

    @Test fun duplicate_error_is_duplicate() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":null,"error":"duplicate of activity 777"}""")
        assertTrue(r is UploadResult.Duplicate)
    }

    @Test fun duplicate_error_extracts_activity_id() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":null,"error":"duplicate of activity 777"}""")
        assertEquals(UploadResult.Duplicate(777), r)
    }

    @Test fun duplicate_error_without_digits_has_null_id() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":null,"error":"duplicate activity"}""")
        assertEquals(UploadResult.Duplicate(null), r)
    }

    @Test fun other_error_is_terminal() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":null,"error":"empty file"}""")
        assertTrue(r is UploadResult.Terminal)
        assertEquals("empty file", (r as UploadResult.Terminal).message)
    }

    @Test fun http_5xx_is_retryable() {
        val r = UploadResponseParser.parse(503, "Service Unavailable")
        assertTrue(r is UploadResult.Retryable)
    }

    @Test fun http_401_is_terminal() {
        val r = UploadResponseParser.parse(401, "Unauthorized")
        assertTrue(r is UploadResult.Terminal)
    }
}
