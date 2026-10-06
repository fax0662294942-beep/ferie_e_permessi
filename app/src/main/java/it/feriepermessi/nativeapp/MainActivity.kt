package it.feriepermessi.nativeapp

import it.feriepermessi.nativeapp.cloud.*
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.feriepermessi.nativeapp.data.*
import it.feriepermessi.nativeapp.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class MainActivity: ComponentActivity() {
    private val back = BackPressController(SystemClock::elapsedRealtime)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[NativeViewModel::class.java]
        setContent {
            val state by model.state.collectAsState()
            val cloud by model.cloud.state.collectAsState()
            MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF4ADE80),secondary=Color(0xFF60A5FA),background=Color(0xFF0F1117),surface=Color(0xFF1A1D27))) {
                FeriePermessiApp(onHint={Toast.makeText(this,"Premi di nuovo Indietro per uscire",Toast.LENGTH_SHORT).show()},onExit=::finish,back=back,state=state,model=model,cloudState=cloud)
            }
        }
    }
}
enum class Screen { Home, Calendar, Stats, Settings, Cloud }
private val entryLabels = linkedMapOf("ferie" to "Ferie","permesso" to "Permesso","permesso_pagato" to "Liquidazione permessi",
    "banca_accumulo" to "Banca ore: accumulo","banca_fruizione" to "Banca ore: fruizione","permesso_104" to "Permesso L.104","permesso_studio" to "Permesso studio")
