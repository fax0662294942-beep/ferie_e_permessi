package it.feriepermessi.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import it.feriepermessi.nativeapp.cloud.*
import kotlinx.coroutines.launch

@Composable internal fun CloudContent(state:CloudState,model:NativeViewModel?) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var uiError by remember {mutableStateOf<String?>(null)}
    var confirmation by remember {mutableStateOf<String?>(null)}
    var deletion by remember {mutableStateOf<RegistryUser?>(null)}
    Text("Account e cloud",style=MaterialTheme.typography.headlineMedium)
    val identity=state.identity
    if(identity==null) {
        Text("Accedi con Google per sincronizzare i dati con la PWA.")
        Button({scope.launch {try{model?.cloud?.login(context)}catch(e:Exception){uiError=e.message ?: "Accesso annullato o non riuscito"}}},enabled=model!=null) {Text("Accedi con Google")}
        Text("I dati locali restano disponibili anche senza accesso.")
    }else {
        Text(identity.email.ifBlank {identity.name})
        when(state.access) {
            PwaCloudContract.Access.Pending -> Text("In attesa di approvazione. L’amministratore deve autorizzare il tuo account.")
            PwaCloudContract.Access.Rejected -> Text("Accesso negato. Contatta l’amministratore.")
            PwaCloudContract.Access.Error -> Text("Impossibile verificare l’accesso. Riprova quando la connessione è disponibile.")
            PwaCloudContract.Access.Approved -> {
                Text(if(state.linked) "Sincronizzazione attiva" else "Confronta i dati prima di attivare la sincronizzazione")
                state.remote?.let {remote ->
                    val snapshot=remote.snapshot
                    Text(if(snapshot==null) "Nessun dato nel cloud" else "Cloud: ${snapshot.users.size} profili, ${snapshot.entries.size} movimenti")
                    Text("Locale: ${model?.state?.value?.users?.size ?: 0} profili")
                    if(!state.linked) {
                        if(snapshot!=null) Button({confirmation="cloud"},enabled=!state.busy) {Text("Carica dati cloud sul dispositivo")}
                        Button({confirmation="local"},enabled=!state.busy) {Text(if(snapshot==null) "Collega e carica i dati locali" else "Usa i dati locali per il cloud")}
                    }else Button({model?.cloud?.syncNow()},enabled=!state.busy) {Text("Sincronizza adesso")}
                }
                if(state.admin) {
                    Text("Amministrazione",style=MaterialTheme.typography.titleLarge)
                    Button({model?.cloud?.loadRegistry()},enabled=!state.busy) {Text("Aggiorna elenco utenti")}
                    state.registry.forEach {user ->
                        Text("${user.name} · ${user.email} · ${user.status} · ${user.role}")
                        if(user.uid!=identity.uid) {
                            Row {
                                TextButton({model?.cloud?.setStatus(user,"approved")},enabled=!state.busy) {Text("Approva")}
                                TextButton({model?.cloud?.setStatus(user,"pending")},enabled=!state.busy) {Text("Revoca")}
                                TextButton({model?.cloud?.setStatus(user,"rejected")},enabled=!state.busy) {Text("Rifiuta")}
                            }
                            TextButton({model?.cloud?.setRole(user,if(user.role=="admin") "user" else "admin")},enabled=!state.busy) {Text(if(user.role=="admin") "Rimuovi admin" else "Promuovi admin")}
                            TextButton({deletion=user},enabled=!state.busy) {Text("Elimina account e dati cloud")}
                        }
                    }
                }
            }
        }
        if(state.mayClaim && !state.admin) TextButton({confirmation="claim"},enabled=!state.busy) {Text("Diventa il primo amministratore")}
        TextButton({model?.cloud?.refresh()},enabled=!state.busy) {Text("Aggiorna stato e confronto")}
        TextButton({confirmation="logout"}) {Text("Esci da Google")}
    }
    if(state.busy) CircularProgressIndicator()
    state.message?.let {Text(it)};uiError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    confirmation?.let {action ->
        val description=when(action) {
            "cloud"->"I dati locali verranno sostituiti da quelli cloud. Una copia dei dati locali verrà conservata e potrà essere esportata dalle impostazioni."
            "local"->"I dati locali diventeranno i dati cloud di questo account e sostituiranno quelli già presenti nel cloud. Una modifica concorrente interromperà l’operazione."
            "claim"->"Il tuo account diventerà amministratore solo se non ne esiste già uno."
            else->"Disconnetterti da Google? I dati restano salvati sul dispositivo."
        }
        AlertDialog(onDismissRequest={confirmation=null},title={Text("Conferma operazione")},text={Text(description)},
            confirmButton={TextButton({when(action) {
                "cloud"->model?.cloud?.useCloud();"local"->model?.cloud?.useLocal();"claim"->model?.cloud?.claimAdmin()
                else->scope.launch {try{model?.cloud?.logout(context)}catch(e:Exception){uiError=e.message}}
            };confirmation=null}) {Text("Conferma")}},dismissButton={TextButton({confirmation=null}) {Text("Annulla")}})
    }
    deletion?.let {user -> AlertDialog(onDismissRequest={deletion=null},title={Text("Eliminare ${user.name}?")},
        text={Text("Verranno eliminati il registro e tutti i dati cloud di questo account. L’operazione è irreversibile.")},
        confirmButton={TextButton({model?.cloud?.deleteUser(user);deletion=null}) {Text("Elimina definitivamente")}},dismissButton={TextButton({deletion=null}) {Text("Annulla")}}) }
}
