package com.example.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

data class SampleReceiptItem(
    val id: String,
    val provider: String,
    val amount: Double,
    val currency: String,
    val senderName: String,
    val senderPhone: String,
    val receiverName: String,
    val receiverPhone: String,
    val txnId: String,
    val dateStr: String,
    val primaryColor: Int,
    val isCash: Boolean = false
)

object SampleReceipts {
    fun getPaymentMethods(contributionAmount: Double, currency: String): List<SampleReceiptItem> {
        val amount = if (contributionAmount > 0) contributionAmount else 50000.0
        val curr = if (currency.isNotBlank()) currency else "XAF"
        return listOf(
            SampleReceiptItem(
                id = "orange_money",
                provider = "Orange Money",
                amount = amount,
                currency = curr,
                senderName = "Membre Cotisant",
                senderPhone = "+237 694 22 18 90",
                receiverName = "Bénéficiaire du Tour",
                receiverPhone = "+237 677 31 09 45",
                txnId = "OM.261024." + (1000..9999).random(),
                dateStr = "Aujourd'hui à 11:45",
                primaryColor = Color.rgb(255, 102, 0),
                isCash = false
            ),
            SampleReceiptItem(
                id = "mtn_momo",
                provider = "MTN MoMo",
                amount = amount,
                currency = curr,
                senderName = "Membre Cotisant",
                senderPhone = "+237 670 11 22 33",
                receiverName = "Bénéficiaire du Tour",
                receiverPhone = "+237 677 31 09 45",
                txnId = "MP261024." + (1000..9999).random() + ".K8",
                dateStr = "Aujourd'hui à 10:20",
                primaryColor = Color.rgb(255, 204, 0),
                isCash = false
            ),
            SampleReceiptItem(
                id = "cash",
                provider = "Cash",
                amount = amount,
                currency = curr,
                senderName = "Membre Cotisant",
                senderPhone = "",
                receiverName = "Bénéficiaire du Tour",
                receiverPhone = "",
                txnId = "CASH-" + System.currentTimeMillis().toString().takeLast(6),
                dateStr = "Remise en main propre",
                primaryColor = Color.rgb(16, 185, 129), // Emerald Green
                isCash = true
            )
        )
    }

    // Default static samples for backward compatibility
    val samples = getPaymentMethods(50000.0, "XAF")

    fun createReceiptBitmap(sample: SampleReceiptItem): Bitmap {
        val width = 720
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark modern background
        canvas.drawColor(Color.rgb(17, 24, 39))

        val paint = Paint().apply { isAntiAlias = true }

        // Top banner header with provider brand color
        paint.color = sample.primaryColor
        canvas.drawRect(0f, 0f, width.toFloat(), 200f, paint)

        // Provider Name
        paint.color = if (sample.id == "mtn_momo") Color.BLACK else Color.WHITE
        paint.textSize = 42f
        paint.isFakeBoldText = true
        canvas.drawText(sample.provider.uppercase(), 40f, 90f, paint)

        paint.textSize = 28f
        paint.isFakeBoldText = false
        paint.color = if (sample.id == "mtn_momo") Color.rgb(30, 30, 30) else Color.rgb(240, 240, 240)
        canvas.drawText(
            if (sample.isCash) "REÇU DE REMISE ESPÈCES (CASH)" else "REÇU OFFICIEL DE TRANSFERT",
            40f,
            145f,
            paint
        )

        // Receipt Card Container
        paint.color = Color.rgb(31, 41, 55)
        val cardRect = RectF(40f, 240f, width - 40f, height - 60f)
        canvas.drawRoundRect(cardRect, 24f, 24f, paint)

        // Amount Header inside card
        paint.color = Color.rgb(156, 163, 175)
        paint.textSize = 26f
        canvas.drawText(if (sample.isCash) "MONTANT VERSÉ EN CASH" else "MONTANT ENVOYÉ", 80f, 310f, paint)

        paint.color = Color.rgb(16, 185, 129) // Emerald
        paint.textSize = 64f
        paint.isFakeBoldText = true
        val formattedAmount = "%,.0f".format(sample.amount).replace(',', ' ')
        canvas.drawText("$formattedAmount ${sample.currency}", 80f, 385f, paint)

        // Status pill
        paint.color = Color.argb(40, 16, 185, 129)
        canvas.drawRoundRect(RectF(80f, 415f, 380f, 465f), 16f, 16f, paint)
        paint.color = Color.rgb(16, 185, 129)
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("✓ PAIEMENT CONFIRMÉ", 96f, 450f, paint)

        // Divider
        paint.color = Color.rgb(55, 65, 81)
        paint.strokeWidth = 2f
        canvas.drawLine(80f, 495f, width - 80f, 495f, paint)

        // Transaction Details
        fun drawRow(label: String, value: String, y: Float, isBold: Boolean = false) {
            paint.color = Color.rgb(156, 163, 175)
            paint.textSize = 26f
            paint.isFakeBoldText = false
            canvas.drawText(label, 80f, y, paint)

            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.isFakeBoldText = isBold
            canvas.drawText(value, 80f, y + 40f, paint)
        }

        drawRow("ID TRANSACTION / RÉF", sample.txnId, 560f, true)
        drawRow("DATE & HEURE", sample.dateStr, 660f)
        drawRow("EXPÉDITEUR", sample.senderName, 760f)
        drawRow("BÉNÉFICIAIRE", sample.receiverName, 860f)
        drawRow("TYPE DE PAIEMENT", if (sample.isCash) "Espèces en main propre" else "Transfert Mobile Money", 960f)

        return bitmap
    }
}
