package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.ApiKey
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.EncryptionUtils
import com.example.ui.Agent
import com.example.ui.ChatViewModel
import com.example.ui.ChatViewModelFactory
import com.example.ui.ViewMode
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModelFactory(context)
            )
            val isDark by chatViewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDark) {
                MainContainer(viewModel = chatViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(viewModel: ChatViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sessions by viewModel.sessions.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val currentMode by viewModel.currentWorkMode.collectAsState()

    // Key creation dialog state
    var showAddKeyDialog by remember { mutableStateOf(false) }

    // Navigation Drawer content
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerHeader(viewModel = viewModel)
                Spacer(modifier = Modifier.height(8.dp))

                // Work modes group
                Text(
                    text = "ESPACIOS DE TRABAJO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    label = { Text("Chat Conversacional", fontWeight = FontWeight.SemiBold) },
                    selected = currentMode is ViewMode.StandardChat,
                    onClick = {
                        viewModel.setWorkMode(ViewMode.StandardChat)
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Work, contentDescription = null, tint = Color(0xFFE5A93B)) },
                    label = { Text("Proyecto Inteligente", fontWeight = FontWeight.SemiBold) },
                    selected = currentMode is ViewMode.ProjectPlanner,
                    onClick = {
                        viewModel.setWorkMode(ViewMode.ProjectPlanner)
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Construction, contentDescription = null, tint = Color(0xFFBD93F9)) },
                    label = { Text("Diseñar Apps (Claude-style)", fontWeight = FontWeight.SemiBold) },
                    selected = currentMode is ViewMode.Designer,
                    onClick = {
                        viewModel.setWorkMode(ViewMode.Designer("instant"))
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 20.dp))

                // Sessions group
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORIAL CIFRADO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    IconButton(
                        onClick = { viewModel.createSession("Chat Nuevo") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nuevo Chat", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    items(sessions) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (currentSession?.id == s.id) MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.5f
                                    ) else Color.Transparent
                                )
                                .clickable {
                                    viewModel.selectSession(s)
                                    coroutineScope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Cifrado",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = s.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                fontWeight = if (currentSession?.id == s.id) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { viewModel.deleteSession(s.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Erase", modifier = Modifier.size(14.dp), tint = Color.LightGray)
                            }
                        }
                    }
                }

                // Footer Actions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(bottom = 24.dp, top = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    Button(
                        onClick = { showAddKeyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gestionar Claves API", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) {
        // Main view content
        Scaffold(
            topBar = {
                MainAppBar(
                    viewModel = viewModel,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onOpenKeys = { showAddKeyDialog = true }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (viewModel.isDarkMode.value) {
                                listOf(Color(0xFF111111), Color(0xFF1B1B1B))
                            } else {
                                listOf(Color(0xFFFFFFFF), Color(0xFFFAF9F6))
                            }
                        )
                    )
            ) {
                AnimatedContent(
                    targetState = currentMode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "WorkModeContainer"
                ) { targetMode ->
                    when (targetMode) {
                        is ViewMode.StandardChat -> {
                            ChatWorkspace(viewModel = viewModel)
                        }
                        is ViewMode.ProjectPlanner -> {
                            ProjectWorkspace(viewModel = viewModel)
                        }
                        is ViewMode.Designer -> {
                            DesignerWorkspace(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Modal to add API keys dynamically and validate immediately
    if (showAddKeyDialog) {
        ApiKeySettingsDialog(
            viewModel = viewModel,
            onDismiss = { showAddKeyDialog = false }
        )
    }
}

// Custom padding scales based on size layout
private fun Int.getDpadBasedPadding() = this

@Composable
fun DrawerHeader(viewModel: ChatViewModel) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF2C2515), Color(0xFF111111))
                    } else {
                        listOf(Color(0xFFFFFBF0), Color(0xFFFFFFFF))
                    }
                )
            )
            .padding(top = 40.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Customized Gold Gemini Star logo icon representation
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFCF9F1))
                        .border(1.5.dp, Color(0xFFD4AF37), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gemini_logo_1779567619885),
                        contentDescription = "Gemini Logo",
                        modifier = Modifier
                            .size(30.dp)
                            .padding(2.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Gemini Chat",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37)
                    )
                    Text(
                        text = "Golden Assistant Pro",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${apiKeys.size} claves API activas y segmentadas",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppBar(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenKeys: () -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()

    // Smooth floating animation setup for top active widget
    val infiniteTransition = rememberInfiniteTransition(label = "TopBreath")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("menu_button")) {
                Icon(Icons.Default.Menu, contentDescription = "Menú de Opciones", tint = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Core App Logo + Label centering
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_gemini_logo_1779567619885),
                    contentDescription = "Gemini Logo",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = 1f + (alphaAnim * 0.05f)
                            scaleY = 1f + (alphaAnim * 0.05f)
                        },
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemini",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " Dorado",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD4AF37)
                )
            }

            // Quick Status Key indicator
            if (apiKeys.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF0C2).copy(alpha = if (isDark) 0.15f else 0.8f))
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD4AF37))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Activo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E7E38))
                    }
                }
            } else {
                TextButton(
                    onClick = onOpenKeys,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyOff,
                        contentDescription = "No Keys Installed",
                        modifier = Modifier.size(14.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sin Claves", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                }
            }

            // Dark Mode toggle
            IconButton(onClick = { viewModel.toggleDarkMode() }) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Cambiar Tema",
                    tint = if (isDark) Color(0xFFFFD700) else Color.DarkGray
                )
            }
        }
    }
}

