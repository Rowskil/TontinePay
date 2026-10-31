package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun QRCodeView(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    qrColor: Color = Color(0xFF0F172A),
    backgroundColor: Color = Color.White
) {
    // Generate valid, standard QR code BitMatrix using ZXing
    val bitMatrix = remember(data) {
        try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            writer.encode(data, BarcodeFormat.QR_CODE, 200, 200, hints)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitMatrix != null) {
            Canvas(modifier = Modifier.size(size - 24.dp)) {
                val matrixWidth = bitMatrix.width
                val matrixHeight = bitMatrix.height
                val moduleWidth = this.size.width / matrixWidth
                val moduleHeight = this.size.height / matrixHeight

                for (y in 0 until matrixHeight) {
                    for (x in 0 until matrixWidth) {
                        if (bitMatrix.get(x, y)) {
                            drawRoundRect(
                                color = qrColor,
                                topLeft = Offset(x * moduleWidth, y * moduleHeight),
                                size = Size(moduleWidth + 0.3f, moduleHeight + 0.3f),
                                cornerRadius = CornerRadius(moduleWidth * 0.15f, moduleHeight * 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}
