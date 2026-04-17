package com.example.sightlinev3

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sightlinev3.camera.QrAnalyzer
import com.example.sightlinev3.graph.GraphRepository
import com.example.sightlinev3.graph.GraphViewModel
import com.example.sightlinev3.graph.GraphViewModelFactory
import com.example.sightlinev3.graph.RouteState
import com.example.sightlinev3.navigation.NavigationViewModelFactory
import com.example.sightlinev3.llm.GeminiLlmService
import com.example.sightlinev3.navigation.HintState
import com.example.sightlinev3.navigation.NavigationViewModel
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val graphViewModel: GraphViewModel by viewModels {
            GraphViewModelFactory(GraphRepository(this)) // Inject the repository dependency through the factory
        }

        val navigationViewModel: NavigationViewModel by viewModels {
            NavigationViewModelFactory(GeminiLlmService())
        }

        setContent {
            CameraScreen(
                graphViewModel = graphViewModel,
                navigationViewModel = navigationViewModel
                )
        }
    }

    @Composable
    fun CameraScreen(
        graphViewModel: GraphViewModel,
        navigationViewModel: NavigationViewModel
    ) {
        val permission = Manifest.permission.CAMERA
        val currentNode by graphViewModel.currentNode.collectAsState()
        val hintState by navigationViewModel.hintState.collectAsState()
        val routeState by graphViewModel.routeState.collectAsState()
        val currentStepIndex by graphViewModel.currentStepIndex.collectAsState()

        val isLoading = hintState is HintState.Loading
        var hasPermission by remember { mutableStateOf(false) }
        val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (LocalContext.current.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            LocalContext.current.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val context = LocalContext.current
        val tts = remember {
            TextToSpeech(context, null).apply {
                language = Locale.UK  // or Locale.US
            }
        }

        DisposableEffect(Unit) {
            onDispose { tts.shutdown() }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun vibrate() {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        /**
         * Speak whenever a new successful response arrives
         */
        LaunchedEffect(hintState) {
            if (hintState is HintState.Success) {
                tts.speak(
                    (hintState as HintState.Success).text,
                    TextToSpeech.QUEUE_FLUSH,  // interrupts any current speech
                    null,
                    "hint_utterance"
                )
            }
        }


        /**
         * Build route context string from current state
         */
        val routeContext = when (val route = routeState) {
            is RouteState.Active -> {
                val currentStep = route.steps.getOrNull(currentStepIndex)
                val nextStep = route.steps.getOrNull(currentStepIndex + 1)
                "currently at ${currentStep?.name}." +
                        "next checkpoint: ${nextStep?.name}." +
                        "visual cue for route: ${nextStep?.description}."
            }
            else -> null
        }

        /**
         * Capture image when next checkpoint QR Code is seen
         */
        LaunchedEffect(Unit) {
            graphViewModel.checkpointReached.collect { step ->
                if(navigationViewModel.hintState.value is HintState.Loading) return@collect
                graphViewModel.advanceStep(step.id)
                val capture = imageCaptureRef.value ?: return@collect
                val routeContext = "Scanned Entrance To ${step.name}" +
                        ", Visual description: ${step.description}"

                capture.takePicture(
                    ContextCompat.getMainExecutor(this@MainActivity),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            image.close()
                            vibrate()
                            navigationViewModel.describeAtCheckpoint(bitmap, routeContext)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("CHECKPOINT", "Capture failed: ${exception.message}")
                        }
                    }
                )
            }
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted -> hasPermission = isGranted }

        /**
         * STT Launcher for general queries
         */
        val sttLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@rememberLauncherForActivityResult

            val capture = imageCaptureRef.value ?: return@rememberLauncherForActivityResult

            capture.takePicture(
                ContextCompat.getMainExecutor(this@MainActivity),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.toBitmap()
                        image.close()
                        vibrate()
                        navigationViewModel.describeWithQuery(bitmap, spokenText, routeContext)
                    }
                    override fun onError(e: ImageCaptureException) {
                        e.printStackTrace()
                    }
                }
            )
        }

        /**
         * STT Launcher for starting a route
         */
        val destinationSttLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@rememberLauncherForActivityResult

            Log.d("STT", "Destination query: $spokenText")
            graphViewModel.startNavigation(spokenText)
        }

        LaunchedEffect(Unit) {
            launcher.launch(permission)
        }

        Box(modifier = Modifier.fillMaxSize()) {

            // ── Camera feed fills screen ──────────────────────────────────
            if (hasPermission) {
                CameraPreview(
                    graphViewModel = graphViewModel,
                    onImageCaptureReady = { imageCaptureRef.value = it }
                )
            }

            // ── Dark scrim at top for status text ─────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    // Location
                    Text(
                        text = currentNode?.name?.uppercase() ?: "NOT LOCALISED",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Route status
                    when (val route = routeState) {
                        is RouteState.Active -> Text(
                            text = "NAVIGATING TO ${route.destination.name.uppercase()}  •  " +
                                    "STEP ${currentStepIndex + 1} OF ${route.steps.size}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        is RouteState.Reached -> Text(
                            text = "✓  ARRIVED AT ${route.destination.name.uppercase()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E676)
                        )
                        is RouteState.Error -> Text(
                            text = route.message,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                        is RouteState.Idle -> Text(
                            text = "SIGHTLINE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            // ── Gemini response card — centre screen ──────────────────────
            when (val state = hintState) {
                is HintState.Loading -> Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                }
                is HintState.Success -> Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = state.text,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                is HintState.Error -> Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .background(Color(0xFFB71C1C).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = state.message,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
                else -> {}
            }

            // ── Dark scrim at bottom for buttons ──────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(bottom = 32.dp, top = 40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Bottom-left — Describe surroundings (Use case 1)
                    LargeAccessibleButton(
                        label = "DESCRIBE",
                        icon = "👁",
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        onClick = {
                            val capture = imageCaptureRef.value ?: return@LargeAccessibleButton
                            capture.takePicture(
                                ContextCompat.getMainExecutor(this@MainActivity),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bitmap = image.toBitmap()
                                        image.close()
                                        vibrate()
                                        navigationViewModel.describeEnvironment(bitmap, routeContext)
                                    }

                                    override fun onError(e: ImageCaptureException) {
                                        e.printStackTrace()
                                    }
                                }
                            )}
                    )

                    // Bottom-centre — Where to go (Use case 3)
                    LargeAccessibleButton(
                        label = "NAVIGATE",
                        icon = "🧭",
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Where do you want to go?")
                            }
                            destinationSttLauncher.launch(intent)
                        }
                    )

                    // Bottom-right — Ask a question (Use case 2)
                    LargeAccessibleButton(
                        label = "ASK",
                        icon = "🎤",
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask a question")
                            }
                            sttLauncher.launch(intent)
                        }
                    )
                }
            }
        }

    }

    @Composable
    fun LargeAccessibleButton(
        label: String,
        icon: String,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.15f),
                disabledContainerColor = Color.White.copy(alpha = 0.05f)
            ),
            border = BorderStroke(
                width = 2.dp,
                color = if (enabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
            ),
            contentPadding = PaddingValues(8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }

    /**
     * Setup the Camera View UI
     */
    @Composable
    fun CameraPreview(
        graphViewModel: GraphViewModel,
        onImageCaptureReady: (ImageCapture) -> Unit
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                previewView.post {
                    /* Get the camera system, this accesses the device cameras and lifecycle control
               , and is also async 'Future'                                                 */
                    val cameraProvideFuture = ProcessCameraProvider.getInstance(ctx)

                    // Wait until Camera is ready
                    cameraProvideFuture.addListener({
                        val cameraProvider = cameraProvideFuture.get()

                        // Connect Camera to previewView, nothing will display without this
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        // Setup image capture
                        val imageCapture = ImageCapture.Builder().build()
                        onImageCaptureReady(imageCapture)

                        // Build image analyzer to send frames to the backend
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val analyzer = QrAnalyzer { qrValue ->
                            graphViewModel.onQrScanned(qrValue)
                        }

                        // attach the 'QrAnalyzer' class
                        imageAnalysis.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx),
                            analyzer
                        )

                        // Choose which camera to use on the device
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        // bind everything to lifecycle so it persists
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
                previewView
            }
        )
    }
}