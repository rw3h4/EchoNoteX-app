package org.rw3h4.echonotex.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.rw3h4.echonotex.R;
import java.util.ArrayList;
import java.util.List;

public class WaveformView extends View {

    private final Paint barPaint = new Paint();
    private final List<Float> amplitudes = new ArrayList<>();
    private final float barWidth;
    private final float barGap;

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false); // Ensure onDraw is called

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.WaveformView,
                0, 0);

        try {
            int defaultColor = ContextCompat.getColor(context, R.color.dark_blue);
            barPaint.setColor(typedArray.getColor(R.styleable.WaveformView_waveBarColor, defaultColor));
            barWidth = typedArray.getDimension(R.styleable.WaveformView_waveBarWidth, 9f);
            barGap = typedArray.getDimension(R.styleable.WaveformView_waveBarGap, 6f);
        } finally {
            typedArray.recycle();
        }
    }

    public void addAmplitude(float amplitude) {
        float normalizedHeight = Math.min(amplitude / 32767f, 1.0f); // Use standard max amplitude
        amplitudes.add(normalizedHeight);

        int maxBars = (int) (getWidth() / (barWidth + barGap));
        if (amplitudes.size() > maxBars && maxBars > 0) {
            amplitudes.subList(0, amplitudes.size() - maxBars).clear();
        }

        invalidate();
    }

    public void clearWaveform() {
        amplitudes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (amplitudes.isEmpty()) {
            return;
        }

        float viewHeight = getHeight();
        float viewWidth = getWidth();
        float currentX = viewWidth;
        float cornerRadius = barWidth / 2; // Make the corners perfectly round

        for (int i = amplitudes.size() - 1; i >= 0; i--) {
            float amplitude = amplitudes.get(i);
            float barHeight = Math.max(barWidth, amplitude * viewHeight); // Min height is bar width
            float top = (viewHeight - barHeight) / 2;
            float bottom = top + barHeight;

            RectF barRect = new RectF(currentX - barWidth, top, currentX, bottom);
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);

            currentX -= (barWidth + barGap);
            if (currentX < 0) {
                break;
            }
        }
    }
}