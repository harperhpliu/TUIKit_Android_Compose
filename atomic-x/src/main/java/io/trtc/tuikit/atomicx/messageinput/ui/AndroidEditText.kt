package io.trtc.tuikit.atomicx.messageinput.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Spannable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.Image
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.target.Target
import io.trtc.tuikit.atomicx.basecomponent.theme.LocalTheme
import io.trtc.tuikit.atomicx.emojipicker.EmojiManager
import io.trtc.tuikit.atomicx.emojipicker.model.Emoji
import io.trtc.tuikit.atomicx.messageinput.utils.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidEditTextState {
    internal var editTextView: EmojiEditTextView? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun setText(text: String) {
        editTextView?.setText(text)
    }

    fun insertText(text: String) {
        editTextView?.let { editText ->
            val selectionStart = editText.selectionStart
            val selectionEnd = editText.selectionEnd
            val currentText = editText.text.toString()

            val newText = currentText.substring(0, selectionStart) +
                    text +
                    currentText.substring(selectionEnd)

            editText.setText(newText)
            editText.setSelection(selectionStart + text.length)
        }
    }

    fun getText(): String {
        return editTextView?.text?.toString() ?: ""
    }

    fun requestFocus() {
        editTextView?.requestFocus()
    }

    fun forceRequestFocus() {
        coroutineScope.launch {
            editTextView?.let { editText ->
                editText.requestFocus()
                editText.isCursorVisible = true
                val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun clearFocus() {
        editTextView?.clearFocus()
    }

    fun setCursorVisible(visible: Boolean) {
        editTextView?.isCursorVisible = visible
    }

    fun deleteAtCursor() {
        editTextView?.deleteAtCursor()
    }

    fun hideKeyboardKeepFocus() {
        editTextView?.let { editText ->
            val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(editText.windowToken, 0)
        }
    }
}

@Composable
fun rememberAndroidEditTextState(): AndroidEditTextState {
    return remember { AndroidEditTextState() }
}

@Composable
fun AndroidEditText(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: AndroidEditTextState? = null,
    hint: String = "",
    textColor: Color = Color.Black,
    hintColor: Color = Color.Gray,
    textSize: TextUnit = 14.sp,
    maxLines: Int = 6,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onSendMessage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        delay(200)
        state?.editTextView?.isFocusable = true
        state?.editTextView?.isFocusableInTouchMode = true
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            EmojiEditTextView(ctx).apply {
                EmojiManager.initialize(ctx)
                setupBasicProperties(text, hint, textColor, hintColor, textSize, maxLines, density)
                setupCursor(colors.textColorLink.toArgb())
                setupListeners(onTextChange, onFocusChanged, onSendMessage)
                isFocusable = false
                isFocusableInTouchMode = false
                state?.editTextView = this
            }
        },
        update = { editText ->
            editText.updateProperties(text, textColor, hintColor, textSize, hint, maxLines)
        }
    )
}

class EmojiEditTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    internal val emojiHelper = EmojiHelper(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        setupMultiLineInput()
    }

    private fun setupMultiLineInput() {
        setSingleLine(false)
        setLines(1)
        maxLines = 6
        minLines = 1
        inputType = EditorInfo.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEND
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = true
        setBackgroundResource(android.R.color.transparent)
    }

    fun setupBasicProperties(
        text: String,
        hint: String,
        textColor: Color,
        hintColor: Color,
        textSize: TextUnit,
        maxLines: Int,
        density: Density
    ) {
        setText(text)
        if (text.isNotEmpty()) {
            setSelection(text.length)
        }
        setHint(hint)
        setTextColor(textColor.toArgb())
        setHintTextColor(hintColor.toArgb())
        setTextSize(textSize)
        this.maxLines = maxLines
        with(density) {
            setPadding(8.dp.toPx().toInt(), 8.dp.toPx().toInt(), 8.dp.toPx().toInt(), 8.dp.toPx().toInt())
        }
        emojiHelper.updateEmojiRanges(this)
        emojiHelper.processEmojiText(this)
    }

    fun setTextSize(size: TextUnit) {
        textSize = size.value
    }

    fun setupListeners(
        onTextChange: (String) -> Unit,
        onFocusChanged: ((Boolean) -> Unit)?,
        onSendMessage: (() -> Unit)?
    ) {
        setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND
                || (actionId == EditorInfo.IME_ACTION_UNSPECIFIED && event != null
                        && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                onSendMessage?.invoke()
                true
            } else {
                false
            }
        }


        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                emojiHelper.updateEmojiRanges(this@EmojiEditTextView)
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null || TextUtils.isEmpty(s.toString().trim())) {
                    emojiHelper.resetProcessingState()
                    onTextChange("")
                    return
                }


                emojiHelper.processEmojiText(this@EmojiEditTextView)

                val newText = s.toString()
                onTextChange(newText)

                coroutineScope.launch { requestLayout() }
            }
        })


        setOnFocusChangeListener { _, hasFocus ->
            onFocusChanged?.invoke(hasFocus)
        }
    }

    fun updateProperties(
        text: String,
        textColor: Color,
        hintColor: Color,
        textSize: TextUnit,
        hint: String,
        maxLines: Int
    ) {
        if (this.text.toString() != text) {
            val selection = selectionStart
            setText(text)
            setSelection(minOf(selection, text.length))
            coroutineScope.launch { requestLayout() }
        }

        if (this.maxLines != maxLines) {
            this.maxLines = maxLines
            coroutineScope.launch { requestLayout() }
        }

        setTextColor(textColor.toArgb())
        setHintTextColor(hintColor.toArgb())
        setTextSize(textSize)
        setHint(hint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        layout?.let { layout ->
            val desiredHeight = layout.height + paddingTop + paddingBottom
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)

            val finalHeight = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
                else -> desiredHeight
            }
            setMeasuredDimension(measuredWidth, finalHeight)
        }
    }

    fun deleteAtCursor() {
        val inputConnection = onCreateInputConnection(EditorInfo())
        inputConnection?.let { ic ->
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    fun setupCursor(color: Int) {
        try {
            val thinCursorDrawable = createThinCursorDrawable(context, color)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = thinCursorDrawable
            } else {

                try {
                    val editorField = EditText::class.java.getDeclaredField("mEditor")
                    editorField.isAccessible = true
                    val editor = editorField.get(this)
                    if (editor != null) {
                        val cursorDrawableField = editor.javaClass.getDeclaredField("mCursorDrawable")
                        cursorDrawableField.isAccessible = true
                        cursorDrawableField.set(editor, arrayOf(thinCursorDrawable, thinCursorDrawable))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val originalConnection = super.onCreateInputConnection(outAttrs)
        val emojiConnection = emojiHelper.createEmojiInputConnection(originalConnection, this)
        return MultiLineInputConnection(emojiConnection, this)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        try {
            emojiHelper.onSelectionChanged(selStart, selEnd, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        emojiHelper.cleanup()
    }

    private inner class MultiLineInputConnection(
        target: InputConnection?,
        private val editText: EditText
    ) : InputConnectionWrapper(target, true) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text == "\n") {
                insertNewLine()
                return true
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val currentText = editText.text.toString()
                val currentLineCount = editText.lineCount

                if (currentText.isEmpty()) {
                    return super.sendKeyEvent(event)
                }

                if (currentLineCount < editText.maxLines) {
                    insertNewLine()
                    return true
                }

                return super.sendKeyEvent(event)
            }
            return super.sendKeyEvent(event)
        }

        private fun insertNewLine() {
            val start = editText.selectionStart
            val end = editText.selectionEnd
            editText.text.replace(start, end, "\n")
            editText.setSelection(start + 1)
        }
    }
}

internal class EmojiHelper(private val context: Context) {
    private val emojiCache = mutableMapOf<String, Drawable>()
    private val imageLoader = ImageUtils.getImageLoader(context)
    private val emojiRanges = mutableListOf<EmojiRange>()
    private var isEmojiSelected = false
    private var lastSelectedRange: EmojiRange? = null
    private var lastProcessedText: String? = null

    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)

    data class EmojiRange(val from: Int, val to: Int, val emojiKey: String) {
        fun contains(start: Int, end: Int) = from <= start && to >= end
        fun isWrappedBy(start: Int, end: Int) = (start > from && start < to) || (end > from && end < to)
        fun isEqual(start: Int, end: Int) = (from == start && to == end) || (from == end && to == start)
        fun getAnchorPosition(value: Int) = if ((value - from) - (to - value) >= 0) to else from
    }

    fun createEmojiInputConnection(target: InputConnection?, editText: EditText): InputConnection {
        return EmojiInputConnection(target, editText)
    }

    fun updateEmojiRanges(editText: EditText) {
        emojiRanges.clear()
        isEmojiSelected = false

        val text = editText.text?.toString() ?: return
        if (text.isEmpty()) return

        EmojiManager.littleEmojiKeyList.forEach { emojiKey ->
            var startIndex = text.indexOf(emojiKey)
            while (startIndex != -1) {
                emojiRanges.add(EmojiRange(startIndex, startIndex + emojiKey.length, emojiKey))
                startIndex = text.indexOf(emojiKey, startIndex + emojiKey.length)
            }
        }
        emojiRanges.sortBy { it.from }
    }

    fun processEmojiText(editText: EditText) {
        val content = editText.text
        if (content == null || content.isEmpty()) {
            lastProcessedText = ""
            return
        }

        val textSize = editText.textSize.toInt()
        val emojiSize = textSize * 2
        val originalText = content.toString()

        if (originalText == lastProcessedText) {
            return
        }

        lastProcessedText = originalText

        val spannable = content

        val existingSpans = spannable.getSpans(0, spannable.length, CenterImageSpan::class.java)
        val existingSpanRanges = mutableSetOf<Pair<Int, Int>>()
        val invalidSpans = mutableListOf<CenterImageSpan>()

        for (span in existingSpans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start >= 0 && end <= spannable.length) {
                val spanText = spannable.substring(start, end)
                if (EmojiManager.littleEmojiKeyList.contains(spanText)) {
                    existingSpanRanges.add(start to end)
                } else {
                    invalidSpans.add(span)
                }
            } else {
                invalidSpans.add(span)
            }
        }

        for (span in invalidSpans) {
            spannable.removeSpan(span)
        }

        val pendingEmojis = mutableListOf<Triple<Int, Int, Emoji>>()
        var hasImmediateChanges = false

        EmojiManager.littleEmojiKeyList.forEach { emojiKey ->
            var startIndex = spannable.indexOf(emojiKey)
            while (startIndex != -1) {
                val endIndex = startIndex + emojiKey.length
                val range = startIndex to endIndex

                if (startIndex >= 0 && endIndex <= spannable.length && !existingSpanRanges.contains(range)) {
                    val emoji = EmojiManager.findEmojiByKey(emojiKey)
                    if (emoji != null) {
                        val cachedDrawable = EmojiManager.getCachedEmojiDrawable(emoji.key) ?: emojiCache[emoji.key]
                        if (cachedDrawable != null) {
                            cachedDrawable.setBounds(0, 0, emojiSize, emojiSize)
                            val imageSpan = CenterImageSpan(cachedDrawable)
                            spannable.setSpan(
                                imageSpan,
                                startIndex,
                                endIndex,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            hasImmediateChanges = true
                        } else {
                            pendingEmojis.add(Triple(startIndex, endIndex, emoji))
                        }
                    }
                }

                startIndex = spannable.indexOf(emojiKey, endIndex)
            }
        }

        if (hasImmediateChanges) {
            val currentSelection = editText.selectionStart
            editText.setText(spannable)
            if (currentSelection >= 0 && currentSelection <= editText.text.length) {
                editText.setSelection(currentSelection)
            }
        }

        if (pendingEmojis.isNotEmpty()) {
            mainCoroutineScope.launch {
                try {
                    var hasAsyncChanges = false
                    pendingEmojis.forEach { (startIndex, endIndex, emoji) ->
                        val drawable = loadEmojiDrawableAsync(emoji)
                        drawable?.let { d ->
                            if (startIndex >= 0 &&
                                startIndex < spannable.length &&
                                endIndex <= spannable.length &&
                                spannable.substring(startIndex, endIndex) == emoji.key
                            ) {
                                val currentSpans = spannable.getSpans(startIndex, endIndex, CenterImageSpan::class.java)
                                if (currentSpans.isEmpty()) {
                                    d.setBounds(0, 0, emojiSize, emojiSize)
                                    val imageSpan = CenterImageSpan(d)
                                    spannable.setSpan(
                                        imageSpan,
                                        startIndex,
                                        endIndex,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                    hasAsyncChanges = true
                                }
                            }
                        }
                    }

                    if (hasAsyncChanges) {
                        val currentSelection = editText.selectionStart
                        editText.setText(spannable)
                        if (currentSelection >= 0 && currentSelection <= editText.text.length) {
                            editText.setSelection(currentSelection)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun resetProcessingState() {
        lastProcessedText = null
        emojiRanges.clear()
    }

    fun onSelectionChanged(selStart: Int, selEnd: Int, editText: EditText) {
        if (selStart < 0 || selEnd < 0) return
        val textLength = editText.text.length
        if (selStart > textLength || selEnd > textLength) return

        if (lastSelectedRange?.isEqual(selStart, selEnd) == true) return

        val closestRange = emojiRanges.find { it.contains(selStart, selEnd) }
        if (closestRange?.to == selEnd) {
            isEmojiSelected = false
        }

        val nearbyRange = emojiRanges.find { it.isWrappedBy(selStart, selEnd) } ?: return

        try {
            if (selStart == selEnd) {
                val anchorPosition = nearbyRange.getAnchorPosition(selStart)
                if (anchorPosition in 0..textLength) {
                    editText.setSelection(anchorPosition)
                }
            } else {
                if (selEnd < nearbyRange.to && nearbyRange.to <= textLength) {
                    editText.setSelection(selStart, nearbyRange.to)
                }
                if (selStart > nearbyRange.from && nearbyRange.from >= 0) {
                    editText.setSelection(nearbyRange.from, selEnd)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cleanup() {
        emojiCache.clear()
        emojiRanges.clear()
        lastProcessedText = null
    }

    private suspend fun loadEmojiDrawableAsync(emoji: Emoji): Drawable? {
        emojiCache[emoji.key]?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            val request = ImageRequest.Builder(context)
                .data(emoji.emojiUrl)
                .target(object : Target {
                    override fun onSuccess(result: Image) {
                        val drawable = result.asDrawable(context.resources)
                        emojiCache[emoji.key] = drawable
                        continuation.resume(drawable)
                    }

                    override fun onError(error: Image?) {
                        continuation.resume(null)
                    }
                })
                .build()

            imageLoader.enqueue(request)

            continuation.invokeOnCancellation {
            }
        }
    }

    private inner class EmojiInputConnection(
        target: InputConnection?,
        private val editText: EditText
    ) : InputConnectionWrapper(target, true) {

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                val closestRange = emojiRanges.find { it.contains(selectionStart, selectionEnd) }

                if (closestRange == null) {
                    isEmojiSelected = false
                    return super.sendKeyEvent(event)
                }

                if (isEmojiSelected || selectionStart == closestRange.from) {
                    isEmojiSelected = false
                    return super.sendKeyEvent(event)
                } else {
                    isEmojiSelected = true
                    lastSelectedRange = closestRange
                    editText.setSelection(closestRange.to, closestRange.from)
                    return sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                }
            }
            return super.sendKeyEvent(event)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0) {
                return sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) &&
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }
    }
}


private fun createThinCursorDrawable(context: Context, color: Int): Drawable {
    val drawable = GradientDrawable()
    drawable.shape = GradientDrawable.RECTANGLE
    drawable.setColor(color)
    val density = context.resources.displayMetrics.density
    val cursorWidth = (1.5f * density).toInt()
    drawable.setSize(cursorWidth, 0)
    return drawable
}
