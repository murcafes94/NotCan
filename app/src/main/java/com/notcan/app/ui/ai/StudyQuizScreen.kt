package com.notcan.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface

private data class QuizAnswerRecord(
    val selected: String,
    val correct: Boolean
)

@Composable
internal fun StudyQuizScreen(
    quiz: ParsedQuizArtifact,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var order by remember(quiz.title, quiz.questions) { mutableStateOf(quiz.questions.indices.toList()) }
    var position by remember(quiz.title, quiz.questions) { mutableIntStateOf(0) }
    var answers by remember(quiz.title, quiz.questions) { mutableStateOf<Map<String, QuizAnswerRecord>>(emptyMap()) }
    var typedAnswer by remember(quiz.title, quiz.questions) { mutableStateOf("") }
    var revealShortAnswer by remember(quiz.title, quiz.questions) { mutableStateOf(false) }
    var finished by remember(quiz.title, quiz.questions) { mutableStateOf(false) }

    fun reset(newOrder: List<Int>) {
        order = newOrder
        position = 0
        answers = emptyMap()
        typedAnswer = ""
        revealShortAnswer = false
        finished = false
    }

    fun next() {
        typedAnswer = ""
        revealShortAnswer = false
        if (position < order.lastIndex) position++ else finished = true
    }

    fun repeatErrors() {
        val wrong = order.filter { index ->
            val q = quiz.questions[index]
            answers[q.id]?.correct == false
        }
        if (wrong.isNotEmpty()) reset(wrong)
    }

    val correctCount = answers.values.count { it.correct }
    val answeredCount = answers.size
    val percentage = if (answeredCount == 0) 0 else ((correctCount * 100f) / answeredCount).toInt()

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (finished) {
                QuizResults(
                    quiz = quiz,
                    answers = answers,
                    correctCount = correctCount,
                    percentage = percentage,
                    onRepeatErrors = ::repeatErrors,
                    onRestart = { reset(quiz.questions.indices.toList()) },
                    onShuffle = { reset(quiz.questions.indices.shuffled()) },
                    onBack = onBack
                )
            } else {
                val questionIndex = order.getOrElse(position.coerceIn(0, order.lastIndex.coerceAtLeast(0))) { 0 }
                val question = quiz.questions[questionIndex]
                val answer = answers[question.id]

                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = NotCanOffWhite) }
                        Column(Modifier.weight(1f)) {
                            Text(quiz.title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${position + 1} / ${order.size}", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { reset(quiz.questions.indices.shuffled()) }) { Icon(Icons.Default.Shuffle, "Mezclar", tint = NotCanBlue) }
                        IconButton(onClick = { reset(quiz.questions.indices.toList()) }) { Icon(Icons.Default.Refresh, "Reiniciar", tint = NotCanBlue) }
                    }

                    LinearProgressIndicator(
                        progress = { (position + 1f) / order.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                question.question,
                                color = NotCanOffWhite,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 26.dp, bottom = 8.dp)
                            )
                        }

                        when (question.type) {
                            StudyQuizQuestionType.MULTIPLE_CHOICE,
                            StudyQuizQuestionType.TRUE_FALSE -> {
                                items(question.options) { option ->
                                    val selected = answer?.selected == option
                                    val isCorrectOption = answer != null && option == question.correctAnswer
                                    val container = when {
                                        isCorrectOption -> NotCanBlue.copy(alpha = 0.22f)
                                        selected && answer.correct.not() -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                        else -> NotCanSurface
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = answer == null) {
                                            answers = answers + (
                                                question.id to QuizAnswerRecord(
                                                    selected = option,
                                                    correct = option == question.correctAnswer
                                                )
                                            )
                                        },
                                        colors = CardDefaults.cardColors(containerColor = container),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(option, color = NotCanOffWhite, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                            if (answer != null && isCorrectOption) Text("✓", color = NotCanBlue, fontWeight = FontWeight.Bold)
                                            else if (selected && answer.correct.not()) Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            StudyQuizQuestionType.SHORT_ANSWER -> {
                                item {
                                    OutlinedTextField(
                                        value = typedAnswer,
                                        onValueChange = { if (!revealShortAnswer) typedAnswer = it },
                                        label = { Text("Tu respuesta") },
                                        minLines = 3,
                                        maxLines = 7,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !revealShortAnswer
                                    )
                                }
                                if (!revealShortAnswer) {
                                    item {
                                        Button(
                                            onClick = { revealShortAnswer = true },
                                            enabled = typedAnswer.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Comparar respuesta") }
                                    }
                                } else {
                                    item {
                                        FeedbackCard(
                                            correct = null,
                                            answer = question.correctAnswer,
                                            explanation = question.explanation,
                                            sourceRef = question.sourceRef
                                        )
                                    }
                                    item {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    answers = answers + (question.id to QuizAnswerRecord(typedAnswer, false))
                                                    next()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Necesito repasar") }
                                            Button(
                                                onClick = {
                                                    answers = answers + (question.id to QuizAnswerRecord(typedAnswer, true))
                                                    next()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("La sabía") }
                                        }
                                    }
                                }
                            }
                        }

                        if (answer != null) {
                            item {
                                FeedbackCard(
                                    correct = answer.correct,
                                    answer = question.correctAnswer,
                                    explanation = question.explanation,
                                    sourceRef = question.sourceRef
                                )
                            }
                        }
                    }

                    if (answer != null) {
                        // Reserva el inset del sistema fuera del botón para que la navegación
                        // del cuestionario quede siempre sobre Atrás / Inicio / Recientes.
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background,
                            tonalElevation = 4.dp
                        ) {
                            Button(
                                onClick = ::next,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                            ) {
                                Text(if (position == order.lastIndex) "Ver resultado" else "Siguiente")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(correct: Boolean?, answer: String, explanation: String?, sourceRef: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            correct?.let {
                Text(
                    if (it) "Correcto" else "Respuesta incorrecta",
                    color = if (it) NotCanBlue else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Respuesta", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
            Text(answer, color = NotCanOffWhite, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            explanation?.let { Text(it, color = NotCanGray) }
            sourceRef?.let { Text(it, color = NotCanBlue, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun QuizResults(
    quiz: ParsedQuizArtifact,
    answers: Map<String, QuizAnswerRecord>,
    correctCount: Int,
    percentage: Int,
    onRepeatErrors: () -> Unit,
    onRestart: () -> Unit,
    onShuffle: () -> Unit,
    onBack: () -> Unit
) {
    val wrongQuestions = quiz.questions.filter { answers[it.id]?.correct == false }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(quiz.title, color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(1.dp).padding(top = 14.dp))
        Text("$percentage%", color = NotCanBlue, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text("$correctCount de ${answers.size} correctas", color = NotCanGray, style = MaterialTheme.typography.titleMedium)
        Text(
            if (wrongQuestions.isEmpty()) "Muy bien: no quedaron preguntas pendientes." else "${wrongQuestions.size} pregunta(s) para repasar.",
            color = NotCanGray,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        if (wrongQuestions.isNotEmpty()) {
            Button(onClick = onRepeatErrors, modifier = Modifier.fillMaxWidth()) { Text("Repetir errores") }
        }
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Repetir todo") }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Nueva ronda mezclada") }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Volver a TuNot") }
    }
}
