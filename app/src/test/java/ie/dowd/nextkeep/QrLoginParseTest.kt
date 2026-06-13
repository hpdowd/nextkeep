package ie.dowd.nextkeep

import ie.dowd.nextkeep.qr.NextcloudLoginUri
import ie.dowd.nextkeep.qr.QrLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLoginParseTest {

    @Test
    fun parses_standard_order() {
        val result = NextcloudLoginUri.parse(
            "nc://login/server:https://cloud.example.com&user:alice&password:abcd-efgh-ijkl"
        )
        assertEquals(
            QrLogin("https://cloud.example.com", "alice", "abcd-efgh-ijkl"),
            result,
        )
    }

    @Test
    fun parses_when_fields_are_reordered() {
        // Nextcloud does not guarantee field order.
        val result = NextcloudLoginUri.parse(
            "nc://login/user:bob&password:secret123&server:https://nc.test:8443/cloud"
        )
        assertEquals(
            QrLogin("https://nc.test:8443/cloud", "bob", "secret123"),
            result,
        )
    }

    @Test
    fun preserves_server_url_with_port_and_path() {
        val result = NextcloudLoginUri.parse(
            "nc://login/server:https://example.org:8443/nextcloud&user:u&password:p"
        )
        assertEquals("https://example.org:8443/nextcloud", result?.server)
    }

    @Test
    fun rejects_non_login_uri() {
        assertNull(NextcloudLoginUri.parse("https://cloud.example.com"))
        assertNull(NextcloudLoginUri.parse("just some text"))
        assertNull(NextcloudLoginUri.parse(""))
    }

    @Test
    fun rejects_when_a_field_is_missing() {
        assertNull(NextcloudLoginUri.parse("nc://login/server:https://x&user:alice"))
        assertNull(NextcloudLoginUri.parse("nc://login/user:alice&password:p"))
    }

    @Test
    fun trims_surrounding_whitespace() {
        val result = NextcloudLoginUri.parse(
            "  nc://login/server:https://x.y&user:a&password:b  "
        )
        assertEquals(QrLogin("https://x.y", "a", "b"), result)
    }
}
