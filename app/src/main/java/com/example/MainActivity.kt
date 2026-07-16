package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ui.theme.*
import java.util.concurrent.TimeUnit

class EyeCareWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        sendNotification(applicationContext)
        return Result.success()
    }

    private fun sendNotification(context: Context) {
        val channelId = "eye_relax_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Eye Relax Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to look away every 20 minutes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to rest your eyes! \uD83D\uDC40")
            .setContentText("Look at something 20 feet away for 20 seconds.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(1, builder.build())
    }
    
    companion object {
        const val WORK_NAME = "EyeCareReminderWork"
        
        fun enqueue(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<EyeCareWorker>(20, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
        
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefs = context.getSharedPreferences("EyeRelaxPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("is_enabled", true)
            
            if (isEnabled) {
                EyeCareWorker.enqueue(context)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                EyeCareScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyeCareScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("EyeRelaxPrefs", Context.MODE_PRIVATE) }
    
    var isEnabled by remember { 
        mutableStateOf(prefs.getBoolean("is_enabled", false)) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            isEnabled = true
            prefs.edit().putBoolean("is_enabled", true).apply()
            EyeCareWorker.enqueue(context)
        } else {
            isEnabled = false
            prefs.edit().putBoolean("is_enabled", false).apply()
            EyeCareWorker.cancel(context)
        }
    }

    LaunchedEffect(Unit) {
        if (!prefs.contains("is_enabled")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    isEnabled = true
                    prefs.edit().putBoolean("is_enabled", true).apply()
                    EyeCareWorker.enqueue(context)
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                isEnabled = true
                prefs.edit().putBoolean("is_enabled", true).apply()
                EyeCareWorker.enqueue(context)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { 
                    Text("EyeRelax", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = TextColor) 
                },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextColor)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("E", color = PrimaryDarkColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BottomNavBgColor)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BottomNavSelectedBgColor)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = PrimaryColor)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Home", color = PrimaryColor, style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
                    Icon(Icons.Outlined.BarChart, contentDescription = "Stats", tint = SecondaryLightTextColor.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Stats", color = SecondaryLightTextColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SecondaryLightTextColor.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Settings", color = SecondaryLightTextColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // Timer Circle
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(224.dp)) {
                    val strokeWidth = 8.dp.toPx()
                    drawCircle(
                        color = CircleTrackColor,
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        color = PrimaryColor,
                        startAngle = -90f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "14:20",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        color = TextColor,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "NEXT REST",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryLightTextColor,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Status Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BadgeBgColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Protection Active", color = BadgeTextColor, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Automatically reminding you to look 20 feet away for 20 seconds every 20 minutes.",
                    color = SecondaryTextColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Control Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBgColor)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Background Service", color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Auto-starts on boot", color = SecondaryTextColor, fontSize = 14.sp)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                isEnabled = true
                                prefs.edit().putBoolean("is_enabled", true).apply()
                                EyeCareWorker.enqueue(context)
                            }
                        } else {
                            isEnabled = false
                            prefs.edit().putBoolean("is_enabled", false).apply()
                            EyeCareWorker.cancel(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryDarkColor,
                        checkedTrackColor = PrimaryColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Efficiency Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                    Icon(Icons.Outlined.BatterySaver, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryTextColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BATTERY OPTIMIZED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SecondaryTextColor, letterSpacing = 1.sp)
                }
                Box(modifier = Modifier.width(1.dp).height(12.dp).background(DividerColor))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    Icon(Icons.Outlined.NotificationImportant, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryTextColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HIGH PRIORITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SecondaryTextColor, letterSpacing = 1.sp)
                }
            }
        }
    }
}
