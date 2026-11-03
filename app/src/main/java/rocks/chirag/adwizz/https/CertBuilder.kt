package rocks.chirag.adwizz.https

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/** Signs with the CA's private key (in the phone's secure hardware in the app; a plain key in tests). */
internal fun interface Signer { fun sign(data: ByteArray): ByteArray }

/**
 * Builds X.509 v3 certificates signed with ECDSA P-256 / SHA-256: AdWizz's own CA, and one short
 * certificate per website it filters. Browsers require subjectAltName and serverAuth on the
 * latter, and CA + keyCertSign on the former.
 */
internal object CertBuilder {
    private const val ECDSA_SHA256 = "1.2.840.10045.4.3.2"
    private const val DAY = 24 * 60 * 60 * 1000L

    fun ca(name: String, key: PublicKey, signer: Signer, now: Long = System.currentTimeMillis()): X509Certificate =
        build(name(name), name(name), key, key, signer, now - DAY, now + 3650 * DAY, listOf(
            ext("2.5.29.19", true, Der.seq(Der.bool(true))),                          // basicConstraints: CA
            ext("2.5.29.15", true, Der.bits(byteArrayOf(0x86.toByte()), 1)),         // digitalSignature, keyCertSign, cRLSign
            ext("2.5.29.14", false, Der.octets(keyId(key))),
        ))

    fun leaf(host: String, key: PublicKey, ca: X509Certificate, signer: Signer, now: Long = System.currentTimeMillis()): X509Certificate =
        build(name(host), ca.subjectX500Principal.encoded, key, ca.publicKey, signer, now - DAY, now + 365 * DAY, listOf(
            ext("2.5.29.19", true, Der.seq()),                                        // not a CA
            ext("2.5.29.15", true, Der.bits(byteArrayOf(0x80.toByte()), 7)),         // digitalSignature
            ext("2.5.29.37", false, Der.seq(Der.oid("1.3.6.1.5.5.7.3.1"))),          // serverAuth
            ext("2.5.29.17", false, Der.seq(Der.implicit(2, host.toByteArray(Charsets.US_ASCII)))),
            ext("2.5.29.14", false, Der.octets(keyId(key))),
            ext("2.5.29.35", false, Der.seq(Der.implicit(0, keyId(ca.publicKey)))),
        ))

    private fun build(
        subject: ByteArray, issuer: ByteArray, key: PublicKey, issuerKey: PublicKey, signer: Signer,
        from: Long, to: Long, extensions: List<ByteArray>,
    ): X509Certificate {
        val alg = Der.seq(Der.oid(ECDSA_SHA256))
        val tbs = Der.seq(
            Der.explicit(0, Der.int(2)),                                       // v3
            Der.int(BigInteger(127, SecureRandom()).add(BigInteger.ONE)),       // positive serial
            alg, issuer,
            Der.seq(Der.time(Date(from)), Der.time(Date(to))),
            subject,
            key.encoded,                                                       // SubjectPublicKeyInfo
            Der.explicit(3, Der.seq(*extensions.toTypedArray())),
        )
        val der = Der.seq(tbs, alg, Der.bits(signer.sign(tbs)))
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        cert.verify(issuerKey)   // never hand out a certificate we couldn't verify ourselves
        return cert
    }

    private fun name(cn: String) = Der.seq(
        Der.set(Der.seq(Der.oid("2.5.4.3"), Der.utf8(cn))),        // CN
        Der.set(Der.seq(Der.oid("2.5.4.10"), Der.utf8("AdWizz"))), // O
    )

    private fun ext(oid: String, critical: Boolean, value: ByteArray) =
        if (critical) Der.seq(Der.oid(oid), Der.bool(true), Der.octets(value)) else Der.seq(Der.oid(oid), Der.octets(value))

    /** SHA-1 of the public key, the usual key identifier. */
    private fun keyId(key: PublicKey) = MessageDigest.getInstance("SHA-1").digest(key.encoded)
}
