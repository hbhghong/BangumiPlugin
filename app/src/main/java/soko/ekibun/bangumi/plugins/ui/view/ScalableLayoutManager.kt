package soko.ekibun.bangumi.plugins.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class ScalableLayoutManager(val context: Context, val updateContent: (View, ScalableLayoutManager) -> Unit) :
    LinearLayoutManager(context) {

    var scale = 1f
        set(value) {
            field = Math.max(1f, Math.min(value, 2f))
        }
    var offsetX = 0

    fun reset() {
        scale = 1f
        offsetX = 0
    }

    fun scrollOnScale(x: Float, y: Float, oldScale: Float){
        recyclerView.scrollBy(((offsetX + x) * (scale - oldScale) / oldScale).toInt(), 0)
        val pos = findFirstVisibleItemPosition()
        findViewByPosition(pos)?.let {
            scrollToPositionWithOffset(pos, (y - (-getDecoratedTop(it) + y) * scale / oldScale).toInt())
        }
    }

    lateinit var recyclerView: RecyclerView
    @SuppressLint("ClickableViewAccessibility")
    fun setupWithRecyclerView(view: RecyclerView, onTap: (Int, Int) -> Unit, onPress: (View, Int) -> Unit) {
        recyclerView = view
        view.layoutManager = this
        var beginScale = scale
        val scaleGestureDetector =
            ScaleGestureDetector(view.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                    beginScale = scale
                    return super.onScaleBegin(detector)
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val oldScale = scale
                    scale = beginScale * detector.scaleFactor
                    scrollOnScale(detector.focusX, detector.focusY, oldScale)
                    requestLayout()
                    return super.onScale(detector)
                }
            })
        val gestureDetector = GestureDetectorCompat(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTap((e.x).toInt(), (e.y).toInt())
                return super.onSingleTapConfirmed(e)
            }

            override fun onLongPress(e: MotionEvent) {
                view.findChildViewUnder(e.x, e.y)?.let { onPress(it, view.getChildAdapterPosition(it)) }
                super.onLongPress(e)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val oldScale = scale
                scale = if (scale < 2f) 2f else 1f
                scrollOnScale(e.x, e.y, oldScale)
                requestLayout()
                return super.onDoubleTap(e)
            }
        })
        view.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    override fun measureChildWithMargins(child: View, widthUsed: Int, heightUsed: Int) {
        super.measureChildWithMargins(child, widthUsed, heightUsed)
        val lp = child.layoutParams as RecyclerView.LayoutParams
        val widthSpec = RecyclerView.LayoutManager.getChildMeasureSpec(
            (width * scale).toInt(), widthMode,
            paddingLeft + paddingRight
                    + lp.leftMargin + lp.rightMargin + widthUsed, lp.width,
            canScrollHorizontally()
        )
        val heightSpec = RecyclerView.LayoutManager.getChildMeasureSpec(
            height, heightMode,
            paddingTop + paddingBottom
                    + lp.topMargin + lp.bottomMargin + heightUsed, lp.height,
            canScrollVertically()
        )
        child.measure(widthSpec, heightSpec)
    }

    override fun layoutDecoratedWithMargins(child: View, left: Int, top: Int, right: Int, bottom: Int) {
        updateContent(child, this)
        super.layoutDecoratedWithMargins(child, left - offsetX, top, right - offsetX, bottom)
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        val ddx = Math.max(Math.min(dx, (width * (scale - 1)).toInt() - offsetX), -offsetX)
        offsetX += ddx
        offsetChildrenHorizontal(-ddx)
        for (i in 0 until recyclerView.childCount) updateContent(recyclerView.getChildAt(i), this)

        return if (scale == 1f) dx else ddx
    }

    override fun canScrollHorizontally(): Boolean = true
    override fun canScrollVertically(): Boolean = true
}