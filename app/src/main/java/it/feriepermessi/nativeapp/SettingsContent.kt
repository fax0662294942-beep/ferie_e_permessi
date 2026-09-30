package it.feriepermessi.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import it.feriepermessi.nativeapp.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable internal fun SettingsContent(state:NativeState,month:YearMonth,model:NativeViewModel?) {
    val p=state.profile ?: return
    val u=p.user; val cfg=p.config(month.year)
    var addName by rememberSaveable {mutableStateOf("")}
    var userDelete by remember {mutableStateOf<UserEntity?>(null)}
    Text("Utenti",style=MaterialTheme.typography.titleLarge)
    state.users.forEach {user -> Row {TextButton({model?.selectUser(user.id)}) {Text("${if(user.id==u.id) "✓ " else ""}${user.name}")}; if(state.users.size>1) TextButton({userDelete=user}) {Text("Elimina utente")}}}
    OutlinedTextField(addName,{addName=it},label={Text("Nuovo utente")})
    Button({if(addName.isNotBlank()){model?.addUser(addName);addName=""}}) {Text("Aggiungi utente")}
    key(u.id,month.year) {
        var name by rememberSaveable {mutableStateOf(u.name)}
        var contract by rememberSaveable {mutableStateOf(u.contractStart.orEmpty())}
        var factor by rememberSaveable {mutableStateOf(u.calcMode.toString())}
        var annualVacation by rememberSaveable {mutableStateOf(cfg.vacationAnnual.toString())}
        var annualLeave by rememberSaveable {mutableStateOf(cfg.leaveAnnual.toString())}
        var ccnl02 by rememberSaveable {mutableStateOf(u.ccnl02.toString())}
        var ccnl24 by rememberSaveable {mutableStateOf(u.ccnl24.toString())}
        var ccnl5 by rememberSaveable {mutableStateOf(u.ccnl5plus.toString())}
        var initialDate by rememberSaveable {mutableStateOf(u.initialDate.orEmpty())}
        var initialVacation by rememberSaveable {mutableStateOf(u.initialVacation.toString())}
        var initialLeave by rememberSaveable {mutableStateOf(u.initialLeave.toString())}
        var initialBank by rememberSaveable {mutableStateOf(u.initialTimeBank.toString())}
        var excludeSat by rememberSaveable {mutableStateOf(u.excludeSaturday)}
        var excludeSun by rememberSaveable {mutableStateOf(u.excludeSunday)}
        var bank by rememberSaveable {mutableStateOf(u.timeBankEnabled)}
        var law104 by rememberSaveable {mutableStateOf(u.law104Enabled)}
        var study by rememberSaveable {mutableStateOf(u.studyEnabled)}
        var lawBudget by rememberSaveable {mutableStateOf(u.law104MonthlyBudget.toString())}
        var studyBudget by rememberSaveable {mutableStateOf(u.studyAnnualBudget.toString())}
        var error by remember {mutableStateOf<String?>(null)}
        Text("Contratto e anno ${month.year}",style=MaterialTheme.typography.titleLarge)
        Field("Nome",name){name=it}; Field("Inizio contratto (AAAA-MM-GG)",contract){contract=it}
        Field("Coefficiente ferie (1 oppure 1.2 Commercio)",factor){factor=it}
        Field("Ferie annue (gg)",annualVacation){annualVacation=it}; Field("Permessi annui (h)",annualLeave){annualLeave=it}
        Field("CCNL 0–23 mesi (h/anno)",ccnl02){ccnl02=it}; Field("CCNL 24–47 mesi (h/anno)",ccnl24){ccnl24=it};Field("CCNL da 48 mesi (h/anno)",ccnl5){ccnl5=it}
        Text("Saldo iniziale",style=MaterialTheme.typography.titleLarge)
        Field("Data saldo iniziale (vuota = disabilitato)",initialDate){initialDate=it}
        Field("Ferie iniziali",initialVacation){initialVacation=it}; Field("Permessi iniziali",initialLeave){initialLeave=it};Field("Banca ore iniziale",initialBank){initialBank=it}
        Toggle("Escludi sabato",excludeSat){excludeSat=it}; Toggle("Escludi domenica",excludeSun){excludeSun=it}
        Toggle("Banca ore",bank){bank=it};Toggle("Permessi Legge 104",law104){law104=it};Toggle("Permessi studio",study){study=it}
        Field("Budget L.104 mensile (h)",lawBudget){lawBudget=it};Field("Budget studio annuo (h)",studyBudget){studyBudget=it}
        Button({try {
            fun number(s:String)=s.replace(',','.').toDouble().also {require(it.isFinite())}
            val multiplier=number(factor);require(multiplier==1.0 || multiplier==1.2);require(name.isNotBlank())
            if(contract.isNotBlank()) LocalDate.parse(contract)
            if(initialDate.isNotBlank()) LocalDate.parse(initialDate)
            val vac=number(annualVacation);val leave=number(annualLeave);require(vac>=0 && leave>=0)
            val c02=number(ccnl02);val c24=number(ccnl24);val c5=number(ccnl5);require(c02>=0 && c24>=0 && c5>=0)
            val l104=number(lawBudget);val stud=number(studyBudget);require(l104>0 && stud>0)
            model?.saveUser(u.copy(name=name.trim(),contractStart=contract.ifBlank{null},calcMode=multiplier,
                ccnl02=c02,ccnl24=c24,ccnl5plus=c5,initialDate=initialDate.ifBlank{null},initialVacation=number(initialVacation),initialLeave=number(initialLeave),initialTimeBank=number(initialBank),
                excludeSaturday=excludeSat,excludeSunday=excludeSun,timeBankEnabled=bank,law104Enabled=law104,studyEnabled=study,law104MonthlyBudget=l104,studyAnnualBudget=stud),cfg.copy(vacationAnnual=vac,leaveAnnual=leave));error=null
        }catch(_:Exception){error="Controlla date e valori numerici"}}) {Text("Salva impostazioni")}
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
    Text("Tag",style=MaterialTheme.typography.titleLarge)
    var tagName by rememberSaveable {mutableStateOf("")}
    var tagColor by rememberSaveable {mutableStateOf("#4ade80")}
    var editingTag by remember {mutableStateOf<String?>(null)}
    state.tags.forEach {tag -> Row {TextButton({editingTag=tag.id;tagName=tag.name;tagColor=tag.color}) {Text("${tag.name} (${tag.color})")};TextButton({model?.deleteTag(tag)}) {Text("Elimina tag")}}}
    Field("Nome tag",tagName){tagName=it};Field("Colore tag (#RRGGBB)",tagColor){tagColor=it}
    Button({if(tagName.isNotBlank() && tagColor.matches(Regex("#[0-9a-fA-F]{6}"))) {model?.putTag(TagEntity(u.id,editingTag ?: UUID.randomUUID().toString(),tagName.trim(),tagColor));editingTag=null;tagName=""}}) {Text("Salva tag")}
    Text("Festività",style=MaterialTheme.typography.titleLarge)
    p.holidays.forEach {holiday -> Row {Text("${holiday.name} ${if(holiday.easterMonday) "Pasquetta" else "${holiday.day}/${holiday.month}"} ${if(holiday.recurring) "ricorrente da ${holiday.fromYear ?: "sempre"}" else "anno ${holiday.year}"}",Modifier.weight(1f));TextButton({model?.deleteHoliday(holiday)}) {Text("Elimina")}}}
    var holidayName by rememberSaveable {mutableStateOf("")}
    var holidayDate by rememberSaveable {mutableStateOf(month.atDay(1).toString())}
    var recurring by rememberSaveable {mutableStateOf(true)}
    var holidayError by remember {mutableStateOf<String?>(null)}
    Field("Nome festività",holidayName){holidayName=it};Field("Data festività (AAAA-MM-GG)",holidayDate){holidayDate=it};Toggle("Ricorrente dalla data indicata",recurring){recurring=it}
    Button({try {val date=LocalDate.parse(holidayDate);require(holidayName.isNotBlank());model?.putHoliday(HolidayEntity(u.id,UUID.randomUUID().toString(),holidayName.trim(),date.monthValue,date.dayOfMonth,recurring=recurring,fromYear=if(recurring) date.year else null,year=if(recurring) null else date.year));holidayName="";holidayError=null}catch(_:Exception){holidayError="Inserisci nome e data validi"}}) {Text("Aggiungi festività")}
    holidayError?.let {Text(it)}
    Text("Backup e autenticazione",style=MaterialTheme.typography.titleLarge)
    Text("Importazione e sincronizzazione saranno disponibili nei blocchi successivi.")
    userDelete?.let {user -> AlertDialog(onDismissRequest={userDelete=null},title={Text("Eliminare ${user.name}?")},text={Text("Verranno eliminati solo i dati locali nativi di questo utente.")},confirmButton={TextButton({model?.action {model.repository.deleteUser(user.id)};userDelete=null}) {Text("Elimina")}},dismissButton={TextButton({userDelete=null}) {Text("Annulla")}}) }
}
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit) {OutlinedTextField(value,onChange,label={Text(label)},singleLine=true)}
@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {Row {Checkbox(value,onChange);Text(label)}}
