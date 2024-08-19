package com.example.steeringwheel

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Inicializar los elementos de la UI
        val steeringAngle: SeekBar = findViewById(R.id.steering_angle)
        val steeringAngleValue: TextView = findViewById(R.id.steering_angle_value)
        val swipeThreshold: SeekBar = findViewById(R.id.swipe_threshold)
        val swipeThresholdValue: TextView = findViewById(R.id.swipe_threshold_value)
        val clickTimeLimit: SeekBar = findViewById(R.id.click_time_limit)
        val clickTimeLimitValue: TextView = findViewById(R.id.click_time_limit_value)
        val acceleratorSensitivity: SeekBar = findViewById(R.id.accelerator_sensitivity)
        val acceleratorValue: TextView = findViewById(R.id.accelerator_value)
        val brakeSensitivity: SeekBar = findViewById(R.id.brake_sensitivity)
        val brakeValue: TextView = findViewById(R.id.brake_value)

        // Cargar valores guardados
        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        val savedAngle = sharedPrefs.getInt("steering_angle", 90)
        val savedSwipeThreshold = sharedPrefs.getFloat(PREF_SWIPE_THRESHOLD, DEFAULT_SWIPE_THRESHOLD)
        val savedClickTimeLimit = sharedPrefs.getFloat(PREF_CLICK_TIME_LIMIT, DEFAULT_CLICK_TIME_LIMIT)
        val savedAcceleratorSensitivity = sharedPrefs.getFloat(PREF_ACCELERATOR_SENSITIVITY, DEFAULT_ACCELERATOR_SENSITIVITY)
        val savedBrakeSensitivity = sharedPrefs.getFloat(PREF_BRAKE_SENSITIVITY, DEFAULT_BRAKE_SENSITIVITY)

        // Establecer valores guardados en los SeekBar y TextView
        steeringAngle.progress = savedAngle
        steeringAngleValue.text = "$savedAngle°"

        swipeThreshold.progress = (savedSwipeThreshold * 10).toInt() // Convertir a decenas para más precisión
        swipeThresholdValue.text = String.format("%.1f mm", savedSwipeThreshold)

        clickTimeLimit.progress = (savedClickTimeLimit * 100).toInt() // Convertir a centésimas para más precisión
        clickTimeLimitValue.text = String.format("%.2f sec", savedClickTimeLimit)

        acceleratorSensitivity.progress = (savedAcceleratorSensitivity * 10).toInt()
        acceleratorValue.text = String.format("%.1f", savedAcceleratorSensitivity)

        brakeSensitivity.progress = (savedBrakeSensitivity * 10).toInt()
        brakeValue.text = String.format("%.1f", savedBrakeSensitivity)

        // Manejar cambios en el SeekBar de ángulo
        steeringAngle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                steeringAngleValue.text = "$progress°"
                saveSteeringAngle(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Manejar cambios en el SeekBar de Swipe Threshold
        swipeThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newValue = progress / 10.0f
                swipeThresholdValue.text = String.format("%.1f mm", newValue)
                saveSwipeThreshold(newValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Manejar cambios en el SeekBar de Click Time Limit
        clickTimeLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newValue = progress / 100.0f
                clickTimeLimitValue.text = String.format("%.2f sec", newValue)
                saveClickTimeLimit(newValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Manejar cambios en el SeekBar de Accelerator Sensitivity
        acceleratorSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newValue = progress / 10.0f
                acceleratorValue.text = String.format("%.1f", newValue)
                saveAcceleratorSensitivity(newValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Manejar cambios en el SeekBar de Brake Sensitivity
        brakeSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newValue = progress / 10.0f
                brakeValue.text = String.format("%.1f", newValue)
                saveBrakeSensitivity(newValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun saveSteeringAngle(angle: Int) {
        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putInt("steering_angle", angle)
            apply()
        }
    }

    private fun saveSwipeThreshold(threshold: Float) {
        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putFloat(PREF_SWIPE_THRESHOLD, threshold)
            apply()
        }
    }

    private fun saveClickTimeLimit(timeLimit: Float) {
        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putFloat(PREF_CLICK_TIME_LIMIT, timeLimit)
            apply()
        }
    }

    private fun saveAcceleratorSensitivity(sensitivity: Float) {
        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putFloat(PREF_ACCELERATOR_SENSITIVITY, sensitivity)
            apply()
        }
    }

    private fun saveBrakeSensitivity(sensitivity: Float) {
        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putFloat(PREF_BRAKE_SENSITIVITY, sensitivity)
            apply()
        }
    }

    companion object {
        // Definir las claves para las preferencias
        const val PREF_SWIPE_THRESHOLD = "pref_swipe_threshold"
        const val PREF_CLICK_TIME_LIMIT = "pref_click_time_limit"
        const val PREF_ACCELERATOR_SENSITIVITY = "pref_accelerator_sensitivity"
        const val PREF_BRAKE_SENSITIVITY = "pref_brake_sensitivity"

        // Valores predeterminados
        const val DEFAULT_SWIPE_THRESHOLD = 4.0f // en mm
        const val DEFAULT_CLICK_TIME_LIMIT = 0.25f // en segundos
        const val DEFAULT_ACCELERATOR_SENSITIVITY = 4.0f // sensibilidad predeterminada
        const val DEFAULT_BRAKE_SENSITIVITY = 4.0f // sensibilidad predeterminada
    }
}
