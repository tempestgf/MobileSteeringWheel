package com.example.steeringwheel

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Configuración del botón de regreso
        val backButton: Button = findViewById(R.id.button_back)
        backButton.setOnClickListener {
            finish()  // Cierra la actividad y vuelve al menú principal
        }
    }
}
