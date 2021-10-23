/*
 * Copyright 2021 Squircle IDE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blacksquircle.ui.plugin.autocomplete

import android.graphics.Rect
import android.util.Log
import android.widget.EditText
import android.widget.MultiAutoCompleteTextView
import com.blacksquircle.ui.language.base.Language
import com.blacksquircle.ui.plugin.base.EditorPlugin
import com.blacksquircle.ui.plugin.base.LinesCollection

class AutoCompletePlugin : EditorPlugin(PLUGIN_ID) {

    var suggestionAdapter: SuggestionAdapter? = null
        set(value) {
            field = value
            updateAdapter()
        }

    private val codeEditor: MultiAutoCompleteTextView
        get() = editText as MultiAutoCompleteTextView // it's always safe
    private val lines: LinesCollection
        get() = accessor?.lines!!
    private val language: Language?
        get() = accessor?.language

    override fun onAttached(editText: EditText) {
        super.onAttached(editText)
        codeEditor.setTokenizer(SymbolsTokenizer())
        Log.d(PLUGIN_ID, "AutoComplete plugin loaded successfully!")
    }

    override fun onDetached(editText: EditText) {
        codeEditor.setTokenizer(null)
        super.onDetached(editText)
    }

    override fun showDropDown() {
        if (!codeEditor.isPopupShowing) {
            if (codeEditor.hasFocus()) {
                super.showDropDown()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onDropDownSizeChange(w, h)
    }

    override fun doOnTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        super.doOnTextChanged(text, start, before, count)
        onPopupChangePosition()
    }

    override fun addLine(lineNumber: Int, lineStart: Int, lineLength: Int) {
        super.addLine(lineNumber, lineStart, lineLength)
        language?.getProvider()?.processLine(
            lineNumber = lineNumber,
            text = codeEditor.text.substring(lineStart, lineStart + lineLength)
        )
    }

    override fun removeLine(lineNumber: Int) {
        super.removeLine(lineNumber)
        language?.getProvider()?.deleteLine(
            lineNumber = lines.getIndexForLine(lineNumber)
        )
    }

    override fun setTextContent(text: CharSequence) {
        super.setTextContent(text)
        language?.getProvider()?.clearLines() // FIXME clears all lines
        updateAdapter() // probably language has been changed
    }

    override fun doOnTextReplaced(newStart: Int, newEnd: Int, newText: CharSequence) {
        super.doOnTextReplaced(newStart, newEnd, newText)
        val startLine = lines.getLineForIndex(newStart)
        val endLine = lines.getLineForIndex(newText.length + newStart)
        for (currentLine in startLine..endLine) {
            val lineStart = lines.getIndexForStartOfLine(currentLine)
            val lineEnd = lines.getIndexForEndOfLine(currentLine)
            if (lineStart <= lineEnd && lineEnd <= codeEditor.text.length) {
                language?.getProvider()?.processLine(
                    lineNumber = currentLine,
                    text = codeEditor.text.substring(lineStart, lineEnd)
                )
            }
        }
    }

    private fun updateAdapter() {
        suggestionAdapter?.let { adapter ->
            language?.getProvider()?.let { provider ->
                adapter.setSuggestionProvider(provider)
            }
            if (editText != null) {
                codeEditor.setAdapter(adapter)
            }
        }
    }

    private fun onDropDownSizeChange(width: Int, height: Int) {
        codeEditor.dropDownWidth = width * 1 / 2
        codeEditor.dropDownHeight = height * 1 / 2
        onPopupChangePosition()
    }

    private fun onPopupChangePosition() {
        val layout = codeEditor.layout ?: return
        val line = layout.getLineForOffset(codeEditor.selectionStart)
        val x = layout.getPrimaryHorizontal(codeEditor.selectionStart)
        val y = layout.getLineBaseline(line)

        val offsetHorizontal = x + codeEditor.paddingStart
        codeEditor.dropDownHorizontalOffset = offsetHorizontal.toInt()

        val offsetVertical = y - codeEditor.scrollY
        val temp = offsetVertical + codeEditor.dropDownHeight
        codeEditor.dropDownVerticalOffset = if (temp > getVisibleHeight()) {
            offsetVertical - codeEditor.dropDownHeight
        } else {
            offsetVertical
        }
    }

    private fun getVisibleHeight(): Int {
        val rect = Rect()
        codeEditor.getWindowVisibleDisplayFrame(rect)
        return rect.bottom - rect.top
    }

    companion object {
        const val PLUGIN_ID = "autocomplete-6743"
    }
}