import android.text.Annotation
import androidx.annotation.StringRes
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.core.text.toSpannable

/**
 * Usage:
 *
 * HyperlinkText(
 *     modifier = Modifier.fillMaxWidth(),
 *     fullTextResId = R.string.text_for_link,
 *     linksActions = listOf("LINK"),
 *     hyperLinks = listOf("https://google.com")
 * )
 *
 * <string name="text_for_link">With text contain a <annotation type="LINK">link</annotation></string>
 *
 * @see https://gist.github.com/stevdza-san/ff9dbec0e072d8090e1e6d16e6b73c91
 */
@Composable
fun HyperlinkText(
    modifier: Modifier = Modifier,
    @StringRes fullTextResId: Int,
    linksActions: List<String>,
    hyperLinks: List<String>,
    textStyle: TextStyle = TextStyle.Default,
    linkTextColor: Color = Color.Blue,
    linkTextFontWeight: FontWeight = FontWeight.Normal,
    linkTextDecoration: TextDecoration = TextDecoration.None,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val fullText = LocalContext.current.getText(fullTextResId).toSpannable()
    val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)

    val annotatedString = buildAnnotatedString {
        append(fullText)
        linksActions.forEachIndexed { index, actionAnnotation ->
            annotations?.find { it.value == actionAnnotation }?.let {
                addStyle(
                    style = SpanStyle(
                        color = linkTextColor,
                        fontSize = fontSize,
                        fontWeight = linkTextFontWeight,
                        textDecoration = linkTextDecoration
                    ), start = fullText.getSpanStart(it), end = fullText.getSpanEnd(it)
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = hyperLinks[index],
                    start = fullText.getSpanStart(it),
                    end = fullText.getSpanEnd(it)
                )
            }
            addStyle(
                style = SpanStyle(
                    fontSize = fontSize
                ), start = 0, end = fullText.length
            )
        }
    }

    val uriHandler = LocalUriHandler.current

    ClickableText(modifier = modifier, text = annotatedString, style = textStyle, onClick = {
        annotatedString.getStringAnnotations("URL", it, it).firstOrNull()?.let { stringAnnotation ->
            uriHandler.openUri(stringAnnotation.item)
        }
    })
}