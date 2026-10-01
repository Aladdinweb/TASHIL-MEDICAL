package dz.iline.tashilmedical
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        installSplashScreen(); super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4DB6AC), onPrimary = Color.Black,
                background = Color(0xFF0F1417), surface = Color(0xFF0F1417))) {
                Surface(Modifier.fillMaxSize().statusBarsPadding()) { App() }
            }
        }
    }
}

@Composable fun Footer() = Text("ILINE TECH COPYRIGHT", Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center,
    letterSpacing = 3.sp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable fun App() {
    val ctx = LocalContext.current; val dao = remember { Db.get(ctx).dao() }; val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf<Profile?>(null) }; var loaded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }; var dir by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { p = dao.profile(); loaded = true }
    if (!loaded) return
    val cur = p
    if (cur == null) { Register { p = it; tab = 0 }; return }
    if (dir) { Directory { dir = false }; return }
    val tabs = listOf("🎯" to "Tableau de bord", "📜" to "Historique", "⚙️" to "Paramètres")
    Scaffold(bottomBar = {
        NavigationBar { tabs.forEachIndexed { i, t -> NavigationBarItem(tab == i, { tab = i }, icon = { Text(t.first) }, label = { Text(t.second, maxLines = 1, fontSize = 11.sp) }) } }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Dashboard(cur) { p = it }
                1 -> History()
                else -> SettingsTab(cur, { dir = true }) {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { Bridge.setStatus(cur.copy(onDuty = false)) }; Db.get(ctx).clearAllTables() }
                        ctx.stopService(Intent(ctx, AlertService::class.java)); Alarm.stop(ctx); p = null
                    }
                }
            }
        }
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
    var name by remember { mutableStateOf("") }; var wi by remember { mutableStateOf("") }; var ty by remember { mutableStateOf("") }
    var pa by remember { mutableStateOf("") }; var fa by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }; var shift by remember { mutableStateOf("") }
    val types = Catalog.tree[wi]?.keys?.toList() ?: emptyList()
    val parents = Catalog.tree[wi]?.get(ty)?.keys?.toList() ?: emptyList()
    val branches = Catalog.tree[wi]?.get(ty)?.get(pa) ?: emptyList()
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TASHIL MEDICAL", style = MaterialTheme.typography.headlineMedium)
            Text("Aucun numéro de téléphone requis.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("Nom & Prénom") }, modifier = Modifier.fillMaxWidth())
            Drop("1. Wilaya", Catalog.tree.keys.toList(), wi) { wi = it; ty = ""; pa = ""; fa = "" }
            Drop("2. Catégorie (CHU / EHU / EPH / EPSP)", types, ty) { ty = it; pa = ""; fa = "" }
            Drop("3. Établissement mère", parents, pa) { pa = it; fa = "" }
            Drop("4. Structure / Polyclinique", branches, fa) { fa = it }
            Drop("Rôle / Spécialité", Catalog.roles, role) { role = it }
            Drop("Équipe de garde", Catalog.shifts, shift) { shift = it }
            Button(enabled = listOf(name, wi, ty, pa, fa, role, shift).all { it.isNotBlank() }, modifier = Modifier.fillMaxWidth(), onClick = {
                val p = Profile(uid = UUID.randomUUID().toString().take(8), name = name.trim(), wilaya = wi, type = ty, parent = pa,
                    facility = fa, role = role, shift = shift, etab = Serial.etab(wi, pa), dept = Serial.dept(wi, pa, fa))
                scope.launch { Db.get(ctx).dao().save(p); done(p) }
            }) { Text("Continuer") }
        }
        Footer()
    }
}

