package com.notcan.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface

@Composable
internal fun StudyFlashcardsScreen(
    deck: ParsedFlashcardArtifact,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var order by remember(deck.title, deck.cards) { mutableStateOf(deck.cards.indices.toList()) }
    var position by remember(deck.title, deck.cards) { mutableIntStateOf(0) }
    var showAnswer by remember(deck.title, deck.cards) { mutableStateOf(false) }
    var hardCount by remember(deck.title, deck.cards) { mutableIntStateOf(0) }
    var goodCount by remember(deck.title, deck.cards) { mutableIntStateOf(0) }
    var easyCount by remember(deck.title, deck.cards) { mutableIntStateOf(0) }

    val currentIndex = order.getOrElse(position.coerceIn(0, order.lastIndex.coerceAtLeast(0))) { 0 }
    val card = deck.cards[currentIndex]

    fun reset(shuffle: Boolean) {
        order = if (shuffle) deck.cards.indices.shuffled() else deck.cards.indices.toList()
        position = 0
        showAnswer = false
        hardCount = 0
        goodCount = 0
        easyCount = 0
    }

    fun next() {
        if (position < order.lastIndex) position++ else position = 0
        showAnswer = false
    }

    fun previous() {
        if (position > 0) position-- else position = order.lastIndex.coerceAtLeast(0)
        showAnswer = false
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = NotCanOffWhite) }
                    Column(Modifier.weight(1f)) {
                        Text(deck.title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${position + 1} / ${deck.cards.size}", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { reset(shuffle = true) }) { Icon(Icons.Default.Shuffle, "Mezclar", tint = NotCanBlue) }
                    IconButton(onClick = { reset(shuffle = false) }) { Icon(Icons.Default.Refresh, "Reiniciar", tint = NotCanBlue) }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 600.dp
                    val cardWidth = if (maxWidth >= 900.dp) 0.58f else if (wide) 0.72f else 0.90f
                    Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(cardWidth)
                                .heightIn(min = if (wide) 360.dp else 300.dp)
                                .clickable { showAnswer = !showAnswer },
                            colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                            shape = RoundedCornerShape(26.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (showAnswer) "Respuesta" else "Pregunta",
                                    color = NotCanBlue,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (showAnswer) card.answer else card.question,
                                    color = NotCanOffWhite,
                                    style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 18.dp)
                                )
                                card.sourceRef?.takeIf { showAnswer }?.let {
                                    Text(it, color = NotCanGray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 18.dp))
                                }
                                Text(
                                    if (showAnswer) "Toca para volver a la pregunta" else "Toca para ver la respuesta",
                                    color = NotCanGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showAnswer) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { hardCount++; next() }, modifier = Modifier.weight(1f)) { Text("Difícil") }
                            Button(onClick = { goodCount++; next() }, modifier = Modifier.weight(1f)) { Text("Bien") }
                            OutlinedButton(onClick = { easyCount++; next() }, modifier = Modifier.weight(1f)) { Text("Fácil") }
                        }
                        Text(
                            "Difícil $hardCount · Bien $goodCount · Fácil $easyCount",
                            color = NotCanGray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Button(onClick = { showAnswer = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Mostrar respuesta") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = ::previous) { Text("← Anterior") }
                        TextButton(onClick = ::next) { Text("Siguiente →") }
                    }
                }
            }
        }
    }
}
