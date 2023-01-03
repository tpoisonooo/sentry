package com.p4f.esp32camai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class BBoxView extends View {
    private BBox mdata;

    public BBoxView(Context context) {
        super(context);
    }

    public BBoxView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BBoxView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BBoxView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setMdata(BBox mdata) {
        this.mdata = mdata;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mdata != null) {
//            RectF rectF = new RectF(mdata.x, mdata.y, mdata.x + mdata.width, mdata.y + mdata.height);
            Paint p = new Paint();
            p.setColor(Color.YELLOW);
            canvas.drawLine(mdata.x, mdata.y, mdata.x + mdata.width, mdata.y, p);
            canvas.drawLine(mdata.x + mdata.width, mdata.y, mdata.x + mdata.width, mdata.y + mdata.height, p);
            canvas.drawLine(mdata.x + mdata.width, mdata.y + mdata.height, mdata.x, mdata.y+mdata.height, p);
            canvas.drawLine(mdata.x, mdata.y+mdata.height, mdata.x, mdata.y, p);
        }
    }
}
