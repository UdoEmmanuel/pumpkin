package com.pumpkin.app.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// PRD 4.1: "a decoy calculator screen where a specific sequence (e.g. 1988=)
// opens the real chat UI." The calculator itself does real arithmetic on the
// display so it holds up to a casual glance — only the exact secret sequence
// (checked in [expression]) triggers navigation.
private const val SECRET_SEQUENCE = "0107="
private val KEYS = listOf(
    "7", "8", "9", "/",
    "4", "5", "6", "*",
    "1", "2", "3", "-",
    "0", ".", "=", "+",
    "C", "⌫"
)

@Composable
fun CalculatorScreen(onSecretSequenceEntered: () -> Unit) {
    var expression by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("0") }

    fun onKeyPress(key: String) {
        when (key) {
            "C" -> {
                expression = ""
                display = "0"
            }
            "⌫" -> {
                expression = expression.dropLast(1)
                display = expression.ifEmpty { "0" }
            }
            "=" -> {
                val attempt = expression + "="
                if (attempt == SECRET_SEQUENCE) {
                    expression = ""
                    display = "0"
                    onSecretSequenceEntered()
                    return
                }
                display = evaluate(expression)
                expression = display
            }
            else -> {
                expression += key
                display = expression
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(text = display, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(KEYS) { key ->
                    Button(
                        onClick = { onKeyPress(key) },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(key)
                    }
                }
            }
        }
    }
}

/** Bare-bones evaluator for single binary operations — enough for a decoy UI, not a real calculator. */
private fun evaluate(expr: String): String {
    val match = Regex("""(-?\d+\.?\d*)([+\-*/])(-?\d+\.?\d*)""").find(expr) ?: return expr
    val (a, op, b) = match.destructured
    val left = a.toDoubleOrNull() ?: return expr
    val right = b.toDoubleOrNull() ?: return expr
    val result = when (op) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        "/" -> if (right != 0.0) left / right else return "Error"
        else -> return expr
    }
    return if (result == result.toLong().toDouble()) result.toLong().toString() else result.toString()
}
