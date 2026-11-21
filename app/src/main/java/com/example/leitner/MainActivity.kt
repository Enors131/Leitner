package com.example.leitner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ---------- Palette ----------
private val Ink = Color(0xFF1C1C1E)
private val Muted = Color(0xFF8E8E93)
private val Line = Color(0xFFE4E4E0)
private val OkGreen = Color(0xFF4CAF50)
private val NoRed = Color(0xFFEF5350)

/** Each text decides its own direction: English words are LTR, Persian meanings are RTL. */
private val ContentDir = TextStyle(textDirection = TextDirection.Content)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeitnerTheme {
                // The interface (Persian labels) is RTL; every word/meaning aligns by its own content.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LeitnerWidgetProvider.refresh(this)
    }
}

@Composable
fun LeitnerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2B2B2B),
            onPrimary = Color.White,
            background = Color(0xFFF5F5F3),
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            outline = Color(0xFFD6D6D1)
        ),
        content = content
    )
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val cards by vm.cards.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Segmented(listOf("مرور", "افزودن", "کلمات"), tab) { tab = it }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ReviewScreen(cards) { card, ok -> vm.review(card, ok) }
                    1 -> AddScreen(cards, vm)
                    else -> WordsScreen(cards, vm)
                }
            }
        }
    }
}

// ---------- Shared bits ----------

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE9E9E5))
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) Ink else Muted
                )
            }
        }
    }
}

// ---------- Review tab ----------

@Composable
fun ReviewScreen(cards: List<Card>, onAnswer: (Card, Boolean) -> Unit) {
    val due = remember(cards) {
        val now = System.currentTimeMillis()
        cards.filter { it.dueAt <= now }.sortedBy { it.dueAt }
    }
    val current = due.firstOrNull()
    var revealed by remember(current?.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BoxStats(cards)
        Text("${due.size} کارت برای مرور", color = Muted, fontSize = 13.sp)

        if (current == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (cards.isEmpty()) "هنوز کلمه‌ای اضافه نکرده‌اید" else "امروز کلمه‌ای برای مرور نیست ✓",
                    color = Muted,
                    fontSize = 15.sp
                )
            }
        } else {
            FlashCard(current, revealed, Modifier.fillMaxWidth().weight(1f)) { revealed = true }
            if (revealed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnswerButton("یادم نبود", NoRed, Modifier.weight(1f)) { onAnswer(current, false) }
                    AnswerButton("یادم بود", OkGreen, Modifier.weight(1f)) { onAnswer(current, true) }
                }
            } else {
                Text(
                    "برای دیدن معنی روی کارت بزنید",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BoxStats(cards: List<Card>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in 1..Leitner.MAX_BOX) {
            val n = cards.count { it.box == b }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$n", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text("جعبه $b", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

@Composable
fun FlashCard(card: Card, revealed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .clickable(enabled = !revealed, onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("جعبه ${card.box}", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(16.dp))
        Text(
            card.word,
            style = ContentDir,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (revealed) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Spacer(Modifier.height(20.dp))
            Text(
                card.meaning.ifBlank { "—" },
                style = ContentDir,
                fontSize = 22.sp,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AnswerButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text, fontSize = 15.sp)
    }
}

// ---------- Add tab ----------

@Composable
fun AddScreen(cards: List<Card>, vm: MainViewModel) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Segmented(listOf("تک‌کلمه", "استخراج از متن"), mode) { mode = it }
        Spacer(Modifier.height(16.dp))
        if (mode == 0) SingleAdd(vm) else BulkAdd(cards, vm)
    }
}

@Composable
fun SingleAdd(vm: MainViewModel) {
    var word by rememberSaveable { mutableStateOf("") }
    var meaning by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = word,
            onValueChange = { word = it; msg = "" },
            label = { Text("کلمه (English)") },
            singleLine = true,
            textStyle = ContentDir,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = meaning,
            onValueChange = { meaning = it; msg = "" },
            label = { Text("معنی / ترجمه") },
            singleLine = true,
            textStyle = ContentDir,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (word.isNotBlank()) {
                    vm.addCard(word, meaning) { ok ->
                        if (ok) {
                            word = ""
                            meaning = ""
                            msg = "اضافه شد ✓"
                        } else {
                            msg = "این کلمه قبلاً ثبت شده است"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("افزودن")
        }
        if (msg.isNotEmpty()) {
            Text(msg, color = Muted, fontSize = 13.sp)
        }
    }
}

/** One extracted word waiting to become a card. */
class Draft(val word: String) {
    var meaning by mutableStateOf("")
    var selected by mutableStateOf(true)
}

@Composable
fun BulkAdd(cards: List<Card>, vm: MainViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<Draft>() }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("متن را اینجا بچسبانید") },
                minLines = 5,
                maxLines = 10,
                textStyle = ContentDir,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val existing = cards.map { it.word.lowercase() }.toHashSet()
                    val all = Leitner.extractWords(text)
                    val fresh = all.filter { it !in existing }
                    drafts.clear()
                    drafts.addAll(fresh.map { Draft(it) })
                    info = "${all.size} کلمه‌ی یکتا · ${fresh.size} جدید · ${all.size - fresh.size} قبلاً موجود"
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("استخراج کلمات")
            }
        }
        if (info.isNotEmpty()) {
            item { Text(info, color = Muted, fontSize = 13.sp) }
        }
        if (drafts.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { drafts.forEach { it.selected = true } }) { Text("انتخاب همه") }
                    TextButton(onClick = { drafts.forEach { it.selected = false } }) { Text("هیچ‌کدام") }
                }
            }
            items(drafts, key = { it.word }) { d -> DraftRow(d) }
            item {
                val count = drafts.count { it.selected }
                Button(
                    onClick = {
                        val chosen = drafts.filter { it.selected }.map { it.word to it.meaning }
                        vm.addBulk(chosen) { inserted ->
                            info = "$inserted کارت ساخته شد ✓"
                            drafts.clear()
                            text = ""
                        }
                    },
                    enabled = count > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("ساخت $count کارت")
                }
            }
        }
    }
}

