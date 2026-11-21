package com.example.leitner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Native 2x3 home-screen widget showing 5 due cards.
 *
 * Per slot (stored in SharedPreferences so it survives process death):
 *   state 0 -> word            (tap -> state 1)
 *   state 1 -> meaning         (tap -> state 2)
 *   state 2 -> green / red     (green = correct, red = wrong; then the slot gets the next due card)
 */
class LeitnerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext
        runAsync { WidgetEngine.render(app) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_TAP -> {
                val slot = intent.getIntExtra(EXTRA_SLOT, -1)
                runAsync {
                    WidgetEngine.onTap(app, slot)
                    WidgetEngine.render(app)
                }
            }
            ACTION_ANSWER -> {
                val slot = intent.getIntExtra(EXTRA_SLOT, -1)
                val correct = intent.getBooleanExtra(EXTRA_CORRECT, false)
                runAsync {
                    WidgetEngine.onAnswer(app, slot, correct)
                    WidgetEngine.render(app)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.example.leitner.ACTION_TAP"
        const val ACTION_ANSWER = "com.example.leitner.ACTION_ANSWER"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_CORRECT = "correct"

        /** Ask all placed widgets to redraw (call after the database changed). */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, LeitnerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(app, LeitnerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            app.sendBroadcast(intent)
        }
    }
}

internal object WidgetEngine {
    const val SLOTS = 5
    private const val PREFS = "widget_state"

    private val ROW = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4)
    private val WORD = intArrayOf(R.id.word0, R.id.word1, R.id.word2, R.id.word3, R.id.word4)
    private val MEANING = intArrayOf(R.id.meaning0, R.id.meaning1, R.id.meaning2, R.id.meaning3, R.id.meaning4)
    private val BTNS = intArrayOf(R.id.btns0, R.id.btns1, R.id.btns2, R.id.btns3, R.id.btns4)
    private val GREEN = intArrayOf(R.id.green0, R.id.green1, R.id.green2, R.id.green3, R.id.green4)
    private val RED = intArrayOf(R.id.red0, R.id.red1, R.id.red2, R.id.red3, R.id.red4)

    private class Slot(val card: Card?, val state: Int)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Interaction ----------

    /** Tap on a row: word -> meaning -> buttons. */
    fun onTap(ctx: Context, slot: Int) {
        if (slot !in 0 until SLOTS) return
        val p = prefs(ctx)
        if (p.getLong("card_$slot", -1L) == -1L) return
        val state = p.getInt("state_$slot", 0)
        if (state < 2) p.edit().putInt("state_$slot", state + 1).commit()
    }

    /** Green (correct) / red (wrong): update the Leitner box and free the slot. */
    suspend fun onAnswer(ctx: Context, slot: Int, correct: Boolean) {
        if (slot !in 0 until SLOTS) return
        val p = prefs(ctx)
        val id = p.getLong("card_$slot", -1L)
        if (id == -1L) return
        if (p.getInt("state_$slot", 0) < 2) return // ignore stale / double taps
        val dao = AppDatabase.get(ctx).cardDao()
        val card = dao.getById(id)
        if (card != null) {
            dao.update(Leitner.review(card, correct, System.currentTimeMillis()))
        }
        p.edit().putLong("card_$slot", -1L).putInt("state_$slot", 0).commit()
    }

    // ---------- Rendering ----------

    suspend fun render(ctx: Context) {
        val slots = loadSlots(ctx)
        val views = build(ctx, slots)
        AppWidgetManager.getInstance(ctx)
            .updateAppWidget(ComponentName(ctx, LeitnerWidgetProvider::class.java), views)
    }

    /** Keeps cards that are still due in their slot, fills empty slots with the next due cards. */
    private suspend fun loadSlots(ctx: Context): List<Slot> {
        val dao = AppDatabase.get(ctx).cardDao()
        val p = prefs(ctx)
        val due = dao.dueCards(System.currentTimeMillis(), 100)
        val dueById = due.associateBy { it.id }

        val ids = LongArray(SLOTS) { -1L }
        val states = IntArray(SLOTS) { p.getInt("state_$it", 0) }
        val used = HashSet<Long>()

        for (i in 0 until SLOTS) {
            val id = p.getLong("card_$i", -1L)
            if (id != -1L && dueById.containsKey(id) && used.add(id)) ids[i] = id
        }
        val queue = due.filter { it.id !in used }.iterator()
        for (i in 0 until SLOTS) {
            if (ids[i] == -1L) {
                states[i] = 0
                if (queue.hasNext()) ids[i] = queue.next().id
            }
        }

        val editor = p.edit()
        for (i in 0 until SLOTS) {
            editor.putLong("card_$i", ids[i])
            editor.putInt("state_$i", states[i])
        }
        editor.commit()

        return (0 until SLOTS).map { Slot(dueById[ids[it]], states[it]) }
    }

    private fun build(ctx: Context, slots: List<Slot>): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_leitner)
        val any = slots.any { it.card != null }

        rv.setViewVisibility(R.id.rows, if (any) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.empty_msg, if (any) View.GONE else View.VISIBLE)
        rv.setOnClickPendingIntent(R.id.empty_msg, openApp(ctx))

        for (i in 0 until SLOTS) {
            val slot = slots[i]
            val card = slot.card
            if (card == null) {
                rv.setViewVisibility(ROW[i], View.INVISIBLE)
                continue
            }
            rv.setViewVisibility(ROW[i], View.VISIBLE)
            rv.setTextViewText(WORD[i], card.word)
            rv.setTextViewText(MEANING[i], card.meaning.ifBlank { "—" })

            rv.setViewVisibility(WORD[i], if (slot.state == 0) View.VISIBLE else View.GONE)
            rv.setViewVisibility(MEANING[i], if (slot.state == 1) View.VISIBLE else View.GONE)
            rv.setViewVisibility(BTNS[i], if (slot.state == 2) View.VISIBLE else View.GONE)
            rv.setInt(
                ROW[i],
                "setBackgroundResource",
                if (slot.state == 1) R.drawable.card_bg_flipped else R.drawable.card_bg
            )

            // Row tap (a harmless no-op while the buttons are visible)
            rv.setOnClickPendingIntent(
                ROW[i],
                broadcast(ctx, LeitnerWidgetProvider.ACTION_TAP, i, false, 100 + i)
            )
            // Solid colour buttons
            rv.setOnClickPendingIntent(
                GREEN[i],
                broadcast(ctx, LeitnerWidgetProvider.ACTION_ANSWER, i, true, 200 + i)
            )
            rv.setOnClickPendingIntent(
                RED[i],
                broadcast(ctx, LeitnerWidgetProvider.ACTION_ANSWER, i, false, 300 + i)
            )
        }
        return rv
    }

    private fun broadcast(
        ctx: Context,
        action: String,
        slot: Int,
        correct: Boolean,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(ctx, LeitnerWidgetProvider::class.java).apply {
            this.action = action
            putExtra(LeitnerWidgetProvider.EXTRA_SLOT, slot)
            putExtra(LeitnerWidgetProvider.EXTRA_CORRECT, correct)
        }
        return PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            ctx,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
