package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SampleReceiptItem
import com.example.data.SampleReceipts
import com.example.model.ExtractedReceiptData
import com.example.model.Tontine
import com.example.model.TontineCycle
import com.example.ui.OcrScanUiState
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentProofSubmissionScreen(
    tontine: Tontine,
    cycle: TontineCycle?,
    ocrState: OcrScanUiState,
    isDuplicateTransactionId: (String) -> Boolean,
    requiredAmount: Double = tontine.contributionAmount,
    penaltyFee: Double = 0.0,
    senderSlotLabel: String = "",
    onBack: () -> Unit,
    onAnalyzeReceipt: (Bitmap, String?) -> Unit,
    onSubmitProof: (ExtractedReceiptData) -> Unit,
    onResetOcr: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onResetOcr()
        onBack()
    }

    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Payment methods: Orange Money, MTN MoMo, Cash
    val paymentMethods = remember(tontine.contributionAmount, tontine.currency) {
        SampleReceipts.getPaymentMethods(tontine.contributionAmount, tontine.currency)
    }
    var selectedMethod by remember { mutableStateOf<SampleReceiptItem>(paymentMethods.first()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                selectedBitmap = bitmap
                onAnalyzeReceipt(bitmap, selectedMethod.provider)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Preuve de paiement",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (senderSlotLabel.isNotBlank()) "Part : $senderSlotLabel • Destinataire : ${cycle?.beneficiaryName ?: "Bénéficiaire"}" else "Tour #${cycle?.cycleNumber ?: 1} • Destinataire : ${cycle?.beneficiaryName ?: "Bénéficiaire"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onResetOcr()
                            onBack()
                        },
                        modifier = Modifier.testTag("ocr_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Note obligatoire Mobile Money avec un seul emoji (Feedback 003 A.1)
                Surface(
                    color = AmberGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NB : Veillez ajouter les frais de retrait pour les dépôts Mobile Money 🫶",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Section Dépôt Mobile Money
            item {
                Text(
                    text = "Mode de paiement",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Indicative Payment Cards: Orange Money, MTN MoMo, Cash
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paymentMethods.forEach { method ->
                        val isSelected = selectedMethod.id == method.id
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedMethod = method
                                    onResetOcr()
                                    selectedBitmap = null
                                }
                                .testTag("payment_method_${method.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(method.primaryColor)) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(method.primaryColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        when {
                                            method.isCash -> Icons.Default.Payments
                                            method.id == "orange_money" -> Icons.Default.PhoneIphone
                                            else -> Icons.AutoMirrored.Filled.ReceiptLong
                                        },
                                        contentDescription = null,
                                        tint = if (method.id == "mtn_momo") Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = method.provider,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${"%,.0f".format(tontine.contributionAmount).replace(',', ' ')} ${tontine.currency}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Expected beneficiary banner
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bénéficiaire à payer : ${cycle?.beneficiaryName ?: "Receveur du tour"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Numéro officiel : ${cycle?.beneficiaryPhone ?: "Non renseigné"} • Montant requis : ${"%,.0f".format(requiredAmount).replace(',', ' ')} ${tontine.currency} ${if (penaltyFee > 0) "(dont pénalité ${penaltyFee.toInt()})" else ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action Button: "Envoyer la requête" for Cash or "Importer une image ou un reçu" for Mobile Money
            item {
                if (selectedMethod.isCash) {
                    Button(
                        onClick = {
                            val cashProof = ExtractedReceiptData(
                                transactionId = "CASH-" + System.currentTimeMillis().toString().takeLast(6),
                                amount = requiredAmount,
                                currency = tontine.currency,
                                senderPhone = "",
                                recipientPhone = cycle?.beneficiaryPhone ?: "",
                                provider = "Cash (Espèces)",
                                timestamp = "Main propre - " + System.currentTimeMillis(),
                                confidence = 1.0f,
                                rawJson = "{\"method\":\"cash\"}"
                            )
                            onSubmitProof(cashProof)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_cash_request_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Envoyer la requête (Espèces en main)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(selectedMethod.primaryColor),
                            contentColor = if (selectedMethod.id == "mtn_momo") Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_receipt_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedBitmap == null) "Importer une image ou un reçu" else "Remplacer l'image du reçu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Preview of selected receipt image
            if (selectedBitmap != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = "Aperçu du reçu",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                    }
                }
            }

            // OCR Scanning Progress State
            when (ocrState) {
                is OcrScanUiState.Idle -> {
                    // Quiet idle state
                }
                is OcrScanUiState.Scanning -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = ocrState.stepMessage,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                is OcrScanUiState.DuplicateError -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = StatusUnpaidRed.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusUnpaidRed)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusUnpaidRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Doublon Détecté (Anti-Fraude)",
                                        color = StatusUnpaidRed,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ocrState.message,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Réf ID : ${ocrState.txnId}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                is OcrScanUiState.GeneralError -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusUnpaidRed.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusUnpaidRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Analyse OCR non concluante",
                                        color = StatusUnpaidRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = ocrState.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Vérifiez que votre capture d'écran présente clairement le SMS Mobile Money ou le reçu de l'application avec le numéro du destinataire, la référence de transaction et le montant.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is OcrScanUiState.Success -> {
                    val data = ocrState.data

                    // CRITERION 1: Numéro du Bénéficiaire présent sur le reçu (Feedback 005 A.1)
                    val beneficiaryDigits = cycle?.beneficiaryPhone?.filter { it.isDigit() }?.takeLast(9).orEmpty()
                    val recipientDigits = data.recipientPhone.filter { it.isDigit() }.takeLast(9)

                    // Le système d'analyse OCR s'assure juste que le numéro du bénéficiaire est présent sur le reçu peu importe son emplacement
                    val cleanOcrDigits = data.fullOcrText.filter { it.isDigit() }
                    val isBeneficiaryNumberFound = if (beneficiaryDigits.isBlank()) true else {
                        recipientDigits == beneficiaryDigits ||
                        data.allDetectedPhones.any { it.filter { c -> c.isDigit() }.takeLast(9) == beneficiaryDigits } ||
                        cleanOcrDigits.contains(beneficiaryDigits) ||
                        data.rawJson.filter { it.isDigit() }.contains(beneficiaryDigits)
                    }

                    val isRecipientMatching = isBeneficiaryNumberFound
                    val recipientDiagnostic = when {
                        beneficiaryDigits.isBlank() -> "Conforme : Bénéficiaire du tour sans numéro spécifique requis."
                        isBeneficiaryNumberFound -> "Conforme : Le numéro du bénéficiaire ($beneficiaryDigits) est bien présent sur le reçu."
                        else -> "Non conforme : Le numéro du bénéficiaire attendu ($beneficiaryDigits - ${cycle?.beneficiaryName}) est introuvable sur le reçu."
                    }

                    // CRITERION 2: ID Transaction & Unicité (Contrôle Anti-Fraude)
                    val isTxnIdPresent = data.transactionId.isNotBlank()
                    val isDuplicate = if (isTxnIdPresent) isDuplicateTransactionId(data.transactionId) else false
                    val isTxnIdValid = isTxnIdPresent && !isDuplicate
                    val txnDiagnostic = when {
                        !isTxnIdPresent -> "Non détecté : Aucune référence de transaction (ID) n'a pu être identifiée sur l'image."
                        isDuplicate -> "Doublon détecté (Anti-Fraude) : L'identifiant '${data.transactionId}' a déjà été enregistré et validé dans la tontine !"
                        else -> "Conforme : Identifiant unique '${data.transactionId}' vérifié avec succès."
                    }

                    // CRITERION 3: Montant Net
                    val isAmountSufficient = data.amount >= requiredAmount && data.amount > 0.0
                    val amountDiagnostic = when {
                        data.amount <= 0.0 -> "Non détecté : Le montant net du transfert n'a pas pu être extrait."
                        !isAmountSufficient -> "Insuffisant : Montant net payé de ${data.amount.toInt()} ${data.currency} inférieur au montant attendu de ${requiredAmount.toInt()} ${tontine.currency}${if (penaltyFee > 0) " (incluant pénalité de retard de ${penaltyFee.toInt()} ${tontine.currency})" else ""}."
                        else -> "Conforme : ${data.amount.toInt()} ${data.currency} >= ${requiredAmount.toInt()} ${tontine.currency}${if (penaltyFee > 0) " (inclut pénalité de retard de ${penaltyFee.toInt()})" else ""}."
                    }

                    val isAllCriteriaConforme = isRecipientMatching && isTxnIdValid && isAmountSufficient
                    val validCount = (if (isRecipientMatching) 1 else 0) + (if (isTxnIdValid) 1 else 0) + (if (isAmountSufficient) 1 else 0)

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isAllCriteriaConforme) StatusValidatedGreen else StatusUnpaidRed
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Diagnostic Header Badge (Requirement B.4)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isAllCriteriaConforme) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (isAllCriteriaConforme) StatusValidatedGreen else StatusUnpaidRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isAllCriteriaConforme) "Reçu Validé ($validCount/3 critères)" else "Reçu Incomplet ($validCount/3 critères)",
                                            color = if (isAllCriteriaConforme) StatusValidatedGreen else StatusUnpaidRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    Surface(
                                        color = if (isAllCriteriaConforme) StatusValidatedGreen.copy(alpha = 0.15f) else StatusUnpaidRed.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isAllCriteriaConforme) "Conforme ✓" else "Non conforme",
                                            color = if (isAllCriteriaConforme) StatusValidatedGreen else StatusUnpaidRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Contrôle d'authenticité et critères obligatoires :",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // CRITERION 1 CARD
                                CriterionCheckCard(
                                    title = "1. Numéro du Destinataire",
                                    isConforme = isRecipientMatching,
                                    foundValue = if (data.recipientPhone.isNotBlank()) data.recipientPhone else "Non trouvé",
                                    expectedValue = cycle?.beneficiaryPhone ?: "Non renseigné",
                                    diagnostic = recipientDiagnostic
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // CRITERION 2 CARD
                                CriterionCheckCard(
                                    title = "2. Identifiant Transaction (Anti-Fraude)",
                                    isConforme = isTxnIdValid,
                                    foundValue = if (data.transactionId.isNotBlank()) data.transactionId else "Non trouvé",
                                    expectedValue = "Référence unique non dupliquée",
                                    diagnostic = txnDiagnostic
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // CRITERION 3 CARD
                                CriterionCheckCard(
                                    title = "3. Montant Net du Transfert",
                                    isConforme = isAmountSufficient,
                                    foundValue = "${"%,.0f".format(data.amount).replace(',', ' ')} ${data.currency}",
                                    expectedValue = "≥ ${"%,.0f".format(requiredAmount).replace(',', ' ')} ${tontine.currency}",
                                    diagnostic = amountDiagnostic
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Submit Button (Enabled strictly when all 3 criteria are valid)
                                Button(
                                    onClick = { onSubmitProof(data) },
                                    enabled = isAllCriteriaConforme,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("submit_receipt_confirmation_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Envoyer la preuve de paiement",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun CriterionCheckCard(
    title: String,
    isConforme: Boolean,
    foundValue: String,
    expectedValue: String,
    diagnostic: String
) {
    Surface(
        color = if (isConforme) StatusValidatedGreen.copy(alpha = 0.08f) else StatusUnpaidRed.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConforme) StatusValidatedGreen.copy(alpha = 0.35f) else StatusUnpaidRed.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isConforme) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isConforme) StatusValidatedGreen else StatusUnpaidRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (isConforme) "✓ Conforme" else "✗ Non conforme",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConforme) StatusValidatedGreen else StatusUnpaidRed
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Extrait : $foundValue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "Requis : $expectedValue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = diagnostic,
                fontSize = 10.5.sp,
                color = if (isConforme) MaterialTheme.colorScheme.onSurfaceVariant else StatusUnpaidRed,
                lineHeight = 14.sp
            )
        }
    }
}
