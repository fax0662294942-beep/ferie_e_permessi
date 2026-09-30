package it.feriepermessi.nativeapp
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity:ComponentActivity(){
 private val back=BackPressController()
 override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContent {
  MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF4ADE80),secondary=Color(0xFF60A5FA),background=Color(0xFF0F1117),surface=Color(0xFF1A1D27))){
   FeriePermessiApp(onHint={Toast.makeText(this,"Premi di nuovo Indietro per uscire",Toast.LENGTH_SHORT).show()},onExit=::finish,back=back)
  }
 }}
}
enum class Screen { Home, Calendar, Stats, Settings }
@Composable fun FeriePermessiApp(onHint:()->Unit={},onExit:()->Unit={},back:BackPressController=BackPressController()){
 var screenName by rememberSaveable{mutableStateOf(Screen.Home.name)}; val screen=Screen.valueOf(screenName)
 BackHandler { if(screen!=Screen.Home){screenName=Screen.Home.name;back.reset()} else when(back.onHomeBack()){BackAction.ShowHint->onHint();BackAction.Exit->onExit()} }
 Scaffold(topBar={TopAppBar(title={Text("Ferie & Permessi")})}){p->
  Column(Modifier.fillMaxSize().padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   when(screen){
    Screen.Home->{Text("Riepilogo",style=MaterialTheme.typography.headlineMedium); Text("Base Android nativa attiva. I dati della PWA non vengono modificati.")
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({screenName=Screen.Calendar.name}){Text("Calendario")};Button({screenName=Screen.Stats.name}){Text("Statistiche")}}
     Button({screenName=Screen.Settings.name}){Text("Impostazioni")}
    }
    Screen.Calendar->Placeholder("Calendario")
    Screen.Stats->Placeholder("Statistiche")
    Screen.Settings->Placeholder("Impostazioni")
   }
  }
 }
}
@Composable private fun Placeholder(title:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(title,style=MaterialTheme.typography.headlineMedium)}}