private fun format(value: Double) = "%.2f".format(java.util.Locale.ITALY,value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FeriePermessiApp(onHint:()->Unit={},onExit:()->Unit={},back:BackPressController=BackPressController(),
    state: NativeState = NativeState(), model: NativeViewModel? = null, cloudState:CloudState=CloudState()) {
    if(cloudState.identity!=null && cloudState.access!=PwaCloudContract.Access.Approved) {
        BackHandler {when(back.onHomeBack()) {BackAction.ShowHint->onHint();BackAction.Exit->onExit()}}
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            CloudContent(cloudState,model)
        }
        return
    }
    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var monthPicker by remember { mutableStateOf(false) }
    var simulation by rememberSaveable { mutableStateOf(false) }
    var filterTag by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<EntryEntity?>(null) }
    var deleting by remember { mutableStateOf<EntryEntity?>(null) }
    var confirmMonth by remember { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val screen = Screen.valueOf(screenName)
    val profile = state.profile
    val engine = LeaveEngine(LocalDate.now(), if(simulation) month else null)
    fun navigate(target: Screen) { screenName=target.name; back.reset() }
    BackHandler {
        when {
            editor != null -> { editor=null; back.reset() }
            deleting != null -> { deleting=null; back.reset() }
            confirmMonth -> { confirmMonth=false; back.reset() }
            screen != Screen.Home -> navigate(Screen.Home)
            else -> when(back.onHomeBack()) { BackAction.ShowHint -> onHint(); BackAction.Exit -> onExit() }
        }
    }
    Scaffold(topBar={TopAppBar(title={Text("Ferie & Permessi")},navigationIcon={if(screen != Screen.Home) TextButton({navigate(Screen.Home)}) { Text("Home") }})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(profile != null) {
                Text(profile.user.name,style=MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton({monthText=month.minusMonths(1).toString()}) { Text("‹") }
                    TextButton({monthPicker=true;back.reset()},Modifier.weight(1f)) {Text(month.toString())}
                    TextButton({monthText=month.plusMonths(1).toString()}) { Text("›") }
                }
                Row { FilterChip(selected=!simulation,onClick={simulation=false},label={Text("Ufficiale")}); Spacer(Modifier.width(8.dp)); FilterChip(selected=simulation,onClick={simulation=true},label={Text("Simulazione")}) }
            }
            when(screen) {
                Screen.Home -> {
                    Text("Riepilogo",style=MaterialTheme.typography.headlineMedium)
                    Row { TextButton({navigate(Screen.Calendar)}) {Text("Calendario")}; TextButton({navigate(Screen.Stats)}) {Text("Statistiche")} }
                    TextButton({navigate(Screen.Settings)}) {Text("Impostazioni")}
                    TextButton({navigate(Screen.Cloud)}) {Text("Account e cloud")}
                    if(cloudState.identity!=null) Text(if(cloudState.linked) "Cloud sincronizzato" else "Cloud da collegare")
                    if(profile != null) {
                        val balance=engine.monthBalance(profile,month.year,month.monthValue,simulation)
                        val totals=engine.annualTotals(profile,month.year,simulation)
                        SummaryCard("Ferie (gg)",balance.start.vacation,balance.accrued.vacation,balance.used.vacation,balance.end.vacation)
                        if(profile.user.calcMode > 1) Text("Saldo giorni lavorativi: ${format(balance.end.vacation/profile.user.calcMode)}")
                        SummaryCard("Permessi (h)",balance.start.leave,balance.accrued.leave,balance.used.leave,balance.end.leave)
                        Text("Anni precedenti — ferie ${format(totals.previousRemaining.vacation)} gg · permessi ${format(totals.previousRemaining.leave)} h")
                        Text("Anno corrente — maturate ${format(totals.accrued.vacation)} gg / ${format(totals.accrued.leave)} h; residue ${format(totals.currentRemaining.vacation)} gg / ${format(totals.currentRemaining.leave)} h")
                        Text("Permessi goduti: ${format(balance.used.enjoyed)} h · liquidati: ${format(balance.used.paid)} h")
                        if(profile.user.timeBankEnabled) Text("Banca ore: ${format(engine.timeBank(profile,month.year,month.monthValue))} h")
                        if(profile.user.law104Enabled) Text("L.104: ${format(engine.specialMonth(profile,"permesso_104",month.year,month.monthValue))}/${format(profile.user.law104MonthlyBudget)} h mese; ${format(engine.specialAnnual(profile,"permesso_104",month.year))} h anno")
                        if(profile.user.studyEnabled) Text("Studio: ${format(engine.specialAnnual(profile,"permesso_studio",month.year))}/${format(profile.user.studyAnnualBudget)} h anno")
                        entryLabels.forEach { (type,label) ->
                            val visible=when(type) { "banca_accumulo","banca_fruizione" -> profile.user.timeBankEnabled; "permesso_104" -> profile.user.law104Enabled; "permesso_studio" -> profile.user.studyEnabled; else -> true }
                            if(visible) OutlinedButton({back.reset(); editor=EntryEntity(profile.user.id,UUID.randomUUID().toString(),type,month.atDay(1).toString(),month.atDay(1).toString(),1.0,year=month.year,month=month.monthValue,simulated=simulation && type in listOf("ferie","permesso"))}) {Text("+ $label")}
                        }
                        if(simulation) Button({confirmMonth=true;back.reset()}) {Text("Conferma simulazioni del mese")}
                        if(state.tags.isNotEmpty()) {
                            TextButton({filterTag=null}) {Text("Tutti i tag")}
                            state.tags.forEach { tag -> FilterChip(selected=filterTag==tag.id,onClick={filterTag=tag.id},label={Text(tag.name)}) }
                        }
                        val entries = profile.entries.filter { it.year==month.year && it.month==month.monthValue && (simulation || !it.simulated) &&
                            (filterTag==null || state.links.any { link -> link.entryId==it.id && link.tagId==filterTag }) }
                        if(entries.isEmpty()) Text("Nessuna voce in questo mese")
                        entries.forEach { entry ->
                            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                                Text("${entryLabels[entry.type] ?: entry.type} · ${format(entry.quantity)} ${if(entry.type=="ferie") "gg" else "h"}${if(entry.simulated) " · simulata" else ""}")
                                Text("${entry.dateFrom.orEmpty()} — ${entry.dateTo.orEmpty()}")
                                if(entry.note.isNotBlank()) Text(entry.note)
                                if(entry.obligatory) Text("Obbligata")
                                Text(state.tags.filter { t -> state.links.any { it.entryId==entry.id && it.tagId==t.id } }.joinToString { it.name })
                                Row {TextButton({editor=entry;back.reset()}) {Text("Modifica")}; TextButton({deleting=entry;back.reset()}) {Text("Elimina")}; if(entry.simulated) TextButton({model?.confirm(entry)}) {Text("Conferma")} }
                            }}
                        }
                    } else Text("Caricamento dati…")
                }
                Screen.Cloud -> CloudContent(cloudState,model)
                Screen.Calendar -> { Text("Calendario",style=MaterialTheme.typography.headlineMedium); if(profile!=null) CalendarContent(profile,month,simulation) }
                Screen.Stats -> { Text("Statistiche",style=MaterialTheme.typography.headlineMedium); if(profile!=null) StatsContent(profile,month,engine) }
                Screen.Settings -> { Text("Impostazioni",style=MaterialTheme.typography.headlineMedium); if(profile!=null) SettingsContent(state,month,model) }
            }
        }
    }
    if(monthPicker) {
        var draft by rememberSaveable {mutableStateOf(monthText)}
        var invalid by remember {mutableStateOf(false)}
        AlertDialog(onDismissRequest={monthPicker=false;back.reset()},title={Text("Seleziona mese")},text={Column {OutlinedTextField(draft,{draft=it},label={Text("AAAA-MM")});if(invalid) Text("Mese non valido")}},confirmButton={TextButton({try{monthText=YearMonth.parse(draft).toString();monthPicker=false;back.reset()}catch(_:Exception){invalid=true}}){Text("Apri")}},dismissButton={TextButton({monthPicker=false;back.reset()}){Text("Annulla")}})
    }
    editor?.let { entry -> if(profile!=null) EntryEditor(entry,profile,state.tags,state.links.filter { it.entryId==entry.id }.map { it.tagId },
        onDismiss={editor=null;back.reset()},onSave={edited,tags -> model?.saveEntry(edited,tags);editor=null;back.reset()}) }
    deleting?.let { entry -> AlertDialog(onDismissRequest={deleting=null},title={Text("Eliminare la voce?")},confirmButton={TextButton({model?.deleteEntry(entry);deleting=null}) {Text("Elimina")}},dismissButton={TextButton({deleting=null}) {Text("Annulla")}}) }
    if(confirmMonth) AlertDialog(onDismissRequest={confirmMonth=false},title={Text("Confermare le simulazioni?")},text={Text("Le voci simulate di $month diventano ufficiali.")},confirmButton={TextButton({profile?.let {model?.confirmMonth(it.user.id,month.year,month.monthValue)};confirmMonth=false}) {Text("Conferma")}},dismissButton={TextButton({confirmMonth=false}) {Text("Annulla")}})
    state.error?.let { error -> AlertDialog(onDismissRequest={model?.clearError()},title={Text("Operazione non riuscita")},text={Text(error)},confirmButton={TextButton({model?.clearError()}) {Text("OK")}}) }
}
@Composable private fun SummaryCard(title:String,start:Double,accrued:Double,used:Double,end:Double) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text("Inizio mese ${format(start)} · maturate ${format(accrued)} · utilizzate ${format(used)}")
        Text("Saldo fine mese ${format(end)}",style=MaterialTheme.typography.titleMedium)
    }}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateField(label:String,value:String,onValue:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    val parsed=runCatching { LocalDate.parse(value) }.getOrNull()
    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) {
        Text("$label: " + (parsed?.let { "%02d/%02d/%04d".format(it.dayOfMonth,it.monthValue,it.year) } ?: value) + "  📅")
    }
    if(open) {
        val initial=parsed?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(
            onDismissRequest={open=false},
            confirmButton={TextButton({
                picker.selectedDateMillis?.let { millis ->
                    onValue(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                }
                open=false
            }) {Text("OK")}},
            dismissButton={TextButton({open=false}) {Text("Annulla")}}
        ) { DatePicker(state=picker) }
    }
}

