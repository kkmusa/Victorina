package com.example.sechinquiz

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SechinQuizApp() }
    }

    override fun onPause() {
        // При блокировке экрана, сворачивании приложения или переходе в другое приложение
        // озвучка не должна продолжаться в фоне.
        QuizAudioPlayer.stop()
        super.onPause()
    }

    override fun onDestroy() {
        QuizAudioPlayer.stop()
        super.onDestroy()
    }
}

enum class Screen { MENU, GAME, RESULT }

data class ShuffledAnswer(val originalIndex: Int, val text: String)

/**
 * Один общий проигрыватель для всей игры.
 * stop() не только останавливает текущий mp3, но и отменяет всю оставшуюся очередь.
 */
object QuizAudioPlayer {
    private var player: MediaPlayer? = null
    private var generation: Int = 0

    fun stop() {
        generation++
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
    }

    fun playSequence(context: Context, ids: List<Int>) {
        stop()
        if (ids.isEmpty()) return

        val myGeneration = generation

        fun playAt(index: Int) {
            if (myGeneration != generation || index >= ids.size) return

            val next = MediaPlayer.create(context, ids[index]) ?: run {
                playAt(index + 1)
                return
            }
            player = next

            next.setOnCompletionListener { finished ->
                finished.release()
                if (player === finished) player = null
                if (myGeneration == generation) playAt(index + 1)
            }
            next.setOnErrorListener { failed, _, _ ->
                failed.release()
                if (player === failed) player = null
                if (myGeneration == generation) playAt(index + 1)
                true
            }
            next.start()
        }

        playAt(0)
    }
}

@Composable
fun SechinQuizApp() {
    var screen by remember { mutableStateOf(Screen.MENU) }
    var session by remember { mutableStateOf(emptyList<QuizQuestion>()) }
    var score by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var speakAnswers by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { QuizAudioPlayer.stop() }
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF090909))) {
            when (screen) {
                Screen.MENU -> StartMenu(
                    speakAnswers = speakAnswers,
                    onSpeakAnswersChanged = { speakAnswers = it },
                    onStart = {
                        QuizAudioPlayer.stop()
                        session = QuestionBank.playable.shuffled().take(3)
                        score = 0
                        failed = false
                        screen = Screen.GAME
                    }
                )

                Screen.GAME -> GameScreen(
                    session = session,
                    speakAnswers = speakAnswers,
                    onFinished = { finalScore, wasFailed ->
                        QuizAudioPlayer.stop()
                        score = finalScore
                        failed = wasFailed
                        screen = Screen.RESULT
                    },
                    onExit = {
                        QuizAudioPlayer.stop()
                        screen = Screen.MENU
                    }
                )

                Screen.RESULT -> ResultScreen(
                    score = score,
                    failed = failed,
                    onAgain = {
                        QuizAudioPlayer.stop()
                        session = QuestionBank.playable.shuffled().take(3)
                        score = 0
                        failed = false
                        screen = Screen.GAME
                    },
                    onMenu = {
                        QuizAudioPlayer.stop()
                        screen = Screen.MENU
                    }
                )
            }
        }
    }
}

@Composable
fun StartMenu(
    speakAnswers: Boolean,
    onSpeakAnswersChanged: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    val gold = Color(0xFFFFB000)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF050505), Color(0xFF17120A), Color(0xFF050505))
                )
            )
            .padding(28.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "ИНТЕЛЛЕКТУАЛЬНАЯ ИГРА",
                color = gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "КТО ХОЧЕТ\nСТАТЬ СЕЧИНЫМ?",
                color = Color.White,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Викторина для операторов ДНГ",
                color = Color(0xFFC6C6C6),
                fontSize = 17.sp
            )
            Spacer(Modifier.height(38.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Проговаривать ответы",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (speakAnswers) "Вопрос + варианты А, Б, В, Г" else "Только номер и текст вопроса",
                        color = Color(0xFF999999),
                        fontSize = 13.sp
                    )
                }
                Switch(
                    checked = speakAnswers,
                    onCheckedChange = onSpeakAnswersChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = gold
                    )
                )
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = gold,
                    contentColor = Color.Black
                )
            ) {
                Text("НАЧАТЬ ИГРУ", fontWeight = FontWeight.Black, fontSize = 19.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("Тестовая версия • 3 вопроса • 1 жизнь", color = Color(0xFF777777), fontSize = 13.sp)
        }
    }
}

