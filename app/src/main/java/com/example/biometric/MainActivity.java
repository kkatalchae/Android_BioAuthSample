package com.example.biometric;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.biometric.bioAuthmanager.BioAuthManager;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layout;
    private Button button;
    private TextView title;

    private BioAuthManager bioAuthManager = BioAuthManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UI 관련
        layout = new LinearLayout(this);
        title = new TextView(this);
        button = new Button(this);


        ViewGroup.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        layout.setLayoutParams(layoutParams);
        layout.setOrientation(LinearLayout.VERTICAL);

        title.setText("생체 인증 테스트 앱");

        button.setText("생체 인증");

        layout.addView(title);
        layout.addView(button);

        setContentView(layout);


        button.setOnClickListener(view -> {
            bioAuthManager.authenticate(this ,getApplicationContext());
        });

    }


}