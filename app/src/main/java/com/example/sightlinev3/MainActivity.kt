package com.example.sightlinev3

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sightlinev3.camera.QrAnalyzer
import com.example.sightlinev3.graph.GraphRepository
import com.example.sightlinev3.graph.GraphViewModel
import com.example.sightlinev3.graph.GraphViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: GraphViewModel by viewModels {
        GraphViewModelFactory(GraphRepository(this)) // Inject the repository dependency through the factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CameraScreen()
        }
    }
}

@Composable
fun GraphScreen(onRunPathfinding: () -> Unit) {
    Column {
        Button(onClick = { onRunPathfinding() }) { // Passing callbacks, this runs whatever function is passed into it
            Text("Run Pathfinding")
        }
    }
}

@Composable
fun CameraScreen() {
    val permission = Manifest.permission.CAMERA

    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Red)) {
        if(hasPermission){
            CameraPreview()
        } else {
            Text("Camera permission required")
        }
    }

}

/**
 * Setup the Camera View UI
 */
@Composable
fun CameraPreview() {
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

                    // Build image analyzer to send frames to the backend
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val analyzer = QrAnalyzer { qrValue ->
                        // send to viewmodel later
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
                        imageAnalysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
            }
            previewView
        }
    )
}