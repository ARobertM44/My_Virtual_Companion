package com.example.demohack.fragments;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.demohack.AudioVisualizerView;
import com.example.demohack.R;
import com.example.demohack.databinding.FragmentSpeechToTextBinding;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Locale;

public class SpeechToTextFragment extends Fragment {

    private FragmentSpeechToTextBinding binding;
    private AudioVisualizerView visualizerView;
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
    );

    private Thread recordingThread;
    private AudioRecord audioRecord;

    private SpeechRecognizer speechRecognizer;
    private boolean isRecording = false;

    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(requireContext(), "Microphone permission granted!", Toast.LENGTH_SHORT).show();
                    startAudioRecording();
                } else {
                    Toast.makeText(requireContext(), "Microphone permission denied!", Toast.LENGTH_SHORT).show();
                }
            });


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentSpeechToTextBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        visualizerView = root.findViewById(R.id.visualizerView);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startAudioRecording();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getActivity());
        speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    String recognizedText = matches.get(0);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
        view.findViewById(R.id.start_speech_recognition).setOnClickListener(v -> startSpeechRecognition());

    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == getActivity().RESULT_OK) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String recognizedText = result.get(0);
                Log.d("Speech", recognizedText);
                Toast.makeText(getActivity(), recognizedText, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioRecording();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAudioRecording();
    }

    private void startAudioRecording() {
        if (isRecording) return;

        File outputDir = new File(requireContext().getExternalFilesDir(null), "AudioRecordings");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return;

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
        );

        isRecording = true;
        audioRecord.startRecording();

        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (isRecording) {
                int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
                if (bytesRead > 0) {
                    float maxAmplitude = calculateMaxAmplitude(buffer, bytesRead);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (visualizerView != null) {
                                visualizerView.updateAmp(maxAmplitude);
                            }
                        });
                    }
                }
            }
        });
        recordingThread.start();
    }

    private void stopAudioRecording() {
        isRecording = false;

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingThread = null;
        }
    }

    private float calculateMaxAmplitude(byte[] buffer, int bytesRead) {
        float maxAmplitude = 0;
        for (int i = 0; i < bytesRead - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            maxAmplitude = Math.max(maxAmplitude, Math.abs(sample));
        }
        return maxAmplitude;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAudioRecording();
        binding = null;
    }
}