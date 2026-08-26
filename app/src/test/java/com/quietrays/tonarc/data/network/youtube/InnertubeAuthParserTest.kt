package com.quietrays.tonarc.data.network.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InnertubeAuthParserTest {

    @Test
    fun `parse empty or blank input returns empty auth`() {
        val res1 = InnertubeAuthParser.parse("")
        assertFalse(res1.isValid)
        assertNull(res1.cookies)
        assertNull(res1.visitorData)

        val res2 = InnertubeAuthParser.parse("   \n\t  ")
        assertFalse(res2.isValid)
    }

    @Test
    fun `parse raw cookie string extracts cookies and sapisid`() {
        val input = "SAPISID=abc123xyz; __Secure-3PAPISID=def456uvw; LOGIN_INFO=some_login_info"
        val parsed = InnertubeAuthParser.parse(input)

        assertTrue(parsed.isValid)
        assertEquals("abc123xyz", parsed.sapisid)
        assertTrue(parsed.cookies!!.contains("SAPISID=abc123xyz"))
        assertTrue(parsed.cookies!!.contains("__Secure-3PAPISID=def456uvw"))
    }

    @Test
    fun `parse cookie header with Cookie prefix`() {
        val input = "Cookie: SAPISID=my_sapisid_token; SID=sid_token"
        val parsed = InnertubeAuthParser.parse(input)

        assertTrue(parsed.isValid)
        assertEquals("my_sapisid_token", parsed.sapisid)
        assertTrue(parsed.cookies!!.contains("SAPISID=my_sapisid_token"))
    }

    @Test
    fun `parse cURL command extracts cookie and visitorData`() {
        val curlCommand = """
            curl 'https://music.youtube.com/youtubei/v1/browse?prettyPrint=false' \
              -H 'accept: */*' \
              -H 'cookie: SAPISID=curl_sapisid; __Secure-3PAPISID=curl_3papisid; HSID=curl_hsid' \
              -H 'x-youtube-visitor-data: Cgt_visitor_data_test_123' \
              -H 'user-agent: Mozilla/5.0'
        """.trimIndent()

        val parsed = InnertubeAuthParser.parse(curlCommand)
        assertTrue(parsed.isValid)
        assertEquals("curl_sapisid", parsed.sapisid)
        assertEquals("Cgt_visitor_data_test_123", parsed.visitorData)
        assertTrue(parsed.cookies!!.contains("SAPISID=curl_sapisid"))
    }

    @Test
    fun `parse multi-line HTTP headers`() {
        val headers = """
            Host: music.youtube.com
            User-Agent: Mozilla/5.0
            Cookie: SAPISID=header_sapisid; __Secure-3PAPISID=header_3papisid
            X-YouTube-Visitor-Data: Cgt_header_visitor_999
            Origin: https://music.youtube.com
        """.trimIndent()

        val parsed = InnertubeAuthParser.parse(headers)
        assertTrue(parsed.isValid)
        assertEquals("header_sapisid", parsed.sapisid)
        assertEquals("Cgt_header_visitor_999", parsed.visitorData)
    }

    @Test
    fun `parse JSON object with cookie and visitorData`() {
        val json = """
            {
                "cookie": "SAPISID=json_sapisid; __Secure-3PAPISID=json_3papisid",
                "visitorData": "Cgt_json_visitor"
            }
        """.trimIndent()

        val parsed = InnertubeAuthParser.parse(json)
        assertTrue(parsed.isValid)
        assertEquals("json_sapisid", parsed.sapisid)
        assertEquals("Cgt_json_visitor", parsed.visitorData)
    }

    @Test
    fun `parse raw token string directly creates SAPISID cookie`() {
        val rawToken = "my_direct_sapisid_token_12345"
        val parsed = InnertubeAuthParser.parse(rawToken)

        assertTrue(parsed.isValid)
        assertEquals("my_direct_sapisid_token_12345", parsed.sapisid)
        assertEquals("SAPISID=my_direct_sapisid_token_12345; __Secure-3PAPISID=my_direct_sapisid_token_12345", parsed.cookies)
    }

    @Test
    fun `parse raw visitorData token`() {
        val visitorToken = "Cgt_only_visitor_token_12345"
        val parsed = InnertubeAuthParser.parse(visitorToken)

        assertTrue(parsed.isValid)
        assertEquals("Cgt_only_visitor_token_12345", parsed.visitorData)
    }

    @Test
    fun `parse tagged INNERTUBE COOKIE and VISITOR DATA format`() {
        val input = """
            ***INNERTUBE COOKIE*** =__Secure-1PSIDTS=sidts-123; HSID=Av582; SAPISID=HUXEMsqUNCiUDLz6/A6X9Dej6nkrabBkwt; __Secure-3PAPISID=HUXEMsqUNCiUDLz6/A6X9Dej6nkrabBkwt; VISITOR_INFO1_LIVE=PCkTUP7lGTI;
            ***VISITOR DATA*** =CgtQQ2tUVVA3bEdUSSj24_jOBjIKCgJJThIEGgAgJGLfAgrcAjE3LllUPVM5bXpXTllfdjQtZlZRd3hnbWdlSXZrdXlYU19rRXpYZ2Y1UVhyZHR4MDJuM0REZjBSYTg4UFNTLTVSc1RjaGR3eEx1QWRySnpvTTRsVldrOWVaSkNVVXltZnh4SHFmamhrcEpVYWhHc3ZKM0g0Vl9kZlhXUGFvTWJrUGd0SXRwM0g4N0RuQTVGX0NnTEZnU1VyZkdyZG1mMUxjbnBFcEJVeGRScjJqd1c3M2swZzJBZDZPTnd1R04wMGxuSXA4bXNwVkN0RGl5emxLVEx0OFQwRm5RQ0dVTEwwc2w5RVM0Q3JyV1BGZUVPNzZURGRmOHM0azBCcThONF9taU5zUU9OQVN6di1WcDNkSy1pMjdPbWZPVllJR2hEZlFIRFNod3NGYjZrWEx1X0t0TXkwa01nM2hhZGM3VnFodzhILThuOHBpeWFCbG9xM1cxTlEzWC1ZSVZkQQ%3D%3D
            ***DATASYNC ID*** =105811609989903499132
            ***ACCOUNT NAME*** =
            ***ACCOUNT EMAIL*** =
            ***ACCOUNT CHANNEL HANDLE*** =
        """.trimIndent()

        val parsed = InnertubeAuthParser.parse(input)
        assertTrue(parsed.isValid)
        assertEquals("HUXEMsqUNCiUDLz6/A6X9Dej6nkrabBkwt", parsed.sapisid)
        assertTrue(parsed.cookies?.contains("SAPISID=HUXEMsqUNCiUDLz6/A6X9Dej6nkrabBkwt") == true)
        assertTrue(parsed.cookies?.contains("__Secure-1PSIDTS=sidts-123") == true)
        assertNotNull(parsed.visitorData)
        assertTrue(parsed.visitorData?.startsWith("CgtQQ2tUVVA3bEdUSS") == true)
        assertTrue(parsed.visitorData?.endsWith("==") == true) // URL decoded %3D%3D -> ==
    }
}
