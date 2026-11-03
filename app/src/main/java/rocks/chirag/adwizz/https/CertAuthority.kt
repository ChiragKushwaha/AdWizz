package rocks.chirag.adwizz.https

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * AdWizz's own certificate authority for HTTPS filtering. Its private key is created inside
 * Android's keystore (secure hardware where available) and can't be read out, not even by
 * AdWizz: it only signs. The CA certificate (public) is kept in files/https-ca.der so the user
 * can install it as trusted, and so it stays the same across restarts.
 */
/** Where the proxy gets certificates for sites (AdWizz's CA in the app, a test CA in tests). */
internal interface SiteCerts {
    fun contextFor(host: String): SSLContext
    fun isTrusted(): Boolean
}

internal class CertAuthority(private val context: Context) : SiteCerts {
    private val caFile = File(context.filesDir, "https-ca.der")
    /** One key pair for every site certificate, in memory only, new each time the VPN starts. */
    private val siteKeys: KeyPair = ec()
    private val contexts = object : LinkedHashMap<String, SSLContext>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SSLContext>?) = size > 256
    }

    /** The CA certificate, creating the CA the first time. */
    @get:Synchronized val certificate: X509Certificate by lazy { loadOrCreate() }

    /** TLS server context presenting a certificate for [host], signed by our CA. */
    @Synchronized override fun contextFor(host: String): SSLContext = contexts.getOrPut(host) {
        val leaf = CertBuilder.leaf(host, siteKeys.public, certificate, signer())
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("site", siteKeys.private, PASSWORD, arrayOf(leaf, certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, PASSWORD) }
        SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
    }

    /** True once the user has installed our CA as trusted (it then appears in Android's user CA store). */
    override fun isTrusted(): Boolean = runCatching {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val mine = certificate.encoded
        store.aliases().toList().any { it.startsWith("user:") && store.getCertificate(it)?.encoded?.contentEquals(mine) == true }
    }.getOrDefault(false)

    private fun loadOrCreate(): X509Certificate {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS) && caFile.exists()) {
            runCatching { return parse(caFile.readBytes()) }
        }
        // First use (or the files were cleared): a fresh CA. Any old one must be reinstalled.
        ks.deleteEntry(ALIAS)
        val pub = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
        }.generateKeyPair().public
        val cert = CertBuilder.ca(CA_NAME, pub, signer())
        caFile.writeBytes(cert.encoded)
        return cert
    }

    /** Deletes the CA (the "Reset certificate" button). The user should remove the old one from Settings too. */
    @Synchronized fun reset() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
        caFile.delete()
        contexts.clear()
    }

    private fun signer() = Signer { data ->
        val key = KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(ALIAS, null) as PrivateKey
        Signature.getInstance("SHA256withECDSA").run { initSign(key); update(data); sign() }
    }

    companion object {
        const val CA_NAME = "AdWizz HTTPS filter"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "adwizz_https_ca"
        private val PASSWORD = CharArray(0)

        private fun ec() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        private fun parse(der: ByteArray) =
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate

        /** PEM text of a certificate, the format Android's "Install certificate" accepts. */
        fun pem(cert: X509Certificate): String =
            "-----BEGIN CERTIFICATE-----\n" +
                android.util.Base64.encodeToString(cert.encoded, android.util.Base64.NO_WRAP).chunked(64).joinToString("\n") +
                "\n-----END CERTIFICATE-----\n"
    }
}
