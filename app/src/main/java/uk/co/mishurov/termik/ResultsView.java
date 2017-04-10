package uk.co.mishurov.termik;


import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.Canvas;
import android.util.Log;


public class ResultsView extends FrameLayout {
    private static final String TAG = "Termik";
    private static final int MARGIN = 5;

    private TextView mTextView;
    private int mTextWidth = 0;
    private int mTextHeight = 0;
    private int mDiag = 0;
    // Rotate canvas in order to prevent cropping
    private int mAngle = 0;


    public ResultsView(Context context) {
        this(context, null, 0, 0);
    }

    public ResultsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public ResultsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ResultsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mTextView = new TextView(context);
        mTextView.setTextAppearance(context, R.style.TermFont);
        // set background to fill the container for w/h calculations
        // first 2 digits are alpha
        setBackgroundColor(0x01FF0000);
        int index = 0;
        this.addView(mTextView, index);
    }

    public void adjust(int rotation) {
        setAngle(rotation);

        /*

        // Bounding Box dimensions

        double theta = Math.toRadians(-rotation);
        double cs = Math.cos(theta);
        double sn = Math.sin(theta);
        double w = mTextWidth;
        double h = mTextHeight;

        double bbw = Math.abs(w * cs + h * sn);
        double bbh = Math.abs(h * cs + w * sn);

        */
    }

    public void setResults(String text) {
        mTextView.setText(text);
        // Calculate text dimensions
        mTextView.measure(0, 0);
        mTextWidth = mTextView.getMeasuredWidth();
        mTextHeight = mTextView.getMeasuredHeight();
        // Prevent gravity propagation
        mTextView.setGravity(Gravity.CENTER | Gravity.CENTER);

        // Calculate diagonal length as max bounds for container
        double w = mTextWidth;
        double h = mTextHeight;
        double d = Math.sqrt(w * w + h * h);
        mDiag = (int) Math.round(d);
        mDiag += MARGIN;

        // Setup container
        setMeasuredDimension(mDiag, mDiag);
        setPivotX(mDiag/2);
        setPivotY(mDiag/2);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            mDiag, mDiag, Gravity.RIGHT|Gravity.CENTER
        );
        setLayoutParams(params);
    }

    public int getAngle() {
        return mAngle;
    }

    public void setAngle(int angle) {
        if (mAngle != angle) {
            mAngle = angle;
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.rotate(-mAngle, getWidth() / 2f, getHeight() / 2f);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
