package com.juanrbcode.panoptic.Controller

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.juanrbcode.panoptic.service.MyAdminReceiver

class ScreenController(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    // 1. Apagar y bloquear (Igualito a presionar el botón de apagado físico)
    fun turnOffScreen() {
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val compName = ComponentName(context, MyAdminReceiver::class.java)

            if (devicePolicyManager.isAdminActive(compName)) {
                // Esto apaga la pantalla y exige PIN de inmediato (como el botón físico)
                devicePolicyManager.lockNow()
                Log.d("ScreenController", "Pantalla apagada y bloqueada con éxito")
            } else {
                Log.e("ScreenController", "Falta activar el permiso de Administrador de Dispositivo para bloquear la pantalla")
            }
        } catch (e: Exception) {
            Log.e("ScreenController", "Error al apagar pantalla: ${e.message}")
        }
    }

    // 2. Volver a encender la pantalla remotamente
    fun turnOnScreen() {
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Panoptic:ScreenWakelock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // Enciende por 10 minutos máx
            Log.d("ScreenController", "¡Pantalla encendida!")
        } catch (e: Exception) {
            Log.e("ScreenController", "Error al encender pantalla: ${e.message}")
        }
    }
}