package pollob.voltify;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class UIUpdate {
    private static final String TAG = "UIUpdate";
    private static final String PREFS_NAME = "MemoryPrefs";

    private UIUpdateListener listener;
    private SharedPreferences memoryPrefs;

    // Current values
    private double outputVolt = 0.0;
    private double outputAmp = 0.0;
    private double outputEnergy = 0.0;
    private String ccCvStatus = "CV";
    private double setVolt = 0.0;
    private double setAmp = 0.0;
    private boolean isOutputOn = false;

    // Graph data
    private List<Entry> voltEntries = new ArrayList<>();
    private List<Entry> ampEntries = new ArrayList<>();
    private int dataCount = 0;
    private static final int MAX_DATA_POINTS = 100;

    public interface UIUpdateListener {
        void updateOutputValues(double outputVolt, double outputAmp, double outputEnergy, String ccCvStatus);
        void updateSetValues(double setVolt, double setAmp);
        void updateSlidersFromReceivedData(double recalledSetVolt, double recalledSetAmp);

        void updateOutputStatus(boolean isOutputOn);
        void updateGraphs(double volt, double amp);
    }

    public UIUpdate(android.content.Context context, UIUpdateListener listener) {
        this.listener = listener;
        this.memoryPrefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
    }

    public void processReceivedData(byte[] data) {
        Log.d(TAG, "Processing received data: " + data.length + " bytes");

        if (data.length >= 12) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                // Parse the data according to ESP32 format
                outputVolt = buffer.getShort() / 1000.0;
                outputAmp = buffer.getShort() / 1000.0;
                outputEnergy = buffer.getShort() / 100.0;
                int ccCv = buffer.getShort();
                setVolt = buffer.getShort() / 1000.0;
                setAmp = buffer.getShort() / 1000.0;

                ccCvStatus = (ccCv == 0) ? "CV" : "CC";

                Log.d(TAG, String.format("Parsed - OutV: %.3fV, OutA: %.3fA, Energy: %.2fWh, Mode: %s, SetV: %.2fV, SetA: %.2fA",
                        outputVolt, outputAmp, outputEnergy, ccCvStatus, setVolt, setAmp));

                // Update UI through listener
                if (listener != null) {
                    listener.updateOutputValues(outputVolt, outputAmp, outputEnergy, ccCvStatus);
                    listener.updateSetValues(setVolt, setAmp);
                    listener.updateGraphs(outputVolt, outputAmp);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error parsing received data", e);
            }
        } else {
            Log.e(TAG, "Invalid data length: " + data.length + " (expected 12)");

            // Print raw data for debugging
            StringBuilder hex = new StringBuilder();
            for (byte b : data) {
                hex.append(String.format("%02X ", b));
            }
            Log.d(TAG, "Raw data: " + hex.toString());
        }
    }

    public void storeMemory(int memoryIndex, double setVolt, double setAmp) {
        SharedPreferences.Editor editor = memoryPrefs.edit();
        String key = "memory_" + memoryIndex;
        String value = setVolt + "," + setAmp;
        editor.putString(key, value);
        editor.apply();
        Log.d(TAG, "Stored memory " + memoryIndex + ": " + value);
    }

    public void recallMemoryForSliders(int memoryIndex) {
        String key = "memory_" + memoryIndex;
        String value = memoryPrefs.getString(key, "");
        if (!value.isEmpty()) {
            String[] parts = value.split(",");
            if (parts.length == 2) {
                try {
                    double recalledSetVolt = Double.parseDouble(parts[0]);
                    double recalledSetAmp = Double.parseDouble(parts[1]);

                    // Update current values
                    //this.setVolt = recalledSetVolt;
                    //this.setAmp = recalledSetAmp;

                    // Notify listener to update sliders
                    if (listener != null) {
                        listener.updateSlidersFromReceivedData(recalledSetVolt, recalledSetAmp);
                    }

                    Log.d(TAG, "Recalled memory " + memoryIndex + ": " + value);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing memory data", e);
                }
            }
        } else {
            Log.d(TAG, "No data stored in memory " + memoryIndex);
        }
    }

    public void updateCharts(LineChart voltChart, LineChart ampChart, double volt, double amp) {
        // Add new data points
        voltEntries.add(new Entry(dataCount, (float) volt));
        ampEntries.add(new Entry(dataCount, (float) amp));
        dataCount++;

        // Remove old data points if exceeding limit
        if (voltEntries.size() > MAX_DATA_POINTS) {
            voltEntries.remove(0);
            ampEntries.remove(0);
        }

        // Update volt chart
        updateChart(voltChart, voltEntries, "Voltage (V)", Color.BLUE);

        // Update amp chart
        updateChart(ampChart, ampEntries, "Current (A)", Color.RED);
    }

    private void updateChart(LineChart chart, List<Entry> entries, String label, int color) {
        if (entries.isEmpty()) return;

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(true);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // Configure chart appearance
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setDrawGridLines(true);

        chart.getAxisRight().setEnabled(false);
        chart.invalidate();
    }

    // Getters for current values
    public double getOutputVolt() { return outputVolt; }
    public double getOutputAmp() { return outputAmp; }
    public double getOutputEnergy() { return outputEnergy; }
    public String getCcCvStatus() { return ccCvStatus; }
    public double getSetVolt() { return setVolt; }
    public double getSetAmp() { return setAmp; }
    public boolean isOutputOn() { return isOutputOn; }
}