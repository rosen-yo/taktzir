package com.taktzir.news.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taktzir.news.data.Category
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SubScreenHeader("הגדרות", onBack)
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp)) {
                item { Column { AiSection(settings.apiKey, settings.model, vm::saveApiKey, vm::saveModel) } }
                item { Divider() }
                item { Column { AmountSection(settings.perDay, vm::savePerDay) } }
                item { Divider() }
                item { Column { NotificationsSection(settings.notifications, vm::saveNotifications) } }
                item { Divider() }
                item {
                    Column {
                    SectionTitle("על האפליקציה")
                    Hint(
                        "כתבות שלא נקראו נשמרות עד שבוע ואז נעלמות. ההיסטוריה נשמרת 30 יום. " +
                            "הרשימה מתרעננת אוטומטית כל כמה שעות, ובמשיכה למטה."
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AiSection(apiKey: String, model: String, onSaveKey: (String) -> Unit, onSaveModel: (String) -> Unit) {
    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember(model) { mutableStateOf(model) }
    var showModel by remember { mutableStateOf(false) }

    SectionTitle("סיכומים ודירוג בעזרת AI")
    Hint(
        if (apiKey.isBlank()) {
            "עם מפתח Claude API, האפליקציה בוחרת רק את הכתבות החשובות באמת, ממזגת כפילויות וכותבת לכל כתבה כותרת וסיכום קצר בעברית."
        } else {
            "מחובר ✓  ·  מפתח שמור: …" + apiKey.takeLast(4)
        }
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(if (apiKey.isBlank()) "מפתח API" else "החלפת מפתח") },
        placeholder = { Text("sk-ant-…") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onSaveKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("שמירה") }
        if (apiKey.isNotBlank()) {
            TextButton(onClick = { onSaveKey("") }) { Text("הסרת המפתח") }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showModel = !showModel }) { Text("מתקדם") }
    }
    if (showModel) {
        OutlinedTextField(
            value = modelInput,
            onValueChange = { modelInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("מודל") },
        )
        TextButton(onClick = { onSaveModel(modelInput) }) { Text("שמירת מודל") }
    }
}

@Composable
private fun AmountSection(perDay: Int, onSave: (Int) -> Unit) {
    var value by remember(perDay) { mutableFloatStateOf(perDay.toFloat()) }
    SectionTitle("כמות כתבות")
    Hint("עד ${value.toInt()} כתבות ביום בכל נושא. כשאין מספיק כתבות חשובות, יוצגו פחות.")
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onSave(value.toInt()) },
        valueRange = 4f..12f,
        steps = 7,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsSection(
    config: Map<Category, List<String>>,
    onSave: (Map<Category, List<String>>) -> Unit,
) {
    val context = LocalContext.current
    var pendingEnable by remember { mutableStateOf<Category?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cat = pendingEnable
        if (granted && cat != null) onSave(config + (cat to listOf("08:00", "20:00")))
        pendingEnable = null
    }

    fun enable(cat: Category) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingEnable = cat
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSave(config + (cat to listOf("08:00", "20:00")))
        }
    }

    fun addTime(cat: Category) {
        TimePickerDialog(context, { _, h, m ->
            val t = String.format(Locale.US, "%02d:%02d", h, m)
            val times = (config[cat].orEmpty() + t).distinct().sorted()
            onSave(config + (cat to times))
        }, 12, 0, true).show()
    }

    SectionTitle("התראות")
    Hint("בשעות שתבחר, תקבל התראה רק אם הופיעה כתבה חשובה במיוחד בנושא שעוד לא התרעת עליה.")
    Spacer(Modifier.height(8.dp))

    Category.entries.forEach { cat ->
        val times = config[cat]
        val enabled = times != null
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(cat.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { on -> if (on) enable(cat) else onSave(config - cat) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
        if (times != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                times.forEach { t ->
                    Pill("$t  ✕") { onSave(config + (cat to times - t)) }
                }
                Pill("+ שעה") { addTime(cat) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
