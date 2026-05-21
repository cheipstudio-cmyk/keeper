package com.secondream.keeper.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.secondream.keeper.data.model.Attachment
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaViewerDialog(
    attachment: Attachment,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            when (attachment.type) {
                "image" -> {
                    ImagePlayerView(attachment = attachment, onDismiss = onDismiss)
                }
                "video" -> {
                    SimulatedVideoView(attachment = attachment, onDismiss = onDismiss)
                }
                "audio" -> {
                    AudioPlayerView(attachment = attachment, onDismiss = onDismiss)
                }
                else -> {
                    SimulatedDocView(attachment = attachment, onDismiss = onDismiss)
                }
            }

            // Top exit button overlay
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Viewer",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ImagePlayerView(attachment: Attachment, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(attachment.uri)
                .crossfade(true)
                .build(),
            contentDescription = attachment.name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = attachment.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${attachment.size} • Pinza o trascina per ingrandire",
                color = Color.LightGray,
                fontSize = 14.sp
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SimulatedVideoView(attachment: Attachment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isError by remember { mutableStateOf(false) }

    // Single ExoPlayer for the entire viewer lifecycle
    val exoPlayer = remember(attachment.uri) {
        ExoPlayer.Builder(context).build().apply {
            try {
                val mediaUri = when {
                    attachment.uri.startsWith("file://") ||
                    attachment.uri.startsWith("http://") ||
                    attachment.uri.startsWith("https://") ||
                    attachment.uri.startsWith("content://") -> Uri.parse(attachment.uri)
                    else -> {
                        val file = java.io.File(attachment.uri)
                        if (file.exists()) Uri.fromFile(file) else Uri.parse(attachment.uri)
                    }
                }
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            } catch (e: Exception) {
                e.printStackTrace()
                isError = true
            }
        }
    }

    // Listen for state, errors, and play/pause toggles
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll position only while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isError) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Impossibile riprodurre video (Anteprima non disponibile)",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false      // disable Media3 controls completely
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = attachment.name,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Video • ${attachment.size}",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Timeline (single, custom)
        val progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f
        val formatTime: (Long) -> String = { ms ->
            val totalSec = ms / 1000
            val seconds = totalSec % 60
            val minutes = totalSec / 60
            String.format("%d:%02d", minutes, seconds)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentPosition),
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { newProgress ->
                    if (duration > 0L) {
                        val newPos = (newProgress * duration).toLong()
                        exoPlayer.seekTo(newPos)
                        currentPosition = newPos
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFFFFA000),
                    inactiveTrackColor = Color.DarkGray,
                    thumbColor = Color(0xFFFFCA28)
                )
            )
            Text(
                text = formatTime(duration),
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Single unified control row: restart + one play/pause
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    exoPlayer.seekTo(0L)
                    currentPosition = 0L
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Riavvia",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            FloatingActionButton(
                onClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                containerColor = Color(0xFFFFB300),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun SimulatedDocView(attachment: Attachment, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFEFEFEF)) // Sophisticated light documents background
                .padding(16.dp)
        ) {
            // Doc Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (attachment.name.contains("xlsx", true)) Icons.Default.GridOn else Icons.Default.Description,
                        contentDescription = "Doc Icon",
                        tint = if (attachment.name.contains("xlsx", true)) Color(0xFF1B5E20) else Color(0xFF0D47A1),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = attachment.name,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Size: ${attachment.size} • PDF Template",
                            color = Color.DarkGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Divider(color = Color.LightGray, thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Document Content Renderer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                if (attachment.name.contains("Travel", true)) {
                    // Display details of the simulated Travel itinerary PDF
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("SUMMER TRAVEL ITINERARY", fontWeight = FontWeight.Black, color = Color(0xFFFFA000), fontSize = 18.sp)
                            Text("Location: Blue Coast, Italy", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        items(listOf(
                            "Day 1: Arrival & Coastal Welcome Dinner at La Trattoria Marina" to "Checked in - 2:00 PM",
                            "Day 2: Sunrise Sailboat Cruise to Amalfi Cliffs" to "Tickets secured - Marina Pier 4",
                            "Day 3: Guided Vespa Ride through Historic Lemon Groves" to "Rental starts at 9:00 AM",
                            "Day 4: Hiking the Path of the Gods and Gelato sampling" to "Peak trail views",
                            "Day 5: Departure flight via Naples Capodichino" to "Flight leaves 3:15 PM"
                        )) { (item, desc) ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, "Accomplished", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(item, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text(desc, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Divider(color = Color(0xFFF0F0F0))
                            }
                        }
                    }
                } else if (attachment.name.contains("Stats", true) || attachment.name.contains("xlsx", true)) {
                    // Render an awesome, interactive grid/table for spreadsheet
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("SHOPPING STATISTICS & RUNNING COSTS", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 16.sp)
                            Text("Quarter Q2 Cost Index Analysis", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9))
                                    .padding(8.dp)
                            ) {
                                Text("Item Description", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Unit Price", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Quantity", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Total", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                            }
                        }

                        items(listOf(
                            Triple("Organic Avocado Case", "$2.50", "12"),
                            Triple("Whole Bean Kona Roast", "$18.00", "4"),
                            Triple("Almond Milk Carton 6-Pack", "$9.50", "5"),
                            Triple("Gluten-Free Oats Batch", "$3.80", "20"),
                            Triple("Pure Blossom Honey Keg", "$42.00", "1")
                        )) { (desc, p, q) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Text(desc, modifier = Modifier.weight(2f), fontSize = 12.sp)
                                Text(p, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Text(q, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                val t = "$" + String.format("%.2f", p.substring(1).toDouble() * q.toInt())
                                Text(t, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Divider(color = Color(0xFFEEEEEE))
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("Grand Sum Total: ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$239.50", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                } else if (attachment.name.contains("Mindmap", true)) {
                    // Mindmap visual diagram layout
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TECH VENTURE MINDMAP", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1565C0))
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                        ) {
                            Text("Core Product Engine", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ArrowDownward, "connector", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                                Text("Database (Room)", modifier = Modifier.padding(8.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                                Text("Offline Upload Async", modifier = Modifier.padding(8.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Icon(Icons.Default.ArrowDownward, "connector", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))) {
                            Text("Jetpack Compose Animations Mode", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF6A1B9A))
                        }
                    }
                } else {
                    // Readme mono-spaced template viewer
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = "=== KEEP NOTES README SYSTEM INTEGRATOR ===\n" +
                                        "Project build.gradle.kts OK\n" +
                                        "Adaptive icons customized successfully\n" +
                                        "Room Reactive State Flows up-and-running\n" +
                                        "File simulation modules compiled successfully\n" +
                                        "Animations timeline runs smoothly <300ms springs.\n\n" +
                                        "[Status] COMPLETED\n" +
                                        "[Version] 1.0.4-LTS\n" +
                                        "[Device] Android Virtual Screen\n" +
                                        "Enjoy using Google Keep with amazing attachments!",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerView(attachment: Attachment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    LaunchedEffect(attachment.uri) {
        try {
            val mp = android.media.MediaPlayer().apply {
                setDataSource(attachment.uri)
                prepare()
                duration = this.duration
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            e.printStackTrace()
            duration = 15000 // Fallback duration 15s
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            kotlinx.coroutines.delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Audio Icon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = attachment.name,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        Text(
            text = attachment.size,
            fontSize = 14.sp,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Play/Pause button
        IconButton(
            onClick = {
                val mp = mediaPlayer
                if (mp != null) {
                    if (isPlaying) {
                        try {
                            mp.pause()
                        } catch (e: Exception) { e.printStackTrace() }
                        isPlaying = false
                    } else {
                        try {
                            mp.start()
                        } catch (e: Exception) { e.printStackTrace() }
                        isPlaying = true
                    }
                } else {
                    isPlaying = !isPlaying
                }
            },
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Basic timeline slider / tracker
        val posF = currentPosition.toFloat()
        val durF = if (duration > 0) duration.toFloat() else 100f
        Slider(
            value = posF.coerceIn(0f, durF),
            onValueChange = { newVal ->
                try {
                    mediaPlayer?.seekTo(newVal.toInt())
                } catch (e: Exception) { e.printStackTrace() }
                currentPosition = newVal.toInt()
            },
            valueRange = 0f..durF,
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format("%02d:%02d", (currentPosition / 1000) / 60, (currentPosition / 1000) % 60),
                color = Color.LightGray,
                fontSize = 12.sp
            )
            Text(
                text = String.format("%02d:%02d", (duration / 1000) / 60, (duration / 1000) % 60),
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}
