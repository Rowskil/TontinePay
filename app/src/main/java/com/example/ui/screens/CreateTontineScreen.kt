package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TontineFrequency
import com.example.ui.components.AppDatePickerModal
import com.example.ui.components.HapticHelper
import com.example.ui.components.bounceClick
import com.example.ui.theme.StatusValidatedGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTontineScreen(
    onCreateTontine: (
        name: String,
        description: String,
        amount: Double,
        frequency: TontineFrequency,
        currency: String,
        memberCount: Int,
        startDate: String,
        endDate: String,
        latePenaltyFee: Double,
        isBeneficiaryExempt: Boolean,
        isPendingLaunch: Boolean,
        autoRotation: Boolean
    ) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("XAF") }
    var membersCountText by remember { mutableStateOf("5") }
    var frequency by remember { mutableStateOf(TontineFrequency.MONTHLY) }

    // Start/launch dates
    var isImmediateStart by remember { mutableStateOf(true) }
    val defaultLaunchDate = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.FRENCH).format(Date(System.currentTimeMillis() + 86400000L * 3))
    }
    var plannedLaunchDate by remember { mutableStateOf(defaultLaunchDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Settings
    var isBeneficiaryExempt by remember { mutableStateOf(true) }
    var autoRotation by remember { mutableStateOf(true) }
    var latePenaltyText by remember { mutableStateOf("0") }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Dynamic pot calculation
    val cleanMembers = membersCountText.replace(" ", "").replace("\u00A0", "")
    val cleanAmount = amountText.replace(" ", "").replace("\u00A0", "").replace(",", ".")
    val cleanPenalty = latePenaltyText.replace(" ", "").replace("\u00A0", "").replace(",", ".")

    val membersInt = cleanMembers.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val amountDouble = cleanAmount.toDoubleOrNull() ?: 0.0
    val penaltyDouble = cleanPenalty.toDoubleOrNull() ?: 0.0
    val contributingMembers = if (isBeneficiaryExempt) (membersInt - 1).coerceAtLeast(1) else membersInt
    val calculatedPot = contributingMembers * amountDouble

    if (showDatePicker) {
        AppDatePickerModal(
            onDateSelected = { formatted, _ ->
                plannedLaunchDate = formatted
                HapticHelper.triggerLightClick(context, haptic)
            },
            onDismiss = { showDatePicker = false },
            title = "Date de début de la tontine"
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nouvelle Tontine",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Créez votre groupe d'épargne en toute simplicité.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Nom de la tontine
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du groupe *") },
                    placeholder = { Text("Ex: Tontine Famille & Amis") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_input_name"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // 2. Cotisation & Devise
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Montant de cotisation *") },
                        placeholder = { Text("Ex: 25 000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_input_amount"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Devise") },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // 3. Fréquence (Quotidien, Hebdomadaire, Bimensuelle, Mensuelle)
            item {
                Text(
                    text = "Fréquence des tours",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TontineFrequency.entries.forEach { freq ->
                        val isSelected = frequency == freq
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    frequency = freq
                                    HapticHelper.triggerLightClick(context, haptic)
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = freq.labelFr,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 4. Nombre de membres prévu
            item {
                OutlinedTextField(
                    value = membersCountText,
                    onValueChange = { membersCountText = it },
                    label = { Text("Nombre de membres estimé") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.PeopleOutline, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_input_members"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // 5. Date de début / Lancement (Calendrier réel)
            item {
                Text(
                    text = "Démarrage des cotisations",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isImmediateStart,
                        onClick = {
                            isImmediateStart = true
                            HapticHelper.triggerLightClick(context, haptic)
                        },
                        label = { Text("Lancement immédiat 🚀", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isImmediateStart,
                        onClick = {
                            isImmediateStart = false
                            HapticHelper.triggerLightClick(context, haptic)
                        },
                        label = { Text("Date planifiée 📅", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isImmediateStart) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDatePicker = true
                                HapticHelper.triggerLightClick(context, haptic)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Date de lancement retenue",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = plannedLaunchDate,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Modifier",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 6. Options avancées : Tours automatiques & Exemption
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Switch 1: Tours automatiques
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tours automatiques",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Attribution automatique de l'ordre de passage à chaque cycle.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = autoRotation,
                                onCheckedChange = {
                                    autoRotation = it
                                    HapticHelper.triggerLightClick(context, haptic)
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        // Switch 2: Exemption du bénéficiaire
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Exemption du bénéficiaire",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBeneficiaryExempt) "Le receveur ne cotise pas à son propre tour." else "Tous les membres cotisent obligatoirement.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = isBeneficiaryExempt,
                                onCheckedChange = {
                                    isBeneficiaryExempt = it
                                    HapticHelper.triggerLightClick(context, haptic)
                                }
                            )
                        }

                        // Optional: Amende de retard toggle
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (showAdvancedSettings) "▲ Moins d'options" else "▼ Plus d'options (Amende, description)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showAdvancedSettings = !showAdvancedSettings }
                                .padding(vertical = 4.dp)
                        )

                        AnimatedVisibility(visible = showAdvancedSettings) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = latePenaltyText,
                                    onValueChange = { latePenaltyText = it },
                                    label = { Text("Amende de retard ($currency)") },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    label = { Text("Description (optionnelle)") },
                                    placeholder = { Text("Ex: Épargne solidaire mensuelle") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 7. Aperçu Cagnotte
            if (amountDouble > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = StatusValidatedGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Cagnotte par tour : ${"%,.0f".format(calculatedPot).replace(',', ' ')} $currency",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = StatusValidatedGreen
                                )
                                Text(
                                    text = "$contributingMembers cotisants effectifs • ${frequency.labelFr}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 8. Bouton de création
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank() && amountDouble > 0) {
                            HapticHelper.triggerMediumClick(context, haptic)
                            val finalStartDate = if (!isImmediateStart) plannedLaunchDate else "Immédiat"
                            onCreateTontine(
                                name.trim(),
                                description.trim(),
                                amountDouble,
                                frequency,
                                currency.trim(),
                                membersInt,
                                finalStartDate,
                                finalStartDate,
                                penaltyDouble,
                                isBeneficiaryExempt,
                                !isImmediateStart,
                                autoRotation
                            )
                        }
                    },
                    enabled = name.isNotBlank() && amountDouble > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bounceClick()
                        .testTag("create_tontine_submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Créer la tontine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
