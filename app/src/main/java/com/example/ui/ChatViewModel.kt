package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ApiKey
import com.example.data.ChatDatabase
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.EncryptionUtils
import com.example.network.ApiKeyValidator
import com.example.network.Content as NetContent
import com.example.network.GenerateContentRequest
import com.example.network.GenerationConfig
import com.example.network.InlineData
import com.example.network.Part as NetPart
import com.example.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

// Representation of an AI Agent
data class Agent(
    val id: String,
    val name: String,
    val keyType: String, // "google", "claude", "poe", "elevenlabs"
    val description: String,
    val initialMessage: String,
    val glowColor: Long, // Hex color for glowing
    val initials: String,
    val category: String // "programar", "tareas", "variadas", "diseno"
)

sealed class ViewMode {
    object StandardChat : ViewMode()
    object ProjectPlanner : ViewMode()
    class Designer(val type: String) : ViewMode()
}

class ChatViewModel(context: Context) : ViewModel() {
    private val database = ChatDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val apiKeyDao = database.apiKeyDao()

    // --- State Streams ---
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _apiKeys = MutableStateFlow<List<ApiKey>>(emptyList())
    val apiKeys: StateFlow<List<ApiKey>> = _apiKeys.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    // Key input statuses
    private val _isKeyValidating = MutableStateFlow(false)
    val isKeyValidating: StateFlow<Boolean> = _isKeyValidating.asStateFlow()

    private val _keyValidationResult = MutableStateFlow<String?>(null) // "valid", "invalid", null
    val keyValidationResult: StateFlow<String?> = _keyValidationResult.asStateFlow()

    // Chat activity states
    private val _isLoadingResponse = MutableStateFlow(false)
    val isLoadingResponse: StateFlow<Boolean> = _isLoadingResponse.asStateFlow()

    private val _responseTimeMs = MutableStateFlow<Long?>(null)
    val responseTimeMs: StateFlow<Long?> = _responseTimeMs.asStateFlow()

    // Custom Mode Selector
    private val _currentWorkMode = MutableStateFlow<ViewMode>(ViewMode.StandardChat)
    val currentWorkMode: StateFlow<ViewMode> = _currentWorkMode.asStateFlow()