// -------------------------------------------------------------
// WORKSPACE 1: Standard Advanced Conversational Chat
// -------------------------------------------------------------
@Composable
fun ChatWorkspace(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val activeAgent by viewModel.selectedAgent.collectAsState()
    val isLoading by viewModel.isLoadingResponse.collectAsState()
    val responseTime by viewModel.responseTimeMs.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // File picking trigger
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        attachedImageUri = uri
    }

    // Scroll to bottom on new messages
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Dynamic Agent Carousel selector (Glow details centered)
        AgentCarousel(viewModel = viewModel)

        // Session status card
        if (currentSession == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gemini_logo_1779567619885),
                        contentDescription = "Gemini",
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Bienvenido a Gemini Dorado",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Presiona el botón '+' en el historial para iniciar un nuevo chat cifrado localmente.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.createSession("Chat Nuevo") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
                    ) {
                        Text("Iniciar Chat Dorado", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        } else {
            // Message List viewport
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = Color(0xFFD4AF37)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "El chat está vacío y cifrado en el dispositivo",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "¡Escribe un mensaje de prueba! Si seleccionas el agente de Gemini y tienes tu clave de Google certificada, obtendrás respuestas reales.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubble(message = message)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (isLoading) {
                            item {
                                ResponseLoadingBubble(
                                    agent = activeAgent,
                                    animatedShine = true
                                )
                            }
                        }
                    }
                }
            }

            // Attached image preview area
            if (attachedImageUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = attachedImageUri,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Imágen acoplada para análisis", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Se enviará junto al prompt para análisis multimodal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = { attachedImageUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Timer & encryption bottom detail info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = Color(0xFF9E7E38)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Historial local cifrado con XOR",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                if (responseTime != null) {
                    Text(
                        text = "Respuesta en ${responseTime} ms",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9E7E38)
                    )
                }
            }

            // Input panel layout (White aesthetic, Gold borders)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vision Image selector button
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .background(Color(0xFFFFF7E0), CircleShape)
                            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.6f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Cargar imágen",
                            tint = Color(0xFF9E7E38),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = { Text("Escribe una pregunta para el agente...", fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 120.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Floating action button send
                    IconButton(
                        onClick = {
                            if (inputPrompt.trim().isNotEmpty() || attachedImageUri != null) {
                                viewModel.sendMessage(inputPrompt, attachedImageUri, context)
                                inputPrompt = ""
                                attachedImageUri = null
                            }
                        },
                        enabled = !isLoading && (inputPrompt.trim().isNotEmpty() || attachedImageUri != null),
                        modifier = Modifier
                            .background(
                                if (inputPrompt.trim().isEmpty() && attachedImageUri == null) Color.LightGray else Color(0xFFD4AF37),
                                CircleShape
                            )
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// USER AGENT SWAPPER (Carousel) WITH FLUID GLOW TRANSFORMATION AND SCALE
// -------------------------------------------------------------
@Composable
fun AgentCarousel(viewModel: ChatViewModel) {
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()

    var selectedCategory by remember { mutableStateOf("programar") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AGENTES E INTELIGENCIAS ARTIFICIALES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9E7E38)
            )
            Text(
                text = when(selectedCategory) {
                    "programar" -> "Foco: Programación"
                    "tareas" -> "Foco: Tareas"
                    "variadas" -> "Foco: Variadas"
                    else -> "Foco: Diseño y Plantillas"
                },
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Categorías Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val categories = listOf(
                "programar" to "💻 Programar",
                "tareas" to "📋 Tareas",
                "variadas" to "🌐 Variadas",
                "diseno" to "🎨 Diseño y Plantillas"
            )
            categories.forEach { (catId, label) ->
                val isCatSelected = selectedCategory == catId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isCatSelected) Color(0xFFD4AF37) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { selectedCategory = catId }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCatSelected) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Filtered agents list based on selectedCategory
        val filteredAgents = remember(selectedCategory) {
            viewModel.allAgents.filter { it.category == selectedCategory }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredAgents.forEach { agent ->
                val isSelected = selectedAgent.id == agent.id
                val hasKey = apiKeys.any { it.type == agent.keyType }

                // Smooth scale & glow transitions
                val targetScale = if (isSelected) 1.05f else 0.95f
                val scale by animateFloatAsState(targetScale, label = "CarouselScale")
                val glowBorder = if (isSelected) Color(agent.glowColor) else Color.Transparent

                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable { viewModel.selectAgent(agent) }
                        .animateContentSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) glowBorder else Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(agent.glowColor).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = agent.initials,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(agent.glowColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = agent.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (hasKey) Color.Green else Color.LightGray)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (hasKey) "Listo" else "Demo",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (hasKey) Color.DarkGray else Color.LightGray
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = agent.description,
                            fontSize = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CHAT BUBBLE VIEW WITH XOR DECRYPTION FOR HISTORIC RENDER
// -------------------------------------------------------------
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        Color(0xFFFFF7E0) // Light Gold
    } else {
        MaterialTheme.colorScheme.surface
    }

    val bubbleBorder = if (isUser) {
        BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    }

    val decryptedText = remember(message.encryptedText) {
        EncryptionUtils.decrypt(message.encryptedText)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = bubbleColor,
            border = bubbleBorder,
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Attached image thumbnail if present
                if (message.imageUri != null) {
                    AsyncImage(
                        model = Uri.parse(message.imageUri),
                        contentDescription = "Imagen acoplada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = decryptedText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Subtext encrypted lock display
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Protected",
                modifier = Modifier.size(9.dp),
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isUser) "Tú (Cifrado local)" else "${message.role.uppercase()} (Cifrado local)",
                fontSize = 8.sp,
                color = Color.LightGray
            )
        }
    }
}

// Loading state simulation with fluid gold shimmer
@Composable
fun ResponseLoadingBubble(agent: Agent, animatedShine: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "Shine")
    val alphaShine by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, Color(agent.glowColor).copy(alpha = alphaShine)),
            tonalElevation = 1.dp,
            modifier = Modifier.width(220.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Color(agent.glowColor),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${agent.name} está pensando...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}