@Composable
fun DraftRow(d: Draft) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = d.selected, onCheckedChange = { d.selected = it })
            Text(
                d.word,
                style = ContentDir,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = d.meaning,
            onValueChange = { d.meaning = it },
            placeholder = { Text("معنی (اختیاری، بعداً هم می‌شود)") },
            singleLine = true,
            textStyle = ContentDir,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------- Words tab ----------

@Composable
fun WordsScreen(cards: List<Card>, vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Card?>(null) }
    val shown = remember(cards, query) {
        if (query.isBlank()) cards
        else cards.filter { it.word.contains(query, true) || it.meaning.contains(query, true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("جستجو") },
            singleLine = true,
            textStyle = ContentDir,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text("${cards.size} کلمه", color = Muted, fontSize = 13.sp)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(shown, key = { it.id }) { c ->
                WordRow(c) { editing = c }
            }
        }
    }

    val target = editing
    if (target != null) {
        EditDialog(
            card = target,
            onDismiss = { editing = null },
            onSave = { vm.update(it); editing = null },
            onDelete = { vm.delete(target); editing = null }
        )
    }
}

@Composable
fun WordRow(card: Card, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                card.word,
                style = ContentDir,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                card.meaning.ifBlank { "—" },
                style = ContentDir,
                fontSize = 14.sp,
                color = Muted,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("جعبه ${card.box}", fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun EditDialog(card: Card, onDismiss: () -> Unit, onSave: (Card) -> Unit, onDelete: () -> Unit) {
    var w by remember { mutableStateOf(card.word) }
    var m by remember { mutableStateOf(card.meaning) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("ویرایش کارت") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = w,
                    onValueChange = { w = it },
                    label = { Text("کلمه") },
                    singleLine = true,
                    textStyle = ContentDir,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = m,
                    onValueChange = { m = it },
                    label = { Text("معنی") },
                    singleLine = true,
                    textStyle = ContentDir,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onDelete) {
                    Text("حذف کارت", color = NoRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (w.isNotBlank()) onSave(card.copy(word = w.trim(), meaning = m.trim()))
            }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
