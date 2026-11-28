package com.orion.voicelab.knowledge

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Pins the TCP connection to an address that ORION has already classified as public while
 * retaining the original hostname for TLS SNI and certificate verification.
 */
internal class PinnedHttpsSocketFactory(
    private val hostname: String,
    private val pinnedAddress: InetAddress,
    private val connectTimeoutMs: Int,
    private val delegate: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(): Socket = secure(connectRaw(443, null, 0), 443)

    override fun createSocket(host: String, port: Int): Socket {
        requireAllowedHost(host)
        return secure(connectRaw(port, null, 0), port)
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        requireAllowedHost(host)
        return secure(connectRaw(port, localHost, localPort), port)
    }

    override fun createSocket(address: InetAddress, port: Int): Socket =
        secure(connectRaw(port, null, 0), port)

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket = secure(connectRaw(port, localAddress, localPort), port)

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        requireAllowedHost(host)
        val remote = socket.remoteSocketAddress as? InetSocketAddress
        if (remote?.address != pinnedAddress) {
            throw IOException("El socket existente no está conectado a la dirección IP fijada.")
        }
        return secure(socket, port, autoClose)
    }

    private fun requireAllowedHost(host: String) {
        if (!hostname.equals(host, ignoreCase = true)) {
            throw IOException("El hostname cambió después de validar DNS.")
        }
    }

    private fun connectRaw(port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        Socket().apply {
            if (localAddress != null || localPort > 0) {
                bind(InetSocketAddress(localAddress, localPort))
            }
            connect(InetSocketAddress(pinnedAddress, port), connectTimeoutMs)
        }

    private fun secure(socket: Socket, port: Int, autoClose: Boolean = true): Socket {
        val ssl = delegate.createSocket(socket, hostname, port, autoClose) as SSLSocket
        val parameters: SSLParameters = ssl.sslParameters
        parameters.endpointIdentificationAlgorithm = "HTTPS"
        if (!isIpLiteral(hostname)) parameters.serverNames = listOf(SNIHostName(hostname))
        ssl.sslParameters = parameters
        return ssl
    }

    private fun isIpLiteral(value: String): Boolean =
        value.contains(':') || value.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}"))
}
