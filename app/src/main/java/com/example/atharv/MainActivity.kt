
package com.example.atharv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atharv.ui.theme.AtharvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import androidx.compose.ui.graphics.nativeCanvas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtharvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    ConverterUI()
                }
            }
        }
    }
}

data class DataPoint(
    val timestamp: Long,
    val value: Float
)

class ESP8266Client {
    private var baseUrl: String = ""

    fun setConnection(ipAddress: String, port: String) {
        baseUrl = "http://$ipAddress:$port"
    }

    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun setVoltage(voltage: Float): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/setVoltage")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val jsonData = JSONObject()
                jsonData.put("voltage", voltage)
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonData.toString())
                writer.flush()
                writer.close()
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun setOutputState(state: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/setOutput")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val jsonData = JSONObject()
                jsonData.put("state", state)
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonData.toString())
                writer.flush()
                writer.close()
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getMeasurement(): Float? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/getMeasurement")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    connection.disconnect()
                    val json = JSONObject(response)
                    json.getDouble("voltage").toFloat()
                } else {
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun ConverterUI() {
    val scrollState = rememberScrollState()
    var isConnected by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("192.168.4.1") }
    var port by remember { mutableStateOf("80") }
    var vset by remember { mutableStateOf("") }
    var vmeas by remember { mutableStateOf("----") }
    var outputOn by remember { mutableStateOf(false) }
    var measurementEnabled by remember { mutableStateOf(false) }
    var setVoltageApplied by remember { mutableStateOf(0.0f) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var isConnecting by remember { mutableStateOf(false) }

    val esp8266Client = remember { ESP8266Client() }
    val coroutineScope = rememberCoroutineScope()
    val inputVoltage = 400.0f

    // Chart config
    val chartTimeWindow = 30_000L // 30 seconds
    val chartMaxVoltage = 200f

    // Data points for 30s at 100ms = 300 points max
    var dataPoints by remember { mutableStateOf(listOf<DataPoint>()) }
    val maxDataPoints = (chartTimeWindow / 100).toInt() + 2

    fun validateVoltage(input: String): Boolean {
        val voltage = input.toFloatOrNull() ?: return false
        return voltage >= 20.0f && voltage <= 150.0f
    }

    fun formatVoltage(voltage: Float): Float {
        return (kotlin.math.round(voltage * 10) / 10.0).toFloat()
    }

    fun toggleConnection() {
        if (isConnected) {
            isConnected = false
            connectionStatus = "Disconnected"
            dataPoints = emptyList()
            vmeas = "--"
            outputOn = false
            measurementEnabled = false
            setVoltageApplied = 0.0f
        } else {
            isConnecting = true
            connectionStatus = "Connecting..."
            coroutineScope.launch {
                esp8266Client.setConnection(ipAddress, port)
                val connected = esp8266Client.testConnection()
                if (connected) {
                    isConnected = true
                    connectionStatus = "Connected"
                } else {
                    connectionStatus = "Connection Failed"
                    delay(2000)
                    connectionStatus = "Disconnected"
                }
                isConnecting = false
            }
        }
    }

    fun applyVoltage() {
        val voltage = vset.toFloatOrNull()
        if (voltage != null && validateVoltage(vset) && isConnected) {
            coroutineScope.launch {
                val success = esp8266Client.setVoltage(formatVoltage(voltage))
                if (success) {
                    setVoltageApplied = formatVoltage(voltage)
                }
            }
        }
    }

    fun toggleOutput() {
        if (isConnected && setVoltageApplied > 0) {
            coroutineScope.launch {
                val newState = !outputOn
                val success = esp8266Client.setOutputState(newState)
                if (success) {
                    outputOn = newState
                }
            }
        }
    }

    // Live data updates
    LaunchedEffect(isConnected, measurementEnabled, outputOn) {
        if (measurementEnabled && isConnected) {
            while (measurementEnabled && isConnected) {
                delay(100)
                val measuredVoltage = esp8266Client.getMeasurement()
                if (measuredVoltage != null) {
                    vmeas = String.format("%.1f", measuredVoltage)
                    val now = System.currentTimeMillis()
                    val newPoint = DataPoint(now, measuredVoltage)
                    // Remove points older than 30s
                    val minTime = now - chartTimeWindow
                    dataPoints = (dataPoints + newPoint)
                        .dropWhile { it.timestamp < minTime }
                        .takeLast(maxDataPoints)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Section with WiFi Connection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when {
                                    isConnected -> Color(0xFF4CAF50)
                                    isConnecting -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        connectionStatus,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConnected -> Color(0xFF4CAF50)
                            isConnecting -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                    )
                }
                Text(
                    "System Specifications: Input 400Vdc → Output 20-150Vdc (1.5kW max)",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    "ESP8266 WiFi Configuration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2196F3)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("IP Address:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(70.dp))
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isConnected,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Port:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(70.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                        enabled = !isConnected,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { toggleConnection() },
                        enabled = !isConnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0xFFF44336) else Color(0xFF2196F3)
                        )
                    ) {
                        Text(if (isConnected) "Disconnect" else "Connect")
                    }
                }
            }
        }

        // Input Voltage Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Input Voltage (Fixed):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${inputVoltage.toInt()} Vdc",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }
        }

        // Setting Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Output Voltage Control",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2196F3)
                )
                Text(
                    "Valid Range: 20.0V - 150.0V (Resolution: 0.1V)",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Set Voltage (V)", fontSize = 14.sp, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = vset,
                        onValueChange = { vset = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = { applyVoltage() },
                        enabled = isConnected && validateVoltage(vset),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Apply")
                    }
                }
                if (setVoltageApplied > 0) {
                    Text(
                        "Applied Set Voltage: ${String.format("%.1f", setVoltageApplied)}V",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text("Output Control", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { toggleOutput() },
                        enabled = isConnected && setVoltageApplied > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (outputOn) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Text(if (outputOn) "Turn OFF" else "Turn ON")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Measured Voltage (V)", fontSize = 14.sp, modifier = Modifier.width(140.dp))
                    OutlinedTextField(
                        value = vmeas,
                        onValueChange = {},
                        singleLine = true,
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                }
            }
        }

        // Only show the chart when measurement is enabled and connected
        if (measurementEnabled && isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        "Live Voltage Chart (0-30s, 0-200V)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2196F3)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveChart(
                        dataPoints = dataPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        timeWindow = chartTimeWindow,
                        maxVoltage = chartMaxVoltage
                    )
                    Text(
                        "Shows last 30 seconds of voltage data",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Enable/Disable button
        Button(
            onClick = { measurementEnabled = !measurementEnabled },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (measurementEnabled) Color(0xFFF44336) else Color(0xFF4CAF50)
            ),
            enabled = isConnected
        ) {
            Text(if (measurementEnabled) "Disable Measurement" else "Enable Measurement")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LiveChart(
    dataPoints: List<DataPoint>,
    modifier: Modifier = Modifier,
    timeWindow: Long = 30_000L,
    maxVoltage: Float = 200f
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 40f

        // Draw background
        drawRect(
            color = Color(0xFFF8F9FA),
            topLeft = Offset(0f, 0f),
            size = size
        )

        // Draw Y-axis grid and labels (0, 40, 80, 120, 160, 200)
        val ySteps = 5
        val gridColor = Color(0xFFE0E0E0)
        val textPaint = android.graphics.Paint().apply {
            color = Color.Gray.toArgb()
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in 0..ySteps) {
            val voltage = maxVoltage * i / ySteps
            val y = padding + (height - 2 * padding) * (ySteps - i) / ySteps
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${voltage.toInt()}V",
                padding / 2,
                y + 8,
                textPaint
            )
        }

        // Draw X-axis grid and labels (0, 3, 6, ..., 30)
        val xSteps = 10
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        for (i in 0..xSteps) {
            val seconds = i * 3
            val x = padding + (width - 2 * padding) * i / xSteps
            drawLine(
                color = gridColor,
                start = Offset(x, padding),
                end = Offset(x, height - padding),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${seconds}s",
                x,
                height - padding / 3,
                textPaint
            )
        }

        // Draw the line (strip chart)
        if (dataPoints.size > 1) {
            val now = System.currentTimeMillis()
            val minTime = now - timeWindow
            val chartWidth = width - 2 * padding
            val chartHeight = height - 2 * padding
            val minVoltage = 0f

            val visiblePoints = dataPoints.filter { it.timestamp >= minTime }
            if (visiblePoints.size > 1) {
                val path = Path()
                visiblePoints.forEachIndexed { idx, point ->
                    val x = padding + chartWidth * (point.timestamp - minTime).toFloat() / timeWindow
                    val y = padding + chartHeight * (1 - (point.value - minVoltage) / (maxVoltage - minVoltage))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Color(0xFF2196F3),
                    style = Stroke(width = 2f)
                )
            }
        }

        // No data message
        if (dataPoints.isEmpty()) {
            val paint = android.graphics.Paint().apply {
                color = Color.Gray.toArgb()
                textSize = 32f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Waiting for data...",
                width / 2,
                height / 2,
                paint
            )
        }
    }
}