package it.feriepermessi.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import it.feriepermessi.nativeapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable internal fun BackupContent(model: NativeViewModel?) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var preview by remember {mutableStateOf<NativeSnapshot?>(null)}
    var replaceConfirm by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri ->
        if(uri!=null && model!=null) scope.launch {
            busy=true
            try {
                val text=withContext(Dispatchers.IO) {BackupCodec.encode(model.repository.snapshot())}
                withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use {it.write(text.toByteArray(Charsets.UTF_8))}}
                status="Backup esportato"
            }catch(e:Exception){status="Esportazione non riuscita: ${e.message}"}finally{busy=false}
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {
            busy=true;preview=null
            try {
                preview=withContext(Dispatchers.IO) {
                    val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use {input ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            require(output.size()+count<=BackupCodec.MAX_BYTES) {"Backup troppo grande"}
                            output.write(buffer,0,count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size<=BackupCodec.MAX_BYTES) {"Backup troppo grande"}
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
                status=null
            }catch(e:Exception){status="File non valido: ${e.message}"}finally{busy=false}
        }
    }
    Text("Backup e importazione",style=MaterialTheme.typography.titleLarge)
    Button({export.launch("ferie_permessi_native_${LocalDate.now()}.json")},enabled=!busy && model!=null) {Text("Esporta backup JSON")}
    Button({import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!busy && model!=null) {Text("Importa backup PWA o nativo")}
    Text("L’importazione aggiunge copie dei profili e conserva tutti i dati attuali.")
    status?.let {Text(it)}
    preview?.takeUnless {replaceConfirm}?.let {snapshot -> AlertDialog(onDismissRequest={preview=null},title={Text("Importare il backup?")},
        text={Text("${snapshot.users.size} profili, ${snapshot.entries.size} movimenti. Verranno aggiunti come nuovi profili; i dati presenti saranno conservati.")},
        confirmButton={TextButton({
            if(model!=null) model.action {model.repository.restore(snapshot)}
            preview=null
        }) {Text("Aggiungi profili")}},dismissButton={androidx.compose.foundation.layout.Column {
            TextButton({replaceConfirm=true}) {Text("Sostituisci tutti i dati…")}
            TextButton({preview=null}) {Text("Annulla")}
        }}) }
    if(replaceConfirm) preview?.let {snapshot -> AlertDialog(onDismissRequest={replaceConfirm=false},
        title={Text("Sostituire tutti i dati locali?")},
        text={Text("Tutti i profili e i movimenti presenti saranno sostituiti dal backup selezionato. Esporta prima un backup dei dati attuali.")},
        confirmButton={TextButton({
            if(model!=null) model.action {model.repository.restore(snapshot,replace=true)}
            replaceConfirm=false;preview=null
        }) {Text("Conferma sostituzione")}},dismissButton={TextButton({replaceConfirm=false}) {Text("Annulla")}}) }

}