    // App Colors/Customization
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // --- Static Agent Configurations ---
    val allAgents = listOf(
        Agent(
            id = "gemini_35_flash",
            name = "Gemini Flash (Pro)",
            keyType = "google",
            description = "Modelo ultra rápido para respuestas creativas y análisis.",
            initialMessage = "¡Hola! Estoy listo para conversar contigo usando Gemini 3.5 Flash en tiempo real.",
            glowColor = 0xFFD4AF37, // Brilliant Gold
            initials = "G⚡",
            category = "variadas"
        ),
        Agent(
            id = "gemini_31_pro",
            name = "Gemini 3.1 Pro",
            keyType = "google",
            description = "Máxima inteligencia para razonamiento lógico y código complejo.",
            initialMessage = "Activado agente de alta fidelidad Gemini 3.1 Pro. ¿En qué lógica compleja profundizamos hoy?",
            glowColor = 0xFFE5A93B, // Warm Gold
            initials = "GP",
            category = "programar"
        ),
        Agent(
            id = "openai_gpt4o",
            name = "GPT-4o (OpenAI)",
            keyType = "openai",
            description = "Asistente insignia multi-modal de OpenAI con razonamiento superior.",
            initialMessage = "¡Hola! GPT-4o conectado. Listo para resolver tus dudas de texto, lógica y visión.",
            glowColor = 0xFF10A37F, // OpenAI Emerald Green
            initials = "O4",
            category = "variadas"
        ),
        Agent(
            id = "claude_sonnet",
            name = "Claude 3.5 Sonnet",
            keyType = "claude",
            description = "Especialista en estructuración de proyectos y diseño funcional.",
            initialMessage = "Claude 3.5 Sonnet inicializado con tu API Key. Diseñemos la arquitectura perfecta.",
            glowColor = 0xFFFF6B6B, // Coral Red
            initials = "CS",
            category = "programar"
        ),
        Agent(
            id = "claude_opus",
            name = "Claude 3 Opus",
            keyType = "claude",
            description = "Maestro escritor de código interactivo y narración profunda.",
            initialMessage = "Hola, soy Claude 3 Opus. Listo para detallar proyectos ambiciosos.",
            glowColor = 0xFFBD93F9, // Purple
            initials = "CO",
            category = "programar"
        ),
        Agent(
            id = "deepseek_v3",
            name = "DeepSeek V3",
            keyType = "deepseek",
            description = "Inteligencia revolucionaria con máxima potencia técnica y matemática.",
            initialMessage = "Hola, DeepSeek-V3 conectado. Optimizado para programar o resolver conceptos complejos.",
            glowColor = 0xFF2D62EC, // DeepSeek Blue
            initials = "DS",
            category = "programar"
        ),
        Agent(
            id = "groq_llama3",
            name = "Groq LLaMA 3",
            keyType = "groq",
            description = "Inferencia ultra-veloz para respuestas instantáneas de baja latencia.",
            initialMessage = "¡Bip bip! LLaMA-3 en Groq listo a velocidad warp. ¡Escribe lo que quieras!",
            glowColor = 0xFFF55030, // Groq Orange/Red
            initials = "GR",
            category = "variadas"
        ),
        Agent(
            id = "replit_agent",
            name = "Replit Agent",
            keyType = "replit",
            description = "Copiloto especialista en entornos sandbox y programación ágil.",
            initialMessage = "Replit Agent iniciado. Listo para estructurar y depurar tu código en tiempo real.",
            glowColor = 0xFFED1B24, // Replit Crimson
            initials = "RA",
            category = "programar"
        ),
        Agent(
            id = "perplexity_pro",
            name = "Perplexity Pro",
            keyType = "perplexity",
            description = "Motor de búsqueda con citas web y fuentes verificadas en vivo.",
            initialMessage = "Perplexity Copilot en línea. Listo para rastrear la web y darte respuestas respaldadas.",
            glowColor = 0xFF19AD9F, // Perplexity Teal
            initials = "PX",
            category = "tareas"
        ),
        Agent(
            id = "cohere_command",
            name = "Cohere Command",
            keyType = "cohere",
            description = "Modelos empresariales de alta fidelidad especializados en análisis RAG.",
            initialMessage = "Cohere Command listo. Configurado para clasificar, traducir y resumir a gran escala.",
            glowColor = 0xFF5D3FD3, // Cohere Purple
            initials = "CH",
            category = "tareas"
        ),
        Agent(
            id = "mistral_large",
            name = "Mistral Large",
            keyType = "mistral",
            description = "El modelo soberano europeo con excelente dominio multilingüe.",
            initialMessage = "Bonjour! Mistral Large iniciado. Apasionado por la precisión y el estilo impecable.",
            glowColor = 0xFFFD5C22, // Mistral Orange
            initials = "MI",
            category = "variadas"
        ),
        Agent(
            id = "poe_gpt",
            name = "Poe GPT Partner",
            keyType = "poe",
            description = "Asistente inteligente dinámico de Poe para diálogos ágiles.",
            initialMessage = "Tu bot personalizado de Poe está conectado. ¡Dime qué plan ejecutamos!",
            glowColor = 0xFF50FA7B, // Green
            initials = "P⚡",
            category = "variadas"
        ),
        Agent(
            id = "eleven_labs_tts",
            name = "ElevenLabs Vocal",
            keyType = "elevenlabs",
            description = "Generador y lector de contenido con voz hiper-realista.",
            initialMessage = "Canal de voz de ElevenLabs activo. Escribe texto que quieras convertir en locución.",
            glowColor = 0xFFFF79C6, // Pink
            initials = "EV",
            category = "tareas"
        ),
        Agent(
            id = "v0_vercel",
            name = "v0 by Vercel",
            keyType = "v0",
            description = "Generador reactivo e interactivo de interfaces web y componentes UI elegantes.",
            initialMessage = "v0 en línea. ¿Qué interfaz web, formulario o landing page diseñamos hoy? Dame directrices detalladas.",
            glowColor = 0xFF00C6FF, // Vercel Vibrant Cyan
            initials = "V0",
            category = "diseno"
        ),
        Agent(
            id = "canva_magic",
            name = "Canva Magic Design",
            keyType = "canva",
            description = "Generador inteligente de plantillas, banners de mi marca y diagramas visuales.",
            initialMessage = "Canva Magic Design listo. ¿Cuál es el concepto visual o de marketing que requieres estructurar?",
            glowColor = 0xFF7D2AE8, // Purple
            initials = "CV",
            category = "diseno"
        ),
        Agent(
            id = "midjourney_v6",
            name = "Midjourney V6",
            keyType = "midjourney",
            description = "Maestro de arte conceptual, visuales fotorealistas y maquetaciones de UI artísticas.",
            initialMessage = "Midjourney V6 inicializado. Describe la atmósfera creativa, colores y elementos de tu template de ensueño.",
            glowColor = 0xFFFF79C6, // Pinkish Magenta
            initials = "MJ",
            category = "diseno"
        )
    )

