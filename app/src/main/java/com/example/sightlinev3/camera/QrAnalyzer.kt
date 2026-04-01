package com.example.sightlinev3.camera

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class QrAnalyzer (private val onQRDetected: (String) -> Unit ) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    /**
     * Process the image using the Google ML Kit to
     * scan for QR codes, call the lambda with the raw
     * QR value as the argument, this calls 'graphViewModel.onQrScanned()'
     */
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if(mediaImage != null) {

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                image.imageInfo.rotationDegrees
            )

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val value = barcode.rawValue

                        if (value != null) {
                            Log.d("QR_ANALYZER", "QR: $value")
                            onQRDetected(value)
                        }
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
                .addOnCompleteListener {
                    image.close()
                }
        } else {
            image.close()
        }
    }

}