@Composable private fun EntryEditor(entry:EntryEntity,p:Profile,tags:List<TagEntity>,initialTags:List<String>,onDismiss:()->Unit,onSave:(EntryEntity,List<String>)->Unit) {
    var from by rememberSaveable(entry.id) {mutableStateOf(entry.dateFrom.orEmpty())}
    var to by rememberSaveable(entry.id) {mutableStateOf(entry.dateTo.orEmpty())}
    var quantity by rememberSaveable(entry.id) {mutableStateOf(entry.quantity.toString())}
    var note by rememberSaveable(entry.id) {mutableStateOf(entry.note)}
    var obligatory by rememberSaveable(entry.id) {mutableStateOf(entry.obligatory)}
    var selected by remember(entry.id) {mutableStateOf(initialTags)}
    var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(entryLabels[entry.type].orEmpty())},
        text={
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                DateField(if(entry.type=="ferie") "Data inizio" else "Data",from) {from=it;if(entry.type!="ferie") to=it}
                if(entry.type=="ferie") {
                    DateField("Data fine",to) {to=it}
                    TextButton({
                        runCatching {quantity=WorkCalendar.workdays(p,LocalDate.parse(from),LocalDate.parse(to.ifBlank{from})).toString()}
                            .onFailure {error="Date non valide"}
                    }) {Text("Calcola giorni lavorativi")}
                }
                OutlinedTextField(
                    quantity,{quantity=it},
                    label={Text(if(entry.type=="ferie") "Giorni" else "Ore")},
                    singleLine=true,
                    modifier=Modifier.fillMaxWidth()
                )
                OutlinedTextField(note,{note=it},label={Text("Note")},modifier=Modifier.fillMaxWidth(),maxLines=2)
                FilterChip(
                    selected=obligatory,
                    onClick={obligatory=!obligatory},
                    label={Text("Obbligata")}
                )
                if(tags.isNotEmpty()) {
                    Text("Tag",style=MaterialTheme.typography.labelLarge)
                    tags.chunked(2).forEach { rowTags ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            rowTags.forEach { tag ->
                                FilterChip(
                                    selected=tag.id in selected,
                                    onClick={selected=if(tag.id in selected) selected-tag.id else selected+tag.id},
                                    label={Text(tag.name)},
                                    modifier=Modifier.weight(1f)
                                )
                            }
                            if(rowTags.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
        },
        confirmButton={TextButton({
            try {
                val d=LocalDate.parse(from)
                val end=if(entry.type=="ferie") LocalDate.parse(to.ifBlank{from}) else d
                val qty=quantity.replace(',','.').toDouble()
                require(qty.isFinite() && qty>0 && end>=d)
                onSave(entry.copy(dateFrom=d.toString(),dateTo=end.toString(),year=d.year,month=d.monthValue,quantity=qty,note=note.trim(),obligatory=obligatory),selected)
            } catch(_:Exception) {error="Inserisci date valide e una quantità positiva"}
        }) {Text("Salva")}},
        dismissButton={TextButton(onDismiss) {Text("Annulla")}}
    )
}
@Composable private fun CalendarContent(p:Profile,month:YearMonth,simulated:Boolean) {
    for(day in 1..month.lengthOfMonth()) {
        val date=month.atDay(day); val entries=Statistics.dayEntries(p,month,date,simulated)
        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(8.dp)) {
            Text("$date · ${date.dayOfWeek}${if(WorkCalendar.isHoliday(p,date)) " · Festività" else if(!WorkCalendar.isWorkday(p,date)) " · Non lavorativo" else ""}")
            entries.forEach {Text("${entryLabels[it.type]} ${format(it.quantity)}${if(it.simulated) " (simulata)" else ""} ${it.note}")}
        }}
    }
}
@Composable private fun StatsContent(p:Profile,month:YearMonth,engine:LeaveEngine) {
    var period by rememberSaveable {mutableStateOf("month")}
    var from by rememberSaveable {mutableStateOf(month.atDay(1).toString())}
    var to by rememberSaveable {mutableStateOf(month.atEndOfMonth().toString())}
    var type by rememberSaveable {mutableStateOf("tutti")}
    var category by rememberSaveable {mutableStateOf("tutte")}
    listOf("month" to "Mese","year" to "Anno","total" to "Totale","custom" to "Personalizzato").forEach {(key,label) -> FilterChip(selected=period==key,onClick={period=key},label={Text(label)})}
    if(period=="custom") {OutlinedTextField(from,{from=it},label={Text("Dal (AAAA-MM-GG)")});OutlinedTextField(to,{to=it},label={Text("Al (AAAA-MM-GG)")})}
    val entries=Statistics.entries(p,period,month,runCatching {LocalDate.parse(from)}.getOrNull(),runCatching {LocalDate.parse(to)}.getOrNull())
    listOf("ferie","permesso").forEach {kind ->
        val b=Statistics.breakdown(p,entries,kind)
        Text("${entryLabels[kind]}: ${format(b.obligatoryQuantity+b.requestedQuantity)} · ${b.obligatoryEpisodes+b.requestedEpisodes} episodi")
        Text("Obbligate: ${format(b.obligatoryQuantity)} (${b.obligatoryEpisodes}); richieste: ${format(b.requestedQuantity)} (${b.requestedEpisodes})")
    }
    Text("Liquidati: ${format(entries.filter {it.type=="permesso_pagato"}.sumOf {it.quantity})} h")
    val mat=Statistics.accrued(p,engine,period,month,runCatching {LocalDate.parse(from)}.getOrNull(),runCatching {LocalDate.parse(to)}.getOrNull())
    Text("Maturazione periodo: ${format(mat.vacation)} gg / ${format(mat.leave)} h")
    val usedVacation=entries.filter {it.type=="ferie"}.sumOf {it.quantity*p.user.calcMode}
    val usedLeave=entries.filter {it.type in listOf("permesso","permesso_pagato")}.sumOf {it.quantity}
    Text("Delta periodo: ${format(mat.vacation-usedVacation)} gg / ${format(mat.leave-usedLeave)} h")
    Text("Distribuzione per giorno della settimana")
    listOf("tutti","ferie","permesso").forEach {v -> FilterChip(selected=type==v,onClick={type=v},label={Text(v)})}
    listOf("tutte","obbligate","richieste").forEach {v -> FilterChip(selected=category==v,onClick={category=v},label={Text(v)})}
    val weekdays=Statistics.weekdays(p,entries,type,category)
    listOf("Lun","Mar","Mer","Gio","Ven","Sab","Dom").forEachIndexed {i,label -> Text("$label: ${weekdays[i]}")}
}
