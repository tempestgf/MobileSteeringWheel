package com.example.steeringwheel

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var accelerateIndicator: View
    private lateinit var brakeIndicator: View
    private lateinit var accelerateTopIndicator: View
    private lateinit var brakeTopIndicator: View
    private lateinit var buttonLeftTop: Button
    private lateinit var buttonLeftBottom: Button
    private lateinit var buttonRightTop: Button
    private lateinit var buttonRightBottom: Button
    private val serverAddress = "192.168.85.221"  // IP para USB tethering
    private val serverPort = 12345
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var lastY: Float = 0f
    private val alpha = 0.5f  // Aumentar el factor de suavizado para mayor sensibilidad

    private var startY: Float = 0f
    private val threshold = 50  // Umbral de arrastre en píxeles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "onCreate called, initializing sensors and UI elements")

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            Log.d("MainActivity", "Accelerometer sensor registered")
        } else {
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Accelerometer sensor not available")
        }

        accelerateIndicator = findViewById(R.id.accelerateIndicator)
        brakeIndicator = findViewById(R.id.brakeIndicator)
        accelerateTopIndicator = findViewById(R.id.accelerateTopIndicator)
        brakeTopIndicator = findViewById(R.id.brakeTopIndicator)
        buttonLeftTop = findViewById(R.id.button_left_top)
        buttonLeftBottom = findViewById(R.id.button_left_bottom)
        buttonRightTop = findViewById(R.id.button_right_top)
        buttonRightBottom = findViewById(R.id.button_right_bottom)

        val leftSide: View = findViewById(R.id.left_side)
        val rightSide: View = findViewById(R.id.right_side)

        leftSide.setOnTouchListener { v, event ->
            handleTouch(v, event, "BRAKE")
            true
        }

        rightSide.setOnTouchListener { v, event ->
            handleTouch(v, event, "ACCELERATE")
            true
        }

        buttonLeftTop.setOnClickListener { handleButtonClick("LEFT_TOP") }
        buttonLeftBottom.setOnClickListener { handleButtonClick("LEFT_BOTTOM") }
        buttonRightTop.setOnClickListener { handleButtonClick("RIGHT_TOP") }
        buttonRightBottom.setOnClickListener { handleButtonClick("RIGHT_BOTTOM") }
        buttonLeftTop.setOnClickListener { handleButtonClick("LEFT_TOP") }
        buttonLeftBottom.setOnClickListener { handleButtonClick("LEFT_BOTTOM") }
        buttonRightTop.setOnClickListener { handleButtonClick("RIGHT_TOP") }
        buttonRightBottom.setOnClickListener { handleButtonClick("RIGHT_BOTTOM") }

        buttonLeftTop.setOnTouchListener { v, event ->
            handleTouch(v, event, "BRAKE")
            false
        }

        buttonRightTop.setOnTouchListener { v, event ->
            handleTouch(v, event, "ACCELERATE")
            false
        }

        buttonLeftBottom.setOnTouchListener { v, event ->
            handleTouch(v, event, "BRAKE")
            false
        }

        buttonRightBottom.setOnTouchListener { v, event ->
            handleTouch(v, event, "ACCELERATE")
            false
        }

        CoroutineScope(Dispatchers.IO).launch {
            establishConnection()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        closeConnection()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val rawY = it.values[1]  // Usamos el eje Y para la rotación lateral
                val y = lowPassFilter(rawY, lastY)
                lastY = y
                Log.d("MainActivity", "Accelerometer raw data: rawY=$rawY, filtered data: y=$y")
                sendSensorDataToServer("ACCELEROMETER", y)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do something here if sensor accuracy changes
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(view: View, event: MotionEvent, command: String) {
        val action = event.action
        val y = event.y
        val screenHeight = resources.displayMetrics.heightPixels
        val sensitivityMultiplier = 1.5  // Ajuste de sensibilidad

        when (command) {
            "ACCELERATE" -> {
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = y
                        Log.d("MainActivity", "Accelerate ACTION_DOWN at y=$y")
                    }
                    MotionEvent.ACTION_MOVE -> {
                        Log.d("MainActivity", "Accelerate ACTION_MOVE at y=$y with startY=$startY")
                        if (Math.abs(y - startY) > threshold) {
                            val progress = ((y / screenHeight) * 100 * sensitivityMultiplier).toInt()
                            val cappedProgress = progress.coerceIn(0, 100)
                            val layoutParams = accelerateIndicator.layoutParams
                            layoutParams.height = (screenHeight * (cappedProgress / 100.0)).toInt()
                            accelerateIndicator.layoutParams = layoutParams
                            accelerateIndicator.visibility = View.VISIBLE
                            if (cappedProgress == 100) {
                                accelerateTopIndicator.visibility = View.VISIBLE
                                accelerateTopIndicator.layoutParams.height = 10.dpToPx() // Mostrar indicador amarillo al fondo
                            } else {
                                accelerateTopIndicator.visibility = View.GONE
                            }
                            Log.d("MainActivity", "Accelerate progress: $cappedProgress")
                            sendCommandToServer("$command:$cappedProgress")
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d("MainActivity", "Accelerate ACTION_UP")
                        accelerateIndicator.visibility = View.GONE
                        accelerateTopIndicator.visibility = View.GONE
                        sendCommandToServer("$command:0")
                    }
                }
            }
            "BRAKE" -> {
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = y
                        Log.d("MainActivity", "Brake ACTION_DOWN at y=$y")
                    }
                    MotionEvent.ACTION_MOVE -> {
                        Log.d("MainActivity", "Brake ACTION_MOVE at y=$y with startY=$startY")
                        if (Math.abs(y - startY) > threshold) {
                            val progress = ((y / screenHeight) * 100 * sensitivityMultiplier).toInt()
                            val cappedProgress = progress.coerceIn(0, 100)
                            val layoutParams = brakeIndicator.layoutParams
                            layoutParams.height = (screenHeight * (cappedProgress / 100.0)).toInt()
                            brakeIndicator.layoutParams = layoutParams
                            brakeIndicator.visibility = View.VISIBLE
                            if (cappedProgress == 100) {
                                brakeTopIndicator.visibility = View.VISIBLE
                                brakeTopIndicator.layoutParams.height = 10.dpToPx() // Mostrar indicador amarillo al fondo
                            } else {
                                brakeTopIndicator.visibility = View.GONE
                            }
                            Log.d("MainActivity", "Brake progress: $cappedProgress")
                            sendCommandToServer("$command:$cappedProgress")
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d("MainActivity", "Brake ACTION_UP")
                        brakeIndicator.visibility = View.GONE
                        brakeTopIndicator.visibility = View.GONE
                        sendCommandToServer("$command:0")
                    }
                }
            }
        }
    }

    // Función de extensión para convertir dp a px
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }



    private fun handleButtonClick(command: String) {
        Log.d("MainActivity", "Button clicked: $command")  // Añade este log para verificar que el botón fue clickeado
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = "$command\n"
                outputStream?.writeBytes(data)
                outputStream?.flush()
                Log.d("MainActivity", "Sent button command: $data")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "Failed to send button command", e)
            }
        }
    }


    private suspend fun establishConnection() {
        try {
            socket = Socket(serverAddress, serverPort)
            outputStream = DataOutputStream(socket?.getOutputStream())
            Log.d("MainActivity", "Connection established to server at $serverAddress:$serverPort")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("MainActivity", "Failed to establish connection to $serverAddress:$serverPort", e)
        }
    }

    private fun closeConnection() {
        try {
            outputStream?.close()
            socket?.close()
            Log.d("MainActivity", "Connection closed")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("MainActivity", "Failed to close connection", e)
        }
    }

    private fun sendSensorDataToServer(sensorType: String, y: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = "$sensorType:$y\n"  // Asegurarse de que el mensaje termina con una nueva línea
                outputStream?.writeBytes(data)
                outputStream?.flush()
                Log.d("MainActivity", "Sent sensor data: $data")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "Failed to send sensor data", e)
            }
        }
    }

    private fun sendCommandToServer(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = "$command\n"  // Asegurarse de que el mensaje termina con una nueva línea
                outputStream?.writeBytes(data)
                outputStream?.flush()
                Log.d("MainActivity", "Sent command: $data")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "Failed to send command", e)
            }
        }
    }

    private fun lowPassFilter(input: Float, output: Float): Float {
        return output + alpha * (input - output)
    }
}
