package rocks.chirag.adwizz.https

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Our own certificates: a CA browsers accept as a root, and per-site certificates it signs. */
class CertBuilderTest {
    private fun ec() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val caKeys = ec()
    private val signer = Signer { data -> Signature.getInstance("SHA256withECDSA").run { initSign(caKeys.private); update(data); sign() } }
    private val ca = CertBuilder.ca("AdWizz HTTPS filter", caKeys.public, signer)

    @Test fun caIsASelfSignedCertificateAuthority() {
        ca.verify(caKeys.public)
        assertTrue(ca.basicConstraints >= 0)                 // CA:true
        assertTrue(ca.keyUsage[5])                           // keyCertSign
        assertEquals(ca.subjectX500Principal, ca.issuerX500Principal)
        assertTrue(ca.subjectX500Principal.name.contains("CN=AdWizz HTTPS filter"))
        assertTrue(ca.notAfter.time - ca.notBefore.time > 3000L * 24 * 3600 * 1000)
    }

    @Test fun siteCertificateIsSignedByTheCaAndNamesTheSite() {
        val leaf = CertBuilder.leaf("www.youtube.com", ec().public, ca, signer)
        leaf.verify(caKeys.public)
        assertEquals(-1, leaf.basicConstraints)              // not a CA
        assertEquals(ca.subjectX500Principal, leaf.issuerX500Principal)
        assertEquals(listOf("www.youtube.com"), leaf.subjectAlternativeNames.map { it[1] })
        assertTrue("1.3.6.1.5.5.7.3.1" in leaf.extendedKeyUsage)
        assertTrue(leaf.keyUsage[0])                         // digitalSignature
        leaf.checkValidity()
    }

    @Test fun derLengthsAndOids() {
        assertArrayEquals(byteArrayOf(0x06, 0x03, 0x55, 0x1D, 0x11), Der.oid("2.5.29.17"))
        assertEquals(0x82.toByte(), Der.octets(ByteArray(300))[1])   // long-form length
        assertEquals(300 + 4, Der.octets(ByteArray(300)).size)
    }
}
