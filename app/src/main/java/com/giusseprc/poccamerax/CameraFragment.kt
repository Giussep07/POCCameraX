package com.giusseprc.poccamerax

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.giusseprc.poccamerax.databinding.CameraFragmentBinding
import com.giusseprc.poccamerax.verticalSlider.VerticalSlider
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

@SuppressLint("RestrictedApi")
class CameraFragment : Fragment(), VerticalSlider.Listener {

    private lateinit var binding: CameraFragmentBinding
    private val viewModel: CameraViewModel by viewModels()

    private lateinit var mainExecutor: Executor

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var photoFile: File
    private lateinit var path: File
    private lateinit var videoFile: File
    private lateinit var fileName: String
    private var recordingTime: Long = 0
    private var mStartToRecordRunnable: Runnable = Runnable { startRecording() }
    private lateinit var mRecordingTimeRunnable: Runnable
    private lateinit var listener: ScaleGestureDetector.SimpleOnScaleGestureListener
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var torchEnable = false
    private var isRecording: Boolean = false
    private val mHandler: Handler by lazy {
        Handler()
    }

    private var flashMode by Delegates.observable(ImageCapture.FLASH_MODE_OFF) { _, _, newValue ->
        binding.imageButtonFlash.setImageResource(
            when (newValue) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on_black
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto_black
                else -> R.drawable.ic_flash_off_black
            }
        )
    }

    companion object {

        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate")
        mainExecutor = ContextCompat.getMainExecutor(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.d("onCreateView")
        binding = DataBindingUtil.inflate(inflater, R.layout.camera_fragment, container, false)

        binding.viewFinder.bindToLifecycle(viewLifecycleOwner)

        flashMode = ImageCapture.FLASH_MODE_OFF

        binding.verticalSlider.setListener(this)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated")
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

//        setupListenerZoom()

        /*binding.viewFinder.post {
            startCamera()
        }*/

        imageButtonSwitchCameraClickListener()

        imageButtonFlashClickListener()

        imageButtonCameraTouchListener()

        viewFinderTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun viewFinderTouchListener() {
        binding.viewFinder.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        val (x: Float, y: Float) = event.x to event.y

                        /*val factory = SurfaceOrientedMeteringPointFactory(
                            binding.viewFinder.width.toFloat(),
                            binding.viewFinder.height.toFloat()
                        )

                        val point = factory.createPoint(x, y)

                        val action = FocusMeteringAction
                            .Builder(point)
                            .build()
                        cameraControl?.startFocusAndMetering(action)*/

                        binding.lottieFocus.x = x - (binding.lottieFocus.width / 2)
                        binding.lottieFocus.y = y - (binding.lottieFocus.height / 2)
                        binding.lottieFocus.playAnimation()
                    }

                    return@setOnTouchListener false
                }
                else -> {
//                    scaleGestureDetector.onTouchEvent(event)
                    return@setOnTouchListener false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun imageButtonCameraTouchListener() {
        binding.imageButtonCamera.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mHandler.postDelayed(mStartToRecordRunnable, 500)
                }
                MotionEvent.ACTION_UP -> {
                    mHandler.removeCallbacks(mStartToRecordRunnable)
                    if (!isRecording) {
                        takePhoto()
                    } else {
                        stopRecording()
                    }
                }
            }
            true
        }
    }

    private fun imageButtonFlashClickListener() {
        /*binding.imageButtonFlash.setOnClickListener {
            torchEnable = !torchEnable
            imageCapture!!.flashMode =
                if (torchEnable) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            binding.imageButtonFlash.setImageDrawable(
                requireContext().resources.getDrawable(
                    if (torchEnable) R.drawable.ic_flash_on_black else R.drawable.ic_flash_off_black,
                    requireContext().theme
                )
            )
        }*/

        binding.imageButtonFlash.setOnClickListener {
            flashMode = when (binding.viewFinder.flash) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            binding.viewFinder.flash = flashMode
        }
    }

    private fun imageButtonSwitchCameraClickListener() {
        binding.imageButtonSwitchCamera.setOnClickListener {
            /*lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            CameraX.getCameraWithLensFacing(lensFacing)
            recreateCamera()*/
            binding.viewFinder.toggleCamera()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.e("onDestroyView")
        CameraX.unbindAll()
        /*try {
            cameraExecutor.shutdown()
            videoCapture?.stopRecording()
            videoCapture?.clear()
            if (::mRecordingTimeRunnable.isInitialized) {
                mHandler.removeCallbacks(mRecordingTimeRunnable)
            }
            mHandler.removeCallbacks(mStartToRecordRunnable)
        } catch (e: Exception) {
            Timber.e(e)
        }*/
    }

    private fun recreateCamera() {
        CameraX.unbindAll()
        startCamera()
    }

    private fun startCamera() {
        /*try {// Get screen metrics used to setup camera for full screen resolution
            val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
            Timber.d("Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

            val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
            Timber.d("Preview aspect ratio: $screenAspectRatio")

            val rotation = binding.viewFinder.display.rotation

            // Bind the CameraProvider to the LifeCycleOwner
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture.addListener(Runnable {

                // CameraProvider
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                preview = Preview.Builder()
                    // We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
                    // Set initial target rotation
                    .setTargetRotation(rotation)
                    .build()

                // ImageCapture
                imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(720, 1280))
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .build()

                // VideoCapture
                videoCapture = VideoCaptureConfig.Builder()
                    .setTargetRotation(rotation)
                    .build()

                try {
                    // A variable number of use-cases can be passed here -
                    // camera provides access to CameraControl & CameraInfo
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture,
                        videoCapture
                    )

                    camera?.let { camera ->
                        cameraControl = camera.cameraControl
                        cameraInfo = camera.cameraInfo
                    }

                    // Default PreviewSurfaceProvider
                    val surfaceProvider = binding.viewFinder.createSurfaceProvider(cameraInfo)
                    preview?.setSurfaceProvider(surfaceProvider)
                } catch (exc: Exception) {
                    Timber.e("Use case binding failed, exc: $exc")
                }

            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) {
            Timber.e(e)
        }*/
    }

    private fun setupListenerZoom() {
        listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                val currentZoomRatio: Float = cameraInfo!!.zoomState.value?.zoomRatio ?: 0F
                val delta = detector?.scaleFactor
                cameraControl?.setZoomRatio(currentZoomRatio * delta!!)
                return true
            }
        }
        scaleGestureDetector = ScaleGestureDetector(context, listener)
    }

    private fun takePhoto() {
        photoFile = createFile(PHOTO_EXTENSION)
        /*val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(ImageCapture.Metadata().apply {
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }).build()

        imageCapture!!.takePicture(
            outputFileOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Timber.d("Photo capture succeeded: ${outputFileResults.savedUri}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e("Photo capture failed: $exception")
                }
            })*/

        binding.viewFinder.takePicture(
            photoFile,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        requireContext(),
                        "Photo capture succeeded: ${outputFileResults.savedUri}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e("Photo capture failed: $exception")
                }
            })
    }

    private fun startRecording() {
        isRecording = true
        binding.imageButtonCamera.setBackgroundResource(R.drawable.bg_button_recoding)
        binding.imageButtonCamera.setImageResource(android.R.color.transparent)
        videoFile = createFile(VIDEO_EXTENSION)
        videoCapture?.startRecording(
            videoFile,
            mainExecutor,
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(file: File) {
                    isRecording = false
                    mHandler.removeCallbacks(mRecordingTimeRunnable)
                    hideRecordingTime()

                    Toast.makeText(
                        requireContext(),
                        "Video capturado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(
                    videoCaptureError: Int,
                    message: String,
                    cause: Throwable?
                ) {
                    mHandler.removeCallbacks(mRecordingTimeRunnable)
                    hideRecordingTime()
                    Timber.e("Video Error: $message")
                    Toast.makeText(
                        requireContext(),
                        "Erro capturando video",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        mRecordingTimeRunnable = Runnable {
            binding.lottieRecording.visibility = View.VISIBLE
            binding.textViewRecordingTime.apply {
                visibility = View.VISIBLE
                text = getDuration(recordingTime, showHours = false)
            }

            val oneSecond = TimeUnit.SECONDS.toMillis(1)
            recordingTime += oneSecond
            mHandler.postDelayed(mRecordingTimeRunnable, oneSecond)
        }
        mHandler.postDelayed(mRecordingTimeRunnable, 0)
    }

    private fun getDuration(millisUntilFinished: Long, showHours: Boolean = true): String {
        var duration = ""
        val hour = ((millisUntilFinished / 1000) / 60) / 60
        val minutes = ((millisUntilFinished / 1000) / 60) % 60
        val seconds = (millisUntilFinished / 1000) % 60

        if (showHours) {
            duration += if (hour < 10) "0${hour}:" else "$hour:"
        } else if (hour > 0) {
            duration += if (hour < 10) "0${hour}:" else "$hour:"
        }

        duration += if (minutes < 10) "0${minutes}:" else "$minutes:"
        duration += if (seconds < 10) "0${seconds}" else "$seconds"

        return duration
    }

    private fun hideRecordingTime() {
        binding.lottieRecording.visibility = View.GONE
        binding.textViewRecordingTime.visibility = View.GONE
    }

    private fun stopRecording() {
        try {
            binding.imageButtonCamera.background =
                resources.getDrawable(R.drawable.bg_button_take_picture, requireContext().theme)
            videoCapture?.stopRecording()
            isRecording = false
            Timber.i("Video File stopped")
            recordingTime = 0L
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun createFile(extension: String): File {
        val timeStamp: String = System.currentTimeMillis().toString()

        val subFolder = when (extension) {
            PHOTO_EXTENSION -> "Images"
            VIDEO_EXTENSION -> "Videos"
            else -> ""
        }

        fileName = "${timeStamp}$extension"
        path = File(requireContext().cacheDir!!, subFolder)
        if (!path.exists())
            path.mkdirs()

        // Create an image file name
        return File(path, fileName)
    }

    //region Implamentation VerticalSlider.Listener
    override fun onSlide(value: Float) {
        val minZoomRatio = binding.viewFinder.minZoomRatio
        val maxZoomRatio = binding.viewFinder.maxZoomRatio

        val finalZoomRatio = (value * 100 - minZoomRatio) / (maxZoomRatio - minZoomRatio)

        Timber.d("onSlide: $value, minZoomRatio: ${binding.viewFinder.minZoomRatio}, maxZoomRatio: ${binding.viewFinder.maxZoomRatio}, finalZoomRatio: $finalZoomRatio")

    }
    //endregion
}
