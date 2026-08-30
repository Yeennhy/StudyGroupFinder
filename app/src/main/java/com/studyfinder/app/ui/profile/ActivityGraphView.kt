package com.studyfinder.app.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.studyfinder.app.R
import com.studyfinder.app.model.ActivityCell
import java.time.format.TextStyle
import java.util.Locale

class ActivityGraphView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var weeks: List<List<ActivityCell>> = emptyList() // outer = column, inner = row (0..6)
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.graphite)
        typeface = try {
            ResourcesCompat.getFont(context, R.font.pjsans_bold)
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    private var cellSize = 0f
    private var cellGap = 0f
    
    private var labelAreaLeft = 30f * resources.displayMetrics.density
    private var labelAreaTop = 20f * resources.displayMetrics.density

    private var customCellSize = 0f
    private var customCellGap = 0f

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ActivityGraphView, 0, 0).apply {
            try {
                customCellSize = getDimension(R.styleable.ActivityGraphView_cellSize, 0f)
                customCellGap = getDimension(R.styleable.ActivityGraphView_cellGap, 0f)
            } finally {
                recycle()
            }
        }
    }

    fun submitData(cells: List<List<ActivityCell>>) {
        weeks = cells
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val columns = weeks.size.coerceAtLeast(1)
        
        cellGap = if (customCellGap > 0) customCellGap else resources.getDimension(R.dimen.activity_cell_gap)
        
        cellSize = if (customCellSize > 0) {
            customCellSize
        } else {
            (width - labelAreaLeft - cellGap * (columns - 1)) / columns
        }

        val height = (labelAreaTop + cellSize * 7 + cellGap * 6).toInt()
        val calculatedWidth = if (customCellSize > 0) {
            (labelAreaLeft + columns * cellSize + (columns - 1) * cellGap).toInt()
        } else {
            width
        }
        
        setMeasuredDimension(calculatedWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (weeks.isEmpty()) return
        
        drawLabels(canvas)

        weeks.forEachIndexed { col, week ->
            week.forEachIndexed { row, cell ->
                val left = labelAreaLeft + col * (cellSize + cellGap)
                val top = labelAreaTop + row * (cellSize + cellGap)
                cellPaint.color = colorForCount(cell.count)
                canvas.drawRoundRect(
                    left, top, left + cellSize, top + cellSize,
                    cellSize * 0.25f, cellSize * 0.25f, cellPaint
                )
            }
        }
    }

    private fun drawLabels(canvas: Canvas) {
        // Day labels (Mon, Wed, Fri)
        val dayLabels = mapOf(1 to "Mon", 3 to "Wed", 5 to "Fri")
        dayLabels.forEach { (row, label) ->
            val y = labelAreaTop + row * (cellSize + cellGap) + (cellSize / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(label, 0f, y, textPaint)
        }

        // Month labels
        var lastMonth = -1
        weeks.forEachIndexed { col, week ->
            // Check if any day in this week belongs to a month we haven't labeled yet
            val newMonthDay = week.find { it.date.monthValue != lastMonth }
            
            if (newMonthDay != null) {
                val dateToLabel = newMonthDay.date
                val monthLabel = dateToLabel.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                val x = labelAreaLeft + col * (cellSize + cellGap)
                
                canvas.drawText(monthLabel, x, labelAreaTop - 8f * resources.displayMetrics.density, textPaint)
                lastMonth = dateToLabel.monthValue
            }
        }
    }

    private fun colorForCount(count: Int): Int = when {
        count == 0 -> ContextCompat.getColor(context, R.color.graphite_10)
        count == 1 -> ContextCompat.getColor(context, R.color.activity_low)
        count == 2 -> ContextCompat.getColor(context, R.color.activity_mid)
        else       -> ContextCompat.getColor(context, R.color.activity_high)
    }
}