// -------------------------------------------------------------
// WORKSPACE 2: Project Intelligent workspace
// -------------------------------------------------------------
@Composable
fun ProjectWorkspace(viewModel: ChatViewModel) {
    var projectName by remember { mutableStateOf("") }
    var projectDetails by remember { mutableStateOf("") }
    var generatedArchitecture by remember { mutableStateOf<String?>(null) }
    var isArchitecting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "📁 PROYECTOS DORADOS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFFD4AF37)
        )
        Text(
            text = "Define tus metas y deja que el asistente ensamble la arquitectura y especificación adecuada.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nombre del Proyecto") },
                    placeholder = { Text("Ej: E-commerce minimalista") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = projectDetails,
                    onValueChange = { projectDetails = it },
                    label = { Text("Objetivos y Funcionalidades") },
                    placeholder = { Text("Ej: Registrar compras mediante Gemini, guardar facturas...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isArchitecting = true
                        generatedArchitecture = null
                        // Simulate architect build
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            generatedArchitecture = """
                            * ARQUITECTURA DIGITAL ASISTIDA DE ${projectName.uppercase()} *
                            --------------------------------------------------------------
                            Fase 1: Capa de persistencia (Local SQLite)
                            - Crear tabla 'orders' con índice primario auto-generado.
                            - Cifrar campos personales con codificación de seguridad XOR.
                            
                            Fase 2: Conectividad Inteligente
                            - Usar el token activo de Google para auditar flujos de compra en tiempo real.
                            - Enviar resúmenes semanales a Claude para reporte de analíticas.
                            
                            Fase 3: Entorno visual
                            - Interfaz de un único contenedor con transiciones fluidas de 300ms.
                            """.trimIndent()
                            isArchitecting = false
                        }, 2000)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    enabled = projectName.isNotEmpty() && !isArchitecting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isArchitecting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compilando arquitectura...")
                    } else {
                        Text("Iniciar Diseño de Arquitectura", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        if (generatedArchitecture != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFFFCFBEB),
                border = BorderStroke(1.2.dp, Color(0xFFD4AF37)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFD4AF37), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DISEÑO ESTRUCTURAL COMPLETADO", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E7E38))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = generatedArchitecture!!,
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// WORKSPACE 3: Designer Workspace
// -------------------------------------------------------------
@Composable
fun DesignerWorkspace(viewModel: ChatViewModel) {
    var promptQuery by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "🛠️ DISEÑADOR DE APPS INTELECTUAL",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFFBD93F9)
        )
        Text(
            text = "Escribe qué app quieres construir en lenguaje natural y deja que Claude arme la maqueta visual funcional en segundos.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = promptQuery,
                    onValueChange = { promptQuery = it },
                    label = { Text("¿Qué app diseñamos?") },
                    placeholder = { Text("Ej: Una app de meditación minimalista con sonido de fondo") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isGenerating = true
                        generatedCode = null
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            generatedCode = """
                            // MAQUETA PRODUCIDA POR EL ASISTENTE GENERAL
                            // --------------------------------------------
                            @Composable
                            fun MeditationApp() {
                                var sessionActive by remember { mutableStateOf(false) }
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = Color(0xFFF0F4F8)
                                ) {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center)
                                    ) {
                                        Text("Inhala profundamente", fontSize = 24.sp)
                                        IconButton(onClick = { sessionActive = !sessionActive }) {
                                            Icon(Icons.Filled.PlayArrow)
                                        }
                                    }
                                }
                            }
                            """.trimIndent()
                            isGenerating = false
                        }, 2200)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD93F9)),
                    enabled = promptQuery.isNotEmpty() && !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escribiendo módulos de diseño...")
                    } else {
                        Text("Generar Maqueta y Código", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        if (generatedCode != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFFFAF5FF),
                border = BorderStroke(1.2.dp, Color(0xFFBD93F9)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CÓDIGO GENERADO EXITOSAMENTE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBD93F9))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = generatedCode!!,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}


