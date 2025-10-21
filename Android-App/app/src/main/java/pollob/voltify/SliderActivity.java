package pollob.voltify;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SliderActivity extends AppCompatActivity {

    private SeekBar setVoltSlider, setAmpSlider, maxVoltSlider, maxAmpSlider;
    private TextView setVoltValue, setAmpValue, maxVoltValue, maxAmpValue;

    private double currentSetVolt = 0.0;
    private double currentSetAmp = 0.0;
    private double currentMaxVolt = 0.0;
    private double currentMaxAmp = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupSliders();
        setDefaultValues();
    }

    private void initializeViews() {
        setVoltSlider = findViewById(R.id.setVoltSlider);
        setAmpSlider = findViewById(R.id.setAmpSlider);
        maxVoltSlider = findViewById(R.id.maxVoltSlider);
        maxAmpSlider = findViewById(R.id.maxAmpSlider);

        setVoltValue = findViewById(R.id.setVoltValue);
        setAmpValue = findViewById(R.id.setAmpValue);
        maxVoltValue = findViewById(R.id.maxVoltValue);
        maxAmpValue = findViewById(R.id.maxAmpValue);
    }

    private void setupSliders() {
        // Set Voltage Slider (2.0V - 30.0V)
        setVoltSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSetVolt = 2.0 + (progress / 100.0); // 0.01V steps
                setVoltValue.setText(String.format("%.2f V", currentSetVolt));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set Current Slider (0.5A - 15.0A)
        setAmpSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSetAmp = 0.5 + (progress / 100.0); // 0.01A steps
                setAmpValue.setText(String.format("%.2f A", currentSetAmp));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Max Voltage Slider (3.0V - 30.0V)
        maxVoltSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentMaxVolt = 3.0 + (progress / 100.0); // 0.01V steps
                maxVoltValue.setText(String.format("%.2f V", currentMaxVolt));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Max Current Slider (1.0A - 15.0A)
        maxAmpSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentMaxAmp = 1.0 + (progress / 100.0); // 0.01A steps
                maxAmpValue.setText(String.format("%.2f A", currentMaxAmp));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setDefaultValues() {
        // Set default values
        setVoltSlider.setProgress(500);  // 7.0V
        setAmpSlider.setProgress(500);   // 5.5A
        maxVoltSlider.setProgress(1200); // 15.0V
        maxAmpSlider.setProgress(700);   // 8.0A
    }

    public double getCurrentSetVolt() { return currentSetVolt; }
    public double getCurrentSetAmp() { return currentSetAmp; }
    public double getCurrentMaxVolt() { return currentMaxVolt; }
    public double getCurrentMaxAmp() { return currentMaxAmp; }

    public void setSliderValues(double setVolt, double setAmp, double maxVolt, double maxAmp) {
        currentSetVolt = setVolt;
        currentSetAmp = setAmp;
        currentMaxVolt = maxVolt;
        currentMaxAmp = maxAmp;

        // Update sliders
        setVoltSlider.setProgress((int)((setVolt - 2.0) * 100));
        setAmpSlider.setProgress((int)((setAmp - 0.5) * 100));
        maxVoltSlider.setProgress((int)((maxVolt - 3.0) * 100));
        maxAmpSlider.setProgress((int)((maxAmp - 1.0) * 100));

        // Update display values
        setVoltValue.setText(String.format("%.2f V", setVolt));
        setAmpValue.setText(String.format("%.2f A", setAmp));
        maxVoltValue.setText(String.format("%.2f V", maxVolt));
        maxAmpValue.setText(String.format("%.2f A", maxAmp));
    }
}