    private val _selectedAgent = MutableStateFlow<Agent>(allAgents.first())
    val selectedAgent: StateFlow<Agent> = _selectedAgent.asStateFlow()

    init {
        // Collect DB updates
        viewModelScope.launch {
            chatDao.getAllSessions().collect {
                _sessions.value = it
                if (it.isNotEmpty() && _currentSession.value == null) {
                    selectSession(it.first())
                }
            }
        }

        viewModelScope.launch {
            apiKeyDao.getAllApiKeysFlow().collect {
                _apiKeys.value = it
            }
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setWorkMode(mode: ViewMode) {
        _currentWorkMode.value = mode
    }

    fun selectAgent(agent: Agent) {
        _selectedAgent.value = agent
        // Add a clean greeting from the agent to the current session if it represents a brand new conversational path
        viewModelScope.launch {
            val session = _currentSession.value
            if (session != null) {
                // If session is empty, insert agent's initial greeting
                val existing = _messages.value
                if (existing.isEmpty()) {
                    val welcomeMsg = ChatMessage(
                        sessionId = session.id,
                        role = "model",
                        encryptedText = EncryptionUtils.encrypt(agent.initialMessage)
                    )
                    chatDao.insertMessage(welcomeMsg)
                }
            }
        }
    }

    // --- API Key Management (Grouped by Provider & Immediate validation) ---

    fun addApiKey(rawKey: String, type: String, alias: String) {
        if (rawKey.trim().isEmpty()) return
        viewModelScope.launch {
            _isKeyValidating.value = true
            _keyValidationResult.value = null

            val isValid = if (type == "google") {
                // Real Validation call
                ApiKeyValidator.validateGoogleKey(rawKey)
            } else {
                // Simulation for Claude/Poe/ElevenLabs API key checks
                delay(1200)
                rawKey.trim().length > 12 // Requires a realistic length to pass
            }

            if (isValid) {
                val dbKey = ApiKey(
                    keyEncrypted = EncryptionUtils.encrypt(rawKey.trim()),
                    type = type,
                    alias = alias.ifEmpty { "Clave ${type.uppercase()}" },
                    isValid = true
                )
                apiKeyDao.insertApiKey(dbKey)
                _keyValidationResult.value = "valid"

                // Auto select corresponding agent if applicable
                val suitableAgent = allAgents.firstOrNull { it.keyType == type }
                if (suitableAgent != null) {
                    selectAgent(suitableAgent)
                }
            } else {
                _keyValidationResult.value = "invalid"
            }
            _isKeyValidating.value = false
        }
    }

    fun deleteApiKey(id: Int) {
        viewModelScope.launch {
            apiKeyDao.deleteApiKeyById(id)
        }
    }

    // --- Session Actions ---

    fun createSession(title: String, type: String = "standard") {
        viewModelScope.launch {
            val session = ChatSession(
                id = UUID.randomUUID().toString(),
                title = title.ifEmpty { "Conversación Gemini" },
                type = type
            )
            chatDao.insertSession(session)
            selectSession(session)
        }
    }

    fun selectSession(session: ChatSession) {
        _currentSession.value = session
        // Collect messages
        viewModelScope.launch {
            chatDao.getMessagesForSession(session.id)
                .catch { e -> e.printStackTrace() }
                .collect {
                    _messages.value = it
                }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatDao.deleteMessagesForSession(sessionId)
            chatDao.deleteSessionById(sessionId)
            if (_currentSession.value?.id == sessionId) {
                _currentSession.value = null
                _messages.value = emptyList()
            }
        }
    }

    // --- Conversational Chat with Image analysis ---

    fun sendMessage(text: String, imageUri: Uri?, context: Context) {
        val session = _currentSession.value ?: return
        if (text.trim().isEmpty() && imageUri == null) return

        viewModelScope.launch {
            // 1. Insert User Message
            val encryptedText = EncryptionUtils.encrypt(text)
            val userMsg = ChatMessage(
                sessionId = session.id,
                role = "user",
                encryptedText = encryptedText,
                imageUri = imageUri?.toString()
            )
            chatDao.insertMessage(userMsg)

            _isLoadingResponse.value = true
            _responseTimeMs.value = null
            val startTime = System.currentTimeMillis()

            // 2. Resolve Key & Model Logic
            val agent = _selectedAgent.value
            val appropriateKeys = _apiKeys.value.filter { it.type == agent.keyType && it.isValid }

            val responseString = if (agent.keyType == "google" && appropriateKeys.isNotEmpty()) {
                // Call true Gemini Rest Service
                val selectedKeyDecrypted = EncryptionUtils.decrypt(appropriateKeys.first().keyEncrypted)
                executeGeminiRestCall(text, imageUri, selectedKeyDecrypted, agent.id, context)
            } else {
                // Fallback simulation representing respective agents
                simulateResponseForAgent(agent, text, imageUri != null)
            }

            val endTime = System.currentTimeMillis()
            _responseTimeMs.value = (endTime - startTime)

            // 3. Insert Model Message
            val encryptedResponse = EncryptionUtils.encrypt(responseString)
            val modelMsg = ChatMessage(
                sessionId = session.id,
                role = "model",
                encryptedText = encryptedResponse
            )
            chatDao.insertMessage(modelMsg)
            _isLoadingResponse.value = false
        }
    }

    private suspend fun executeGeminiRestCall(
        prompt: String,
        imageUri: Uri?,
        apiKey: String,
        agentId: String,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        try {
            val mappedModelName = if (agentId == "gemini_31_pro") {
                "gemini-3.1-pro-preview"
            } else {
                "gemini-3.5-flash"
            }

            // Build appropriate prompt request list
            val partsList = mutableListOf<NetPart>()

            // Add Text
            if (prompt.trim().isNotEmpty()) {
                partsList.add(NetPart(text = prompt))
            } else if (imageUri != null) {
                // Prompt default context for picture
                partsList.add(NetPart(text = "Analiza detalladamente esta imagen."))
            }

            // Add Multi-modal Image Base64 from Uri if attached
            if (imageUri != null) {
                val base64Data = convertUriToBase64(imageUri, context)
                if (base64Data != null) {
                    val mineType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                    partsList.add(
                        NetPart(
                            inlineData = InlineData(
                                mimeType = mineType,
                                data = base64Data
                            )
                        )
                    )
                }
            }

            val request = GenerateContentRequest(
                contents = listOf(NetContent(parts = partsList)),
                generationConfig = GenerationConfig(temperature = 0.7f)
            )

            val rawResponse = RetrofitClient.service.generateContent(
                model = mappedModelName,
                apiKey = apiKey,
                request = request
            )

            rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Recibí una respuesta pero no contenía datos de texto."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error en la llamada API: ${e.localizedMessage ?: "Conexión rechazada. Verifica que tu api key sea válida."}"
        }
    }

    // Helper method to convert an Image Uri to Base64 strictly
    private fun convertUriToBase64(uri: Uri, context: Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Scale down bitmap to save tokens and request size safely
            val finalBitmap = scaleBitmap(bitmap, 800)
            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        return if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (ratio > 1) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }

    private suspend fun simulateResponseForAgent(agent: Agent, prompt: String, hasImage: Boolean): String {
        delay(1800) // Realistic delay

        val lowerPrompt = prompt.lowercase()
        val imageTextContext = if (hasImage) " que obtuve al analizar tu imagen cargada" else ""

        return when (agent.id) {
            "openai_gpt4o" -> {
                "🔑 *[Simulación de OpenAI GPT-4o]*\nAnalizando tu consulta con razonamiento avanzado de OpenAI$imageTextContext:\n\nHe procesado tu prompt de entrada y aquí tienes la respuesta optimizada:\n- **Lógica principal**: Desarrollar funciones puras y desacopladas.\n- **Visión / Imagen**: Patrones extraídos con alta resolución espectral.\n\n¿Quieres que profundicemos en algún aspecto específico?"
            }
            "claude_sonnet" -> {
                "🔑 *[Simulación de Claude 3.5 Sonnet]*\nAnalizando detalladamente tu requerimiento arquitectónico$imageTextContext:\n\nPara el desarrollo de tu idea, sugiero estructurar los siguientes módulos base:\n1. Módulo de Autenticación mediante Tokens.\n2. Almacenamiento local mediante SQLite/Room.\n3. Integración asíncrona de WebSockets para comunicación persistente.\n"
            }
            "claude_opus" -> {
                "🔑 *[Simulación de Claude 3 Opus]*\nProcesando tu requerimiento creativo$imageTextContext.\n\nHe compilado la lógica de negocio y su respectiva maqueta interactiva. El código fuente cumple con estándares de alto rendimiento:\n```kotlin\nclass ApplicationBuilder {\n    fun designApp() = println(\"Estructurando aplicación modular con Claude Opus...\")\n}\n```"
            }
            "deepseek_v3" -> {
                "🔑 *[Simulación de DeepSeek-V3]*\nProcesando consulta técnica de alta complejidad en DeepSeek clusters$imageTextContext.\n\n*Análisis del problema:* El algoritmo ha sido balanceado para optimizar recursos computacionales.\nResultado óptimo obtenido:\n```math\nO(N \\log N) \\rightarrow \\text{Estructura de Datos Balanceada}\n```"
            }
            "groq_llama3" -> {
                "🔑 *[Simulación de Groq ⚡ LLaMA-3]*\n[Tiempo de inferencia: 42ms | Rendimiento: 850 token/seg]\n\nRespuesta instantánea$imageTextContext:\nTareas procesadas en paralelo exitosamente. ¿Cuál es el siguiente comando a nivel del kernel?"
            }
            "replit_agent" -> {
                "🔑 *[Simulación de Replit Agent]*\nIniciando contenedor sandbox virtual...\nEstableciendo dependencias locales...\nReplit Agent ha resuelto tu prompt con un entorno web interactivo listo para probar en tu browser a través de un workspace virtual."
            }
            "perplexity_pro" -> {
                "🔑 *[Simulación de Perplexity Search]*\nBuscando fuentes actualizadas en la web para su consulta...\n- [1] Redes Neuronales Modernas (https://arxiv.org)\n- [2] Avances en APIs Inteligentes, Mayo 2026.\n\nSintetizando información$imageTextContext:\nLas principales tendencias convergen en una inferencia más veloz de tipo local combinada con llamadas seguras en la nube."
            }
            "cohere_command" -> {
                "🔑 *[Simulación de Cohere Command]*\nProcesando contexto y aplicando modelo de Recuperación Aumentada (RAG)$imageTextContext.\n\nSegmentación semántica:\n- Cohere detecta el tono clave y filtra el contenido redundante, devolviendo un resumen ejecutivo óptimo para reportes y toma de decisiones corporativas."
            }
            "mistral_large" -> {
                "🔑 *[Simulación de Mistral Large]*\nBonjour! Procesando tu solicitud lingüística con Mistral Large$imageTextContext.\n\nHe estructurado la respuesta con total elegancia y alineación europea de alta fidelidad:\n- Lenguaje refinado y preciso.\n- Respuestas estructuradas con sintaxis óptima en múltiples idiomas."
            }
            "poe_gpt" -> {
                "🔑 *[Simulación de Poe Bot]*\n¡Entendido! Me he enlazado al canal inteligente para procesar tu consulta: \"$prompt\"$imageTextContext.\nAquí tienes la respuesta optimizada listando las mejores alternativas del mercado."
            }
            "eleven_labs_tts" -> {
                "🔑 *[Simulación de ElevenLabs Voice]*\nGenerando archivo de audio sintético con voz del locutor preseleccionado.\nTexto convertido: \"$prompt\".\n\n📢 *[Voz Activa]*: \"Hola, esta es una demostración de síntesis de voz premium generada eficazmente basada en tu API Key de ElevenLabs.\""
            }
            "v0_vercel" -> {
                "🔑 *[Simulación de v0 by Vercel]*\nGenerando el diseño del componente interactivo de acuerdo a tu solicitud:\n\n*Plantilla generada (HTML, Tailwind CSS, React):*\n```tsx\nimport React from 'react';\n\nexport default function Component() {\n  return (\n    <div className=\"p-8 bg-zinc-950 text-white rounded-2xl border border-zinc-800 shadow-2xl\">\n      <h2 className=\"text-2xl font-black bg-gradient-to-r from-cyan-400 to-blue-500 bg-clip-text text-transparent\">Dashboard de Control Digital</h2>\n      <p className=\"text-zinc-400 mt-2\">Diseño responsivo optimizado por el motor v0 de Vercel.</p>\n      <button className=\"mt-6 px-5 py-2.5 bg-cyan-500 text-zinc-950 font-semibold rounded-lg hover:scale-105 transition-all\">Explorar Analíticas</button>\n    </div>\n  );\n}\n```"
            }
            "canva_magic" -> {
                "🔑 *[Simulación de Canva Magic Design]*\nCompilando una gama de plantillas corporativas inspiradas en tu idea:\n\n*Resultado de Recursos Visuales:*\n- **Paleta de Colores**: Coral Radiante `#FF6B6B` y Azul Profundo `#1E293B`.\n- **Tipografía**: Montserrat Bold (Títulos) + Inter Regular (Cuerpo).\n- **Banners Disponibles**: 3 variaciones horizontales y 2 verticales listos para redes sociales con layouts preestablecidos de alta conversión."
            }
            "midjourney_v6" -> {
                "🔑 *[Simulación de Midjourney V6]*\nProcesando el Prompt Generativo /imagine prompt: \"$prompt\" con la versión v6 de Midjourney...\n\n*Estructurando imagen conceptual y texturas:*\n```text\nVisual style: Photorealistic UI mockups meet futuristic cyberpunk aesthetics\nLighting: Ambient volumetric light, cybernetic gold glows\nDetails: Ultra-crisp, depth-of-field, Hasselblad 8k photography style\n```\nHecho. La composición de la interfaz del template ha cargado un render espectacular con reflejos de cristal pulido."
            }
            else -> {
                "🔑 *[Licencia Agente]*\nNo has provisto una API Key registrada de tipo **${agent.keyType.uppercase()}** para realizar una conexión de producción real. \nHe activado el simulador del agente **${agent.name}** para procesar: \"$prompt\"$imageTextContext."
            }
        }
    }
}

class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
