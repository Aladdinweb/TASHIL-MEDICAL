package dz.iline.tashilmedical
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.*
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        installSplashScreen(); super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable fun App() {
    val ctx = LocalContext.current; val dao = remember { Db.get(ctx).dao() }
    var p by remember { mutableStateOf<Profile?>(null) }; var loaded by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { p = dao.profile(); loaded = true }
    if (!loaded) return
    val cur = p
    when {
        cur == null -> Register { p = it }
        settings -> Settings { settings = false }
        else -> Dashboard(cur, { p = it }) { settings = true }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Drop(label: String, opts: List<String>, value: String, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }) { opts.forEach { o -> DropdownMenuItem({ Text(o) }, { pick(o); open = false }) } }
    }
}

@Composable fun Register(done: (Profile) -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }; var st by remember { mutableStateOf("") }; var fac by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }; var shift by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TASHIL MEDICAL", style = MaterialTheme.typography.headlineMedium)
        Text("Aucun numéro de téléphone requis.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("Nom & Prénom") }, modifier = Modifier.fillMaxWidth())
        Drop("Structure", Catalog.establishments.keys.toList(), st) { st = it; fac = "" }
        Drop("Établissement / Site", Catalog.establishments[st] ?: emptyList(), fac) { fac = it }
        Drop("Rôle / Spécialité", Catalog.roles, role) { role = it }
        Drop("Équipe de garde", Catalog.shifts, shift) { shift = it }
        Button(enabled = listOf(name, st, fac, role, shift).all { it.isNotBlank() }, modifier = Modifier.fillMaxWidth(), onClick = {
            val p = Profile(uid = UUID.randomUUID().toString().take(8), name = name.trim(), structure = st, facility = fac,
                role = role, shift = shift, etab = Serial.etab(st), dept = Serial.dept(st, fac))
            scope.launch { Db.get(ctx).dao().save(p); done(p) }
        }) { Text("Continuer") }
    }
}

@Composable fun Dashboard(p: Profile, upd: (Profile) -> Unit, openSettings: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val dao = remember { Db.get(ctx).dao() }
    var staff by remember { mutableStateOf<List<Staff>>(emptyList()) }
    var show by remember { mutableStateOf(false) }; var target by remember { mutableStateOf(Catalog.roles[0]) }
    var info by remember { mutableStateOf("") }
    LaunchedEffect(p.onDuty) { while (true) { staff = withContext(Dispatchers.IO) { runCatching { Bridge.staff(p) }.getOrDefault(staff) }; delay(20000) } }
    fun send(t: String) = scope.launch {
        info = withContext(Dispatchers.IO) { runCatching { Bridge.sendAlert(p, t); "Alerte envoyée → $t" }.getOrElse { "Échec d'envoi" } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (p.onDuty) "🟢 En Service" else "⚪ Hors Service", style = MaterialTheme.typography.titleLarge)
                    Text("${p.name} · ${p.role} · ${p.shift}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(p.onDuty, { on ->
                    val n = p.copy(onDuty = on)
                    scope.launch {
                        dao.save(n); upd(n)
                        withContext(Dispatchers.IO) { runCatching { Bridge.setStatus(n) } }
                        val i = Intent(ctx, AlertService::class.java)
                        if (on) ContextCompat.startForegroundService(ctx, i) else { ctx.stopService(i); Alarm.stop(ctx) }
                    }
                })
            }
        }
        AssistChip({ show = true }, { Text("🟢 ${staff.size} Personnel en service") })
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Demande d'urgence ciblée", style = MaterialTheme.typography.titleMedium)
                Drop("Rôle demandé", Catalog.roles, target) { target = it }
                Button({ send(target) }, Modifier.fillMaxWidth(), enabled = p.onDuty) { Text("Envoyer l'alerte") }
            }
        }
        Button({ send("Tous") }, Modifier.fillMaxWidth().height(60.dp), enabled = p.onDuty,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00), contentColor = Color.White)) { Text("SOS - Appel & Panique d'Urgence") }
        OutlinedButton({ Alarm.stop(ctx); scope.launch { dao.ackAll() } }, Modifier.fillMaxWidth()) { Text("Acquitter / Arrêter l'alarme") }
        if (info.isNotEmpty()) Text(info)
        TextButton(openSettings) { Text("Paramètres") }
        Text("ILINE TECH BY FERAK ALADDIN", style = MaterialTheme.typography.labelSmall)
    }
    if (show) AlertDialog({ show = false }, confirmButton = { TextButton({ show = false }) { Text("Fermer") } },
        title = { Text("Personnel en service") },
        text = { Column { if (staff.isEmpty()) Text("Personne"); staff.forEach { Text("🟢 ${it.name} — ${it.role}") } } })
}

@Composable fun Settings(back: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("Version ${BuildConfig.VERSION_NAME}") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paramètres", style = MaterialTheme.typography.headlineSmall)
        Text(info)
        Button({
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Bridge.latest() }.getOrNull() }
                info = when {
                    r == null -> "Erreur réseau"
                    r.first.removePrefix("v") == BuildConfig.VERSION_NAME -> "Application à jour"
                    else -> { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.second)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Nouvelle version ${r.first}" }
                }
            }
        }) { Text("Vérifier les mises à jour") }
        TextButton(back) { Text("Retour") }
    }
}
