package com.example.meuafinador;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;

    // Elementos da UI
    private TextView textViewNote;
    private TextView textViewFrequency;
    private TextView textViewCents;
    private TextView textViewTuningName;
    private Spinner spinnerTuningType;
    private Button buttonToggleListen;
    private View viewTuningNeedle;
    private View viewTuningIndicatorTrack;

    // Configurações de áudio
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;

    // --- Definições para Afinações ---
    private static class TuningNote {
        String noteName;
        double targetFrequency;
        TuningNote(String noteName, double targetFrequency) {
            this.noteName = noteName;
            this.targetFrequency = targetFrequency;
        }
        public String getNoteName() { return noteName; }
        public double getTargetFrequency() { return targetFrequency; }
    }

    private static class TuningPattern {
        String name;
        List<TuningNote> notes;
        TuningPattern(String name, List<TuningNote> notes) {
            this.name = name;
            this.notes = notes;
        }
        public String getName() { return name; }
        public List<TuningNote> getNotes() { return notes; }
        @NonNull @Override public String toString() { return name; }
    }

    private List<TuningPattern> tuningPatterns;
    private TuningPattern currentTuning;
    private ArrayAdapter<TuningPattern> spinnerAdapter;
    private List<TuningNote> chromaticNotes; // Lista para o modo cromático

    // Frequências base
    private static final double FREQ_C2  = 65.41;
    private static final double FREQ_CS2 = 69.30;
    private static final double FREQ_D2  = 73.42;
    private static final double FREQ_DS2 = 77.78;
    private static final double FREQ_E2  = 82.41;
    private static final double FREQ_F2  = 87.31;
    private static final double FREQ_FS2 = 92.50;
    private static final double FREQ_G2  = 98.00;
    private static final double FREQ_GS2 = 103.83;
    private static final double FREQ_A2  = 110.00;
    private static final double FREQ_AS2 = 116.54;
    private static final double FREQ_B2  = 123.47;
    private static final double FREQ_C3  = 130.81;
    private static final double FREQ_CS3 = 138.59;
    private static final double FREQ_D3  = 146.83;
    private static final double FREQ_DS3 = 155.56;
    private static final double FREQ_E3  = 164.81;
    private static final double FREQ_F3  = 174.61;
    private static final double FREQ_FS3 = 185.00;
    private static final double FREQ_G3  = 196.00;
    private static final double FREQ_GS3 = 207.65;
    private static final double FREQ_A3  = 220.00;
    private static final double FREQ_AS3 = 233.08;
    private static final double FREQ_B3  = 246.94;
    private static final double FREQ_C4  = 261.63;
    private static final double FREQ_CS4 = 277.18;
    private static final double FREQ_D4  = 293.66;
    private static final double FREQ_DS4 = 311.13;
    private static final double FREQ_E4  = 329.63;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        textViewNote = findViewById(R.id.textViewNote);
        textViewFrequency = findViewById(R.id.textViewFrequency);
        textViewCents = findViewById(R.id.textViewCents);
        textViewTuningName = findViewById(R.id.textViewTuningName);
        spinnerTuningType = findViewById(R.id.spinnerTuningType);
        buttonToggleListen = findViewById(R.id.buttonToggleListen);
        viewTuningNeedle = findViewById(R.id.viewTuningNeedle);
        viewTuningIndicatorTrack = findViewById(R.id.viewTuningIndicatorTrack);

        initializeChromaticScale();
        initializeTunings();

        spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_custom, tuningPatterns);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_custom);
        spinnerTuningType.setAdapter(spinnerAdapter);

        if (!tuningPatterns.isEmpty()) {
            currentTuning = tuningPatterns.get(0);
            spinnerTuningType.setSelection(0);
            updateTuningDisplay();
        }

        spinnerTuningType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < tuningPatterns.size()) {
                    currentTuning = tuningPatterns.get(position);
                    Log.d(TAG, "Spinner: Afinação selecionada: " + currentTuning.getName());
                    updateTuningDisplay();
                    if (isRecording) {
                        runOnUiThread(() -> {
                            textViewNote.setText("--");
                            textViewFrequency.setText("Frequência: -- Hz");
                            textViewCents.setText("Cents: --");
                            updateTuningNeedlePosition(0, true, false);
                        });
                    }
                } else {
                    Log.w(TAG, "Spinner: Posição selecionada inválida: " + position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "Spinner: Nada selecionado.");
            }
        });

        buttonToggleListen.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                if (permissionToRecordAccepted) {
                    startRecording();
                } else {
                    Toast.makeText(MainActivity.this, "Permissão de áudio necessária.", Toast.LENGTH_SHORT).show();
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
                }
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Tamanho do buffer inválido: " + minBufferSize);
            Toast.makeText(this, "Não foi possível inicializar o buffer de áudio.", Toast.LENGTH_SHORT).show();
            buttonToggleListen.setEnabled(false);
            bufferSize = -1;
        } else {
            bufferSize = Math.max(minBufferSize, 2048 * 2);
            if (bufferSize > 4096 * 2) bufferSize = 4096 * 2;
            Log.d(TAG, "Min buffer size: " + minBufferSize + ", Selected buffer size: " + bufferSize + " bytes (" + (bufferSize/2) + " shorts)");
        }

        updateButtonUI();
        updateTuningNeedlePosition(0, true, false);
    }

    private void initializeChromaticScale() {
        chromaticNotes = new ArrayList<>();
        chromaticNotes.add(new TuningNote("C2", FREQ_C2));
        chromaticNotes.add(new TuningNote("C#2/Db2", FREQ_CS2));
        chromaticNotes.add(new TuningNote("D2", FREQ_D2));
        chromaticNotes.add(new TuningNote("D#2/Eb2", FREQ_DS2));
        chromaticNotes.add(new TuningNote("E2", FREQ_E2));
        chromaticNotes.add(new TuningNote("F2", FREQ_F2));
        chromaticNotes.add(new TuningNote("F#2/Gb2", FREQ_FS2));
        chromaticNotes.add(new TuningNote("G2", FREQ_G2));
        chromaticNotes.add(new TuningNote("G#2/Ab2", FREQ_GS2));
        chromaticNotes.add(new TuningNote("A2", FREQ_A2));
        chromaticNotes.add(new TuningNote("A#2/Bb2", FREQ_AS2));
        chromaticNotes.add(new TuningNote("B2", FREQ_B2));
        chromaticNotes.add(new TuningNote("C3", FREQ_C3));
        chromaticNotes.add(new TuningNote("C#3/Db3", FREQ_CS3));
        chromaticNotes.add(new TuningNote("D3", FREQ_D3));
        chromaticNotes.add(new TuningNote("D#3/Eb3", FREQ_DS3));
        chromaticNotes.add(new TuningNote("E3", FREQ_E3));
        chromaticNotes.add(new TuningNote("F3", FREQ_F3));
        chromaticNotes.add(new TuningNote("F#3/Gb3", FREQ_FS3));
        chromaticNotes.add(new TuningNote("G3", FREQ_G3));
        chromaticNotes.add(new TuningNote("G#3/Ab3", FREQ_GS3));
        chromaticNotes.add(new TuningNote("A3", FREQ_A3));
        chromaticNotes.add(new TuningNote("A#3/Bb3", FREQ_AS3));
        chromaticNotes.add(new TuningNote("B3", FREQ_B3));
        chromaticNotes.add(new TuningNote("C4", FREQ_C4));
        chromaticNotes.add(new TuningNote("C#4/Db4", FREQ_CS4));
        chromaticNotes.add(new TuningNote("D4", FREQ_D4));
        chromaticNotes.add(new TuningNote("D#4/Eb4", FREQ_DS4));
        chromaticNotes.add(new TuningNote("E4", FREQ_E4));
    }

    private void initializeTunings() {
        tuningPatterns = new ArrayList<>();
        tuningPatterns.add(new TuningPattern("Modo Livre (Cromático)", chromaticNotes));
        tuningPatterns.add(new TuningPattern("E Standard", Arrays.asList(
                new TuningNote("E2", FREQ_E2), new TuningNote("A2", FREQ_A2),
                new TuningNote("D3", FREQ_D3), new TuningNote("G3", FREQ_G3),
                new TuningNote("B3", FREQ_B3), new TuningNote("E4", FREQ_E4)
        )));
        tuningPatterns.add(new TuningPattern("Eb Standard", Arrays.asList(
                new TuningNote("D#2/Eb2", FREQ_DS2), new TuningNote("G#2/Ab2", FREQ_GS2),
                new TuningNote("C#3/Db3", FREQ_CS3), new TuningNote("F#3/Gb3", FREQ_FS3),
                new TuningNote("A#3/Bb3", FREQ_AS3), new TuningNote("D#4/Eb4", FREQ_DS4)
        )));
        tuningPatterns.add(new TuningPattern("Drop D", Arrays.asList(
                new TuningNote("D2", FREQ_D2), new TuningNote("A2", FREQ_A2),
                new TuningNote("D3", FREQ_D3), new TuningNote("G3", FREQ_G3),
                new TuningNote("B3", FREQ_B3), new TuningNote("E4", FREQ_E4)
        )));
        tuningPatterns.add(new TuningPattern("Drop C", Arrays.asList(
                new TuningNote("C2", FREQ_C2), new TuningNote("G2", FREQ_G2),
                new TuningNote("C3", FREQ_C3), new TuningNote("F3", FREQ_F3),
                new TuningNote("A3", FREQ_A3), new TuningNote("D4", FREQ_D4)
        )));
        tuningPatterns.add(new TuningPattern("D Standard", Arrays.asList(
                new TuningNote("D2", FREQ_D2), new TuningNote("G2", FREQ_G2),
                new TuningNote("C3", FREQ_C3), new TuningNote("F3", FREQ_F3),
                new TuningNote("A3", FREQ_A3), new TuningNote("D4", FREQ_D4)
        )));
    }

    private void updateTuningDisplay() {
        if (currentTuning != null && textViewTuningName != null) {
            if (currentTuning.getName().contains("Modo Livre")) {
                textViewTuningName.setText(currentTuning.getName());
            } else {
                StringBuilder tuningText = new StringBuilder(currentTuning.getName() + ": ");
                for (int i = 0; i < currentTuning.getNotes().size(); i++) {
                    tuningText.append(currentTuning.getNotes().get(i).getNoteName().split("/")[0]);
                    if (i < currentTuning.getNotes().size() - 1) {
                        tuningText.append(" ");
                    }
                }
                textViewTuningName.setText(tuningText.toString());
            }
        } else if (textViewTuningName != null) {
            textViewTuningName.setText("Nenhuma afinação selecionada");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionToRecordAccepted = true;
                Log.d(TAG, "Permissão de áudio concedida.");
                if (bufferSize <= 0) {
                    int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                    if (minBufferSize > 0) {
                        bufferSize = Math.max(minBufferSize, 2048 * 2);
                        if (bufferSize > 4096 * 2) bufferSize = 4096 * 2;
                    } else {
                        Log.e(TAG, "Falha ao obter bufferSize mesmo após permissão: " + minBufferSize);
                        Toast.makeText(this, "Erro crítico com o buffer de áudio.", Toast.LENGTH_SHORT).show();
                        buttonToggleListen.setEnabled(false);
                    }
                }
            } else {
                permissionToRecordAccepted = false;
                Log.w(TAG, "Permissão de áudio negada.");
                Toast.makeText(this, "Permissão de áudio negada.", Toast.LENGTH_LONG).show();
            }
            updateButtonUI();
        }
    }

    private void startRecording() {
        if (!permissionToRecordAccepted || bufferSize <= 0) {
            Log.w(TAG, "Tentativa de iniciar gravação sem permissão ou buffer inválido.");
            Toast.makeText(this, "Permissão ou buffer de áudio inválido.", Toast.LENGTH_SHORT).show();
            if (!permissionToRecordAccepted) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
            }
            return;
        }
        if (isRecording) return;

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord não pôde ser inicializado. Estado: " + audioRecord.getState());
            Toast.makeText(this, "Não foi possível inicializar o gravador.", Toast.LENGTH_SHORT).show();
            if (audioRecord != null) audioRecord.release();
            audioRecord = null;
            return;
        }
        try {
            audioRecord.startRecording();
            isRecording = true;
            Log.d(TAG, "Gravação iniciada com buffer de " + bufferSize + " bytes.");
            recordingThread = new Thread(this::processAudio, "AudioProcessingThread");
            recordingThread.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioRecord.startRecording() falhou", e);
            Toast.makeText(this, "Falha ao iniciar a gravação.", Toast.LENGTH_SHORT).show();
            isRecording = false;
            if (audioRecord != null) audioRecord.release();
            audioRecord = null;
        }
        updateButtonUI();
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        if (recordingThread != null) {
            try {
                recordingThread.join(500);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupção ao aguardar a thread de gravação.", e);
                Thread.currentThread().interrupt();
            }
            recordingThread = null;
        }
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "AudioRecord.stop() falhou", e);
                }
            }
            audioRecord.release();
            audioRecord = null;
            Log.d(TAG, "AudioRecord liberado.");
        }
        Log.d(TAG, "Gravação parada.");
        updateButtonUI();
        runOnUiThread(() -> {
            textViewNote.setText("--");
            textViewFrequency.setText("Frequência: -- Hz");
            textViewCents.setText("Cents: --");
            updateTuningNeedlePosition(0, true, false);
        });
    }

    private void updateButtonUI() {
        runOnUiThread(() -> {
            if (isRecording) {
                buttonToggleListen.setText("Parar Afinação");
            } else {
                buttonToggleListen.setText("Iniciar Afinação");
            }
            buttonToggleListen.setEnabled(permissionToRecordAccepted && bufferSize > 0);
        });
    }

    private void processAudio() {
        short[] audioData = new short[bufferSize / 2];

        while (isRecording && audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            int shortsRead = audioRecord.read(audioData, 0, audioData.length);

            if (shortsRead > 0) {
                double detectedFrequency = calculateDominantFrequency(audioData, shortsRead);
                String actualNote = "--";
                String targetNote = "--";
                double centsOff = 0.0;
                boolean isNoteInTune = false;

                if (detectedFrequency > 0) {
                    // 1. Sempre encontre a nota cromática real mais próxima
                    actualNote = findClosestNote(detectedFrequency, chromaticNotes);

                    // 2. Encontre a nota alvo da afinação atual (se não for modo livre)
                    if (!currentTuning.getName().contains("Modo Livre")) {
                        targetNote = findClosestNote(detectedFrequency, currentTuning.getNotes());
                    } else {
                        targetNote = actualNote; // No modo livre, a nota alvo é a própria nota tocada
                    }

                    // 3. Calcule os cents de desvio em relação à nota alvo
                    centsOff = getCentsOff(detectedFrequency, targetNote);

                    // 4. Verifique se a nota tocada é a mesma que a nota alvo
                    if (!actualNote.equals("--") && !targetNote.equals("--")) {
                        // Comparando a parte principal do nome da nota (ex: "E2" de "E2" e "E2")
                        isNoteInTune = actualNote.split("/")[0].equals(targetNote.split("/")[0]);
                    }
                }

                final String finalActualNote = actualNote;
                final double finalCentsOff = centsOff;
                final double finalDetectedFrequency = detectedFrequency;
                final boolean finalIsNoteInTune = isNoteInTune;

                runOnUiThread(() -> {
                    if (finalDetectedFrequency > 0 && !finalActualNote.equals("--")) {
                        textViewFrequency.setText(String.format("Frequência: %.2f Hz", finalDetectedFrequency));
                        textViewNote.setText(finalActualNote.split("/")[0]); // Sempre mostra a nota real
                        textViewCents.setText(String.format("Cents: %.1f", finalCentsOff));
                    } else {
                        textViewFrequency.setText("Frequência: -- Hz");
                        textViewNote.setText("--");
                        textViewCents.setText("Cents: --");
                    }
                    updateTuningNeedlePosition(finalCentsOff, finalDetectedFrequency <= 0, finalIsNoteInTune);
                });

            } else if (shortsRead < 0) {
                Log.e(TAG, "Erro ao ler áudio. Código: " + shortsRead);
            }
        }
        Log.d(TAG, "Thread de processamento de áudio terminando.");
    }

    private final float YIN_THRESHOLD = 0.15f;

    private float[] shortToFloatArray(short[] pcmData, int readSize) {
        float[] floatData = new float[readSize];
        for (int i = 0; i < readSize; i++) {
            floatData[i] = pcmData[i] / 32768.0f;
        }
        return floatData;
    }

    private float yinProcess(float[] audioBuffer, int sampleRate) {
        int yinBufferSize = audioBuffer.length / 2;
        if (yinBufferSize == 0) return -1.0f;
        float[] diffFunction = new float[yinBufferSize];

        for (int tau = 0; tau < yinBufferSize; tau++) {
            diffFunction[tau] = 0;
            for (int j = 0; j < yinBufferSize; j++) {
                if (j + tau < audioBuffer.length) {
                    float delta = audioBuffer[j] - audioBuffer[j + tau];
                    diffFunction[tau] += delta * delta;
                }
            }
        }

        float runningSum = 0;
        diffFunction[0] = 1;
        for (int tau = 1; tau < yinBufferSize; tau++) {
            runningSum += diffFunction[tau];
            if (runningSum == 0) {
                diffFunction[tau] = 1;
            } else {
                diffFunction[tau] *= tau / runningSum;
            }
        }

        int periodTau = -1;
        int minLag = sampleRate / 1500;
        if (minLag < 1) minLag = 1;
        int maxLag = sampleRate / 50;
        if (maxLag >= yinBufferSize) maxLag = yinBufferSize - 1;

        for (int tau = minLag; tau <= maxLag; tau++) {
            if (tau == 0) continue;
            if (diffFunction[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= maxLag && diffFunction[tau + 1] < diffFunction[tau]) {
                    tau++;
                }
                periodTau = tau;
                break;
            }
        }

        if (periodTau != -1) {
            if (periodTau > 0 && periodTau < yinBufferSize - 1) {
                float s0 = diffFunction[periodTau - 1];
                float s1 = diffFunction[periodTau];
                float s2 = diffFunction[periodTau + 1];
                float divisor = 2 * s1 - s0 - s2;
                if (Math.abs(divisor) > 1e-6) {
                    float betterTau = periodTau + (s0 - s2) / (2 * divisor);
                    if (!Float.isNaN(betterTau) && !Float.isInfinite(betterTau) && betterTau > 0 && betterTau < yinBufferSize) {
                        return (float) sampleRate / betterTau;
                    }
                }
            }
            return (float) sampleRate / periodTau;
        }
        return -1.0f;
    }

    private double calculateDominantFrequency(short[] audioData, int shortsRead) {
        if (shortsRead <= 0) {
            return 0.0;
        }
        float[] floatAudioBuffer = shortToFloatArray(audioData, shortsRead);
        float detectedPitchInHz = yinProcess(floatAudioBuffer, SAMPLE_RATE);
        return (detectedPitchInHz > 0) ? detectedPitchInHz : 0.0;
    }

    // MÉTODO MODIFICADO: renomeado para maior clareza, agora é um método de busca genérico.
    private String findClosestNote(double frequency, List<TuningNote> notesToSearch) {
        if (frequency <= 0 || notesToSearch == null || notesToSearch.isEmpty()) {
            return "--";
        }
        String bestMatchNote = "--";
        double minDifference = Double.MAX_VALUE;

        for (TuningNote note : notesToSearch) {
            double targetFreq = note.getTargetFrequency();
            double centsDifference = Math.abs(1200 * (Math.log(frequency / targetFreq) / Math.log(2)));

            if (centsDifference < minDifference) {
                minDifference = centsDifference;
                bestMatchNote = note.getNoteName();
            }
        }

        if (minDifference > 50) { // Tolerância de meio semitom
            return "--";
        }
        return bestMatchNote;
    }

    // MÉTODO MODIFICADO: Calcula os cents em relação a uma NOTA ALVO específica.
    private double getCentsOff(double detectedFrequency, String targetNoteName) {
        if (detectedFrequency <= 0 || targetNoteName == null || targetNoteName.equals("--")) {
            return 0.0;
        }
        double targetFrequency = 0.0;

        // Procura a frequência da nota alvo na lista cromática (que contém todas as notas)
        for (TuningNote note : chromaticNotes) {
            if (note.getNoteName().equals(targetNoteName)) {
                targetFrequency = note.getTargetFrequency();
                break;
            }
        }
        if (targetFrequency == 0.0) return 0.0;

        return 1200 * (Math.log(detectedFrequency / targetFrequency) / Math.log(2));
    }

    // MÉTODO MODIFICADO: Recebe um booleano para saber se a cor deve ser verde.
    private void updateTuningNeedlePosition(double centsOff, boolean reset, boolean isNoteInTune) {
        if (viewTuningNeedle == null || viewTuningIndicatorTrack == null) return;

        float trackWidth = viewTuningIndicatorTrack.getWidth();
        if (trackWidth == 0 && !reset) return;

        float maxCentsDisplay = 50.0f;
        float normalizedPosition = 0f;

        if (!reset) {
            normalizedPosition = (float) Math.max(-1.0, Math.min(1.0, centsOff / maxCentsDisplay));
        }

        float translationX = normalizedPosition * (trackWidth / 2.0f);

        viewTuningNeedle.animate()
                .translationX(translationX)
                .setDuration(reset ? 0 : 100)
                .start();

        int color = ContextCompat.getColor(this, R.color.default_text_color);
        if (!reset) {
            // A nota SÓ fica verde se a nota tocada for a nota alvo E estiver afinada em cents.
            if (isNoteInTune && Math.abs(centsOff) < 5) {
                color = ContextCompat.getColor(this, R.color.tuned_green);
            } else if (Math.abs(centsOff) < 20) {
                color = ContextCompat.getColor(this, R.color.near_yellow);
            } else {
                color = ContextCompat.getColor(this, R.color.untuned_red);
            }
        }
        textViewNote.setTextColor(color);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() chamado.");
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() chamado.");
        updateButtonUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() chamado.");
        if (isRecording) {
            stopRecording();
        } else if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }
}
