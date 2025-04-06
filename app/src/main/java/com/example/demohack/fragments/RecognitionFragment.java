package com.example.demohack.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.example.demohack.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecognitionFragment extends Fragment implements CameraBridgeViewBase.CvCameraViewListener2 {

    private JavaCameraView cameraView;
    private Mat matHsv;
    private TextToSpeech tts;
    private String lastSpokenType = "";
    private long detectionStartTime = 0;
    private static final long SPEAK_DELAY = 3000;

    private final Scalar[] colorBounds = {
            new Scalar(0, 150, 200), new Scalar(10, 255, 255),
            new Scalar(170, 150, 200), new Scalar(180, 255, 255),
            new Scalar(35, 100, 200), new Scalar(70, 255, 255),
            new Scalar(0, 0, 0), new Scalar(180, 50, 50),
            new Scalar(0, 0, 220), new Scalar(180, 30, 255)
    };

    private final String[] colorNames = {"Traffic Light Red", "Traffic Light Red", "Traffic Light Green", "Black", "White"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recognition, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        if(audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        }

        tts = new TextToSpeech(requireContext(), status -> {
            if(status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("ro", "RO"));
                if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Limba română nu este suportată");
                }
            } else {
                Log.e("TTS", "Eroare inițializare TextToSpeech");
            }
        });

        cameraView = view.findViewById(R.id.java_camera_view);
        if(!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initializare esuata");
        }

        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            cameraView.setCameraPermissionGranted();
        }
        cameraView.setVisibility(View.VISIBLE);
        cameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(cameraView != null) {
            cameraView.enableView();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if(cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        matHsv = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        if(matHsv != null) {
            matHsv.release();
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();
        Imgproc.cvtColor(rgba, matHsv, Imgproc.COLOR_RGB2HSV_FULL);

        int redArea = 0, greenArea = 0, blackArea = 0, whiteArea = 0;

        for(int i = 0; i < colorBounds.length; i += 2) {
            Mat mask = new Mat();
            Core.inRange(matHsv, colorBounds[i], colorBounds[i+1], mask);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            int area = 0;
            for(MatOfPoint contour : contours) {
                Rect rect = Imgproc.boundingRect(contour);
                if(rect.area() > 1000) {
                    area += rect.area();
                }
            }

            String colorName = colorNames[i/2];
            if(colorName.equals("Traffic Light Red")) redArea += area;
            else if(colorName.equals("Traffic Light Green")) greenArea += area;
            else if(colorName.equals("Black")) blackArea += area;
            else if(colorName.equals("White")) whiteArea += area;

            mask.release();
            hierarchy.release();
        }

        String situationType = "";
        int maxArea = Math.max(Math.max(whiteArea, blackArea), Math.max(redArea, greenArea));

        if(maxArea > 10000) {
            if(maxArea == whiteArea) situationType = "Trecere de pietoni";
            else if(maxArea == blackArea) situationType = "Obstacol";
            else if(maxArea == greenArea) situationType = "Semafor verde";
            else if(maxArea == redArea) situationType = "Semafor rosu";
        }

        if(!situationType.isEmpty()) {
            Imgproc.putText(rgba, situationType, new Point(30, 100),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 1.5, new Scalar(255,0,0), 3);

            if(situationType.equals(lastSpokenType)) {
                if(System.currentTimeMillis() - detectionStartTime > SPEAK_DELAY) {
                    speakOut(situationType);
                    detectionStartTime = System.currentTimeMillis();
                }
            } else {
                lastSpokenType = situationType;
                detectionStartTime = System.currentTimeMillis();
            }
        } else {
            lastSpokenType = "";
            detectionStartTime = 0;
        }

        return rgba;
    }

    private void speakOut(String text) {
        if(tts != null && !tts.isSpeaking()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

}
