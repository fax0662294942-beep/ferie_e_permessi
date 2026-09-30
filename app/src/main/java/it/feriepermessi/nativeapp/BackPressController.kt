package it.feriepermessi.nativeapp
enum class BackAction { ShowHint, Exit }
class BackPressController(private val clock:()->Long=System::currentTimeMillis, private val exitWindowMillis:Long=2_000) {
 private var lastPress:Long?=null
 fun onHomeBack():BackAction { val now=clock(); val exit=lastPress?.let { now-it in 0..exitWindowMillis }==true; lastPress=if(exit)null else now; return if(exit) BackAction.Exit else BackAction.ShowHint }
 fun reset(){ lastPress=null }
}
