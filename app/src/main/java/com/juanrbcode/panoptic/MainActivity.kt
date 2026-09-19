package com.juanrbcode.panoptic

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.textfield.TextInputEditText
import com.juanrbcode.panoptic.Activity.ConnectedActivity
import com.juanrbcode.panoptic.service.ConnectionService
import com.juanrbcode.panoptic.service.MyAdminReceiver

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 100
    private val ADMIN_REQUEST_CODE = 200
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var etServerUrl: TextInputEditText
    private lateinit var etRoomCode: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnConnect: Button

    private lateinit var authReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("PanopticPrefs", Context.MODE_PRIVATE)

        etServerUrl = findViewById(R.id.etServerUrl)
        etRoomCode = findViewById(R.id.etRoomCode)
        etPassword = findViewById(R.id.etPassword)
        btnConnect = findViewById(R.id.btnConnect)

        // Cargar datos previos si existen guardados
        etServerUrl.setText(sharedPreferences.getString("server_url", "https://panoptic-server-production.up.railway.app"))
        etRoomCode.setText(sharedPreferences.getString("room_code", ""))
        etPassword.setText(sharedPreferences.getString("room_password", ""))

        btnConnect.setOnClickListener {
            validarYGuardarDatos()
        }

        configurarBroadcastReceiver()
        verificarPermisosYIniciar()
    }

    private fun configurarBroadcastReceiver() {
        authReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ConnectionService.ACTION_AUTH_SUCCESS -> {
                        Toast.makeText(this@MainActivity, "¡Vinculación y conexión exitosa!", Toast.LENGTH_SHORT).show()

                        // Navegar a la pantalla camuflada (Bloc de notas) solo si conectó bien
                        val navIntent = Intent(this@MainActivity, ConnectedActivity::class.java)
                        startActivity(navIntent)
                        finish()
                    }
                    ConnectionService.ACTION_AUTH_ERROR -> {
                        val errorMsg = intent.getStringExtra(ConnectionService.EXTRA_ERROR_MSG) ?: "Credenciales incorrectas"
                        Toast.makeText(this@MainActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()

                        // Reactivar el botón para permitir reintentar
                        btnConnect.isEnabled = true
                        btnConnect.alpha = 1.0f
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ConnectionService.ACTION_AUTH_SUCCESS)
            addAction(ConnectionService.ACTION_AUTH_ERROR)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(authReceiver, filter)
    }

    private fun verificarPermisosYIniciar() {
        val permisosNecesarios = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisosNecesarios.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permisosFaltantes = permisosNecesarios.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permisosFaltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permisosFaltantes.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            verificarPermisoAdministrador()
        }
    }

    private fun verificarPermisoAdministrador() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, MyAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Se requiere este permiso para bloquear la pantalla de forma remota.")
            }
            startActivityForResult(intent, ADMIN_REQUEST_CODE)
        }
    }

    private fun validarYGuardarDatos() {
        val serverUrl = etServerUrl.text.toString().trim()
        val roomCode = etRoomCode.text.toString().trim().uppercase()
        val password = etPassword.text.toString().trim()

        if (serverUrl.isEmpty() || roomCode.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPreferences.edit().apply {
            putString("server_url", serverUrl)
            putString("room_code", roomCode)
            putString("room_password", password)
            apply()
        }

        btnConnect.isEnabled = false
        btnConnect.alpha = 0.6f

        Toast.makeText(this, "Conectando con Panoptic...", Toast.LENGTH_SHORT).show()

        iniciarConnectionService(serverUrl, roomCode, password)
    }

    private fun iniciarConnectionService(serverUrl: String, roomCode: String, password: String) {
        try {
            val serviceIntent = Intent(this, ConnectionService::class.java).apply {
                putExtra("SERVER_URL", serverUrl)
                putExtra("ROOM_CODE", roomCode)
                putExtra("PASSWORD", password)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al iniciar el Servicio: ${e.message}")
            btnConnect.isEnabled = true
            btnConnect.alpha = 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(authReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al desregistrar receiver: ${e.message}")
        }
    }
}