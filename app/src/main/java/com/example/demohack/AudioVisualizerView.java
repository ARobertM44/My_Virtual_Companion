package com.example.demohack;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.content.*;

import java.util.ArrayList;
import java.util.List;

public class AudioVisualizerView extends View {
    private Paint paint = new Paint();
    private float amp = 0;
    private boolean isActive = false;
    private int maxLines = 100;
    private List<Float> amplitudes = new ArrayList<>();

    public AudioVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(20f);
    }

    public void updateAmp(float amp) {
        this.amp = amp;
        if (amp != 0)
            this.isActive = true;

        if (amplitudes.size() >= maxLines)
            amplitudes.remove(0);

        amplitudes.add(amp);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isActive) return;

        float middle = getHeight() / 2;
        float maxHeight = getHeight() / 2;
        float gap = getWidth() / maxLines;

        for (int i = 0; i < amplitudes.size(); i++) {
            float x = i * gap;
            float amp = (amplitudes.get(i) / 32768) * maxHeight * 2; // În loc de *1, pune *2 sau *3
            canvas.drawLine(x, middle - amp, x, middle + amp, paint);
        }
    }

}
