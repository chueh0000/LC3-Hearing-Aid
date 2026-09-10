# LC3-Hearing-Aid

An advanced, low-latency hearing aid application for Android designed specifically for **Bluetooth LE Audio (LC3)** devices. Built with a high-performance C++ DSP pipeline and an intuitive Jetpack Compose user interface, this application acts as a real-time audio enhancement bridge between the device microphone and the user's LE Audio headset.

## Key Features

*   **Low-Latency Audio Engine**: Built on top of **Google Oboe**, utilizing a `FullDuplexStream` for robust clock-drift synchronization between input and output audio endpoints. Supports exclusive stream modes for bypassing the system mixer to achieve the lowest possible latency.
*   **Real-time DSP Pipeline (C++)**: 
    *   **6-Band Graphic Equalizer**: Biquad peaking EQ filters with linear parameter smoothing.
    *   **Dynamic Range Compressor (DRC)**: Optimized with fast math approximations to provide efficient gain reduction and peak limiting, preventing sudden loud noises from damaging hearing.
    *   **Speech Boost**: A simplified control that intelligently adjusts DRC and gain parameters to enhance speech clarity.
*   **Hardware Validation**: Automatically detects if the active audio output is a Bluetooth LE Audio endpoint (`TYPE_BLE_HEADSET` or `TYPE_BLE_SPEAKER`) to warn the user about potential high latency on standard Bluetooth connections.
*   **Background Processing**: Runs as a robust Android foreground service with microphone access, utilizing partial wakelocks, audio focus management, and automatic audio ducking during notifications or incoming calls.
*   **Performance Optimized**: Integrates with the **Android Dynamic Performance Framework (ADPF)** to provide CPU hints, ensuring the DSP audio thread remains on a performant CPU core at an adequate frequency without thermal throttling.
*   **Modern Android UI**: Built entirely with **Jetpack Compose**, featuring real-time interactive vertical drag faders for the EQ, hardware routing status indicators, and immediate parameter updates via JNI.

## Project Structure

The project is structured into several core layers:

*   **Native DSP & Audio Engine (`app/src/main/cpp/`)**:
    *   `AudioEngine.cpp/h`: Manages Oboe streams, duplex synchronization, and ADPF hints.
    *   `DspFilters.cpp/h`: Implements the `Biquad` EQ, `DynamicRangeCompressor`, and `DspPipeline` with atomic parameter updates.
    *   `native-lib.cpp`: Provides the JNI bridge for Kotlin interoperability.
*   **Android Service Layer (`app/src/main/java/.../AudioProcessingService.kt`)**:
    *   Manages the audio lifecycle, foreground service notification, audio focus/ducking, and JNI communication.
*   **User Interface (`app/src/main/java/.../ui/HearingAidScreen.kt`)**:
    *   Provides the Compose UI for controlling master gains, speech boost, EQ bands, and latency mode toggles.

## Prerequisites

*   **Android Studio** (Koala or newer recommended)
*   **Android NDK** (Version 27.0.12077973 specified in `build.gradle.kts`)
*   **Min SDK**: API 33 (Android 13)
*   **Target SDK**: API 34 (Android 14)
*   For the best experience, an Android device and a headset that both support **Bluetooth LE Audio** are required.

## Building and Running

1.  Clone the repository:
    ```bash
    git clone git@github.com:chueh0000/LC3-Hearing-Aid.git
    ```
2.  Open the project in Android Studio.
3.  Sync the project with Gradle files. The project uses Google's Prefab to automatically fetch and link the Oboe C++ library.
4.  Build and run on a physical Android device (emulators will not provide accurate audio latency or Bluetooth LE Audio routing).
5.  **Permissions**: On first launch, grant the required Microphone, Nearby Devices (Bluetooth), and Notification permissions.

## Architecture

The application employs a thread-safe architecture where the Jetpack Compose UI pushes state changes to `SettingsManager` (for persistence) and `AudioProcessingService`. The service then calls native JNI functions to update `std::atomic` variables in the `DspPipeline`. This ensures the high-priority C++ audio thread is never blocked by UI or Java garbage collection pauses, maintaining stable, glitch-free audio processing.