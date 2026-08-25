package com.pumpkin.app.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// PRD 4.1: "a decoy calculator screen where a specific sequence (e.g. 1988=)
// opens the real chat UI." The calculator itself does real arithmetic on the
// display so it holds up to a casual glance — only the exact secret sequence
// (checked in [expression]) triggers navigation.
private const val SECRET_SEQUENCE = "0107="

// Row-major, same 18 keys as before — laid out as a Column of Rows (rather
// than a LazyVerticalGrid) so every button can be given equal weight and
// genuinely fill a fixed fraction of the screen, not just size to its own
// content. The trailing "" pads C/⌫'s row out to 4 columns so it lines up
// with the rows above instead of being narrower.
private val KEY_ROWS = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "=", "+"),
    listOf("C", "⌫", "", "")
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
            Text(
                text = display,
                fontSize = 40.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
            // Fixed at ~65% of the screen's height, buttons weighted equally
            // within it — this is what actually makes each key bigger,
            // rather than just sized to fit its own label.
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KEY_ROWS.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                            } else {
                                Button(
                                    onClick = { onKeyPress(key) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text(key, fontSize = 26.sp)
                                }
                            }
                        }
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
