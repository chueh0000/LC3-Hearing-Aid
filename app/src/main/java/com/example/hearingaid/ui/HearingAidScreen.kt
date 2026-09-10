package com.example.hearingaid.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HearingAidScreen(
    isEngineRunning: Boolean,
    isExclusiveModeActive: Boolean,
    hasPermissions: Boolean,
    isLeAudioActive: Boolean,
    initialBoostLevel: Float,
    initialInputGain: Float,
    initialOutputGain: Float,
    initialDuckAudio: Boolean,
    initialLowLatencyMode: Boolean,
    initialEqGains: FloatArray,
    onToggleEngine: () -> Unit,
    onEqChanged: (bandIndex: Int, dbGain: Float) -> Unit,
    onEqChangedFinished: (bandIndex: Int, dbGain: Float) -> Unit,
    onBoostChanged: (boostLevel: Float) -> Unit,
    onBoostChangedFinished: (boostLevel: Float) -> Unit,
    onInputGainChanged: (dbGain: Float) -> Unit,
    onInputGainChangedFinished: (dbGain: Float) -> Unit,
    onOutputGainChanged: (dbGain: Float) -> Unit,
    onOutputGainChangedFinished: (dbGain: Float) -> Unit,
    onDuckAudioChanged: (Boolean) -> Unit,
    onLowLatencyModeChanged: (Boolean) -> Unit,
    onResetDefaults: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var resetTrigger by remember { mutableIntStateOf(0) }

    var currentBoostLevel by remember(resetTrigger) { mutableFloatStateOf(if (resetTrigger > 0) 0f else initialBoostLevel) }
    var currentInputGain by remember(resetTrigger) { mutableFloatStateOf(if (resetTrigger > 0) 0f else initialInputGain) }
    var currentOutputGain by remember(resetTrigger) { mutableFloatStateOf(if (resetTrigger > 0) 0f else initialOutputGain) }
    var currentDuckAudio by remember(resetTrigger) { mutableStateOf(if (resetTrigger > 0) false else initialDuckAudio) }
    var currentLowLatencyMode by remember(resetTrigger) { mutableStateOf(if (resetTrigger > 0) false else initialLowLatencyMode) }
    val currentEqGains = remember(resetTrigger) { 
        mutableStateListOf(*(if (resetTrigger > 0) Array(6){0f} else initialEqGains.toTypedArray()))
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Controls") },
                    label = { Text("Controls") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.List, contentDescription = "Equalizer") },
                    label = { Text("Equalizer") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Persistent Header ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LE Audio Hearing Aid",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { 
                    onResetDefaults()
                    resetTrigger++
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset to Defaults")
                }
            }

            StatusIndicator("Permissions", hasPermissions)
            StatusIndicator("LE Audio Routing", isLeAudioActive)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Audio Sharing Mode", fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val modeColor = if (!isEngineRunning) Color.Gray else if (isExclusiveModeActive) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    val modeText = if (!isEngineRunning) "Off" else if (isExclusiveModeActive) "Exclusive" else "Shared"
                    Text(
                        text = modeText,
                        color = modeColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermissions) {
                Text(
                    text = "Please grant all permissions in settings.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }

            if (!isLeAudioActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Warning: LE Audio headset not detected! Standard Bluetooth routing may result in high latency (>150ms).",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onToggleEngine,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEngineRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isEngineRunning) "STOP HEARING AID" else "START HEARING AID")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Tab Content Area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Crossfade(targetState = selectedTab, label = "TabTransition") { tab ->
                    when (tab) {
                        0 -> ControlsTab(
                            boostLevel = currentBoostLevel,
                            inputGain = currentInputGain,
                            outputGain = currentOutputGain,
                            onBoostChanged = { 
                                currentBoostLevel = it
                                onBoostChanged(it) 
                            },
                            onBoostChangedFinished = { onBoostChangedFinished(currentBoostLevel) },
                            onInputGainChanged = { 
                                currentInputGain = it
                                onInputGainChanged(it) 
                            },
                            onInputGainChangedFinished = { onInputGainChangedFinished(currentInputGain) },
                            onOutputGainChanged = { 
                                currentOutputGain = it
                                onOutputGainChanged(it) 
                            },
                            onOutputGainChangedFinished = { onOutputGainChangedFinished(currentOutputGain) },
                            duckAudio = currentDuckAudio,
                            onDuckAudioChanged = {
                                currentDuckAudio = it
                                onDuckAudioChanged(it)
                            },
                            lowLatencyMode = currentLowLatencyMode,
                            onLowLatencyModeChanged = {
                                currentLowLatencyMode = it
                                onLowLatencyModeChanged(it)
                            }
                        )
                        1 -> EqualizerTab(
                            eqGains = currentEqGains,
                            onEqChanged = { index, gain -> 
                                currentEqGains[index] = gain
                                onEqChanged(index, gain) 
                            },
                            onEqChangedFinished = onEqChangedFinished
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlsTab(
    boostLevel: Float,
    inputGain: Float,
    outputGain: Float,
    onBoostChanged: (Float) -> Unit,
    onBoostChangedFinished: () -> Unit,
    onInputGainChanged: (Float) -> Unit,
    onInputGainChangedFinished: () -> Unit,
    onOutputGainChanged: (Float) -> Unit,
    onOutputGainChangedFinished: () -> Unit,
    duckAudio: Boolean,
    onDuckAudioChanged: (Boolean) -> Unit,
    lowLatencyMode: Boolean,
    onLowLatencyModeChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Lower Background Audio", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Automatically turns down YouTube and other apps while Hearing Aid is running.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = duckAudio,
                onCheckedChange = onDuckAudioChanged
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Low Latency Mode", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Bypasses the system audio mixer for lowest latency. Other app audio may be paused.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = lowLatencyMode,
                onCheckedChange = onLowLatencyModeChanged
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // DRC Slider (Speech Boost)
        Text(text = "Speech Boost (Dynamic Range Compression)", fontWeight = FontWeight.SemiBold)
        Slider(
            value = boostLevel,
            onValueChange = onBoostChanged,
            onValueChangeFinished = onBoostChangedFinished,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Normal")
            Text("Max Boost")
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Microphone Gain Slider (Drive)
        Text(text = "Microphone Gain (Drive)", fontWeight = FontWeight.SemiBold)
        Text(text = "Pushes audio harder into Speech Boost", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = inputGain,
            onValueChange = onInputGainChanged,
            onValueChangeFinished = onInputGainChangedFinished,
            valueRange = -12f..24f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-12 dB")
            Text("${inputGain.toInt()} dB")
            Text("+24 dB")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Output Volume Slider
        Text(text = "Output Volume", fontWeight = FontWeight.SemiBold)
        Slider(
            value = outputGain,
            onValueChange = onOutputGainChanged,
            onValueChangeFinished = onOutputGainChangedFinished,
            valueRange = -12f..24f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-12 dB")
            Text("${outputGain.toInt()} dB")
            Text("+24 dB")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun EqualizerTab(
    eqGains: List<Float>,
    onEqChanged: (Int, Float) -> Unit,
    onEqChangedFinished: (Int, Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Graphic Equalizer (dB)", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val labels = listOf("250", "500", "1k", "2k", "4k", "8k")
            for (i in 0 until 6) {
                VerticalSliderWithLabel(
                    label = labels[i],
                    bandIndex = i,
                    gain = eqGains[i],
                    onGainChanged = onEqChanged,
                    onGainChangedFinished = onEqChangedFinished
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            text = if (isActive) "OK" else "ERROR",
            color = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VerticalSliderWithLabel(
    label: String,
    bandIndex: Int,
    gain: Float,
    onGainChanged: (Int, Float) -> Unit,
    onGainChangedFinished: (Int, Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        var maxHeightPx by remember { mutableFloatStateOf(1f) } // Default to 1f to avoid division by zero
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp)
                .padding(vertical = 8.dp)
                .onSizeChanged { size ->
                    maxHeightPx = size.height.toFloat()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            // The thumb & interaction logic
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { onGainChangedFinished(bandIndex, gain) },
                            onDragCancel = { onGainChangedFinished(bandIndex, gain) },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                // Calculate position based on drag (y=0 is top, y=maxHeightPx is bottom)
                                val y = change.position.y.coerceIn(0f, maxHeightPx)
                                // Map y to gain (-12 to +24)
                                val mappedGain = 24f - (y / maxHeightPx) * 36f
                                onGainChanged(bandIndex, mappedGain)
                            }
                        )
                    }
            ) {
                // Calculate thumb position (gain -12 to +24 mapped to y offset maxHeightPx to 0)
                val thumbY = (1f - ((gain + 12f) / 36f)) * maxHeightPx
                
                // Draw filled track
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = with(density) { thumbY.toDp() })
                        .height(with(density) { (maxHeightPx - thumbY).toDp() })
                        .width(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                // Draw thumb
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = with(density) { thumbY.toDp() } - 10.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        
        Text(text = "${gain.toInt()} dB", fontSize = 12.sp)
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HearingAidScreenPreview() {
    MaterialTheme {
        Surface {
            HearingAidScreen(
                isEngineRunning = false,
                isExclusiveModeActive = false,
                hasPermissions = true,
                isLeAudioActive = true,
                initialBoostLevel = 0f,
                initialInputGain = 0f,
                initialOutputGain = 0f,
                initialDuckAudio = false,
                initialLowLatencyMode = false,
                initialEqGains = FloatArray(6) { 0f },
                onToggleEngine = {},
                onEqChanged = { _, _ -> },
                onEqChangedFinished = { _, _ -> },
                onBoostChanged = {},
                onBoostChangedFinished = {},
                onInputGainChanged = {},
                onInputGainChangedFinished = {},
                onOutputGainChanged = {},
                onOutputGainChangedFinished = {},
                onDuckAudioChanged = {},
                onLowLatencyModeChanged = {},
                onResetDefaults = {}
            )
        }
    }
}
