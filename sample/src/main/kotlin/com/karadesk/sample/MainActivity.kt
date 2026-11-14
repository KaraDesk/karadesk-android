package com.karadesk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karadesk.KaraDesk
import com.karadesk.KaraDeskUser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Demo() }
            }
        }

        if (savedInstanceState == null) {
            openFromExtras()
        }
    }

    private fun openFromExtras() {
        val style = if (intent.getBooleanExtra("standalone", false)) KaraDesk.PresentationStyle.STANDALONE else KaraDesk.PresentationStyle.STANDARD
        val destination = when (intent.getStringExtra("open")) {
            "home" -> KaraDesk.Destination.Home
            "messages" -> KaraDesk.Destination.Messages
            "help" -> KaraDesk.Destination.HelpCenter
            "new" -> KaraDesk.Destination.NewConversation(intent.getStringExtra("message"))
            "conversation" -> KaraDesk.Destination.Conversation(intent.getLongExtra("id", 0))
            "article" -> KaraDesk.Destination.Article(intent.getLongExtra("id", 0))
            else -> return
        }

        window.decorView.post { KaraDesk.present(this, destination, style) }
    }
}

@Composable
private fun Demo() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unread by KaraDesk.unreadConversationCount.collectAsState()
    var status by remember { mutableStateOf("Visitor") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    ) {
        Text("KaraDesk ${KaraDesk.version}", style = MaterialTheme.typography.headlineSmall)
        Text(status, style = MaterialTheme.typography.bodyMedium)

        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { KaraDesk.present(context) }, modifier = Modifier.fillMaxWidth()) { Text("Open messenger") }
        }
        OutlinedButton(onClick = { KaraDesk.present(context, KaraDesk.Destination.Messages) }, modifier = Modifier.fillMaxWidth()) { Text("Messages") }
        OutlinedButton(onClick = { KaraDesk.present(context, KaraDesk.Destination.NewConversation("I need help with my order")) }, modifier = Modifier.fillMaxWidth()) { Text("New conversation") }
        OutlinedButton(onClick = { KaraDesk.present(context, KaraDesk.Destination.HelpCenter, KaraDesk.PresentationStyle.STANDALONE) }, modifier = Modifier.fillMaxWidth()) { Text("Help center (standalone)") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    status = runCatching {
                        KaraDesk.login(KaraDeskUser(id = "user-42", email = "taylor@example.com", name = "Taylor Swift"))
                        "Signed in as Taylor"
                    }.getOrElse { it.message ?: "Sign-in failed" }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Log in as Taylor") }
        OutlinedButton(onClick = { KaraDesk.logout(); status = "Visitor" }, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
    }
}