@Composable
fun GameScreen(
    session: List<QuizQuestion>,
    speakAnswers: Boolean,
    onFinished: (Int, Boolean) -> Unit,
    onExit: () -> Unit
) {
    if (session.isEmpty()) return

    var index by remember(session) { mutableIntStateOf(0) }
    var score by remember(session) { mutableIntStateOf(0) }
    val question = session[index]
    var shuffled by remember(question.id) {
        mutableStateOf(question.answers.mapIndexed { i, t -> ShuffledAnswer(i, t) }.shuffled())
    }
    var selectedOriginal by remember(question.id) { mutableStateOf<Int?>(null) }
    var locked by remember(question.id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(question.id, speakAnswers) {
        selectedOriginal = null
        locked = false
        shuffled = question.answers.mapIndexed { i, t -> ShuffledAnswer(i, t) }.shuffled()

        fun raw(name: String) = context.resources.getIdentifier(name, "raw", context.packageName)

        // Для номера вопроса можно положить две озвучки:
        // question_number_1.mp3      -> например «Вопрос первый»
        // question_number_1_alt.mp3  -> например «Первый вопрос»
        // То же самое для 2, 3, ... 15. Если _alt ещё не добавлен,
        // приложение спокойно использует единственный существующий вариант.
        val questionNumber = index + 1
        val numberVoiceIds = listOf(
            raw("question_number_$questionNumber"),
            raw("question_number_${questionNumber}_alt")
        ).filter { it != 0 }

        val ids = mutableListOf<Int>()
        numberVoiceIds.randomOrNull()?.let { ids += it }
        ids += raw("l%04d_question".format(question.id))

        if (speakAnswers) {
            shuffled.forEachIndexed { pos, answer ->
                ids += raw(listOf("option_a", "option_b", "option_v", "option_g")[pos])
                ids += raw("l%04d_answer%d".format(question.id, answer.originalIndex + 1))
            }
        }

        QuizAudioPlayer.playSequence(context, ids.filter { it != 0 })
    }

    DisposableEffect(question.id) {
        onDispose { QuizAudioPlayer.stop() }
    }

    // После правильного ответа показываем зелёную подсветку и автоматически
    // переходим дальше через 1,8 секунды. Кнопка «Следующий вопрос» не нужна.
    LaunchedEffect(question.id, locked, selectedOriginal) {
        if (locked && selectedOriginal == question.correctOriginalIndex) {
            delay(1800)
            QuizAudioPlayer.stop()
            if (index < 2) {
                index++
            } else {
                onFinished(score, false)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ВОПРОС ${index + 1} / 3",
                    color = Color(0xFFFFB000),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Выйти",
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.clickable {
                        QuizAudioPlayer.stop()
                        onExit()
                    }
                )
            }

            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF6A4A00), RoundedCornerShape(18.dp))
                    .background(Color(0xFF151515), RoundedCornerShape(18.dp))
                    .padding(22.dp)
            ) {
                Text(
                    question.text,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
            val letters = listOf("А", "Б", "В", "Г")

            shuffled.forEachIndexed { pos, answer ->
                val isSelected = selectedOriginal == answer.originalIndex
                val isCorrect = answer.originalIndex == question.correctOriginalIndex
                val borderColor = when {
                    locked && isCorrect -> Color(0xFF24C96B)
                    locked && isSelected && !isCorrect -> Color(0xFFE34B4B)
                    isSelected -> Color(0xFFFFB000)
                    else -> Color(0xFF3A3A3A)
                }
                val bg = if (isSelected) Color(0xFF241B08) else Color(0xFF151515)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                        .background(bg, RoundedCornerShape(16.dp))
                        .clickable(enabled = !locked) {
                            // Как только человек дал ответ — старая озвучка сразу прекращается.
                            QuizAudioPlayer.stop()
                            selectedOriginal = answer.originalIndex
                            locked = true

                            if (isCorrect) {
                                score++
                            } else {
                                // Одна жизнь: неверный ответ сразу завершает игру.
                                onFinished(score, true)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        letters[pos],
                        color = Color(0xFFFFB000),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(answer.text, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            if (locked && selectedOriginal == question.correctOriginalIndex) {
                Text(
                    "ПРАВИЛЬНО",
                    color = Color(0xFF24C96B),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Следующий вопрос…",
                    color = Color(0xFF8A8A8A),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun ResultScreen(
    score: Int,
    failed: Boolean,
    onAgain: () -> Unit,
    onMenu: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (failed) "ИГРА ОКОНЧЕНА" else "ИГРА ЗАВЕРШЕНА",
            color = Color(0xFFFFB000),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(18.dp))
        Text("$score / 3", color = Color.White, fontWeight = FontWeight.Black, fontSize = 64.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                failed -> "Неверный ответ. В игре одна жизнь."
                score == 3 -> "Отличный результат!"
                else -> "Игра завершена"
            },
            color = if (failed) Color(0xFFE65C5C) else Color(0xFFC8C8C8),
            fontSize = 19.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(42.dp))
        Button(
            onClick = onAgain,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFB000),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("СЫГРАТЬ ЕЩЁ", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onMenu) {
            Text("В главное меню", color = Color.White)
        }
    }
}
