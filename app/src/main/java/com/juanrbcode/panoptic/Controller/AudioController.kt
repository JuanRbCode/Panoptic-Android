package com.juanrbcode.panoptic.Controller

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.juanrbcode.panoptic.data.SocketManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AudioController(private val context: Context, private val socketManager: SocketManager) {

    // --- Variables para Capturar y Enviar Audio (Micrófono del Celular -> PC) ---
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val recordingExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // --- Variables para Reproducir Audio Remoto (PC -> Altavoz del Celular) ---
    private var audioTrack: AudioTrack? = null
    private var isPlayingIncomingAudio = false

    // Configuración de audio estándar para streaming de voz (bidireccional)
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val minPlayBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

    @SuppressLint("MissingPermission")
    fun startAudioCapture() {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioController", "No se pudo inicializar AudioRecord")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingExecutor.execute {
                val buffer = ByteArray(bufferSize)
                Log.d("AudioController", "Grabación y streaming de audio iniciados")

                while (isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        val base64Chunk = Base64.encodeToString(buffer, 0, readBytes, Base64.NO_WRAP)
                        // Envía el fragmento de audio al servidor Node.js
                        socketManager.emit("audio_chunk", base64Chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Error al iniciar audio: ${e.message}")
            isRecording = false
        }
    }

    fun stopAudioCapture() {
        if (!isRecording) return
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d("AudioController", "Grabación de audio detenida")
        } catch (e: Exception) {
            Log.e("AudioController", "Error al detener audio: ${e.message}")
        }
    }

    // ==========================================
    // REPRODUCCIÓN DE VOZ ENTRANTE (DESDE LA PC)
    // ==========================================

    fun startPlayingIncomingAudio() {
        if (isPlayingIncomingAudio) return
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlayingIncomingAudio = true
            Log.d("AudioController", "Reproducción de voz remota (PC) iniciada en el celular")
        } catch (e: Exception) {
            Log.e("AudioController", "Error al iniciar AudioTrack: ${e.message}")
        }
    }

    fun playAudioChunk(base64Chunk: String) {
        if (!isPlayingIncomingAudio) {
            startPlayingIncomingAudio()
        }
        try {
            val audioBytes = Base64.decode(base64Chunk, Base64.NO_WRAP)
            audioTrack?.write(audioBytes, 0, audioBytes.size)
        } catch (e: Exception) {
            Log.e("AudioController", "Error escribiendo chunk de audio entrante: ${e.message}")
        }
    }

    fun stopPlayingIncomingAudio() {
        if (!isPlayingIncomingAudio) return
        isPlayingIncomingAudio = false

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            Log.d("AudioController", "Reproducción de voz remota detenida")
        } catch (e: Exception) {
            Log.e("AudioController", "Error al detener AudioTrack: ${e.message}")
        }
    }
}