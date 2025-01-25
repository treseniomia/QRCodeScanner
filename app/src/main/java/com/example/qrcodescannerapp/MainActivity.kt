package com.example.qrcodescannerapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        resultTextView = findViewById(R.id.resultTextView)

        // Initialize a single-thread executor for camera operations
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permission
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                resultTextView.text = "Camera permission is required."
            }
        }

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Set up the preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Set up the image analysis use case
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            // Set up the camera selector (back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Bind the use cases to the lifecycle
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                resultTextView.text = "Failed to bind camera use cases."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        // Safely get the media image from the image proxy
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                // Initialize BarcodeScanner
                val barcodeScanner = BarcodeScanning.getClient()

                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            handleBarcode(barcode)
                        }
                    }
                    .addOnFailureListener {
                        resultTextView.text = "Failed to scan QR code."
                    }
                    .addOnCompleteListener {
                        imageProxy.close() // Ensure the imageProxy is always closed
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                resultTextView.text = "Error processing image."
                imageProxy.close()
            }
        } else {
            // Log if imageProxy.image is null
            resultTextView.text = "No valid image available for analysis."
            imageProxy.close()
        }
    }

    private fun handleBarcode(barcode: Barcode) {
        val url = barcode.url?.url ?: barcode.displayValue
        if (url != null) {
            resultTextView.text = url
            resultTextView.setOnClickListener {
                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("url", url)
                startActivity(intent)
            }
        } else {
            resultTextView.text = "No QR Code detected."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
