package rocks.chirag.adwizz.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Banking, UPI and payment apps must work flawlessly. Many of them refuse to run (or fail payments)
 * when they see a VPN, a system proxy (HTTPS filtering) or a blocked fraud-check server. So, when
 * "Keep banking & payment apps working" is on:
 *  - every finance app found on the phone skips AdWizz completely (no VPN, no proxy, no filtering);
 *  - [ESSENTIAL_DOMAINS] are never blocked, so checkout inside other apps (food, shopping, travel)
 *    still reaches its payment gateway, 3-D Secure page and fraud check.
 * Finance apps carry no ads, so nothing is lost by leaving them alone.
 */
object FinanceApps {
    /** Well-known finance apps whose package names don't give them away. */
    val KNOWN = setOf(
        // India: UPI, wallets, banks
        "com.google.android.apps.nbu.paisa.user",   // Google Pay
        "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp", "com.dreamplug.androidapp",
        "com.mobikwik_new", "com.freecharge.android", "com.enstage.wibmo.hdfc",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.sbi.upi", "com.snapwork.hdfc",
        "com.csam.icici.bank.imobile", "com.axis.mobile", "com.msf.kbank.mobile", "com.fampay.in",
        "money.super.payments",
        // Global
        "com.google.android.apps.walletnfcrel", "com.samsung.android.spay", "com.samsung.android.samsungpay.gear",
        "com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash", "com.chase.sig.android",
        "com.wf.wellsfargomobile", "com.infonow.bofa", "com.konylabs.capitalone", "com.citi.citimobile",
        "com.usaa.mobile.android.usaa", "com.revolut.revolut", "com.transferwise.android",
        "de.number26.android", "co.uk.getmondo", "com.starlingbank.android", "com.klarna.android",
        "com.oplus.pay", "com.coloros.wallet", "com.mipay.wallet", "com.huawei.wallet",
    )

    /** Package-name parts that mark a finance app ("com.examplebank.mobile", "in.xyz.upi", "com.foopay"). */
    private val NAME_PARTS = listOf("bank", "banking", "upi", "netbanking", "imobile", "yono", "wallet")

    /** True if [pkg] looks like a banking / payment app from its name alone. */
    fun looksLikeFinance(pkg: String): Boolean {
        if (pkg in KNOWN) return true
        val parts = pkg.lowercase().split('.', '_')
        return parts.any { p ->
            NAME_PARTS.any { p.contains(it) } ||
                // "paytm", "payzapp", "paypal", "amazonpay", "samsungpay" — but not "display"/"replay"
                p == "pay" || p.startsWith("pay") || (p.endsWith("pay") && !p.endsWith("play"))
        }
    }

    /**
     * Finance apps installed on this phone: [KNOWN] or named like one, plus every app that offers
     * tap-to-pay (a payment card-emulation service). Never returns our own package.
     */
    fun installed(context: Context): Set<String> {
        val pm = context.packageManager
        val own = context.packageName
        val byName = runCatching { pm.getInstalledApplications(0).map { it.packageName } }.getOrDefault(emptyList())
            .filter { looksLikeFinance(it) }
        val tapToPay = runCatching {
            pm.queryIntentServices(Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE"), PackageManager.GET_META_DATA)
                .map { it.serviceInfo.packageName }
        }.getOrDefault(emptyList())
        return (byName + tapToPay)
            .filter { it != own && !it.startsWith("rocks.chirag.adwizz") && !isCoreSystem(it) }.toSet()
    }

    /**
     * Core system packages that also offer tap-to-pay (Play services hosts Google Wallet's card,
     * System UI a wallet shortcut). Excluding them would let all of Google's own tracking through,
     * and tap-to-pay itself works over NFC, not through our DNS.
     */
    internal fun isCoreSystem(pkg: String) =
        pkg == "com.google.android.gms" || pkg == "android" || pkg.startsWith("com.android.systemui")

    /**
     * Payment gateways, card checks (3-D Secure), UPI and the fraud / bot checks payments depend
     * on. Tracker lists often carry the fraud checks (they fingerprint the device), and a blocked
     * one makes the payment fail with a vague "something went wrong".
     */
    val ESSENTIAL_DOMAINS = listOf(
        // Indian gateways, UPI and card networks
        "razorpay.com", "razorpay.in", "rzp.io", "juspay.in", "juspay.net", "payu.in", "payumoney.com", "payubiz.in",
        "cashfree.com", "cashfree.in", "ccavenue.com", "billdesk.com", "billdesk.io", "paytm.com", "paytm.in",
        "paytmpayments.com", "phonepe.com", "npci.org.in", "rupay.co.in", "easebuzz.in", "instamojo.com",
        "wibmo.com", "enstage-sas.com", "enstage.com", "acs.icicibank.com",
        // Global gateways and wallets
        "stripe.com", "stripe.network", "stripecdn.com", "paypal.com", "paypalobjects.com", "braintreegateway.com",
        "braintree-api.com", "adyen.com", "adyenpayments.com", "checkout.com", "squareup.com", "klarna.com",
        "affirm.com", "afterpay.com", "pay.google.com", "payments.google.com", "apple-pay-gateway.apple.com",
        // Card 3-D Secure (the "verify it's you" page)
        "cardinalcommerce.com", "songbird.cardinalcommerce.com", "3dsecure.io", "3ds.adyen.com", "arcot.com",
        "visa.com", "mastercard.com", "securecode.com", "americanexpress.com", "netcetera.com", "gpayments.net",
        // Fraud and bot checks used at checkout and log-in
        "online-metrix.net", "threatmetrix.com", "sift.com", "siftscience.com", "forter.com", "riskified.com",
        "signifyd.com", "iovation.com", "iesnare.com", "kount.net", "kount.com", "perimeterx.net", "px-cdn.net",
        "px-cloud.net", "hcaptcha.com", "recaptcha.net", "arkoselabs.com", "funcaptcha.com", "seon.io",
        "bureau.id", "nudatasecurity.com", "biocatch.com", "callsign.com",
    )
}
