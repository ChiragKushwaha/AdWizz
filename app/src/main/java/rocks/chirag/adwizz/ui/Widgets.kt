package rocks.chirag.adwizz.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.chirag.adwizz.data.*

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp).testTag("switch_$title"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("switchToggle_$title"))
    }
}

@Composable
internal fun DomainAdder(hint: String, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it.trim().lowercase() }, placeholder = { Text(hint) },
            singleLine = true, modifier = Modifier.weight(1f).testTag("adder_$hint")
        )
        IconButton(onClick = {
            val d = text.removePrefix("https://").removePrefix("http://").substringBefore('/')
            if (d.contains('.') && d.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) {
                onAdd(d); text = ""
            } else toast(context, "Enter a domain like $hint")
        }) { Icon(Icons.Default.Add, "Add") }
    }
}

@Composable
internal fun DomainRow(domain: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(domain, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
    }
}
