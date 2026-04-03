package com.example.sightlinev3

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
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
import com.example.sightlinev3.navigation.NavigationViewModelFactory
import com.example.sightlinev3.llm.GeminiLlmService
import com.example.sightlinev3.navigation.HintState
import com.example.sightlinev3.navigation.NavigationViewModel

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

        var hasPermission by remember { mutableStateOf(false) }
        val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted -> hasPermission = isGranted }

        // Speech-To-Text launcher
        // launches when user finished speaking
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
                        navigationViewModel.describeWithQuery(bitmap, spokenText)
                    }
                    override fun onError(e: ImageCaptureException) {
                        e.printStackTrace()
                    }
                }
            )
        }

        LaunchedEffect(Unit) {
            launcher.launch(permission)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(
                    graphViewModel = graphViewModel,
                    onImageCaptureReady = { imageCaptureRef.value = it }

                )
            }


            /**
             * Image Capture text and button, shows the user:
             * - the state the API is currently in, as well as success and error messsages
             * - what node the user is currently localized at
             * - a button to capture an image and send to Gemini API
             */
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                when (val state = hintState) {
                    is HintState.Idle -> {}
                    is HintState.Loading -> Text("Thinking...", color = Color.White)
                    is HintState.Success -> Text(state.text, color = Color.White)
                    is HintState.Error -> Text("Error: ${state.message}", color = Color.Red)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currentNode?.name ?: "No location",
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    val capture = imageCaptureRef.value ?: return@Button
                    capture.takePicture(
                        ContextCompat.getMainExecutor(this@MainActivity),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap()
                                image.close()
                                navigationViewModel.describeEnvironment(bitmap)
                            }

                            override fun onError(e: ImageCaptureException) {
                                e.printStackTrace()
                            }
                        }
                    )
                }) {
                    Text("Describe Surroundings")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask a question")
                    }
                    sttLauncher.launch(intent)
                }) {
                    Text("Ask a question")
                }
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