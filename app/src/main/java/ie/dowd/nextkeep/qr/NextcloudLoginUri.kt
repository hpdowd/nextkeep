package ie.dowd.nextkeep.qr

/** Credentials decoded from a Nextcloud login QR code / Login Flow v2 redirect. */
data class QrLogin(
    val server: String,
    val user: String,
    val password: String,
)

/**
 * Parses the `nc://login/...` URI that Nextcloud encodes in its login QR codes
 * and the Login Flow v2 redirect, e.g.
 *
 *     nc://login/server:https://cloud.example.com&user:alice&password:abcd-efgh
 *
 * Field order is not guaranteed by Nextcloud, and the server value itself
 * contains `:` and `/`, so each field is read up to the next known `&key:`
 * marker rather than by naive splitting on `:` or `&`.
 */
object NextcloudLoginUri {

    private const val PREFIX = "nc://login/"
    private val KEYS = listOf("server", "user", "password")

    fun parse(raw: String): QrLogin? {
        val payload = raw.trim()
        if (!payload.startsWith(PREFIX, ignoreCase = true)) return null
        val body = payload.substring(PREFIX.length)

        val server = field("server", body) ?: return null
        val user = field("user", body) ?: return null
        val password = field("password", body) ?: return null
        return QrLogin(server = server, user = user, password = password)
    }

    private fun field(name: String, body: String): String? {
        // Value runs from "<name>:" until the next "&server:|&user:|&password:"
        // marker or the end of the string.
        val others = KEYS.joinToString("|")
        val regex = Regex("(?:^|&)" + name + ":(.*?)(?=&(?:" + others + "):|\$)")
        return regex.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
