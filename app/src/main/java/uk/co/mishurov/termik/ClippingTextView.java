package uk.co.mishurov.termik;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;
import android.util.Log;


public class ClippingTextView extends TextView {
    private static final String TAG = "Termik";

    public int origWidth = 0;
    public int origHeight = 0;
    public int diag = 0;

    public ClippingTextView(Context context) {
      super(context);
    }

    public ClippingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClippingTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public void setClippingDims() {
        measure(0, 0);
        origWidth = getMeasuredWidth();
        origHeight = getMeasuredHeight();
    }
    
    /*
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        origWidth = getMeasuredWidth();
        origHeight = getMeasuredHeight();
        double w = getMeasuredWidth();
        double h = getMeasuredHeight();
        double d = Math.sqrt(w * w + h * h);
        int diag = (int) Math.round(d);
        setPivotX(diag/2);
        setPivotY(diag/2);
        setMeasuredDimension(diag, diag);
        Log.i(TAG, "measured");
    }

    @Override
    public void onTextChanged (CharSequence text, 
                int start, 
                int lengthBefore, 
                int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        Log.i(TAG, "text changed w:" + getMeasuredWidth());
    }
    */
}
