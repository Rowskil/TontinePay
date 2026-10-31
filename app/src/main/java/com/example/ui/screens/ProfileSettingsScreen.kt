package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.NotificationPreferences
import com.example.model.User
import com.example.ui.components.TontinePayLogo
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DisplayMode
import com.example.ui.theme.StatusValidatedGreen
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    currentUser: User,
    displayMode: DisplayMode = DisplayMode.SYSTEM,
    isDarkMode: Boolean = false,
    accentTheme: AccentTheme = AccentTheme.EMERALD,
    currentAppVersion: String = "v1.5.0",
    latestServerVersion: String = "v1.5.0",
    isCheckingUpdate: Boolean = false,
    isUpdateAvailable: Boolean = false,
    isDownloadingUpdate: Boolean = false,
    updateProgress: Float = 0f,
    isUpdateReadyToRestart: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onRestartApp: () -> Unit = {},
    onSelectDisplayMode: (DisplayMode) -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onSelectAccentTheme: (AccentTheme) -> Unit,
    onSaveProfile: (fullName: String, phoneNumber: String, avatarUrl: String, prefs: NotificationPreferences) -> Unit,
    onDeleteProfilePhoto: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onTestNotification: () -> Unit = {},
    onBack: () -> Unit,
    isPhoneRegistered: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false
) {
    if (showBackButton) {
        BackHandler { onBack() }
    }

    val context = LocalContext.current
    var fullName by remember(currentUser.fullName) { mutableStateOf(currentUser.fullName) }
    var phoneNumber by remember(currentUser.phoneNumber) { mutableStateOf(currentUser.phoneNumber) }
    var avatarUriString by remember(currentUser.avatarUrl) { mutableStateOf(currentUser.avatarUrl) }

    var notifyInvitations by remember(currentUser.preferences.notifyInvitations) {
        mutableStateOf(currentUser.preferences.notifyInvitations)
    }
    var notifyDeadlines by remember(currentUser.preferences.notifyDeadlines) {
        mutableStateOf(currentUser.preferences.notifyDeadlines)
    }
    var notifyValidations by remember(currentUser.preferences.notifyValidations) {
        mutableStateOf(currentUser.preferences.notifyValidations)
    }
    var notifyPayoutTurn by remember(currentUser.preferences.notifyPayoutTurn) {
        mutableStateOf(currentUser.preferences.notifyPayoutTurn)
    }

    var showSaveSuccessBanner by remember { mutableStateOf(false) }

    val cleanPhone = phoneNumber.replace(" ", "").replace("-", "")
    val isPhoneFormatValid = cleanPhone.length in 9..14 && cleanPhone.all { it.isDigit() || it == '+' }
    val isPhoneSameAsCurrent = cleanPhone == currentUser.phoneNumber.replace(" ", "").replace("-", "")
    val isPhoneDuplicate = !isPhoneSameAsCurrent && isPhoneRegistered(phoneNumber)
    val isPhoneValid = isPhoneFormatValid && !isPhoneDuplicate

    // Confirmation dialog states
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Image Cropper Dialog State
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    // Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
            showCropDialog = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Paramètres",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("profile_back_button")
                        ) {
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Identity, Avatar & Phone Modification Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar view with camera badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (avatarUriString.isNotBlank()) {
                                AsyncImage(
                                    model = avatarUriString,
                                    contentDescription = "Avatar de profil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .testTag("profile_avatar_image")
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        )
                                        .testTag("profile_avatar_initials"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser.initials,
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            // Edit camera badge button
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .testTag("profile_camera_button")
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Changer la photo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Photo Actions Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("change_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (avatarUriString.isNotBlank()) "Changer la photo" else "Ajouter une photo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (avatarUriString.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        avatarUriString = ""
                                        onDeleteProfilePhoto()
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("delete_photo_button")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Supprimer la photo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full Name input
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Nom complet") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_full_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Phone Number input
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Numéro Mobile Money") },
                            supportingText = {
                                Text(
                                    "Numéro pour l'envoi et la réception de vos cotisations tontine",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Numéro modifiable",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_phone_input")
                        )

                        if (isPhoneDuplicate) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ce numéro de téléphone est déjà associé à un autre compte.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (!isPhoneFormatValid && phoneNumber.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Format invalide (minimum 9 chiffres).",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Explicit Save Button (Feedback D.2)
                        Button(
                            onClick = {
                                val updatedPrefs = NotificationPreferences(
                                    notifyInvitations = notifyInvitations,
                                    notifyDeadlines = notifyDeadlines,
                                    notifyValidations = notifyValidations,
                                    notifyPayoutTurn = notifyPayoutTurn
                                )
                                onSaveProfile(fullName.trim(), phoneNumber.trim(), avatarUriString, updatedPrefs)
                                showSaveSuccessBanner = true
                            },
                            enabled = fullName.isNotBlank() && isPhoneValid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_save_changes_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enregistrer les modifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (showSaveSuccessBanner) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = StatusValidatedGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusValidatedGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Modifications enregistrées avec succès !", color = StatusValidatedGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Préférences de Notifications Card (PLACED BEFORE APPARENCE AS REQUESTED)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Préférences de Notifications",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        PrefToggleRow(
                            title = "Nouvelles invitations",
                            subtitle = "Alertes quand vous êtes invité(e) dans une tontine",
                            checked = notifyInvitations,
                            onCheckedChange = { notifyInvitations = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        PrefToggleRow(
                            title = "Rappels d'échéances",
                            subtitle = "Rappels avant la date limite du cycle",
                            checked = notifyDeadlines,
                            onCheckedChange = { notifyDeadlines = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        PrefToggleRow(
                            title = "Validations de paiement",
                            subtitle = "Confirmation dès que le receveur valide votre reçu",
                            checked = notifyValidations,
                            onCheckedChange = { notifyValidations = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        PrefToggleRow(
                            title = "Mon tour d'encaissement (Payout)",
                            subtitle = "Alerte prioritaire lorsque la cagnotte vous revient",
                            checked = notifyPayoutTurn,
                            onCheckedChange = { notifyPayoutTurn = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        OutlinedButton(
                            onClick = onTestNotification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_notifications_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Tester les notifications de l'application",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Apparence Card (Placed AFTER Préférences de Notifications)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Apparence",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Mode d'affichage :",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3 Exact Modes: Système, Clair Épuré, Sombre
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DisplayMode.entries.forEach { mode ->
                                val isSelected = displayMode == mode
                                Surface(
                                    onClick = { onSelectDisplayMode(mode) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("display_mode_${mode.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val icon = when (mode) {
                                            DisplayMode.SYSTEM -> Icons.Default.BrightnessAuto
                                            DisplayMode.LIGHT -> Icons.Default.LightMode
                                            DisplayMode.DARK -> Icons.Default.DarkMode
                                        }

                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.labelFr,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = mode.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onSelectDisplayMode(mode) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Accent Color Selector
                        Text(
                            text = "Couleur d'accentuation :",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AccentTheme.entries.forEach { accent ->
                                val isSelected = accentTheme == accent
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onSelectAccentTheme(accent) }
                                        .padding(2.dp)
                                        .testTag("accent_color_${accent.name}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(accent.primary)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = accent.labelFr.take(7),
                                        fontSize = 10.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Mises à jour in-app (User Request 2)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("system_update_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mises à jour de l'application",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Version installée : $currentAppVersion",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            if (isUpdateAvailable) {
                                Surface(
                                    color = AmberGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$latestServerVersion dispo",
                                        color = AmberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = StatusValidatedGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "À jour ✓",
                                        color = StatusValidatedGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (isUpdateAvailable) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "✨ Nouveautés : Gestion avancée des parts multiples indépendantes, optimisation de l'analyse OCR et stabilité.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            if (isDownloadingUpdate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { updateProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Téléchargement de la mise à jour... ${(updateProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (isUpdateReadyToRestart) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onRestartApp,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Redémarrer pour appliquer ($latestServerVersion)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onDownloadUpdate,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Installer la mise à jour ($latestServerVersion)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onCheckForUpdate,
                                enabled = !isCheckingUpdate,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vérification en cours...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Vérifier les mises à jour", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Déconnexion Button (User requirement D.12)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_logout_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Déconnexion",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5. Supprimer le compte Button (User requirement D.13)
            item {
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_delete_account_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Supprimer le compte",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))

                // Developer & App Signature (Feedback 004 C.2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "TontinePay $currentAppVersion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Copyright © 2026 Anthony zaMer. Tous droits réservés.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Déconnexion Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Déconnexion",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Voulez-vous vraiment vous déconnecter de votre compte TontinePay ?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Se déconnecter", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Supprimer le compte Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Supprimer le compte",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Cette action est irréversible. Votre compte, votre historique et vos accès aux tontines seront définitivement supprimés.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Oui, supprimer définitivement", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Interactive Image Cropper Dialog
    if (showCropDialog && pendingCropUri != null) {
        ImageCropDialog(
            imageUri = pendingCropUri!!,
            onConfirm = { croppedUriString ->
                try {
                    val srcUri = Uri.parse(croppedUriString)
                    val avatarsDir = File(context.filesDir, "avatars")
                    avatarsDir.mkdirs()
                    val destFile = File(avatarsDir, "avatar_${currentUser.id}.jpg")
                    context.contentResolver.openInputStream(srcUri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    avatarUriString = Uri.fromFile(destFile).toString()
                } catch (e: Exception) {
                    avatarUriString = croppedUriString
                }
                showCropDialog = false
                pendingCropUri = null
            },
            onDismiss = {
                showCropDialog = false
                pendingCropUri = null
            }
        )
    }
}

@Composable
private fun PrefToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
