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
import android.widget.RelativeLayout;

public class ResultsView extends FrameLayout {
    private static final String TAG = "Termik";
    private static final int MARGIN = 5;

    private TextView textView;
    private int textWidth = 0;
    private int textHeight = 0;
    private int diag = 0;
    private int orientation = 0;
    private boolean refreshing = false;

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
        textView = new ClippingTextView(context);
        textView.setTextAppearance(context, R.style.TermFont);
        // set background to fill the container for w/h calculations
        setBackgroundColor(0x88FF0000);
        int index = 0;
        this.addView(textView, index);
    }

    public void adjust(int rotation) {
        orientation = rotation - 90;
        if (!refreshing) {
            setRotation(orientation);
        }
        /*

        // Bounding Box dimensions

        double theta = Math.toRadians(-rotation);
        double cs = Math.cos(theta);
        double sn = Math.sin(theta);
        double w = textWidth;
        double h = textHeight;

        double bbw = Math.abs(w * cs + h * sn);
        double bbh = Math.abs(h * cs + w * sn);

        */
    }

    public void setResults(String text) {
        refreshing = true;
        textView.setText(text);
        // calculate text dimensions
        textView.measure(0, 0);
        textWidth = textView.getMeasuredWidth();
        textHeight = textView.getMeasuredHeight();
        // prevent gravity propagation
        textView.setGravity(Gravity.CENTER | Gravity.CENTER);

        // center text inside container
        //setGravity(Gravity.CENTER | Gravity.CENTER);
        // calculate diagonal length as max bounds for container
        double w = textWidth;
        double h = textHeight;
        double d = Math.sqrt(w * w + h * h);
        diag = (int) Math.round(d);
        diag += MARGIN;

        // setup container
        setMeasuredDimension(diag, diag);
        setPivotX(diag/2);
        setPivotY(diag/2);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            diag, diag, Gravity.RIGHT|Gravity.CENTER
        );
        setLayoutParams(params);
        //setVisibility(View.VISIBLE);
        refreshing = false;
    }
}
