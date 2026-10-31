package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.StatusValidatedGreen
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.ui.components.TontinePayLogo
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class AuthTab {
    LOGIN,
    REGISTER
}

private data class OnboardingSlideData(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val badge: String
)

fun isValidMobileMoneyPhone(phone: String): Boolean {
    val clean = phone.replace(" ", "").replace("-", "")
    return clean.matches(Regex("""^(\+?[0-9]{8,15})$"""))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onRegister: (fullName: String, phoneNumber: String, password: String, avatarUrl: String, pendingInviteCode: String?) -> Unit,
    onLogin: (phoneNumber: String, password: String) -> Unit,
    onResetPassword: (phoneNumber: String, newPassword: String) -> Unit,
    onJoinTontineCode: (code: String) -> Unit,
    isPhoneRegistered: (String) -> Boolean = { false },
    initialInviteCode: String? = null,
    modifier: Modifier = Modifier
) {
    // Flow state: false = Welcome slides; true = Choice / Auth page (auto-skipped if deep link invite code is present)
    var showAuthFlow by remember(initialInviteCode) { mutableStateOf(initialInviteCode != null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!showAuthFlow) {
            WelcomeSlidesView(
                onSkip = { showAuthFlow = true },
                onFinish = { showAuthFlow = true }
            )
        } else {
            AuthChoiceView(
                onBackToSlides = { showAuthFlow = false },
                onRegister = onRegister,
                onLogin = onLogin,
                onResetPassword = onResetPassword,
                onJoinTontineCode = onJoinTontineCode,
                isPhoneRegistered = isPhoneRegistered,
                initialInviteCode = initialInviteCode
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WelcomeSlidesView(
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val slides = remember {
        listOf(
            OnboardingSlideData(
                title = "Bienvenue sur TontinePay",
                subtitle = "Épargne Collective Sécurisée",
                description = "Digitalisez vos tontines d'épargne rotative entre amis, collègues ou en famille. Gestion claire, transparente et sans litiges.",
                icon = Icons.Default.Savings,
                badge = "Épargne & Tontine"
            ),
            OnboardingSlideData(
                title = "Vérification Intelligente par IA",
                subtitle = "Contrôle Automatisé des Reçus",
                description = "Scannez instantanément vos reçus de transfert Orange Money, MTN MoMo, Wave ou Moov. L'IA Gemini extrait et certifie les montants sans doublons.",
                icon = Icons.Default.ReceiptLong,
                badge = "IA Gemini Vision"
            ),
            OnboardingSlideData(
                title = "Distribution & Transparence",
                subtitle = "Versement Ponctuel de la Cagnotte",
                description = "Suivez la matrice en temps réel, respectez l'ordre de passage convenu et recevez des notifications dès qu'arrive votre tour d'encaisser.",
                icon = Icons.Default.Groups,
                badge = "Tour de rôle garanti"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar with Skip ("Passer") button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TontinePayLogo(
                size = 36.dp,
                iconSize = 22.dp,
                cornerRadius = 10.dp
            )

            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text(
                    text = "Passer",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.4f))

        // Dynamic 3-Slide Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f)
        ) { pageIndex ->
            val slide = slides[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Central Icon Card
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = slide.badge,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = slide.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = slide.subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = slide.description,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // Indicator dots
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            repeat(slides.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (isSelected) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next / Start Button
        val isLastSlide = pagerState.currentPage == slides.size - 1

        Button(
            onClick = {
                if (isLastSlide) {
                    onFinish()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("onboarding_next_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLastSlide) "Commencer l'expérience" else "Suivant",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AuthChoiceView(
    onBackToSlides: () -> Unit,
    onRegister: (fullName: String, phoneNumber: String, password: String, avatarUrl: String, pendingInviteCode: String?) -> Unit,
    onLogin: (phoneNumber: String, password: String) -> Unit,
    onResetPassword: (phoneNumber: String, newPassword: String) -> Unit,
    onJoinTontineCode: (code: String) -> Unit,
    isPhoneRegistered: (String) -> Boolean,
    initialInviteCode: String? = null
) {
    var activeTab by remember(initialInviteCode) {
        mutableStateOf(if (initialInviteCode != null) AuthTab.REGISTER else AuthTab.LOGIN)
    }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var pendingInviteCode by remember(initialInviteCode) { mutableStateOf<String?>(initialInviteCode) }

    // Register Form States (Avatar picker removed from onboarding - Feedback E.2)
    var registerFullName by remember { mutableStateOf("") }
    var registerPhone by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerConfirmPassword by remember { mutableStateOf("") }
    var registerPasswordVisible by remember { mutableStateOf(false) }
    var registerTermsAccepted by remember { mutableStateOf(false) }
    var registerError by remember { mutableStateOf<String?>(null) }

    // Login Form States
    var loginPhone by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginSubmitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToSlides,
                modifier = Modifier.testTag("auth_back_to_slides")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour aux explications",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            TontinePayLogo(
                size = 38.dp,
                iconSize = 24.dp,
                cornerRadius = 10.dp
            )

            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Espace Sécurisé",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Rejoignez une tontine ou accédez à votre compte unique",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Card 1: Option "Rejoindre une tontine"
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showJoinDialog = true }
                .testTag("auth_join_tontine_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rejoindre une tontine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Avec un code d'invitation ou un lien reçu",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Switch Tabs: Se connecter vs Créer un compte (Feedback E.1)
        TabRow(
            selectedTabIndex = if (activeTab == AuthTab.LOGIN) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = activeTab == AuthTab.LOGIN,
                onClick = {
                    activeTab = AuthTab.LOGIN
                    loginError = null
                    loginSubmitted = false
                },
                text = {
                    Text(
                        "Se connecter",
                        fontWeight = if (activeTab == AuthTab.LOGIN) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
            Tab(
                selected = activeTab == AuthTab.REGISTER,
                onClick = {
                    activeTab = AuthTab.REGISTER
                    registerError = null
                },
                text = {
                    Text(
                        "Créer un compte",
                        fontWeight = if (activeTab == AuthTab.REGISTER) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Tab Content
        if (activeTab == AuthTab.LOGIN) {
            // LOGIN FORM (E.1 & E.4: Red border markers on error)
            val isLoginPhoneError = loginSubmitted && (loginPhone.isBlank() || !isValidMobileMoneyPhone(loginPhone))
            val isLoginPassError = (loginSubmitted && loginPassword.isBlank()) || loginError != null

            OutlinedTextField(
                value = loginPhone,
                onValueChange = {
                    loginPhone = it
                    loginError = null
                },
                label = { Text("Numéro Mobile Money") },
                placeholder = { Text("Ex: +237 690 00 00 00") },
                leadingIcon = { Icon(Icons.Default.PhoneIphone, contentDescription = null) },
                isError = isLoginPhoneError,
                supportingText = {
                    if (isLoginPhoneError) {
                        Text(
                            text = "Numéro Mobile Money invalide ou manquant",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_phone_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = loginPassword,
                onValueChange = {
                    loginPassword = it
                    loginError = null
                },
                label = { Text("Mot de passe") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                        Icon(
                            imageVector = if (loginPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                isError = isLoginPassError,
                supportingText = {
                    if (isLoginPassError) {
                        Text(
                            text = loginError ?: "Veuillez entrer votre mot de passe",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Text(
                        "Mot de passe oublié ?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    loginSubmitted = true
                    if (loginPhone.isBlank() || !isValidMobileMoneyPhone(loginPhone)) {
                        return@Button
                    }
                    if (loginPassword.isBlank()) {
                        return@Button
                    }
                    onLogin(loginPhone.trim(), loginPassword.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Se connecter", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = { activeTab = AuthTab.REGISTER },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Pas encore de compte ? S'inscrire", fontSize = 13.sp)
            }

        } else {
            // REGISTER FORM
            // Banner if user came from "Rejoindre une tontine"
            if (!pendingInviteCode.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Code de groupe '$pendingInviteCode' enregistré !",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Complétez la création de votre compte pour rejoindre directement la tontine.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = registerFullName,
                onValueChange = {
                    registerFullName = it
                    registerError = null
                },
                label = { Text("Nom complet") },
                placeholder = { Text("Ex: Amadou Diallo") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_fullname_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Check if phone already registered in database (Feedback E.3)
            val cleanRegPhone = registerPhone.replace(" ", "").replace("-", "")
            val isRegPhoneValid = registerPhone.isNotBlank() && isValidMobileMoneyPhone(registerPhone)
            val isPhoneAlreadyInDb = remember(cleanRegPhone) {
                if (cleanRegPhone.length >= 8) isPhoneRegistered(registerPhone) else false
            }
            val isRegPhoneFormatError = registerPhone.isNotBlank() && !isValidMobileMoneyPhone(registerPhone)
            val isRegPhoneError = isRegPhoneFormatError || isPhoneAlreadyInDb

            OutlinedTextField(
                value = registerPhone,
                onValueChange = {
                    registerPhone = it
                    registerError = null
                },
                label = { Text("Numéro Mobile Money") },
                placeholder = { Text("Ex: +237 690 00 00 00") },
                leadingIcon = { Icon(Icons.Default.PhoneIphone, contentDescription = null) },
                trailingIcon = {
                    if (isRegPhoneValid && !isPhoneAlreadyInDb) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Numéro valide",
                            tint = StatusValidatedGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (isRegPhoneError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Erreur numéro",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                isError = isRegPhoneError,
                supportingText = {
                    if (isPhoneAlreadyInDb) {
                        Text(
                            text = "Ce numéro est déjà enregistré. Veuillez vous connecter.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    } else if (isRegPhoneFormatError) {
                        Text(
                            text = "Format attendu : indicatif + 8 à 15 chiffres (Ex: +237 690 00 00 00)",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_phone_input")
            )

            // Suggest to login if phone already exists (Feedback E.3)
            AnimatedVisibility(visible = isPhoneAlreadyInDb) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ce numéro possède déjà un compte", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Un compte TontinePay est déjà associé à ce numéro. Vous pouvez vous y connecter directement sans en créer un nouveau.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                loginPhone = registerPhone
                                activeTab = AuthTab.LOGIN
                                loginError = null
                                loginSubmitted = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Se connecter avec ce numéro", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val isRegPassShort = registerPassword.isNotBlank() && registerPassword.length < 4
            OutlinedTextField(
                value = registerPassword,
                onValueChange = {
                    registerPassword = it
                    registerError = null
                },
                label = { Text("Mot de passe") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { registerPasswordVisible = !registerPasswordVisible }) {
                        Icon(
                            imageVector = if (registerPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                isError = isRegPassShort,
                supportingText = {
                    if (isRegPassShort) {
                        Text(
                            text = "Le mot de passe doit comporter au moins 4 caractères",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                visualTransformation = if (registerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            val isRegPassMismatch = registerConfirmPassword.isNotBlank() && registerConfirmPassword != registerPassword
            OutlinedTextField(
                value = registerConfirmPassword,
                onValueChange = {
                    registerConfirmPassword = it
                    registerError = null
                },
                label = { Text("Confirmer le mot de passe") },
                leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                isError = isRegPassMismatch,
                supportingText = {
                    if (isRegPassMismatch) {
                        Text(
                            text = "Les mots de passe ne correspondent pas",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                },
                visualTransformation = if (registerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_confirm_password_input")
            )

            // Terms and conditions consent checkbox (Feedback E.5)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = registerTermsAccepted,
                    onCheckedChange = { registerTermsAccepted = it },
                    modifier = Modifier.testTag("terms_cgu_checkbox")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "J'accepte les ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Conditions Générales d'Utilisation (CGU)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showTermsDialog = true }
                        .testTag("open_terms_dialog")
                )
            }

            AnimatedVisibility(visible = registerError != null) {
                Text(
                    text = registerError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val canSubmitRegistration = registerTermsAccepted &&
                    !isPhoneAlreadyInDb &&
                    isRegPhoneValid &&
                    registerFullName.isNotBlank() &&
                    registerPassword.length >= 4 &&
                    registerPassword == registerConfirmPassword

            Button(
                onClick = {
                    if (registerFullName.isBlank()) {
                        registerError = "Veuillez entrer votre nom complet"
                        return@Button
                    }
                    if (registerPhone.isBlank() || !isValidMobileMoneyPhone(registerPhone)) {
                        registerError = "Format de numéro Mobile Money invalide"
                        return@Button
                    }
                    if (isPhoneAlreadyInDb) {
                        registerError = "Ce numéro de téléphone possède déjà un compte"
                        return@Button
                    }
                    if (registerPassword.length < 4) {
                        registerError = "Le mot de passe doit contenir au moins 4 caractères"
                        return@Button
                    }
                    if (registerPassword != registerConfirmPassword) {
                        registerError = "Les deux mots de passe ne correspondent pas"
                        return@Button
                    }
                    if (!registerTermsAccepted) {
                        registerError = "Veuillez accepter les Conditions Générales d'Utilisation (CGU)"
                        return@Button
                    }
                    onRegister(
                        registerFullName.trim(),
                        registerPhone.trim(),
                        registerPassword.trim(),
                        "",
                        pendingInviteCode
                    )
                },
                enabled = canSubmitRegistration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("register_submit_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (pendingInviteCode != null) "Créer et rejoindre le groupe" else "Créer mon compte",
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = { activeTab = AuthTab.LOGIN },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Déjà un compte ? Se connecter", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Join Tontine Dialog
    if (showJoinDialog) {
        var inviteCodeInput by remember { mutableStateOf("") }
        var joinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Rejoindre une Tontine",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Entrez le code d'invitation partagé par l'administrateur de la tontine :",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = {
                            inviteCodeInput = it
                            joinError = null
                        },
                        placeholder = { Text("Ex: YAO-2026") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_join_code_input")
                    )
                    if (joinError != null) {
                        Text(
                            text = joinError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteCodeInput.isBlank()) {
                            joinError = "Veuillez entrer un code"
                        } else {
                            val code = inviteCodeInput.trim().uppercase()
                            pendingInviteCode = code
                            showJoinDialog = false
                            activeTab = AuthTab.REGISTER
                        }
                    },
                    modifier = Modifier.testTag("onboarding_join_confirm_button")
                ) {
                    Text("Valider et s'inscrire", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        var resetPhone by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmNewPass by remember { mutableStateOf("") }
        var resetError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    Icons.Default.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Réinitialiser le mot de passe",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Indiquez votre numéro Mobile Money associé à votre compte et choisissez un nouveau mot de passe :",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = resetPhone,
                        onValueChange = {
                            resetPhone = it
                            resetError = null
                        },
                        label = { Text("Numéro Mobile Money") },
                        placeholder = { Text("Ex: +237 690 00 00 00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = {
                            newPass = it
                            resetError = null
                        },
                        label = { Text("Nouveau mot de passe") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmNewPass,
                        onValueChange = {
                            confirmNewPass = it
                            resetError = null
                        },
                        label = { Text("Confirmer le mot de passe") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resetError != null) {
                        Text(
                            text = resetError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetPhone.isBlank()) {
                            resetError = "Veuillez entrer votre numéro Mobile Money"
                            return@Button
                        }
                        if (newPass.length < 4) {
                            resetError = "Le mot de passe doit comporter au moins 4 caractères"
                            return@Button
                        }
                        if (newPass != confirmNewPass) {
                            resetError = "Les deux mots de passe ne correspondent pas"
                            return@Button
                        }
                        showForgotPasswordDialog = false
                        onResetPassword(resetPhone.trim(), newPass.trim())
                    }
                ) {
                    Text("Réinitialiser", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Terms and Conditions Dialog (Feedback E.5)
    if (showTermsDialog) {
        TermsAndConditionsDialog(
            onDismiss = { showTermsDialog = false },
            onAccept = {
                registerTermsAccepted = true
                showTermsDialog = false
            }
        )
    }
}

@Composable
fun TermsAndConditionsDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Gavel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Conditions Générales d'Utilisation",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "En utilisant TontinePay, vous acceptez les principes régissant l'épargne rotative communautaire :",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "1. Engagement financier",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Chaque membre s'engage formellement à verser sa cotisation avant la date limite fixée pour chaque tour par l'administrateur.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            "2. Authenticité des preuves Mobile Money",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Les preuves de dépôt (Orange Money, MTN MoMo, Cash) doivent comporter une référence de transaction authentique et le montant net requis.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            "3. Ordre de passage et rotation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "L'ordre de passage détermine la séquence de réception de la cagnotte. Un membre ayant déjà bénéficié de sa cagnotte ne peut quitter le groupe avant la clôture complète du cycle.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            "4. Pénalités de retard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "En cas de retard au-delà de la date butoir, les frais de pénalité configurés par le groupe sont automatiquement appliqués à la cotisation.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            "5. Transparence et sécurité",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "TontinePay assure la traçabilité des cotisations et validations en temps réel. Vos données et accès sont strictement confidentiels.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("J'accepte les CGU", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1.35f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recadrer la photo",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ajustée automatiquement pour remplir le canvas. Glissez pour repositionner.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Aperçu de recadrage",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ZoomOut,
                        contentDescription = "Zoom arrière",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1.0f..3.0f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Zoom avant",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = { onConfirm(imageUri.toString()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirmer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
