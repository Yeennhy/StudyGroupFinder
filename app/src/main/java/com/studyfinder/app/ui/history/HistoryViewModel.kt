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
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.DateTimeUtils
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository

    private val _exportResult = MutableStateFlow<ActionResult?>(null)
    val exportResult: StateFlow<ActionResult?> = _exportResult

    val historySessions = sessionRepository.observeMySessions(includeCancelled = true)
        .map { state ->
            val now = System.currentTimeMillis()
            fun past(list: List<Session>) = list.filter { 
                it.isPast(now) || 
                it.status == SessionStatus.CANCELLED || 
                it.status == SessionStatus.FINISHED 
            }.sortedByDescending { it.startTimeMillis }

            when (state) {
                is UiState.Success -> past(state.data)
                    .let { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
                is UiState.Offline -> past(state.cached)
                    .let { if (it.isEmpty()) UiState.Empty() else UiState.Offline(it) }
                else -> state
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun exportPdfToUri(context: Context, uri: Uri) {
        val sessions = when (val sessionsState = historySessions.value) {
            is UiState.Success -> sessionsState.data
            is UiState.Offline -> sessionsState.cached
            else -> null
        }
        if (sessions == null) {
            _exportResult.value = ActionResult.Failure("No history available to export yet")
            return
        }

        viewModelScope.launch {
            try {
                // Fetch profile info first
                val profileState = ServiceLocator.profileRepository.observeCurrentProfile().first { it !is UiState.Loading }
                val profile = (profileState as? UiState.Success)?.data

                withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    
                    // Paints for styling
                    val headerBgPaint = Paint().apply { color = Color.parseColor("#FFD54F") } // Ginkgo Yellow
                    val headerTextPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 28f
                        color = Color.parseColor("#333333") // Graphite
                    }
                    val subHeaderTextPaint = Paint().apply {
                        textSize = 14f
                        color = Color.parseColor("#666666")
                    }
                    val studierInfoPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 18f
                        color = Color.parseColor("#222222")
                    }
                    val titlePaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 18f
                        color = Color.parseColor("#333333")
                    }
                    val labelPaint = Paint().apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 12f
                        color = Color.parseColor("#555555")
                    }
                    val bodyPaint = Paint().apply {
                        textSize = 12f
                        color = Color.parseColor("#333333")
                    }
                    val dividerPaint = Paint().apply {
                        color = Color.LTGRAY
                        strokeWidth = 1f
                    }
                    val thickDividerPaint = Paint().apply {
                        color = Color.BLACK
                        strokeWidth = 2f
                    }
                    val statusBgPaint = Paint().apply { style = Paint.Style.FILL }

                    var pageNumber = 1
                    var myPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var myPage = pdfDocument.startPage(myPageInfo)
                    var canvas: Canvas = myPage.canvas
                    
                    fun drawPageHeader(canv: Canvas) {
                        canv.drawRect(0f, 0f, 595f, 150f, headerBgPaint)
                        canv.drawText("Study Session History", 40f, 55f, headerTextPaint)
                        canv.drawText("Generated on: ${DateTimeUtils.formatDateTime(System.currentTimeMillis())}", 40f, 80f, subHeaderTextPaint)
                        
                        profile?.let {
                            canv.drawText("${it.name} (${it.studentId})", 40f, 115f, studierInfoPaint)
                            canv.drawText("Community: ${it.communityId}", 40f, 135f, subHeaderTextPaint)
                        }
                        
                        canv.drawLine(0f, 150f, 595f, 150f, thickDividerPaint)
                    }

                    drawPageHeader(canvas)
                    var y = 190f

                    sessions.forEach { session ->
                        // Check for page overflow
                        if (y > 750f) {
                            pdfDocument.finishPage(myPage)
                            pageNumber++
                            myPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            myPage = pdfDocument.startPage(myPageInfo)
                            canvas = myPage.canvas
                            drawPageHeader(canvas)
                            y = 190f
                        }

                        // Session Title
                        canvas.drawText(session.title, 40f, y, titlePaint)
                        y += 25f

                        // Course Info
                        canvas.drawText("COURSE:", 40f, y, labelPaint)
                        canvas.drawText("${session.courseId} - ${session.courseName}", 110f, y, bodyPaint)
                        y += 20f

                        // Time and Duration
                        canvas.drawText("TIME:", 40f, y, labelPaint)
                        val durationMinutes = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()
                        val timeStr = "${DateTimeUtils.formatDateTime(session.startTimeMillis)} (${DateTimeUtils.formatDuration(durationMinutes)})"
                        canvas.drawText(timeStr, 110f, y, bodyPaint)
                        y += 20f

                        // Location
                        canvas.drawText("LOCATION:", 40f, y, labelPaint)
                        canvas.drawText(session.locationName, 110f, y, bodyPaint)
                        y += 20f

                        // Status Badge
                        canvas.drawText("STATUS:", 40f, y, labelPaint)
                        val statusStr = session.status.wire.uppercase()
                        val statusColor = when (session.status) {
                            com.studyfinder.app.model.SessionStatus.FINISHED -> "#E8F5E9" // Light Green
                            com.studyfinder.app.model.SessionStatus.CANCELLED -> "#FFEBEE" // Light Red
                            else -> "#E3F2FD" // Light Blue
                        }
                        statusBgPaint.color = Color.parseColor(statusColor)
                        val textWidth = bodyPaint.measureText(statusStr)
                        canvas.drawRect(110f, y - 12f, 115f + textWidth + 5f, y + 5f, statusBgPaint)
                        canvas.drawText(statusStr, 115f, y, bodyPaint)
                        y += 30f

                        // Goals (if present)
                        if (session.goals.isNotBlank()) {
                            canvas.drawText("GOALS:", 40f, y, labelPaint)
                            val goalsLines = session.goals.lines()
                            goalsLines.take(3).forEach { line ->
                                if (y > 800f) return@forEach // Safety
                                canvas.drawText(line, 110f, y, bodyPaint)
                                y += 18f
                            }
                            y += 10f
                        }

                        // Divider line
                        canvas.drawLine(40f, y, 555f, y, dividerPaint)
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
