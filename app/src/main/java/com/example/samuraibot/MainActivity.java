package com.example.samuraibot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etBranchName;
    private RadioGroup rgShiftType;
    private RadioButton rbFullShift;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etBranchName = findViewById(R.id.etBranchName);
        rgShiftType = findViewById(R.id.rgShiftType);
        rbFullShift = findViewById(R.id.rbFullShift);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        Button btnGrantPermission = findViewById(R.id.btnGrantPermission);

        prefs = getSharedPreferences("SamuraiBotPrefs", Context.MODE_PRIVATE);

        // Load saved settings
        etBranchName.setText(prefs.getString("target_branch", ""));
        if (prefs.getBoolean("book_full_shift", true)) {
            rbFullShift.setChecked(true);
        } else {
            findViewById(R.id.rbPartialShift).performClick();
        }

        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String branch = etBranchName.getText().toString();
                boolean isFull = rbFullShift.isChecked();

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("target_branch", branch);
                editor.putBoolean("book_full_shift", isFull);
                editor.apply();

                Toast.makeText(MainActivity.this, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show();
            }
        });

        btnGrantPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(MainActivity.this, "يرجى تفعيل خدمة Samurai Bot", Toast.LENGTH_LONG).show();
            }
        });
    }
}