@Composable fun Dashboard(p: Profile, upd: (Profile) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val dao = remember { Db.get(ctx).dao() }
    var staff by remember { mutableStateOf<List<Staff>>(emptyList()) }
    var show by remember { mutableStateOf(false) }; var target by remember { mutableStateOf(Catalog.roles[0]) }
    var info by remember { mutableStateOf("") }; var hours by remember { mutableStateOf("8") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val c = dao.profile()
            if (c != null) {
                if (c.onDuty && c.dutyEnd > 0 && now >= c.dutyEnd) {   // auto-off when the shift timer expires
                    val n = c.copy(onDuty = false, dutyEnd = 0); dao.save(n)
                    withContext(Dispatchers.IO) { runCatching { Bridge.setStatus(n) } }
                    ctx.stopService(Intent(ctx, AlertService::class.java)); Alarm.stop(ctx); upd(n)
                } else upd(c)
                staff = withContext(Dispatchers.IO) { runCatching { Bridge.staff(c) }.getOrDefault(staff) }
            }
            delay(15000)
        }
    }
    fun send(t: String, sos: Boolean) = scope.launch {
        info = withContext(Dispatchers.IO) {
            runCatching { Bridge.sendAlert(p, t, sos); if (sos) "🚨 SOS envoyé aux agents de sécurité" else "Alerte envoyée → $t" }.getOrElse { "Échec d'envoi" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (p.onDuty) "🟢 En Service" else "⚪ Hors Service", style = MaterialTheme.typography.titleLarge)
                        Text("${p.name} · ${p.role} · ${p.shift}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(p.onDuty, { on ->
                        val h = (hours.toDoubleOrNull() ?: 8.0).coerceIn(0.5, 24.0)
                        val n = p.copy(onDuty = on, dutyEnd = if (on) System.currentTimeMillis() + (h * 3600000).toLong() else 0)
                        scope.launch {
                            dao.save(n); upd(n)
                            withContext(Dispatchers.IO) { runCatching { Bridge.setStatus(n) } }
                            val i = Intent(ctx, AlertService::class.java)
                            if (on) ContextCompat.startForegroundService(ctx, i) else { ctx.stopService(i); Alarm.stop(ctx) }
                        }
                    })
                }
                if (p.onDuty && p.dutyEnd > 0) {
                    val m = ((p.dutyEnd - now) / 60000).coerceAtLeast(0)
                    Text("⏱ Arrêt automatique dans ${m / 60} h ${"%02d".format(m % 60)} min")
                } else if (!p.onDuty) {
                    Text("⏱ Minuteur de garde (arrêt automatique)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip({ hours = "8" }, { Text("8 h") }); AssistChip({ hours = "12" }, { Text("12 h") })
                        OutlinedTextField(hours, { hours = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Durée (h)") },
                            singleLine = true, modifier = Modifier.width(130.dp))
                    }
                }
            }
        }
        AssistChip({ show = true }, { Text("🟢 ${staff.size} Personnel en service") })
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Demande d'urgence ciblée", style = MaterialTheme.typography.titleMedium)
                Drop("Rôle demandé", Catalog.roles, target) { target = it }
                Button({ send(target, false) }, Modifier.fillMaxWidth(), enabled = p.onDuty) { Text("Envoyer l'alerte") }
            }
        }
        if (p.role == Catalog.SEC) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1111)), border = BorderStroke(2.dp, Color(0xFFD32F2F))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🚨 PANIQUE SÉCURITÉ", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF8A80))
                Text("Alerte immédiate à tous les agents de sécurité en service.", style = MaterialTheme.typography.bodySmall)
                Button({ send(Catalog.SEC, true) }, Modifier.fillMaxWidth().height(56.dp), enabled = p.onDuty, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00), contentColor = Color.White)) { Text("SOS - Appel & Panique d'Urgence") }
            }
        }
        OutlinedButton({ Alarm.stop(ctx); scope.launch { dao.ackAll() } }, Modifier.fillMaxWidth()) { Text("Acquitter / Arrêter l'alarme") }
        if (info.isNotEmpty()) Text(info)
    }
    if (show) AlertDialog({ show = false }, confirmButton = { TextButton({ show = false }) { Text("Fermer") } },
        title = { Text("Personnel en service") },
        text = { Column { if (staff.isEmpty()) Text("Personne"); staff.forEach { Text("🟢 ${it.name} — ${it.role}") } } })
}

@Composable fun History() {
    val ctx = LocalContext.current; var rows by remember { mutableStateOf<List<AlertRow>>(emptyList()) }
    val f = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    LaunchedEffect(Unit) { while (true) { rows = Db.get(ctx).dao().alerts(); delay(5000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Historique des alertes", style = MaterialTheme.typography.headlineSmall)
        if (rows.isEmpty()) Text("Aucune alerte reçue")
        rows.forEach {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text((if (it.sos) "🚨 SOS" else "🔔 Urgence") + " · " + f.format(Date(it.ts)))
                    Text(it.fromName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable fun SettingsTab(p: Profile, openDir: () -> Unit, logout: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("Version ${BuildConfig.VERSION_NAME}") }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paramètres", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("${p.role} · ${p.shift}"); Text(p.facility); Text("${p.parent} · ${p.type} · ${p.wilaya}")
                }
            }
            OutlinedButton(openDir, Modifier.fillMaxWidth()) { Text("📞 Numéros d'urgence") }
            Text(info, style = MaterialTheme.typography.bodySmall)
            OutlinedButton({
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Bridge.latest() }.getOrNull() }
                    info = when {
                        r == null -> "Erreur réseau"
                        r.first.removePrefix("v") == BuildConfig.VERSION_NAME -> "Application à jour"
                        else -> { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.second)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Nouvelle version ${r.first}" }
                    }
                }
            }, Modifier.fillMaxWidth()) { Text("Vérifier les mises à jour") }
            Button(logout, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)) { Text("Se déconnecter") }
        }
        Footer()
    }
}

@Composable fun Directory(back: () -> Unit) {
    val ctx = LocalContext.current; BackHandler(onBack = back)
    val list = listOf("👮 Police Secours" to listOf("17", "1548"), "🛡️ Gendarmerie Nationale" to listOf("1055"), "🚒 Protection Civile" to listOf("14", "1021"))
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(back) { Text("← Retour") }
        Text("Numéros d'urgence", style = MaterialTheme.typography.headlineSmall)
        list.forEach { (n, nums) ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(n, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        nums.forEach { x -> Button({ ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$x")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("📞 $x") } }
                    }
                }
            }
        }
    }
}
