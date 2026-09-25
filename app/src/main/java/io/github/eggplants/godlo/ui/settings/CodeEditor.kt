package io.github.eggplants.godlo.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eggplants.godlo.core.ConfigFormat
import io.github.eggplants.godlo.ui.theme.DarkSyntax
import io.github.eggplants.godlo.ui.theme.LightSyntax
import io.github.eggplants.godlo.ui.theme.SyntaxColors
import kotlin.math.roundToInt

/** A plain text editor with line numbers, coloured as [format]. */
@Composable
fun CodeEditor(
    text: String,
    onChange: (String) -> Unit,
    format: ConfigFormat,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val syntax = if (scheme.surface.luminance() < 0.5f) DarkSyntax else LightSyntax
    val style = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        color = scheme.onSurface
    )
    val colouring = remember(format, syntax) { SyntaxTransformation(format, syntax) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // One scroll for both, so the numbers stay beside their lines.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = maxHeight
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LineNumbers(
                text = text,
                textLayout = textLayout,
                style = style.copy(color = scheme.outline),
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp)
            )
            BasicTextField(
                value = text,
                onValueChange = onChange,
                // As tall as the screen at least, so a tap below the text still starts typing.
                modifier = Modifier.weight(1f).heightIn(min = viewport)
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                textStyle = style,
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation = colouring,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                onTextLayout = { textLayout = it },
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                placeholder,
                                style = style.copy(color = scheme.outline)
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/** The number of each line of [text], at the height its first row is laid out at. */
@Composable
private fun LineNumbers(
    text: String,
    textLayout: TextLayoutResult?,
    style: TextStyle,
    modifier: Modifier
) {
    val starts = remember(text) { lineStarts(text) }
    Layout(
        content = { starts.indices.forEach { Text("${it + 1}", style = style) } },
        modifier = modifier
    ) { measurables, _ ->
        val numbers = measurables.map { it.measure(Constraints()) }
        val width = numbers.maxOfOrNull { it.width } ?: 0
        val laidOut = textLayout?.layoutInput?.text?.length
        layout(width, textLayout?.size?.height ?: numbers.sumOf { it.height }) {
            var y = 0
            numbers.forEachIndexed { i, number ->
                // Soft-wrapped lines take more than one row: follow the text's own layout,
                // unless it is from before the last edit and the line is not there yet.
                if (textLayout != null && laidOut != null && starts[i] <= laidOut) {
                    y = textLayout.getLineTop(textLayout.getLineForOffset(starts[i])).roundToInt()
                }
                number.place(width - number.width, y)
                y += number.height
            }
        }
    }
}

/** Colours the text with [highlight]; the characters stay where they are. */
private class SyntaxTransformation(
    private val format: ConfigFormat,
    private val colors: SyntaxColors
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val coloured = AnnotatedString.Builder(text)
        for (token in highlight(text.text, format)) {
            coloured.addStyle(style(token.kind), token.start, token.end)
        }
        return TransformedText(coloured.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun style(kind: TokenKind): SpanStyle = when (kind) {
        TokenKind.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
        TokenKind.STRING -> SpanStyle(color = colors.string)
        TokenKind.NUMBER -> SpanStyle(color = colors.number)
        TokenKind.KEYWORD -> SpanStyle(color = colors.keyword)
        TokenKind.KEY -> SpanStyle(color = colors.key)
        TokenKind.SECTION -> SpanStyle(color = colors.section, fontWeight = FontWeight.Bold)
        TokenKind.OPTION -> SpanStyle(color = colors.option)
    }

    override fun equals(other: Any?): Boolean =
        other is SyntaxTransformation && other.format == format && other.colors == colors

    override fun hashCode(): Int = 31 * format.hashCode() + colors.hashCode()
}
