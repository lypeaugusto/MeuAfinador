package com.example.meuafinador;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView; // Adicionado
import android.widget.ArrayAdapter;  // Adicionado
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList; // Adicionado
import java.util.Arrays;    // Adicionado
import java.util.List;      // Adicionado

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
    private TextView textViewTuningName; // Certifique-se que este ID existe no XML
    private Spinner spinnerTuningType;
    private Button buttonToggleListen;
    private View viewTuningNeedle;

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

        public String getNoteName() {
            return noteName;
        }

        public double getTargetFrequency() {
            return targetFrequency;
        }
    }

    private static class TuningPattern {
        String name;
        List<TuningNote> notes;

        TuningPattern(String name, List<TuningNote> notes) {
            this.name = name;
            this.notes = notes;
        }

        public String getName() {
            return name;
        }

        public List<TuningNote> getNotes() {
            return notes;
        }

        @NonNull
        @Override
        public String toString() {
            return name; // Isso é o que será mostrado no Spinner
        }
    }

    private List<TuningPattern> tuningPatterns;
    private TuningPattern currentTuning;
    private ArrayAdapter<TuningPattern> spinnerAdapter;

    // Frequências base (podem ser ajustadas conforme necessário)
    private static final double FREQ_C2  = 65.41;
    private static final double FREQ_CS2 = 69.30; // C#/Db2
    private static final double FREQ_D2  = 73.42;
    private static final double FREQ_DS2 = 77.78; // D#/Eb2
    private static final double FREQ_E2  = 82.41;
    private static final double FREQ_F2  = 87.31;
    private static final double FREQ_FS2 = 92.50; // F#/Gb2
    private static final double FREQ_G2  = 98.00;
    private static final double FREQ_GS2 = 103.83; // G#/Ab2
    private static final double FREQ_A2  = 110.00;
    private static final double FREQ_AS2 = 116.54; // A#/Bb2
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
    // --- Fim das Definições para Afinações ---


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        textViewNote = findViewById(R.id.textViewNote);
        textViewFrequency = findViewById(R.id.textViewFrequency);
        textViewCents = findViewById(R.id.textViewCents);
        textViewTuningName = findViewById(R.id.textViewTuningName); // VERIFIQUE SE ESTE ID ESTÁ NO SEU XML
        spinnerTuningType = findViewById(R.id.spinnerTuningType);
        buttonToggleListen = findViewById(R.id.buttonToggleListen);
        viewTuningNeedle = findViewById(R.id.viewTuningNeedle);

        // --- Configuração do Spinner de Afinação ---
        initializeTunings();

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tuningPatterns);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTuningType.setAdapter(spinnerAdapter);

        if (!tuningPatterns.isEmpty()) {
            currentTuning = tuningPatterns.get(0); // Padrão para E Standard
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
                    if (isRecording) { // Se a afinação mudar durante a gravação, limpe as informações antigas
                        runOnUiThread(() -> {
                            textViewNote.setText("--");
                            textViewFrequency.setText("Frequência: -- Hz");
                            textViewCents.setText("Cents: --");
                            // updateTuningNeedlePosition(0); // Resetar agulha se aplicável
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
        // --- Fim da Configuração do Spinner ---

        buttonToggleListen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Tamanho do buffer inválido: " + bufferSize);
            Toast.makeText(this, "Não foi possível inicializar o buffer de áudio.", Toast.LENGTH_SHORT).show();
            buttonToggleListen.setEnabled(false);
        }

        updateButtonUI();
    }

    private void initializeTunings() {
        tuningPatterns = new ArrayList<>();

        // E Standard (E A D G B E)
        List<TuningNote> eStandardNotes = Arrays.asList(
                new TuningNote("E2", FREQ_E2), new TuningNote("A2", FREQ_A2),
                new TuningNote("D3", FREQ_D3), new TuningNote("G3", FREQ_G3),
                new TuningNote("B3", FREQ_B3), new TuningNote("E4", FREQ_E4)
        );
        tuningPatterns.add(new TuningPattern("E Standard", eStandardNotes));

        // Eb Standard (Eb Ab Db Gb Bb Eb)
        List<TuningNote> ebStandardNotes = Arrays.asList(
                new TuningNote("D#2/Eb2", FREQ_DS2), new TuningNote("G#2/Ab2", FREQ_GS2),
                new TuningNote("C#3/Db3", FREQ_CS3), new TuningNote("F#3/Gb3", FREQ_FS3),
                new TuningNote("A#3/Bb3", FREQ_AS3), new TuningNote("D#4/Eb4", FREQ_DS4)
        );
        tuningPatterns.add(new TuningPattern("Eb Standard", ebStandardNotes));

        // Drop D (D A D G B E)
        List<TuningNote> dropDNotes = Arrays.asList(
                new TuningNote("D2", FREQ_D2), new TuningNote("A2", FREQ_A2),
                new TuningNote("D3", FREQ_D3), new TuningNote("G3", FREQ_G3),
                new TuningNote("B3", FREQ_B3), new TuningNote("E4", FREQ_E4)
        );
        tuningPatterns.add(new TuningPattern("Drop D", dropDNotes));

        // Drop C (C G C F A D)
        List<TuningNote> dropCNotes = Arrays.asList(
                new TuningNote("C2", FREQ_C2), new TuningNote("G2", FREQ_G2),
                new TuningNote("C3", FREQ_C3), new TuningNote("F3", FREQ_F3),
                new TuningNote("A3", FREQ_A3), new TuningNote("D4", FREQ_D4)
        );
        tuningPatterns.add(new TuningPattern("Drop C", dropCNotes));

        // D Standard (D G C F A D)
        List<TuningNote> dStandardNotes = Arrays.asList(
                new TuningNote("D2", FREQ_D2), new TuningNote("G2", FREQ_G2),
                new TuningNote("C3", FREQ_C3), new TuningNote("F3", FREQ_F3),
                new TuningNote("A3", FREQ_A3), new TuningNote("D4", FREQ_D4)
        );
        tuningPatterns.add(new TuningPattern("D Standard", dStandardNotes));
    }

    private void updateTuningDisplay() {
        if (currentTuning != null && textViewTuningName != null) {
            StringBuilder tuningText = new StringBuilder(currentTuning.getName() + ": ");
            for (int i = 0; i < currentTuning.getNotes().size(); i++) {
                tuningText.append(currentTuning.getNotes().get(i).getNoteName().split("/")[0]); // Pega o primeiro nome da nota se tiver "/"
                if (i < currentTuning.getNotes().size() - 1) {
                    tuningText.append(" ");
                }
            }
            textViewTuningName.setText(tuningText.toString());
            Log.d(TAG, "Display atualizado para: " + tuningText.toString());
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
                if (bufferSize <= 0) {
                    bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                    if (bufferSize <= 0) {
                        Log.e(TAG, "Falha ao obter bufferSize mesmo após permissão: " + bufferSize);
                        Toast.makeText(this, "Erro crítico com o buffer de áudio.", Toast.LENGTH_SHORT).show();
                        buttonToggleListen.setEnabled(false);
                    } else {
                        buttonToggleListen.setEnabled(true);
                    }
                }
            } else {
                permissionToRecordAccepted = false;
                Log.w(TAG, "Permissão de áudio negada.");
                Toast.makeText(this, "Permissão de áudio negada. O afinador não funcionará.", Toast.LENGTH_LONG).show();
                buttonToggleListen.setEnabled(false);
            }
            updateButtonUI();
        }
    }

    private void startRecording() {
        if (!permissionToRecordAccepted) {
            Log.w(TAG, "Tentativa de iniciar gravação sem permissão.");
            Toast.makeText(this, "Permissão de áudio é necessária.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        if (isRecording) {
            Log.w(TAG, "startRecording() chamado enquanto já estava gravando.");
            return;
        }
        if (bufferSize <= 0) {
            Log.e(TAG, "Tamanho do buffer inválido ou não inicializado: " + bufferSize);
            Toast.makeText(this, "Erro no buffer de áudio.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            audioRecord = null;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissão de gravação de áudio não concedida ao tentar criar AudioRecord.");
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord não pôde ser inicializado. Estado: " + audioRecord.getState());
            Toast.makeText(this, "Não foi possível inicializar o gravador.", Toast.LENGTH_SHORT).show();
            if(audioRecord != null) audioRecord.release();
            audioRecord = null;
            return;
        }
        try {
            audioRecord.startRecording();
            isRecording = true;
            Log.d(TAG, "Gravação iniciada.");
            recordingThread = new Thread(this::processAudio, "AudioProcessingThread");
            recordingThread.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioRecord.startRecording() falhou", e);
            Toast.makeText(this, "Falha ao iniciar a gravação.", Toast.LENGTH_SHORT).show();
            isRecording = false;
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
        }
        updateButtonUI();
    }

    private void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "stopRecording() chamado quando não estava gravando.");
            return;
        }
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
        });
    }

    private void updateButtonUI() {
        runOnUiThread(() -> {
            if (isRecording) {
                buttonToggleListen.setText("Parar Afinação");
                buttonToggleListen.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                buttonToggleListen.setText("Iniciar Afinação");
                buttonToggleListen.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
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
                String detectedNote = getNoteFromFrequency(detectedFrequency); // Agora usa a versão adaptada
                double centsOff = getCentsOff(detectedFrequency, detectedNote);

                runOnUiThread(() -> {
                    textViewFrequency.setText(String.format("Frequência: %.2f Hz", detectedFrequency));
                    if (!detectedNote.equals("--")) {
                        textViewNote.setText(detectedNote);
                        textViewCents.setText(String.format("Cents: %.1f", centsOff));
                    } else {
                        textViewNote.setText("--");
                        textViewCents.setText("Cents: --");
                    }
                });
            } else if (shortsRead < 0) {
                Log.e(TAG, "Erro ao ler áudio. Código: " + shortsRead);
            }
        }
        Log.d(TAG, "Thread de processamento de áudio terminando.");
    }

    // --- MÉTODOS DE CÁLCULO DE FREQUÊNCIA (PLACEHOLDERS) ---
    private double calculateDominantFrequency(short[] audioData, int shortsRead) {
        // Placeholder: Simula detecção baseada na amplitude média.
        if (shortsRead > 0) {
            long sum = 0;
            for (int i = 0; i < shortsRead; i++) {
                sum += Math.abs(audioData[i]);
            }
            double averageAmplitude = (double) sum / shortsRead;

            // Simulações baseadas em limiares de amplitude
            if (currentTuning != null && !currentTuning.getNotes().isEmpty()) {
                // Tenta simular uma das frequências da afinação atual
                // Isso é muito artificial, apenas para teste com o spinner
                int noteIndexToSimulate = (int) (averageAmplitude / 100) % currentTuning.getNotes().size();
                if (averageAmplitude > 50) { // Só simula se houver algum "som"
                    double freqToSimulate = currentTuning.getNotes().get(noteIndexToSimulate).getTargetFrequency();
                    Log.d(TAG, "Simulando freq da afinação atual: " + freqToSimulate + " Hz (ampl: " + averageAmplitude + ")");
                    return freqToSimulate;
                }
            } else { // Fallback para simulação original se não houver afinação
                if (averageAmplitude > 700) return 82.41;  // E2
                else if (averageAmplitude > 600) return 110.00; // A2
                else if (averageAmplitude > 500) return 146.83; // D3
                else if (averageAmplitude > 400) return 196.00; // G3
                else if (averageAmplitude > 300) return 246.94; // B3
                else if (averageAmplitude > 200) return 329.63; // E4
                else if (averageAmplitude > 100) return 440.00; // A4
            }
        }
        return 0.0;
    }

    // Adaptado para usar currentTuning
    private String getNoteFromFrequency(double frequency) {
        if (frequency <= 0 || currentTuning == null) {
            return "--";
        }

        String bestMatchNote = "--";
        double minDifference = Double.MAX_VALUE;

        for (TuningNote tuningNote : currentTuning.getNotes()) {
            double difference = Math.abs(tuningNote.getTargetFrequency() - frequency);

            // Tolerância: +/- 5% da frequência da nota alvo parece razoável para placeholders
            double tolerance = tuningNote.getTargetFrequency() * 0.05;

            if (difference < minDifference && difference < tolerance) {
                minDifference = difference;
                bestMatchNote = tuningNote.getNoteName();
            }
        }

        if (bestMatchNote.equals("--")) {
            Log.d(TAG, "getNote: Freq=" + String.format("%.2f", frequency) + " -> Nenhuma nota próxima na afinação '" + currentTuning.getName() + "'");
        } else {
            Log.d(TAG, "getNote: Freq=" + String.format("%.2f", frequency) + " -> Nota '" + bestMatchNote + "' da afinação '" + currentTuning.getName() + "'");
        }
        return bestMatchNote;
    }


    // Adaptado para usar currentTuning e a nota encontrada
    private double getCentsOff(double detectedFrequency, String detectedNoteName) {
        if (detectedFrequency <= 0 || detectedNoteName == null || detectedNoteName.equals("--") || currentTuning == null) {
            return 0.0;
        }

        double targetFrequency = 0.0;
        // Encontra a frequência alvo da nota detectada na afinação atual
        for (TuningNote note : currentTuning.getNotes()) {
            if (note.getNoteName().equals(detectedNoteName)) {
                targetFrequency = note.getTargetFrequency();
                break;
            }
        }

        if (targetFrequency == 0.0) {
            Log.d(TAG, "getCents: Nota '" + detectedNoteName + "' não encontrada na afinação atual para pegar targetFrequency.");
            return 0.0; // Nota não faz parte da afinação atual ou erro
        }

        double cents = 1200 * (Math.log(detectedFrequency / targetFrequency) / Math.log(2));
        Log.d(TAG, "getCents: Nota=" + detectedNoteName + ", FreqDetect=" + String.format("%.2f", detectedFrequency) +
                ", FreqAlvo=" + String.format("%.2f", targetFrequency) + " -> Cents=" + String.format("%.1f", cents));
        return cents;
    }
    // -----------------------------------------------------------


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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() chamado.");
        if (isRecording) {
            stopRecording();
        }
        if (audioRecord != null) {
            if(audioRecord.getState() == AudioRecord.STATE_INITIALIZED || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
                Log.d(TAG, "onDestroy: Liberando AudioRecord.");
                audioRecord.release();
            }
            audioRecord = null;
        }
    }

    // --- MÉTODO DE ATUALIZAÇÃO DA AGULHA (PLACEHOLDER) ---
    /*
    private void updateTuningNeedlePosition(double centsOff) {
        // ... (seu código da agulha aqui)
    }
    */
}
