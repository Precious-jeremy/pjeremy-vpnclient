package com.example.vpnclient

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import javax.net.SocketFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Step 3a: real SSH connection test, wrapped inside an SSL/stunnel socket.
 *
 * This is NOT a full system-wide VPN yet — it proves the server details
 * actually work (real auth success/failure) before we add the harder part:
 * routing all device traffic through the tunnel (needs a tun2socks-style
 * packet router, added in a later step).
 *
 * Connects via SSL first (the stunnel wrapper most free SSH providers use
 * to disguise SSH traffic as HTTPS on port 443), then performs SSH
 * authentication inside that encrypted socket.
 */
data class TunnelConfig(
    val host: String,
    val sslPort: Int,
    val username: String,
    val password: String,
    // The hostname sent in the TLS handshake's SNI field. Usually this
    // matches `host`, but "bug host" SNI spoofing (common with these free
    // SSH providers) sends a different, carrier-zero-rated domain here
    // while the real encrypted traffic still goes to `host`/`sslPort`.
    // Defaults to `host` when no override is given.
    val sniHostname: String? = null
)

/**
 * sshj creates sockets via the no-arg SocketFactory.createSocket() and
 * connects them separately, which means Android's default SSL socket
 * never learns the target hostname and so never sends SNI (Server Name
 * Indication) during the TLS handshake. Many stunnel setups route
 * connections based on SNI, so without it the server can't tell which
 * backend to hand us off to and just closes the connection. This factory
 * sets SNI explicitly before the handshake happens.
 */
private class SniSocketFactory(
    private val delegate: SocketFactory,
    private val sniHostname: String
) : SocketFactory() {

    override fun createSocket(): Socket {
        val socket = delegate.createSocket()
        if (socket is SSLSocket) {
            val params = socket.sslParameters
            params.serverNames = listOf(SNIHostName(sniHostname))
            socket.sslParameters = params
        }
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket =
        throw UnsupportedOperationException("not used by sshj")

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        throw UnsupportedOperationException("not used by sshj")

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        throw UnsupportedOperationException("not used by sshj")

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        throw UnsupportedOperationException("not used by sshj")
}

object TunnelManager {

    init {
        // sshj needs modern key-exchange algorithms (e.g. X25519) that
        // Android's built-in crypto providers don't supply. BouncyCastle is
        // bundled as a dependency but isn't registered with the JVM security
        // system just by being on the classpath — we have to add it
        // ourselves, once, before any SSH connection is attempted.
        // Appended (not inserted at top) so Android's own providers (used
        // for the SSL/stunnel socket) stay preferred for everything else.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Volatile
    private var client: SSHClient? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    /** Runs on a background thread; invokes [onResult] with (success, message). */
    fun connect(config: TunnelConfig, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                // Many free SSH providers use self-signed certs on their
                // stunnel port, so we trust-all here. This is acceptable for
                // a personal relay you chose yourself, same approach other
                // SSH-tunnel apps take — but it does mean we're not
                // verifying the server's identity, only encrypting the link.
                val trustAllCerts = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())

                val sshClient = SSHClient()
                sshClient.addHostKeyVerifier(PromiscuousVerifier())
                sshClient.connectTimeout = 15000
                sshClient.socketFactory = SniSocketFactory(
                    sslContext.socketFactory,
                    config.sniHostname ?: config.host
                )

                sshClient.connect(config.host, config.sslPort)
                sshClient.authPassword(config.username, config.password)

                if (sshClient.isAuthenticated) {
                    client = sshClient
                    isConnected = true
                    onResult(true, "Connected")
                } else {
                    sshClient.disconnect()
                    isConnected = false
                    onResult(false, "Authentication failed")
                }
            } catch (e: Exception) {
                isConnected = false
                onResult(false, e.message ?: "Connection failed")
            }
        }.start()
    }

    /** Runs on a background thread; invokes [onDone] when fully torn down. */
    fun disconnect(onDone: () -> Unit) {
        Thread {
            try {
                client?.disconnect()
            } catch (_: Exception) {
                // already gone, nothing to do
            }
            client = null
            isConnected = false
            onDone()
        }.start()
    }
}
