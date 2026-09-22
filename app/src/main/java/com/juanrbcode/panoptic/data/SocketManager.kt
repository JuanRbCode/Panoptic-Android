package com.juanrbcode.panoptic.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

class SocketManager(private val context: Context, private val serverUrl: String) {
    private var socket: Socket? = null

    @SuppressLint("HardwareIds")
    fun connect(
        roomCode: String,
        password: String,
        onConnected: () -> Unit,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onDisconnected: () -> Unit,
        onCommandReceived: (String, JSONObject?) -> Unit
    ) {
        Log.d("SocketManager", "Intentando conectar a la URL: $serverUrl con sala: $roomCode")

        try {
            if (socket?.connected() == true) {
                Log.d("SocketManager", "El socket ya estaba conectado previamente.")
                return
            }

            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 3000
                timeout = 10000
            }

            socket = IO.socket(URI.create(serverUrl), options)

            // 1. Error de conexión general
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                if (args.isNotEmpty()) {
                    val errorMsg = args[0].toString()
                    Log.e("SocketManager", "Fallo al conectar con el servidor: $errorMsg")
                    onAuthError("No se pudo conectar al servidor.")
                }
            }

            // 2. Conexión establecida -> Enviamos credenciales completas
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "¡Socket conectado! Enviando registro al servidor...")
                try {
                    val deviceUid = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ) ?: "unknown_device"

                    val roomData = JSONObject().apply {
                        put("deviceUid", deviceUid)
                        put("name", Build.MODEL ?: "Android Device")
                        put("roomCode", roomCode)
                        put("roomPassword", password)
                        put("battery", 100)
                    }
                    socket?.emit("register_device_to_room", roomData)

                } catch (e: Exception) {
                    Log.e("SocketManager", "Excepción al emitir registro: ${e.message}")
                    e.printStackTrace()
                }
                onConnected()
            }

            // 3. Éxito de registro único
            socket?.on("device_registered_success") { _ ->
                Log.d("SocketManager", "¡Dispositivo autenticado y aceptado en la sala!")
                onAuthSuccess()
            }

            // 4. Error de autenticación devuelto por el servidor
            socket?.on("room_auth_error") { args ->
                val errorMsg = if (args.isNotEmpty()) {
                    try {
                        val data = args[0]
                        if (data is JSONObject) data.optString("message", "Error de autenticación")
                        else data.toString()
                    } catch (e: Exception) {
                        "Credenciales incorrectas"
                    }
                } else {
                    "Sala o contraseña incorrecta"
                }
                Log.e("SocketManager", "Error de auth recibido: $errorMsg")
                onAuthError(errorMsg)
            }

            // 5. Comandos del panel web (CORREGIDO: Escucha 'command_to_phone' que es el que emite el Node.js)
            socket?.on("command_to_phone") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = when (val arg = args[0]) {
                            is JSONObject -> arg
                            else -> JSONObject(arg.toString())
                        }
                        val action = data.optString("action")
                        Log.d("SocketManager", "Comando recibido del panel web -> Acción: $action")
                        onCommandReceived(action, data)
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error al procesar comando: ${e.message}")
                    }
                }
            }

            // 6. Desconexión
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("SocketManager", "Desconectado del servidor de sockets.")
                onDisconnected()
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("SocketManager", "Excepción crítica al inicializar socket: ${e.message}")
            e.printStackTrace()
            onAuthError("Excepción crítica de red: ${e.message}")
        }
    }


    fun onPlayAudioChunk(listener: (String) -> Unit) {
        socket?.on("play_audio_chunk") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = when (val arg = args[0]) {
                        is JSONObject -> arg
                        else -> JSONObject(arg.toString())
                    }
                    val chunk = data.optString("chunk")
                    if (chunk.isNotEmpty()) {
                        listener(chunk)
                    }
                } catch (e: Exception) {
                    Log.e("SocketManager", "Error al procesar chunk de audio entrante: ${e.message}")
                }
            }
        }
    }

    fun emit(event: String, data: String?) {
        socket?.emit(event, data)
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}