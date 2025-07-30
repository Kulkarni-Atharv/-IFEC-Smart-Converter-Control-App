# IFEC Smart Converter Control App

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin)

A native Android application for **Smart DC-DC Converter** project, submitted to the IEEE International Future Energy Challenge (IFEC) 2025.

The app provides a clean and intuitive interface to wirelessly monitor and control the converter hardware via an ESP8266 Wi-Fi module.

![1751719154874 (1) (1)](https://github.com/user-attachments/assets/f6158fcc-3514-4a5f-8cb4-0d18b56bbf67)

## ✨ Features

* **Wireless Connectivity:** Connects to the ESP8266 module over a local Wi-Fi network using its IP address and port.
* **Live Status Indicator:** Immediately see the connection status (Connected, Disconnected, Connecting).
* **Output Voltage Control:** Set a precise output voltage between 20V and 150V. The app includes input validation to prevent out-of-range values.
* **Output Power Control:** Remotely turn the converter's output ON or OFF.
* **Real-time Monitoring:** View the measured output voltage (`Vmeas`) updated live from the hardware.
* **Dynamic Voltage Chart:** A live-scrolling chart visualizes the measured voltage over a 30-second window, providing immediate feedback on performance and stability.
* **Enable/Disable Measurement:** Users can start or stop the live data stream to conserve network bandwidth.

## 🛠️ How It Works

The application communicates with the ESP8266 microcontroller running on the converter hardware. It uses a simple client-server model over HTTP:

1.  The Android app acts as an HTTP client.
2.  The ESP8266 runs a lightweight web server with specific API endpoints (e.g., `/setVoltage`, `/getMeasurement`, `/setOutput`).
3.  The app sends POST requests with JSON payloads to control the converter and GET requests to retrieve measurement data.
4.  The UI is built with Jetpack Compose and uses Kotlin Coroutines to handle asynchronous network operations without blocking the main thread.

## 🚀 Technologies Used

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
* **Asynchronous Programming:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
* **Networking:** [HttpURLConnection](https://developer.android.com/reference/java/net/HttpURLConnection) for making HTTP requests.
* **JSON Parsing:** Built-in `org.json` library.

## ⚙️ Setup and Installation

To build and run this project yourself:

1.  Clone the repository:
    ```bash
    git clone [https://github.com/your-username/your-repo-name.git](https://github.com/your-username/your-repo-name.git)
    ```
2.  Open the project in [Android Studio](https://developer.android.com/studio).
3.  Let Gradle sync and download the required dependencies.
4.  Run the app on an Android emulator or a physical device.
5.  To connect to hardware, ensure your Android device is on the same Wi-Fi network as your ESP8266 and enter the correct IP address and port.

## 📄 License

This project is open-source and licensed under the **MIT License**. See the `LICENSE` file for more details.
