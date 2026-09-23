package gr.distsystems.fruiting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import gr.distsystems.fruiting.R;
import gr.distsystems.fruiting.network.NetworkCallback;
import gr.distsystems.fruiting.network.TCPClient;
import gr.distsystems.fruiting.util.Hash;

public class MainActivity extends AppCompatActivity {
    
    private TCPClient tcpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tcpClient = new TCPClient();

        TextInputEditText etUsername = findViewById(R.id.etUsername);
        TextInputEditText etPassword = findViewById(R.id.etPassword);

        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> showLoginDialog());

        Button btnRegister = findViewById(R.id.btnRegister);
        btnRegister.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password;
            try {
                password = Hash.sha256(etPassword.getText().toString().trim());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            tcpClient.registerPlayer(username, password, new NetworkCallback<Double>() {
                @Override
                public void onSuccess(Double balance) {
                    Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                    intent.putExtra("username", username);
                    intent.putExtra("balance", balance);
                    startActivity(intent);
                    finish();
                }
                
                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

    private void showLoginDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null);
        TextInputEditText etLoginUsername = dialogView.findViewById(R.id.etLoginUsername);
        TextInputEditText etLoginPassword = dialogView.findViewById(R.id.etLoginPassword);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Login")
                .setView(dialogView)
                .setPositiveButton("Login", (dialog, which) -> {
                    String username = etLoginUsername.getText().toString().trim();
                    String password = etLoginPassword.getText().toString().trim();
                    try {
                        password = Hash.sha256(password);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    if (username.isEmpty() || password.isEmpty()) {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    tcpClient.loginPlayer(username, password, new NetworkCallback<Double>() {
                        @Override
                        public void onSuccess(Double balance) {
                            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                            intent.putExtra("username", username);
                            intent.putExtra("balance", balance);
                            startActivity(intent);
                            finish();
                        }
                        @Override
                        public void onError(String errorMessage) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                        }
                    });

                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
