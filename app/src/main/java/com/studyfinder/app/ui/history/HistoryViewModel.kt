package com.studyfinder.app.ui.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.DateTimeUtils
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** §7.6. Same query as My Sessions, filtered to `endTime` in the past. */
class HistoryViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository

    private val _exportResult = MutableStateFlow<ActionResult?>(null)
    val exportResult: StateFlow<ActionResult?> = _exportResult

    val historySessions = sessionRepository.observeMySessions(includeCancelled = true)
        .map { state ->
            if (state is UiState.Success) {
                val now = System.currentTimeMillis()
                val items = state.data.filter { 
                    it.isPast(now) || 
                    it.status == SessionStatus.CANCELLED || 
                    it.status == SessionStatus.FINISHED 
                }.sortedByDescending { it.startTimeMillis }
                if (items.isEmpty()) UiState.Empty() else UiState.Success(items)
            } else state
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun exportPdfToUri(context: Context, uri: Uri) {
        val sessionsState = historySessions.value
        if (sessionsState !is UiState.Success) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    val titlePaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 20f
                    }
                    val bodyPaint = Paint().apply {
                        textSize = 14f
                    }
                    val headerPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 24f
                        color = Color.BLACK
                    }

                    var pageNumber = 1
                    var myPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var myPage = pdfDocument.startPage(myPageInfo)
                    var canvas: Canvas = myPage.canvas
                    var y = 50f

                    // Header
                    canvas.drawText("Study Session History", 50f, y, headerPaint)
                    y += 40f
                    canvas.drawText("Generated on: ${DateTimeUtils.formatDateTime(System.currentTimeMillis())}", 50f, y, bodyPaint)
                    y += 50f

                    sessionsState.data.forEach { session ->
                        if (y > 750f) {
                            pdfDocument.finishPage(myPage)
                            pageNumber++
                            myPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            myPage = pdfDocument.startPage(myPageInfo)
                            canvas = myPage.canvas
                            y = 50f
                        }

                        canvas.drawText(session.title, 50f, y, titlePaint)
                        y += 25f
                        canvas.drawText("Date: ${DateTimeUtils.formatDateTime(session.startTimeMillis)}", 70f, y, bodyPaint)
                        y += 20f
                        canvas.drawText("Location: ${session.locationName}", 70f, y, bodyPaint)
                        y += 20f
                        canvas.drawText("Status: ${session.status.wire.uppercase()}", 70f, y, bodyPaint)
                        y += 40f
                    }

                    pdfDocument.finishPage(myPage)

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    pdfDocument.close()
                }
                _exportResult.value = ActionResult.Success
            } catch (e: Exception) {
                _exportResult.value = ActionResult.Failure(e.message ?: "Export failed")
            }
        }
    }
    
    fun resetExportResult() {
        _exportResult.value = null
    }
}
