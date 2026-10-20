package com.example.logipass.ui.screens.vault

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.logipass.model.Service
import java.text.Collator
import java.util.Locale

/** A service in the list; [revealCredentials] = the query matched inside its accounts, so the card opens. */
data class ServiceMatch(val service: Service, val revealCredentials: Boolean)

fun searchTerms(query: String): List<String> =
    query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.PRIMARY }

/**
 * Services sorted by name; with [terms], only those where every term appears in the name,
 * description, or an account's login or note. Passwords are never searched.
 */
fun filterServices(services: List<Service>, terms: List<String>): List<ServiceMatch> {
    val sorted = services.sortedWith { a, b -> collator.compare(a.name, b.name) }
    if (terms.isEmpty()) return sorted.map { ServiceMatch(it, revealCredentials = false) }
    return sorted.mapNotNull { service ->
        val header = (service.name + "\n" + service.description).lowercase()
        val accounts = service.credentials.joinToString("\n") { it.username + "\n" + it.note }.lowercase()
        if (terms.all { it in header || it in accounts }) {
            ServiceMatch(service, revealCredentials = terms.any { it !in header })
        } else {
            null
        }
    }
}

/** [text] with every occurrence of each term marked. */
fun highlight(text: String, terms: List<String>, color: Color): AnnotatedString {
    if (terms.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    // Lowercasing can change length for a few scripts; skip highlighting rather than mis-mark.
    if (lower.length != text.length) return AnnotatedString(text)
    val marked = BooleanArray(text.length)
    for (term in terms) {
        var from = lower.indexOf(term)
        while (from >= 0) {
            for (i in from until from + term.length) marked[i] = true
            from = lower.indexOf(term, from + term.length)
        }
    }
    val style = SpanStyle(background = color.copy(alpha = 0.16f), color = color, fontWeight = FontWeight.SemiBold)
    return buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            if (!marked[i]) {
                i++
                continue
            }
            val start = i
            while (i < text.length && marked[i]) i++
            addStyle(style, start, i)
        }
    }
}
