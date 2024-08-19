package com.example.steeringwheel

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.InetAddress
import androidx.appcompat.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class SteeringWheelActivity : AppCompatActivity(), SensorEventListener {
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
    private var serverAddress = "192.168.176.150"  // IP para USB tethering
    private val serverPort = 12345
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var lastY: Float = 0f
    private val threshold = 0.00f // Umbral para filtrar datos insignificantes
    private var maxSteeringAngle: Float = 90f // Declarar como Float en lugar de Int
    private var swipeThresholdInPx: Float = 0f
    private var accelerationSensitivity: Float = 0.5f // Valor por defecto
    private var brakeSensitivity: Float = 0.5f // Valor por defecto
    private var clickTimeLimit: Float = 0.25f // Valor por defecto
    private var swipeThreshold: Float = 4.0f // Valor por defecto en mm



    // Cola para comandos a ser enviados al servidor
    private val commandQueue = LinkedBlockingQueue<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeUIElements()
        setupTouchListeners()
        setupButtonListeners()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            Log.d("SteeringWheelActivity", "Accelerometer sensor registered")
        } else {
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_SHORT).show()
            Log.e("SteeringWheelActivity", "Accelerometer sensor not available")
        }

        showConnectionOptions()
    }




    private fun initializeUIElements() {
        accelerateIndicator = findViewById(R.id.accelerateIndicator)
        brakeIndicator = findViewById(R.id.brakeIndicator)
        accelerateTopIndicator = findViewById(R.id.accelerateTopIndicator)
        brakeTopIndicator = findViewById(R.id.brakeTopIndicator)
        buttonLeftTop = findViewById(R.id.button_left_top)
        buttonLeftBottom = findViewById(R.id.button_left_bottom)
        buttonRightTop = findViewById(R.id.button_right_top)
        buttonRightBottom = findViewById(R.id.button_right_bottom)
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListeners() {
        val leftSide: View = findViewById(R.id.left_side)
        val rightSide: View = findViewById(R.id.right_side)

        leftSide.setOnTouchListener(object : View.OnTouchListener {
            private var initialY: Float = 0f
            private var initialTime: Long = 0

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.y
                        initialTime = System.currentTimeMillis()
                        startBrake()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - initialY
                        if (Math.abs(deltaY) > swipeThresholdInPx) {
                            updateBrake(deltaY)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val elapsedTime = (System.currentTimeMillis() - initialTime) / 1000.0
                        if (elapsedTime <= clickTimeLimit) {
                            // Handle click within time limit
                        }
                        stopBrake()
                    }
                }
                return true
            }
        })

        rightSide.setOnTouchListener(object : View.OnTouchListener {
            private var initialY: Float = 0f
            private var initialTime: Long = 0

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.y
                        initialTime = System.currentTimeMillis()
                        startAccelerate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - initialY
                        if (Math.abs(deltaY) > swipeThresholdInPx) {
                            updateAccelerate(deltaY)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val elapsedTime = (System.currentTimeMillis() - initialTime) / 1000.0
                        if (elapsedTime <= clickTimeLimit) {
                            // Handle click within time limit
                        }
                        stopAccelerate()
                    }
                }
                return true
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        closeConnection()
    }

    private fun limitSteeringAngle(angle: Float, maxAngle: Float): Float {
        return angle.coerceIn(-maxAngle, maxAngle)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val rawY = it.values[1]  // Usamos el eje Y para la rotación lateral

                // Limitar el ángulo de giro usando el maxSteeringAngle calculado
                val limitedY = limitSteeringAngle(rawY, maxSteeringAngle)

                // Mostrar indicadores si se alcanza el ángulo máximo
                if (limitedY >= maxSteeringAngle) {
                    findViewById<View>(R.id.right_max_angle_indicator).visibility = View.VISIBLE
                } else {
                    findViewById<View>(R.id.right_max_angle_indicator).visibility = View.GONE
                }

                if (limitedY <= -maxSteeringAngle) {
                    findViewById<View>(R.id.left_max_angle_indicator).visibility = View.VISIBLE
                } else {
                    findViewById<View>(R.id.left_max_angle_indicator).visibility = View.GONE
                }

                if (Math.abs(limitedY - lastY) > threshold) {
                    lastY = limitedY
                    //Log.v("MainActivity", "Accelerometer limited data: y=$limitedY")
                    queueCommand("A:$limitedY")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do something here if sensor accuracy changes
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                handleButtonClick("VOLUME_UP")
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleButtonClick("VOLUME_DOWN")
                return true
            }

            else -> return super.onKeyDown(keyCode, event)
        }
    }

    private fun handleButtonClick(command: String) {
        Log.d("MainActivity", "Button clicked: $command")
        queueCommand(command)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonListeners() {
        val buttonLeftTop: Button = findViewById(R.id.button_left_top)
        val buttonLeftBottom: Button = findViewById(R.id.button_left_bottom)
        val buttonRightTop: Button = findViewById(R.id.button_right_top)
        val buttonRightBottom: Button = findViewById(R.id.button_right_bottom)

        val buttonTouchListener = View.OnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.tag = Pair(event.y, System.currentTimeMillis())  // Save initial Y position and time
                }
                MotionEvent.ACTION_MOVE -> {
                    val (initialY, initialTime) = v.tag as Pair<Float, Long>
                    val deltaY = event.y - initialY

                    if (Math.abs(deltaY) > swipeThresholdInPx) {
                        when (v.id) {
                            R.id.button_left_top, R.id.button_left_bottom -> updateBrake(deltaY)
                            R.id.button_right_top, R.id.button_right_bottom -> updateAccelerate(deltaY)
                        }
                        return@OnTouchListener true  // Consumes the event as a slide
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val (initialY, initialTime) = v.tag as Pair<Float, Long>
                    val elapsedTime = (System.currentTimeMillis() - initialTime) / 1000.0

                    if (Math.abs(event.y - initialY) <= swipeThresholdInPx && elapsedTime <= clickTimeLimit) {
                        when (v.id) {
                            R.id.button_left_top -> queueCommand("D")
                            R.id.button_left_bottom -> queueCommand("E")
                            R.id.button_right_top -> queueCommand("F")
                            R.id.button_right_bottom -> queueCommand("G")
                        }
                        v.performClick()  // Trigger button's click action
                    } else {
                        // Detect end of slide
                        when (v.id) {
                            R.id.button_left_top, R.id.button_left_bottom -> stopBrake()
                            R.id.button_right_top, R.id.button_right_bottom -> stopAccelerate()
                        }
                    }
                }
            }
            true
        }

        // Assign the same listener to all buttons
        buttonLeftTop.setOnTouchListener(buttonTouchListener)
        buttonLeftBottom.setOnTouchListener(buttonTouchListener)
        buttonRightTop.setOnTouchListener(buttonTouchListener)
        buttonRightBottom.setOnTouchListener(buttonTouchListener)
    }

    private fun startBrake() {
        brakeIndicator.visibility = View.VISIBLE
        brakeIndicator.layoutParams.height = 0 // Reset the indicator height
        Log.d("SteeringWheelActivity", "Brake Started")
    }
    private fun startAccelerate() {
        accelerateIndicator.visibility = View.VISIBLE
        accelerateIndicator.layoutParams.height = 0 // Reset the indicator height
        Log.d("SteeringWheelActivity", "Accelerate Started")
    }
    private fun stopBrake() {
        // Al soltar, se ocultan los indicadores y se envía el comando para detener el freno
        brakeIndicator.visibility = View.GONE
        brakeTopIndicator.visibility = View.GONE
        queueCommand("C:0") // Enviar comando para detener completamente el freno
        Log.d("SteeringWheelActivity", "Brake Stopped")
    }

    private fun stopAccelerate() {
        // Al soltar, se ocultan los indicadores y se envía el comando para detener la aceleración
        accelerateIndicator.visibility = View.GONE
        accelerateTopIndicator.visibility = View.GONE
        queueCommand("B:0") // Enviar comando para detener completamente la aceleración
        Log.d("SteeringWheelActivity", "Accelerate Stopped")
    }


    private fun updateBrake(deltaY: Float) {
        val progress = (deltaY * brakeSensitivity).toInt().coerceIn(0, 100)

        val layoutParams = brakeIndicator.layoutParams
        layoutParams.height = (resources.displayMetrics.heightPixels * (progress / 100.0)).toInt()
        brakeIndicator.layoutParams = layoutParams
        brakeIndicator.visibility = View.VISIBLE

        if (progress >= 100) {
            brakeTopIndicator.visibility = View.VISIBLE
        } else {
            brakeTopIndicator.visibility = View.GONE
        }

        queueCommand("C:$progress")
        Log.d("SteeringWheelActivity", "Brake Updated with progress: $progress")
    }

    private fun updateAccelerate(deltaY: Float) {
        val progress = (deltaY * accelerationSensitivity).toInt().coerceIn(0, 100)

        val layoutParams = accelerateIndicator.layoutParams
        layoutParams.height = (resources.displayMetrics.heightPixels * (progress / 100.0)).toInt()
        accelerateIndicator.layoutParams = layoutParams
        accelerateIndicator.visibility = View.VISIBLE

        if (progress >= 100) {
            accelerateTopIndicator.visibility = View.VISIBLE
        } else {
            accelerateTopIndicator.visibility = View.GONE
        }

        queueCommand("B:$progress")
        Log.d("SteeringWheelActivity", "Accelerate Updated with progress: $progress")
    }


    private fun queueCommand(command: String) {
        if (isConnected()) {
            commandQueue.offer(command)
            Log.d("MainActivity", "Command queued: $command")
        } else {
            Log.e("MainActivity", "Cannot queue command, socket is not connected")
        }
    }


    private suspend fun processCommandQueue() {
        while (isConnected()) {
            val command = commandQueue.poll()
            if (command != null) {
                try {
                    outputStream?.writeBytes("$command\n")
                    outputStream?.flush()
                    Log.d("MainActivity", "Sent command: $command")
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e("MainActivity", "Failed to send command", e)
                }
            }
            delay(1)  // Mantén un retraso mínimo para evitar saturación
        }
    }

    @Synchronized
    private fun establishConnection() {
        if (isConnected()) {
            Log.d("MainActivity", "Already connected, no need to reconnect.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Cerrar cualquier conexión existente antes de abrir una nueva
                closeConnection()

                // Intentar conectar
                socket = Socket(serverAddress, serverPort)
                outputStream = DataOutputStream(socket?.getOutputStream())
                Log.d("MainActivity", "Connection established to $serverAddress:$serverPort via USB Tethering")

                // Iniciar el procesamiento de la cola de comandos
                processCommandQueue()

            } catch (e: IOException) {
                handleConnectionError(e)
            }
        }
    }
    private fun handleConnectionError(e: IOException) {
        e.printStackTrace()
        Log.e("MainActivity", "Failed to establish connection", e)
        runOnUiThread {
            Toast.makeText(this@SteeringWheelActivity, "Failed to connect to server", Toast.LENGTH_SHORT).show()
            showConnectionOptions()  // Mostrar opciones si no se puede conectar
        }
    }

    private fun closeConnection() {
        try {
            socket?.shutdownOutput()  // Señalar que no se enviarán más datos
            outputStream?.close()      // Cerrar el flujo de salida
            socket?.close()            // Cerrar el socket
            Log.d("MainActivity", "Connection closed")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("MainActivity", "Failed to close connection", e)
        } finally {
            socket = null
            outputStream = null
        }
    }

    private fun isConnected(): Boolean {
        return socket?.isConnected == true && !socket!!.isClosed
    }

    override fun onResume() {
        super.onResume()

        val sharedPrefs = getSharedPreferences("steering_prefs", MODE_PRIVATE)
        val maxSteeringAngle = sharedPrefs.getInt("steering_angle", 90) // Valor por defecto 90°

        // Cargar sensibilidades del acelerador, freno, click time limit y swipe threshold
        accelerationSensitivity = sharedPrefs.getFloat(SettingsActivity.PREF_ACCELERATOR_SENSITIVITY, SettingsActivity.DEFAULT_ACCELERATOR_SENSITIVITY)
        brakeSensitivity = sharedPrefs.getFloat(SettingsActivity.PREF_BRAKE_SENSITIVITY, SettingsActivity.DEFAULT_BRAKE_SENSITIVITY)
        clickTimeLimit = sharedPrefs.getFloat(SettingsActivity.PREF_CLICK_TIME_LIMIT, SettingsActivity.DEFAULT_CLICK_TIME_LIMIT)
        val savedSwipeThreshold = sharedPrefs.getFloat(SettingsActivity.PREF_SWIPE_THRESHOLD, SettingsActivity.DEFAULT_SWIPE_THRESHOLD)

        // Convertir swipe threshold de mm a píxeles
        swipeThresholdInPx = savedSwipeThreshold * resources.displayMetrics.xdpi / 25.4f

        // Aplicar el ángulo máximo al volante
        updateMaxSteeringAngle(maxSteeringAngle)
    }
    private fun updateMaxSteeringAngle(angle: Int) {
        // Convertir el ángulo a radianes y mapearlo a la escala de 9.81 m/s² (gravedad)
        val maxAngleInRadians = Math.toRadians(angle.toDouble())
        val maxAngle = Math.sin(maxAngleInRadians) * 9.81f  // mapea a la escala de 9.81 m/s²
        this.maxSteeringAngle = maxAngle.toFloat()
    }

    private fun discoverServerViaUDP() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val udpSocket = DatagramSocket()
                udpSocket.broadcast = true

                val message = "DISCOVER_SERVER".toByteArray()
                val packet = DatagramPacket(
                    message,
                    message.size,
                    InetAddress.getByName("255.255.255.255"),
                    serverPort
                )
                udpSocket.send(packet)

                val buffer = ByteArray(1024)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                udpSocket.soTimeout = 5000  // 5 segundos de tiempo de espera para la respuesta

                try {
                    udpSocket.receive(responsePacket)
                    val response = String(responsePacket.data, 0, responsePacket.length)
                    val (serverIp, port) = response.split(":")
                    serverAddress = serverIp
                    udpSocket.close()

                    Log.d("SteeringWheelActivity", "Server found at IP: $serverAddress")

                    // Establecer la conexión con el servidor utilizando la IP descubierta y el puerto especificado
                    establishConnection()

                } catch (e: SocketTimeoutException) {
                    runOnUiThread {
                        Toast.makeText(this@SteeringWheelActivity, "Server discovery timed out", Toast.LENGTH_SHORT).show()
                        showConnectionOptions()  // Mostrar opciones si no se encuentra el servidor
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "Failed to discover server via UDP", e)
                runOnUiThread {
                    Toast.makeText(this@SteeringWheelActivity, "Error in UDP discovery", Toast.LENGTH_SHORT).show()
                    showConnectionOptions()
                }
            }
        }
    }
    private fun discoverServerViaUsbTethering() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener la IP de USB Tethering
                val usbIp = getUsbTetheringIp()
                if (usbIp != null) {
                    val subnet = usbIp.substringBeforeLast(".")
                    val broadcastAddress = "$subnet.255"  // Direccion de broadcast para el rango

                    val udpSocket = DatagramSocket()
                    udpSocket.broadcast = true

                    val message = "DISCOVER_SERVER".toByteArray()
                    val packet = DatagramPacket(
                        message,
                        message.size,
                        InetAddress.getByName(broadcastAddress),
                        serverPort
                    )
                    udpSocket.send(packet)

                    val buffer = ByteArray(1024)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    udpSocket.soTimeout = 3000  // 3 segundos de tiempo de espera para la respuesta

                    try {
                        udpSocket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length)
                        val (serverIp, port) = response.split(":")
                        serverAddress = serverIp
                        udpSocket.close()

                        Log.d("SteeringWheelActivity", "Server found at IP: $serverAddress")

                        // Establecer la conexión con el servidor utilizando la IP descubierta y el puerto especificado
                        establishConnection()

                    } catch (e: SocketTimeoutException) {
                        runOnUiThread {
                            Toast.makeText(this@SteeringWheelActivity, "Server discovery timed out", Toast.LENGTH_SHORT).show()
                            showConnectionOptions()  // Mostrar opciones si no se encuentra el servidor
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SteeringWheelActivity, "No USB Tethering IP found", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("MainActivity", "No USB Tethering IP found")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "Failed to discover server via UDP over USB tethering", e)
                runOnUiThread {
                    Toast.makeText(this@SteeringWheelActivity, "Error in UDP discovery", Toast.LENGTH_SHORT).show()
                    showConnectionOptions()
                }
            }
        }
    }

    private var isDialogShowing = false

    private fun showConnectionOptions() {
        // Asegúrate de que el diálogo no se muestra dos veces al evitar múltiples llamadas
        if (isDialogShowing) {
            return
        }

        val options = arrayOf("WiFi", "USB Tethering", "Manual Input IP")
        val builder = AlertDialog.Builder(this, R.style.TokyoNightDialogTheme)
        builder.setTitle("Choose Connection Mode")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    discoverServerViaUDP()  // Opción para WiFi
                    isDialogShowing = false
                }
                1 -> {
                    discoverServerViaUsbTethering()  // Opción para USB Tethering Automático
                    isDialogShowing = false
                }
                2 -> {
                    showManualIpInputDialog()  // Opción para IP manual
                    isDialogShowing = false
                }
            }
        }

        builder.setOnDismissListener {
            isDialogShowing = false  // Resetear el estado cuando se cierra el diálogo
        }

        val dialog = builder.create()
        dialog.show()

        // Variable de estado para controlar si el diálogo está mostrando o no
        isDialogShowing = true
    }


    private fun showManualIpInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Server IP")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("Connect") { _, _ ->
            val enteredIp = input.text.toString()
            if (enteredIp.isNotEmpty()) {
                serverAddress = enteredIp
                establishConnection()
            } else {
                Toast.makeText(this, "IP address cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }
    private fun getUsbTetheringIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (networkInterface in interfaces) {
                if (networkInterface.name.contains("rndis") || networkInterface.name.contains("usb")) {
                    for (address in networkInterface.inetAddresses) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            Log.d(
                                "NetworkInterface",
                                "Found USB tethering interface: ${networkInterface.name} with IP: ${address.hostAddress}"
                            )
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("getUsbTetheringIp", "Error retrieving USB Tethering IP", e)
        }
        return null
    }
}