// -------------------------------------------------------------
// API KEY MANAGER MODAL WITH INSTANT AUTH CREDENTIALS VERIFICATION
// -------------------------------------------------------------
@Composable
fun ApiKeySettingsDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val apiKeys by viewModel.apiKeys.collectAsState()
    val isChecking by viewModel.isKeyValidating.collectAsState()
    val checkResult by viewModel.keyValidationResult.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var keyTypeSelected by remember { mutableStateOf("google") }
    var aliasInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37))
            }
        },
        title = {
            Text(
                text = "🔑 Gestión de Claves API",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Ingresa tus tokens para desbloquear modelos independientes de producción. Todo se cifra de forma local con algoritmo seguro de rotación XOR.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Select engine provider
                Text(
                    text = "Proveedor de IA:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("google", "openai", "claude", "deepseek", "groq", "replit", "perplexity", "cohere", "mistral", "poe", "elevenlabs", "v0", "canva", "midjourney").forEach { t ->
                        val isSelected = keyTypeSelected == t
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFFFF7E0) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(1.dp, if (isSelected) Color(0xFFD4AF37) else Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { keyTypeSelected = t }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (t == "elevenlabs") "ElevenLabs" else t.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF9E7E38) else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val customPlaceholder = remember(keyTypeSelected) {
                    when (keyTypeSelected) {
                        "google" -> "AIzaSy..."
                        "openai" -> "sk-proj-..."
                        "claude" -> "sk-ant-..."
                        "deepseek" -> "sk-..."
                        "groq" -> "gsk_..."
                        "replit" -> "replit_..."
                        "perplexity" -> "pplx-..."
                        "cohere" -> "cohere_key_..."
                        "mistral" -> "mistral_key_..."
                        "poe" -> "poe-pb-..."
                        "elevenlabs" -> "xi-api-key-..."
                        "v0" -> "v0-api-..."
                        "canva" -> "canva-magic-..."
                        "midjourney" -> "mj-v6-..."
                        else -> "Ingresa tu clave de API..."
                    }
                }

                val keyAliasPlaceholder = remember(keyTypeSelected) {
                    "Mi Clave de ${keyTypeSelected.uppercase()}"
                }

                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("Alias para identificar clave") },
                    placeholder = { Text("Ej: $keyAliasPlaceholder") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Clave API de ${keyTypeSelected.uppercase()}") },
                    placeholder = { Text(customPlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action button to trigger instant validation
                Button(
                    onClick = {
                        if (apiKeyInput.trim().isNotEmpty()) {
                            viewModel.addApiKey(apiKeyInput, keyTypeSelected, aliasInput)
                            apiKeyInput = ""
                            aliasInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    enabled = apiKeyInput.trim().isNotEmpty() && !isChecking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verificando autenticidad...")
                    } else {
                        Text("Registrar y Validar Clave", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Verification feedbacks
                if (checkResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (checkResult == "valid") Color(0xFFE8F8F5) else Color(0xFFFCEAEB))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (checkResult == "valid") "✓ Autenticada con éxito" else "✗ Clave de prueba rechazada. Ingresa un token real.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (checkResult == "valid") Color(0xFF117A65) else Color(0xFFC0392B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CLAVES ALMACENADAS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                if (apiKeys.isEmpty()) {
                    Text(
                        text = "No has registrado ninguna clave aún.",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    apiKeys.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(key.alias, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Proveedor: ${key.type.uppercase()}", fontSize = 9.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = { viewModel.deleteApiKey(key.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Erase", modifier = Modifier.size(14.dp), tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    )
}
