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
import androidx.core.content.ContextCompat; // Adicionado para cores
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
    private View viewTuningNeedle; // Usaremos para a agulha
    private View viewTuningIndicatorTrack; // Usaremos para a base da agulha

    // Configurações de áudio
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize; // Tamanho do buffer do AudioRecord em bytes

    // --- Definições para Afinações (como antes) ---
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

    // Frequências base (como antes)
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
        // Supondo que você tenha um view para o "trilho" da agulha no seu XML:
        viewTuningIndicatorTrack = findViewById(R.id.viewTuningIndicatorTrack);


        initializeTunings(); // Inicializa as afinações

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tuningPatterns);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
                            updateTuningNeedlePosition(0, true); // Resetar agulha
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

        // bufferSize é em bytes. Para shorts, o número de amostras será bufferSize / 2
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Tamanho do buffer inválido: " + minBufferSize);
            Toast.makeText(this, "Não foi possível inicializar o buffer de áudio.", Toast.LENGTH_SHORT).show();
            buttonToggleListen.setEnabled(false);
            bufferSize = -1; // Indica erro
        } else {
            // Usar um buffer um pouco maior que o mínimo pode ser bom,
            // e idealmente uma potência de 2 para alguns algoritmos de FFT,
            // mas para YIN, o minBufferSize * 2 ou * 4 é geralmente OK.
            // Para YIN, um buffer de 1024 a 2048 amostras (2048 a 4096 bytes) é comum.
            bufferSize = Math.max(minBufferSize, 2048 * 2); // Ex: 2048 amostras (shorts) = 4096 bytes
            if (bufferSize > 4096 * 2) bufferSize = 4096 * 2; // Limite superior
            Log.d(TAG, "Min buffer size: " + minBufferSize + ", Selected buffer size: " + bufferSize + " bytes (" + (bufferSize/2) + " shorts)");
        }

        updateButtonUI();
        updateTuningNeedlePosition(0, true); // Inicializa a posição da agulha
    }

    private void initializeTunings() {
        tuningPatterns = new ArrayList<>();
        // E Standard
        tuningPatterns.add(new TuningPattern("E Standard", Arrays.asList(
                new TuningNote("E2", FREQ_E2), new TuningNote("A2", FREQ_A2),
                new TuningNote("D3", FREQ_D3), new TuningNote("G3", FREQ_G3),
                new TuningNote("B3", FREQ_B3), new TuningNote("E4", FREQ_E4)
        )));
        // Eb Standard
        tuningPatterns.add(new TuningPattern("Eb Standard", Arrays.asList(
                new TuningNote("D#2/Eb2", FREQ_DS2), new TuningNote("G#2/Ab2", FREQ_GS2),
                new TuningNote("C#3/Db3", FREQ_CS3), new TuningNote("F#3/Gb3", FREQ_FS3),
                new TuningNote("A#3/Bb3", FREQ_AS3), new TuningNote("D#4/Eb4", FREQ_DS4)
        )));
        // Drop D
        tuningPatterns.add(new TuningPattern("Drop D", Arrays.asList(
                new TuningNote("D2", FREQ_D2), new TuningNote("A2", FREQ_A2),
                new TuningNote("D3", FREQ_D3), new TuningNote("G3", FREQ_G3),
                new TuningNote("B3", FREQ_B3), new TuningNote("E4", FREQ_E4)
        )));
        // Drop C
        tuningPatterns.add(new TuningPattern("Drop C", Arrays.asList(
                new TuningNote("C2", FREQ_C2), new TuningNote("G2", FREQ_G2),
                new TuningNote("C3", FREQ_C3), new TuningNote("F3", FREQ_F3),
                new TuningNote("A3", FREQ_A3), new TuningNote("D4", FREQ_D4)
        )));
        // D Standard
        tuningPatterns.add(new TuningPattern("D Standard", Arrays.asList(
                new TuningNote("D2", FREQ_D2), new TuningNote("G2", FREQ_G2),
                new TuningNote("C3", FREQ_C3), new TuningNote("F3", FREQ_F3),
                new TuningNote("A3", FREQ_A3), new TuningNote("D4", FREQ_D4)
        )));
    }

    private void updateTuningDisplay() {
        if (currentTuning != null && textViewTuningName != null) {
            StringBuilder tuningText = new StringBuilder(currentTuning.getName() + ": ");
            for (int i = 0; i < currentTuning.getNotes().size(); i++) {
                tuningText.append(currentTuning.getNotes().get(i).getNoteName().split("/")[0]);
                if (i < currentTuning.getNotes().size() - 1) {
                    tuningText.append(" ");
                }
            }
            textViewTuningName.setText(tuningText.toString());
        } else if (textViewTuningName != null) {
            textViewTuningName.setText("Selecione uma afinação");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionToRecordAccepted = true;
                Log.d(TAG, "Permissão de áudio concedida.");
                // Se o bufferSize não foi inicializado corretamente antes devido à falta de permissão
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
            return; // Já verificado acima, mas para segurança
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
        isRecording = false; // Sinaliza para a thread parar
        if (recordingThread != null) {
            try {
                recordingThread.join(500); // Espera a thread terminar
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
            updateTuningNeedlePosition(0, true); // Resetar agulha
        });
    }

    private void updateButtonUI() {
        runOnUiThread(() -> {
            if (isRecording) {
                buttonToggleListen.setText("Parar Afinação");
                // buttonToggleListen.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            } else {
                buttonToggleListen.setText("Iniciar Afinação");
                // buttonToggleListen.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            }
            buttonToggleListen.setEnabled(permissionToRecordAccepted && bufferSize > 0);
        });
    }

    private void processAudio() {
        // O número de shorts é bufferSize (em bytes) / 2 (bytes por short)
        short[] audioData = new short[bufferSize / 2];
        Log.d(TAG, "Thread de processamento de áudio iniciada. Lendo " + audioData.length + " shorts.");

        while (isRecording && audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            int shortsRead = audioRecord.read(audioData, 0, audioData.length);

            if (shortsRead > 0) {
                // Log.d(TAG, "Lidos " + shortsRead + " shorts.");
                double detectedFrequency = calculateDominantFrequency(audioData, shortsRead);
                String detectedNote = "--";
                double centsOff = 0.0;

                if (detectedFrequency > 0) {
                    detectedNote = getNoteFromFrequency(detectedFrequency);
                    centsOff = getCentsOff(detectedFrequency, detectedNote);
                }

                final String finalDetectedNote = detectedNote;
                final double finalCentsOff = centsOff;
                final double finalDetectedFrequency = detectedFrequency;

                runOnUiThread(() -> {
                    if (finalDetectedFrequency > 0) {
                        textViewFrequency.setText(String.format("Frequência: %.2f Hz", finalDetectedFrequency));
                        if (!finalDetectedNote.equals("--")) {
                            textViewNote.setText(finalDetectedNote);
                            textViewCents.setText(String.format("Cents: %.1f", finalCentsOff));
                        } else {
                            textViewNote.setText("--");
                            textViewCents.setText("Cents: --");
                        }
                    } else { // Nenhuma frequência detectada claramente
                        textViewFrequency.setText("Frequência: -- Hz");
                        textViewNote.setText("--");
                        textViewCents.setText("Cents: --");
                    }
                    updateTuningNeedlePosition(finalCentsOff, finalDetectedFrequency <= 0);
                });

            } else if (shortsRead < 0) {
                Log.e(TAG, "Erro ao ler áudio. Código: " + shortsRead);
                // Pode ser necessário parar a gravação ou tratar o erro de forma mais robusta
                // stopRecording(); // Exemplo: para em caso de erro grave
                // break;
            }
            // Pequena pausa para não sobrecarregar a CPU, mas YIN já é intensivo
            // try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
        Log.d(TAG, "Thread de processamento de áudio terminando.");
    }


    // --- IMPLEMENTAÇÃO DO YIN ---
    private final float YIN_THRESHOLD = 0.15f; // Ajuste este valor! (0.10 a 0.20 é um bom começo)

    private float[] shortToFloatArray(short[] pcmData, int readSize) {
        float[] floatData = new float[readSize];
        for (int i = 0; i < readSize; i++) {
            floatData[i] = pcmData[i] / 32768.0f;
        }
        return floatData;
    }

    private float yinProcess(float[] audioBuffer, int sampleRate) {
        int yinBufferSize = audioBuffer.length / 2; // Tamanho do buffer da função de diferença (metade do buffer de áudio)
        if (yinBufferSize == 0) return -1.0f;
        float[] diffFunction = new float[yinBufferSize]; // Função de diferença d(tau)

        // Passo 2: Função de Diferença (Quadrática)
        for (int tau = 0; tau < yinBufferSize; tau++) {
            diffFunction[tau] = 0;
            // tau = 0 não é usado para o período, mas é calculado para a normalização
            for (int j = 0; j < yinBufferSize; j++) { // yinBufferSize é bufferSize/2, que é o número de lags que podemos calcular
                if (j + tau < audioBuffer.length) {
                    float delta = audioBuffer[j] - audioBuffer[j + tau];
                    diffFunction[tau] += delta * delta;
                }
            }
        }

        // Passo 3: Função de Diferença Cumulativa Normalizada (CMNDF)
        // diffFunction[tau] agora se torna d'(tau)
        float runningSum = 0;
        diffFunction[0] = 1; // d'(0) é 1 por definição
        for (int tau = 1; tau < yinBufferSize; tau++) {
            runningSum += diffFunction[tau];
            if (runningSum == 0) { // Evita divisão por zero se o sinal for completamente silencioso até tau
                diffFunction[tau] = 1;
            } else {
                diffFunction[tau] *= tau / runningSum;
            }
        }

        // Passo 4: Encontrar o primeiro mínimo local abaixo do limiar
        int periodTau = -1;
        // Definir limites de lag para frequências razoáveis (ex: 50Hz a 1500Hz)
        // Frequência = sampleRate / tau  => tau = sampleRate / Frequência
        int minLag = sampleRate / 1500; // Frequência máxima de interesse
        if (minLag < 1) minLag = 1;
        int maxLag = sampleRate / 50;   // Frequência mínima de interesse
        if (maxLag >= yinBufferSize) maxLag = yinBufferSize - 1;

        for (int tau = minLag; tau <= maxLag; tau++) {
            if (tau == 0) continue; // Não deve acontecer se minLag >=1
            if (diffFunction[tau] < YIN_THRESHOLD) {
                // Procurar o mínimo real em torno deste tau
                while (tau + 1 <= maxLag && diffFunction[tau + 1] < diffFunction[tau]) {
                    tau++;
                }
                periodTau = tau;
                break;
            }
        }

        // Passo 5: Calcular a Frequência e aplicar Interpolação Parabólica
        if (periodTau != -1) {
            // Verifica se temos vizinhos para a interpolação
            if (periodTau > 0 && periodTau < yinBufferSize - 1) {
                float s0 = diffFunction[periodTau - 1];
                float s1 = diffFunction[periodTau];
                float s2 = diffFunction[periodTau + 1];

                // Evitar divisão por zero ou valores estranhos na interpolação
                float divisor = 2 * s1 - s0 - s2;
                if (Math.abs(divisor) > 1e-6) { // Se o divisor não for muito próximo de zero
                    float betterTau = periodTau + (s0 - s2) / (2 * divisor); // Corrigido sinal do numerador
                    if (!Float.isNaN(betterTau) && !Float.isInfinite(betterTau) && betterTau > 0 && betterTau < yinBufferSize) {
                        return (float) sampleRate / betterTau;
                    }
                }
            }
            // Fallback para o tau discreto se a interpolação não for aplicável ou falhar
            return (float) sampleRate / periodTau;
        }
        return -1.0f; // Nenhuma frequência clara encontrada
    }

    private double calculateDominantFrequency(short[] audioData, int shortsRead) {
        if (shortsRead <= 0) {
            return 0.0;
        }
        float[] floatAudioBuffer = shortToFloatArray(audioData, shortsRead);
        float detectedPitchInHz = yinProcess(floatAudioBuffer, SAMPLE_RATE);

        if (detectedPitchInHz > 0) {
            // Log.d(TAG, "YIN Detected Pitch: " + detectedPitchInHz + " Hz");
            return detectedPitchInHz;
        } else {
            // Log.d(TAG, "YIN: No clear pitch detected.");
            return 0.0;
        }
    }
    // --- FIM DA IMPLEMENTAÇÃO DO YIN ---


    // Adaptado para usar currentTuning (como antes)
    private String getNoteFromFrequency(double frequency) {
        if (frequency <= 0 || currentTuning == null) return "--";
        String bestMatchNote = "--";
        double minDifference = Double.MAX_VALUE;
        for (TuningNote tuningNote : currentTuning.getNotes()) {
            double targetFreq = tuningNote.getTargetFrequency();
            // A comparação de frequência para encontrar a nota pode ser feita em escala logarítmica (cents)
            // ou diretamente. A tolerância é importante.
            // Tolerância de +/- 25 cents (meio semitom = 50 cents)
            double lowerBound = targetFreq * Math.pow(2, -25.0 / 1200.0);
            double upperBound = targetFreq * Math.pow(2, 25.0 / 1200.0);

            if (frequency >= lowerBound && frequency <= upperBound) {
                double difference = Math.abs(frequency - targetFreq);
                if (difference < minDifference) {
                    minDifference = difference;
                    bestMatchNote = tuningNote.getNoteName();
                }
            }
        }
        // if (bestMatchNote.equals("--")) {
        //     Log.d(TAG, "getNote: Freq=" + String.format("%.2f", frequency) + " -> Nenhuma nota próxima na afinação '" + currentTuning.getName() + "'");
        // }
        return bestMatchNote;
    }

    // Adaptado para usar currentTuning e a nota encontrada (como antes)
    private double getCentsOff(double detectedFrequency, String detectedNoteName) {
        if (detectedFrequency <= 0 || detectedNoteName == null || detectedNoteName.equals("--") || currentTuning == null) {
            return 0.0;
        }
        double targetFrequency = 0.0;
        for (TuningNote note : currentTuning.getNotes()) {
            if (note.getNoteName().equals(detectedNoteName)) {
                targetFrequency = note.getTargetFrequency();
                break;
            }
        }
        if (targetFrequency == 0.0) return 0.0;
        return 1200 * (Math.log(detectedFrequency / targetFrequency) / Math.log(2));
    }


    // --- MÉTODO DE ATUALIZAÇÃO DA AGULHA ---
    private void updateTuningNeedlePosition(double centsOff, boolean reset) {
        if (viewTuningNeedle == null || viewTuningIndicatorTrack == null) return;

        float trackWidth = viewTuningIndicatorTrack.getWidth();
        if (trackWidth == 0 && !reset) return; // View ainda não foi medida, exceto no reset

        float maxCentsDisplay = 50.0f; // A agulha representa +/- 50 cents
        float normalizedPosition = 0f;

        if (!reset) {
            normalizedPosition = (float) Math.max(-1.0, Math.min(1.0, centsOff / maxCentsDisplay));
        }

        // A translação será metade da largura do trilho para ir do centro para as bordas
        float translationX = normalizedPosition * (trackWidth / 2.0f);

        viewTuningNeedle.animate()
                .translationX(translationX)
                .setDuration(reset ? 0 : 100) // Sem animação no reset
                .start();

        // Atualizar cor da nota e/ou agulha
        int color = ContextCompat.getColor(this, R.color.default_text_color); // Defina uma cor padrão
        if (!reset) {
            if (Math.abs(centsOff) < 5) { // Limiar para "afinado" (ex: +/- 5 cents)
                color = ContextCompat.getColor(this, R.color.tuned_green); // Defina em colors.xml
            } else if (Math.abs(centsOff) < 20) { // Limiar para "próximo"
                color = ContextCompat.getColor(this, R.color.near_yellow); // Defina em colors.xml
            } else { // Desafinado
                color = ContextCompat.getColor(this, R.color.untuned_red); // Defina em colors.xml
            }
        }
        textViewNote.setTextColor(color);
        // viewTuningNeedle.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN); // Se a agulha for um drawable
    }
    // ------------------------------------


    // --- MÉTODOS DO CICLO DE VIDA DA ACTIVITY ---
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
        if (!isRecording && permissionToRecordAccepted && bufferSize > 0) {
            // Opcional: reiniciar a escuta se estava ativa antes, ou deixar para o usuário
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() chamado.");
        if (isRecording) {
            stopRecording(); // Garante que a thread e o AudioRecord sejam liberados
        } else if (audioRecord != null) { // Mesmo se não estiver gravando, pode ter sido inicializado
            audioRecord.release();
            audioRecord = null;
        }
    }
}
