package com.tempestgf.steeringwheel

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.tempestgf.steeringwheel.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)  // Cargar el menú principal

        val startButton: Button = findViewById(R.id.button_start)
        startButton.setOnClickListener {
            // Lanzar la actividad de la sección de volante
            val intent = Intent(this, SteeringWheelActivity::class.java)
            startActivity(intent)
        }

        // Otros botones del menú
        val settingsButton: Button = findViewById(R.id.button_settings)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        val aboutButton: Button = findViewById(R.id.button_about)
        aboutButton.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }
}
