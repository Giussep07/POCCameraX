package com.giusseprc.poccamerax.verticalSlider

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.giusseprc.poccamerax.R
import com.giusseprc.poccamerax.databinding.CustomVerticalSliderBinding
import timber.log.Timber
import kotlin.math.roundToLong


class VerticalSlider constructor(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet), IContractVerticalSlider {

    private val binding: CustomVerticalSliderBinding
    private var mListener: Listener? = null

    private var mProgressInitialHeight = 0
    private var mActivePointerId = MotionEvent.INVALID_POINTER_ID
    private var mInitialY: Float = 0.0f
    private var mLastTouchY: Float = 0.0f
    private var mPosY: Float = 0f

    interface Listener {
        fun onSlide(value: Float)
    }

    init {
        val infService = Context.LAYOUT_INFLATER_SERVICE
        val layoutInflater = getContext().getSystemService(infService) as LayoutInflater
        binding = DataBindingUtil.inflate(
            layoutInflater,
            R.layout.custom_vertical_slider,
            this@VerticalSlider,
            true
        )

        binding.backgroundProgress.post {
            binding.fabThumb.setOnTouchListener { v, event ->
                super.onTouchEvent(event)

                when (event.actionMasked) {
                    MotionEvent.ACTION_CANCEL -> {
                        Timber.d("ACTION_CANCEL")
                    }
                    MotionEvent.ACTION_DOWN -> {
                        Timber.d("ACTION_DOWN")
                        actionDown(event)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        actionMove(event)
                    }
                    MotionEvent.ACTION_UP -> {
                        Timber.d("ACTION_UP")
                        actionUp()
                        performClick()
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        Timber.d("ACTION_POINTER_UP")
                        actionPointerUp(event)
                    }
                }

                true
            }
        }

        val progressLayoutParams = binding.backgroundProgress.layoutParams
        mProgressInitialHeight = progressLayoutParams.height
    }

    private fun actionMove(event: MotionEvent): Boolean {
        if (mActivePointerId != MotionEvent.INVALID_POINTER_ID) {
            val y = event.findPointerIndex(mActivePointerId).let { pointerIndex ->
                event.getY(pointerIndex)
            }

            mPosY += y - mLastTouchY

            val futureY = this.binding.fabThumb.y + mPosY

            val maxY: Float = binding.imageViewAdd.y + binding.imageViewAdd.height

            if (futureY < mInitialY && futureY > maxY) {
                Timber.d("futureY: $futureY, mInitialY: $mInitialY, maxY: $maxY")
                val maximo = mInitialY - (binding.imageViewAdd.y + binding.imageViewAdd.height)

                if (futureY > maxY) {
                    val difference = mInitialY - futureY
                    val percentage = difference / maximo

                    val finalPercentage = (percentage * 100.0).roundToLong() / 100.0
//                    Timber.d("finalPercentage: ${finalPercentage.toFloat()}")
                    mListener?.onSlide(finalPercentage.toFloat())
                    this.binding.fabThumb.y += mPosY
                }
            }

            invalidate()
            // Remember this touch position for the next move event
            mLastTouchY = y
            return true
        } else {
            return false
        }
    }

    private fun actionDown(event: MotionEvent) {
        if (mInitialY == 0.0f) {
            mInitialY = this.binding.fabThumb.y
        }

        event.actionIndex.also { pointerIndex ->
            // Remember where we started (for dragging)
            mLastTouchY = event.getY(pointerIndex)
        }

        // Save the ID of this pointer (for dragging)
        mActivePointerId = event.getPointerId(0)
    }

    private fun actionUp() {
        mActivePointerId = MotionEvent.INVALID_POINTER_ID
        mPosY = 0f
        mLastTouchY = 0f
    }

    private fun actionPointerUp(ev: MotionEvent) {
        ev.actionIndex.also { pointerIndex ->
            ev.getPointerId(pointerIndex)
                .takeIf { it == mActivePointerId }
                ?.run {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    mLastTouchY = ev.getY(newPointerIndex)
                    mActivePointerId =
                        ev.getPointerId(newPointerIndex)
                }
        }
    }

    //region Implementation IContractVerticalSlider
    override fun setListener(listener: Listener) {
        this.mListener = listener
    }
    //endregion
}