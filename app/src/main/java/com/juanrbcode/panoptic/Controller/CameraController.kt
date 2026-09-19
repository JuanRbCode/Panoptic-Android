package com.juanrbcode.panoptic.Controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.juanrbcode.panoptic.data.SocketManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(private val context: Context, private val socketManager: SocketManager) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isStreaming = false

    // Guardamos el último bitmap procesado por si piden una foto rápida
    private var lastCapturedBitmap: Bitmap? = null

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("CameraController", "Error al inicializar CameraProvider: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Tomar Foto instantánea usando el frame actual del streaming
    fun takePhoto(lens: String) {
        ContextCompat.getMainExecutor(context).execute {
            try {
                if (lastCapturedBitmap != null) {
                    val base64Image = bitmapToBase64(lastCapturedBitmap!!, quality = 90)
                    val responseObj = JSONObject().apply {
                        put("type", "photo")
                        put("image", base64Image)
                    }
                    socketManager.emit("phone_response", responseObj)
                    Log.d("CameraController", "Foto tomada del streaming actual y enviada con éxito")
                    return@execute
                }

                val provider = cameraProvider ?: return@execute
                provider.unbindAll()

                val cameraSelector = if (lens == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val imageAnalysisTemp = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val lifecycleOwner = context as? LifecycleOwner
                if (lifecycleOwner == null) {
                    Log.e("CameraController", "El contexto proporcionado no es un LifecycleOwner válido.")
                    return@execute
                }

                imageAnalysisTemp.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        val bitmap = imageProxyToBitmapSafe(imageProxy)
                        if (bitmap != null) {
                            val base64Image = bitmapToBase64(bitmap, quality = 90)
                            val responseObj = JSONObject().apply {
                                put("type", "photo")
                                put("image", base64Image)
                            }
                            socketManager.emit("phone_response", responseObj)
                            Log.d("CameraController", "Foto de respaldo tomada y enviada")
                        }
                    } catch (e: Exception) {
                        Log.e("CameraController", "Error en foto de respaldo: ${e.message}")
                    } finally {
                        imageProxy.close()
                        provider.unbindAll()
                    }
                }

                provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysisTemp)

            } catch (e: Exception) {
                Log.e("CameraController", "Excepción al ejecutar takePhoto: ${e.message}")
            }
        }
    }

    // Iniciar Streaming de Cámara en tiempo real
    fun startCamera(lens: String) {
        ContextCompat.getMainExecutor(context).execute {
            try {
                val provider = cameraProvider ?: return@execute
                provider.unbindAll()
                isStreaming = true

                val cameraSelector = if (lens == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        if (!isStreaming) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val bitmap = imageProxyToBitmapSafe(imageProxy)
                        if (bitmap != null) {
                            lastCapturedBitmap = bitmap
                            val base64Frame = bitmapToBase64(bitmap, quality = 40) // Compresión ligera para fluidez en red
                            socketManager.emit("camera_frame", base64Frame)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraController", "Error en analyzer: ${e.message}")
                    } finally {
                        imageProxy.close()
                    }
                }

                val lifecycleOwner = context as? LifecycleOwner
                if (lifecycleOwner != null) {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis!!)
                    Log.d("CameraController", "Streaming iniciado con lente: $lens")
                } else {
                    Log.e("CameraController", "No se pudo vincular la cámara: Context no es LifecycleOwner")
                }
            } catch (e: Exception) {
                Log.e("CameraController", "Error al iniciar streaming: ${e.message}")
            }
        }
    }

    fun stopCamera() {
        ContextCompat.getMainExecutor(context).execute {
            isStreaming = false
            lastCapturedBitmap = null
            socketManager.emit("camera_frame", JSONObject().apply {
                put("frame", JSONObject.NULL)
                put("status", "stopped")
            })
            try {
                cameraProvider?.unbindAll()
                Log.d("CameraController", "Streaming detenido y caché limpia")
            } catch (e: Exception) {
                Log.e("CameraController", "Error al detener cámara: ${e.message}")
            }
        }
    }

    private fun imageProxyToBitmapSafe(image: ImageProxy): Bitmap? {
        return try {
            when (image.format) {
                ImageFormat.JPEG -> {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                else -> {
                    if (image.planes.size < 3) return null
                    val yBuffer = image.planes[0].buffer
                    val uBuffer = image.planes[1].buffer
                    val vBuffer = image.planes[2].buffer

                    val ySize = yBuffer.remaining()
                    val uSize = uBuffer.remaining()
                    val vSize = vBuffer.remaining()

                    val nv21 = ByteArray(ySize + uSize + vSize)
                    yBuffer.get(nv21, 0, ySize)
                    vBuffer.get(nv21, ySize, vSize)
                    uBuffer.get(nv21, ySize + vSize, uSize)

                    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                    val out = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
                    val imageBytes = out.toByteArray()
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}