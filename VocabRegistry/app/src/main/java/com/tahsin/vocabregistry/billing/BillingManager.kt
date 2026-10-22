package com.tahsin.vocabregistry.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * One-time, non-consumable "Pro" unlock via Google Play Billing.
 *
 * Product id: [PRO_PRODUCT] — create an in-app product with EXACTLY this id in the
 * Play Console (Monetise → Products → In-app products) once the developer account exists.
 * Until then every call fails gracefully: the button reports that the purchase is
 * unavailable and nothing crashes.
 *
 * [onProUnlocked] is invoked (possibly repeatedly — it must be idempotent) whenever a
 * valid purchase is found, whether from a fresh buy or from restore-on-launch.
 */
class BillingManager(
    private val ctx: Context,
    private val onProUnlocked: () -> Unit,
) : PurchasesUpdatedListener {

    companion object { const val PRO_PRODUCT = "pro_unlock" }

    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    /** UI hook: called with a short human-readable status after a buy attempt fails. */
    var onStatus: (String) -> Unit = {}

    fun start() {
        runCatching {
            val c = BillingClient.newBuilder(ctx)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
                )
                .build()
            client = c
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        restore()
                        fetchDetails()
                    }
                }
                override fun onBillingServiceDisconnected() { /* retried on next start() */ }
            })
        }
    }

    /** Re-grants Pro on reinstall / new device. */
    private fun restore() {
        val c = client ?: return
        runCatching {
            c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP).build(),
            ) { _, purchases -> purchases.forEach { handle(it) } }
        }
    }

    private fun fetchDetails() {
        val c = client ?: return
        runCatching {
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
            c.queryProductDetailsAsync(params) { result, list ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    details = list.firstOrNull()
                }
            }
        }
    }

    /** Launches the purchase sheet, or reports why it can't. */
    fun buy(activity: Activity) {
        val c = client
        val d = details
        if (c == null || !c.isReady) { onStatus("Store connection unavailable \u2014 try again shortly."); start(); return }
        if (d == null) { onStatus("Pro isn\u2019t available for purchase yet."); fetchDetails(); return }
        runCatching {
            val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d).build(),
                ),
            ).build()
            c.launchBillingFlow(activity, flow)
        }.onFailure { onStatus("Could not open the purchase screen.") }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { handle(it) }
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restore()
        }
    }

    private fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (p.products.none { it == PRO_PRODUCT }) return
        onProUnlocked()
        if (!p.isAcknowledged) {
            val c = client ?: return
            runCatching {
                c.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken).build(),
                ) { }
            }
        }
    }
}
