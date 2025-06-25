package com.example.cameraapplication.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.camera.view.PreviewView

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.cameraapplication.R
import com.example.cameraapplication.databinding.ActivityCameraBinding
import com.example.cameraapplication.models.PhotoType
import com.example.cameraapplication.utils.ImageCropper
import com.example.cameraapplication.utils.ImageSaver
import com.example.cameraapplication.utils.ImageValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageCapture: ImageCapture
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private var lastAcceleration = FloatArray(3)
    private var alignmentStartTime: Long = 0
    private var isAligned = false

    private var selectedPhotoType: PhotoType = PhotoType.ID_PHOTO

    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make status bar black
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)

        // Immersive Fullscreen Mode
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        binding.previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE

         /*------Photo Type Spinner Setup------*/
        setupRatioSelector()


        /*------Check permissions------*/
        if (allPermissionsGranted()) {
            Handler(Looper.getMainLooper()).postDelayed({
                startCamera()
            }, 300)
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }


        binding.btnCapture.setOnClickListener {
            if (!isAligned) {
                Toast.makeText(this, "Please hold steady before capturing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            capturePhoto()
        }

        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

            startCamera(selectedPhotoType.cameraXAspectRatio)

            binding.btnSwitchCamera.animate()
                .rotationBy(180f)
                .setDuration(300)
                .start()
        }


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
    }


    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val alpha = 0.8f
            val threshold = 0.5f

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val dx = Math.abs(lastAcceleration[0] - x)
            val dy = Math.abs(lastAcceleration[1] - y)
            val dz = Math.abs(lastAcceleration[2] - z)

            if (dx > threshold || dy > threshold || dz > threshold) {
                alignmentStartTime = 0
                binding.frameOverlay.updateFrameColor(false)
                isAligned = false
            } else {
                if (alignmentStartTime == 0L) {
                    alignmentStartTime = System.currentTimeMillis()
                }
                if (System.currentTimeMillis() - alignmentStartTime > 1000) {
                    binding.frameOverlay.updateFrameColor(true)
                    isAligned = true
                }
            }

            lastAcceleration[0] = alpha * lastAcceleration[0] + (1 - alpha) * x
            lastAcceleration[1] = alpha * lastAcceleration[1] + (1 - alpha) * y
            lastAcceleration[2] = alpha * lastAcceleration[2] + (1 - alpha) * z
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }


    private fun startCamera(@AspectRatio.Ratio aspectRatio: Int = AspectRatio.RATIO_4_3) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(binding.previewView.display.rotation)
                .build()
                .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(binding.previewView.display.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera binding failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun capturePhoto() {

        if (!isAligned) {
            Toast.makeText(this, "Please hold steady before capturing.", Toast.LENGTH_SHORT).show()
            return
        }
        // Flash && scale animation
        binding.previewView.animate()
            .alpha(0.5f)
            .setDuration(100)
            .withEndAction {
                binding.previewView.animate()
                    .alpha(1f)
                    .setDuration(100)
                    .start()
            }.start()

        binding.btnCapture.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                binding.btnCapture.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }.start()

        //Continue with capture process
        val photoFile = File(
            getOutputDirectory(),
            "${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        val rotation = binding.previewView.display.rotation
        imageCapture.targetRotation = rotation

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    processPhoto(photoFile, rotation)
                }


                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@CameraActivity,
                            "Capture failed: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }


    private fun processPhoto(photoFile: File, rotation: Int) {
        // coroutine for background processing
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                  /*------Fix rotation--------*/
                ImageSaver.fixImageRotation(this@CameraActivity, photoFile, rotation)

                  /*------Validate image quality - detect blur--------*/
                if (ImageValidator.isImageBlurry(photoFile)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CameraActivity, "Image is too blurry. Please retake.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch // Stop further processing
                }

                /*------Validate image quality - lighting check--------*/
                if (ImageValidator.isImageTooDark(photoFile)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CameraActivity, "Image is too dark. Please improve lighting.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }


                  /*------Crop if needed--------*/
                if (selectedPhotoType.aspectRatioText == "3:2") {
                    ImageCropper.cropImageToAspectRatio(photoFile, selectedPhotoType.aspectRatio)
                }

                  /*------Save to gallery--------*/
                ImageSaver.savePhotoToGallery(this@CameraActivity, photoFile)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CameraActivity, "Photo saved: ${photoFile.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CameraActivity, "Error processing image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun getOutputDirectory(): File {
        val appContext = applicationContext
        val mediaDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }


     /*------Permissions------*/
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }


    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }



    private fun getGuidanceMessage(photoType: PhotoType): String {
        return when (photoType) {
            PhotoType.ID_PHOTO -> "Center your face in the frame."
            PhotoType.MEMBER_PHOTO -> "Keep your full face in the frame."
            PhotoType.COMBO -> "Place ID and face correctly in the frame."
        }
    }


    private fun setupRatioSelector() {
        val photoTypes = PhotoType.values()

        photoTypes.forEach { photoType ->
            val ratioTextView = TextView(this).apply {
                text = photoType.aspectRatioText
                textSize = 20f
                setPadding(24, 16, 24, 16)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.ratio_unselected_background)
                setOnClickListener {
                    updateSelectedRatio(this, photoType)
                }
            }

            binding.ratioSelectorLayout.addView(ratioTextView)

            // Set default selected
            if (photoType == PhotoType.ID_PHOTO) {
                updateSelectedRatio(ratioTextView, photoType)
            }
        }
    }

    private fun updateSelectedRatio(selectedView: TextView, selectedType: PhotoType) {
        // Reset all ratios to unselected
        for (i in 0 until binding.ratioSelectorLayout.childCount) {
            val child = binding.ratioSelectorLayout.getChildAt(i) as TextView
            child.setBackgroundResource(R.drawable.ratio_unselected_background)
            child.setTextColor(Color.WHITE)
        }

        // Highlight selected
        selectedView.setBackgroundResource(R.drawable.ratio_selected_background)
        selectedView.setTextColor(Color.YELLOW)

        selectedPhotoType = selectedType
        binding.frameOverlay.updateAspectRatio(selectedType.aspectRatio)
        startCamera(selectedType.cameraXAspectRatio)

        binding.guidanceText.text = "${selectedType.aspectRatioText} - ${getGuidanceMessage(selectedType)}"
    }




    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

}
