package uk.co.mishurov.termik;

import android.widget.LinearLayout;
import android.content.Context;
import android.widget.TextView;
import android.util.AttributeSet;
import uk.co.mishurov.termik.R;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.util.Log;
import android.graphics.Canvas;
import android.view.View;

public class ResultsView extends LinearLayout {
    private static final String TAG = "Termik";

    ClippingTextView nView;
    int textWidth = 0;
    int textHeight = 0;

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
        nView = new ClippingTextView(context);
        nView.setText("TROLOLOHA");
        nView.setTextAppearance(context, R.style.TermFont);
        nView.setBackgroundColor(0xFF00FF00);
        /*
        int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            WRAP, WRAP, Gravity.CENTER|Gravity.CENTER
        );
        nView.setLayoutParams(params);
        */
        int index = 0;
        this.addView(nView, index);
        textWidth = 130; //nView.getWidth();
        textHeight = 30; //nView.getHeight();

        Log.i(TAG, "VIEW HEIGHT: " + getWidth());

        double w = textWidth;
        double h = textHeight;
        double d = Math.sqrt(w * w + h * h);
        int diag = (int) Math.round(d);
        nView.setWidth(diag);
        nView.setHeight(diag);
        nView.setPivotX(diag/2);
        nView.setPivotY(diag/2);
    }

    public void adjust(int rotation) {
        /*
        int WRAP = FrameLayout.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            WRAP, WRAP, Gravity.CENTER|Gravity.CENTER
        );
        params.setMargins(20, 20, 20, 20);
        setLayoutParams(params);
        */
        rotation -= 90;
        /*
        int w = 130;
        nView.setWidth(w);
        nView.setHeight(w);
        nView.setPivotX(w/2);
        nView.setPivotY(w/2);
        */
        nView.setRotation(rotation);
        //nView.offsetTopAndBottom(rotation);
        //Log.i(TAG, "Width:" + w);
        //Log.i(TAG, "Rotation:" + getRotation());
    }

    public void setResults() {
     
    }

    /*
    @Override
    protected boolean drawChild(Canvas canvas, View child, long time) {
        //TextBaloon - is view that I'm trying to rotate
        if(!(child instanceof ClippingTextView)) {
            return super.drawChild(canvas, child, time);
        }
        final int width = child.getWidth();
        final int height = child.getHeight();

        final int left = child.getLeft();
        final int top = child.getTop();
        final int right = left + width;
        final int bottom = top + height;

        int restoreTo = canvas.save();

        canvas.translate(left, top);

        invalidate(left - width, top - height, right + width, bottom + height);
        child.draw(canvas);

        canvas.restoreToCount(restoreTo);

        return true;
    }
    */
}
