package com.example.leitner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).cardDao()

    val cards: StateFlow<List<Card>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun refreshWidget() = LeitnerWidgetProvider.refresh(getApplication())

    /** Single word entry. onResult(false) means the word already exists. */
    fun addCard(word: String, meaning: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val id = dao.insert(
                Card(
                    word = word.trim(),
                    meaning = meaning.trim(),
                    box = 1,
                    dueAt = System.currentTimeMillis()
                )
            )
            refreshWidget()
            onResult(id != -1L)
        }
    }

    /** Bulk entry: list of (word, meaning). onDone gets the number of cards actually created. */
    fun addBulk(items: List<Pair<String, String>>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val ids = dao.insertAll(
                items.map { Card(word = it.first.trim(), meaning = it.second.trim(), box = 1, dueAt = now) }
            )
            refreshWidget()
            onDone(ids.count { it != -1L })
        }
    }

    fun review(card: Card, correct: Boolean) {
        viewModelScope.launch {
            dao.update(Leitner.review(card, correct, System.currentTimeMillis()))
            refreshWidget()
        }
    }

    fun update(card: Card) {
        viewModelScope.launch {
            dao.update(card)
            refreshWidget()
        }
    }

    fun delete(card: Card) {
        viewModelScope.launch {
            dao.delete(card)
            refreshWidget()
        }
    }
}
