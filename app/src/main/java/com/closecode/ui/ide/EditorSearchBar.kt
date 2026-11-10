package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

internal data class EditorSearchUiState(
    val open: Boolean = false,
    val query: String = "",
    val replaceOpen: Boolean = false,
    val replacement: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val menuOpen: Boolean = false,
)

internal class SoraSearchController(private val editor: CodeEditor) {
    fun search(query: String): Pair<Int, Int> {
        if (query.isEmpty()) {
            editor.searcher.stopSearch()
            return 0 to 0
        }
        editor.searcher.search(query, EditorSearcher.SearchOptions(true, false))
        return counts()
    }

    fun next(): Pair<Int, Int> {
        editor.searcher.gotoNext()
        return counts()
    }

    fun previous(): Pair<Int, Int> {
        editor.searcher.gotoPrevious()
        return counts()
    }

    fun last(): Pair<Int, Int> {
        editor.searcher.gotoPrevious()
        return counts()
    }

    fun replaceCurrent(replacement: String): Pair<Int, Int> {
        editor.searcher.replaceCurrentMatch(replacement)
        return counts()
    }

    fun replaceAll(replacement: String): Pair<Int, Int> {
        editor.searcher.replaceAll(replacement)
        return counts()
    }

    fun close() = editor.searcher.stopSearch()

    fun counts(): Pair<Int, Int> {
        val total = editor.searcher.matchedPositionCount
        val index = if (total == 0) 0 else editor.searcher.currentMatchedPositionIndex + 1
        return index to total
    }
}

private val SearchBarBackground = Color(0xFF14172A)
private val SearchFieldBackground = Color(0xFF1C2038)
private val SearchDivider = Color(0xFF23274A)
private val SearchMuted = Color(0xFF8A8FA8)
private val SearchText = Color(0xFFF2F3F7)
private val SearchSoftText = Color(0xFFC9CCE0)
private val SearchAccent = Color(0xFF7B6BF0)
private val SearchCounterText = Color(0xFFB7ACFF)
private val SearchCounterBackground = Color(0xFF2A2E52)
private val SearchMenuBorder = Color(0xFF2F3460)

@Composable
internal fun EditorSearchBar(
    state: EditorSearchUiState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onMenuToggle: (Boolean) -> Unit,
    onLast: () -> Unit,
    onReplaceRow: () -> Unit,
    onReplaceAllOpen: () -> Unit,
    onReplacementChange: (String) -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAllNow: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val replaceFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.open) {
        if (state.open) focus.requestFocus()
    }
    LaunchedEffect(state.replaceOpen) {
        if (state.replaceOpen) replaceFocus.requestFocus()
    }

    BackHandler(enabled = state.open) { onClose() }

    AnimatedVisibility(
        visible = state.open,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Column(Modifier.fillMaxWidth().background(SearchBarBackground)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIconButton("chevron-down", 34.dp) {
                    focusManager.clearFocus()
                    onClose()
                }
                Row(
                    Modifier
                        .weight(1f)
                        .widthIn(min = 0.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(SearchFieldBackground)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcIcon("search", SearchMuted, 16.dp)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(SearchAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNext() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    if (state.query.isNotEmpty()) {
                        Text(
                            text = if (state.total == 0) "0" else "${state.current}/${state.total}",
                            color = SearchCounterText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SearchCounterBackground)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                SearchIconButton("chevron-up", 30.dp, onPrevious)
                SearchIconButton("chevron-down", 30.dp, onNext)
                Box {
                    SearchIconButton("more-vertical", 30.dp) { onMenuToggle(true) }
                    SearchOverflowMenu(
                        open = state.menuOpen,
                        onDismiss = { onMenuToggle(false) },
                        onLast = onLast,
                        onReplace = onReplaceRow,
                        onReplaceAll = onReplaceAllOpen,
                    )
                }
            }
            AnimatedVisibility(state.replaceOpen) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(start = 52.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(SearchFieldBackground)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon("replace", SearchMuted, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = state.replacement,
                            onValueChange = onReplacementChange,
                            singleLine = true,
                            textStyle = TextStyle(color = SearchSoftText, fontSize = 13.sp),
                            cursorBrush = SolidColor(SearchAccent),
                            modifier = Modifier.weight(1f).focusRequester(replaceFocus),
                        )
                    }
                    SearchPillButton("Replace", filled = true, onClick = onReplaceOne)
                    SearchPillButton("All", filled = false, onClick = onReplaceAllNow)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SearchDivider))
        }
    }
}

@Composable
private fun SearchIconButton(
    icon: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, SearchSoftText, 19.dp)
    }
}

@Composable
private fun SearchPillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) SearchAccent else SearchFieldBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) Color.White else SearchSoftText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchOverflowMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onLast: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SearchFieldBackground)
            .border(1.dp, SearchMenuBorder, RoundedCornerShape(20.dp)),
    ) {
        SearchMenuItem("last", "Last match") {
            onDismiss()
            onLast()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(SearchCounterBackground),
        )
        SearchMenuItem("replace", "Replace") {
            onDismiss()
            onReplace()
        }
        SearchMenuItem("replace-all", "Replace all") {
            onDismiss()
            onReplaceAll()
        }
    }
}

@Composable
private fun SearchMenuItem(icon: String, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = SearchText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = { CcIcon(icon, SearchAccent, 18.dp) },
        onClick = onClick,
    )
}
