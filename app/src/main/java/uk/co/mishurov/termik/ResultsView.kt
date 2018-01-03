package uk.co.mishurov.termik

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Canvas

import android.util.Log

class ResultsView @JvmOverloads constructor(context: Context,
                                    attrs: AttributeSet? = null,
                                    defStyleAttr: Int = 0,
                                    defStyleRes: Int = 0)
                    : FrameLayout(context, attrs, defStyleAttr, defStyleRes)
{
    private var mTextView: TextView? = null
    private var mTextWidth = 0
    private var mTextHeight = 0
    private var mDiag = 0

    // Rotate canvas in order to prevent cropping
    var angle = 0
        set(angle)
        {
            if (this.angle != angle) {
                field = angle
                requestLayout()
                invalidate()
            }
        }

    init {
        init(context)
    }

    private fun init(context: Context)
    {
        mTextView = TextView(context)
        mTextView?.setTextAppearance(R.style.TermFont)
        setBackgroundColor(0x01FF0000)
        val index = 0
        this.addView(mTextView, index)
    }

    fun adjust(rotation: Int)
    {
        angle = rotation
    }

    fun setVrStyle()
    {
        mTextView?.setTextAppearance(R.style.TermFontVR)
    }

    fun setResults(text: String)
    {
        mTextView!!.text = text
        // Calculate text dimensions
        mTextView!!.measure(0, 0)
        mTextWidth = mTextView!!.measuredWidth
        mTextHeight = mTextView!!.measuredHeight
        // Prevent gravity propagation
        mTextView!!.gravity = Gravity.CENTER or Gravity.CENTER

        // Calculate diagonal length as max bounds for container
        val w = mTextWidth.toDouble()
        val h = mTextHeight.toDouble()
        val d = Math.sqrt(w * w + h * h)
        mDiag = Math.round(d).toInt()
        mDiag += MARGIN

        // Setup container
        setMeasuredDimension(mDiag, mDiag)
        pivotX = (mDiag / 2).toFloat()
        pivotY = (mDiag / 2).toFloat()

        val params = FrameLayout.LayoutParams(
                mDiag, mDiag, Gravity.RIGHT or Gravity.CENTER
        )
        layoutParams = params
    }

    fun drawText(c: Canvas)
    {
        val x = (c.getWidth() - mDiag).toFloat()
        val y = (c.getHeight() - mDiag).toFloat()
        c.translate(x, y)
        draw(c)
    }

    override fun dispatchDraw(canvas: Canvas)
    {
        canvas.save()
        canvas.rotate((-angle).toFloat(), width / 2.0f, height / 2.0f)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    companion object
    {
        private val TAG = "Termik ResultsView"
        private val MARGIN = 5
    }
}
