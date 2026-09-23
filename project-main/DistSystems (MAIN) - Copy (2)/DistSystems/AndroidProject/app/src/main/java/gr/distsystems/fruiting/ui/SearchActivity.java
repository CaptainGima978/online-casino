package gr.distsystems.fruiting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gr.distsystems.fruiting.R;
import domain.Game;
import gr.distsystems.fruiting.adapter.GameAdapter;
import gr.distsystems.fruiting.network.NetworkCallback;
import gr.distsystems.fruiting.network.TCPClient;

public class SearchActivity extends AppCompatActivity {

    private String username;
    private double balance;
    private List<Game> games;
    private Map<String, Object> filters;

    private TCPClient tcpClient;

    private TextView textViewPlayerName;
    private TextView textViewBalance;
    private MaterialButton buttonAddBalance;
    private MaterialButton buttonFilters;
    private RecyclerView recyclerViewGames;
    private SearchView searchView;
    private ProgressBar progressBar;
    private GameAdapter gameAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get username and balance from intent
        username = getIntent().getStringExtra("username");
        balance = getIntent().getDoubleExtra("balance", 0.0);

        tcpClient = new TCPClient();


        // Initialize views
        textViewPlayerName = findViewById(R.id.textViewPlayerName);
        textViewBalance = findViewById(R.id.textViewBalance);
        buttonAddBalance = findViewById(R.id.buttonAddBalance);
        buttonFilters = findViewById(R.id.buttonFilters);
        recyclerViewGames = findViewById(R.id.recyclerViewGames);
        searchView = findViewById(R.id.searchView);
        progressBar = findViewById(R.id.progressBar);

        textViewPlayerName.setText(username);

        // Setup RecyclerView
        gameAdapter = new GameAdapter();
        recyclerViewGames.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerViewGames.setAdapter(gameAdapter);

        // Setup SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                gameAdapter.filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                gameAdapter.filter(newText);
                return false;
            }
        });

        gameAdapter.setOnGameClickListener(game -> {
            Intent intent = new Intent(SearchActivity.this, PlayActivity.class);
            intent.putExtra("username", username);
            intent.putExtra("balance", balance);
            intent.putExtra("game", game);
            startActivity(intent);
        });

        filters = new HashMap<>();

        buttonAddBalance.setOnClickListener(v -> showAddBalanceDialog());

        buttonFilters.setOnClickListener(v -> showFiltersDialog());

        // Initialize with empty filters to fetch all games
        fetchGames(filters);
    }

    private void fetchGames(Map<String, Object> filters) {
        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
        tcpClient.searchGames(filters, new NetworkCallback<List<Game>>() {
            @Override
            public void onSuccess(List<Game> games) {
                SearchActivity.this.games = games;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    update();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SearchActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePlayerUI();
    }


    private void showAddBalanceDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_balance, null);
        TextInputEditText editTextAmount = dialogView.findViewById(R.id.editTextAmount);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Balance")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String amountStr = editTextAmount.getText().toString();
                    if (!amountStr.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(amountStr);

                            tcpClient.addBalance(username, amount, new NetworkCallback<>() {
                                @Override
                                public void onSuccess(String response) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(SearchActivity.this, response, Toast.LENGTH_SHORT).show();
                                        updatePlayerUI();
                                    });
                                }
                                @Override
                                public void onError(String errorMessage) {
                                    runOnUiThread(() -> Toast.makeText(SearchActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                                }
                            });

                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFiltersDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filters, null);

        MaterialSwitch switchBetManual = dialogView.findViewById(R.id.switchBetManual);
        ChipGroup chipGroupBetLimit = dialogView.findViewById(R.id.chipGroupBetLimit);
        View layoutBetManual = dialogView.findViewById(R.id.layoutBetManual);

        switchBetManual.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                chipGroupBetLimit.setVisibility(View.GONE);
                layoutBetManual.setVisibility(View.VISIBLE);
            } else {
                chipGroupBetLimit.setVisibility(View.VISIBLE);
                layoutBetManual.setVisibility(View.GONE);
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle("Filters")
                .setView(dialogView)
                .setPositiveButton("Apply", (dialog, which) -> {
                    Map<String, Object> filters = getFilters(dialogView);
                    fetchGames(filters);
                    Toast.makeText(this, "Filters applied!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Reset", null)
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void updatePlayerUI() {
        tcpClient.getBalance(username, new NetworkCallback<>() {
            @Override
            public void onSuccess(Double balance) {
                SearchActivity.this.balance = balance;
                runOnUiThread(() -> textViewBalance.setText(String.format("%.2fFUN", SearchActivity.this.balance)));
            }
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> Toast.makeText(SearchActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private Map<String, Object> getFilters(View view) {
        Map<String, Object> filters = new HashMap<>();

        ChipGroup chipGroupRisk = view.findViewById(R.id.chipGroupRisk);
        int checkedRiskId = chipGroupRisk.getCheckedChipId();
        if (checkedRiskId != View.NO_ID) {
            Chip chip = view.findViewById(checkedRiskId);
            filters.put("riskLevel", chip.getText().toString());
        }

        MaterialSwitch switchBetManual = view.findViewById(R.id.switchBetManual);
        if (switchBetManual.isChecked()) {
            TextInputEditText editTextMin = view.findViewById(R.id.editTextMinBet);
            TextInputEditText editTextMax = view.findViewById(R.id.editTextMaxBet);
            try {
                String minStr = editTextMin.getText().toString();
                if (!minStr.isEmpty()) filters.put("minBet", Double.parseDouble(minStr));
                
                String maxStr = editTextMax.getText().toString();
                if (!maxStr.isEmpty()) filters.put("maxBet", Double.parseDouble(maxStr));
            } catch (NumberFormatException ignored) {}
        } else {
            ChipGroup chipGroupBetLimit = view.findViewById(R.id.chipGroupBetLimit);
            int checkedBetId = chipGroupBetLimit.getCheckedChipId();
            if (checkedBetId != View.NO_ID) {
                Chip chip = view.findViewById(checkedBetId);
                String betCategory = chip.getText().toString();
                filters.put("betCategory", betCategory);
            }
        }

        RatingBar ratingBar = view.findViewById(R.id.ratingBarFilter);
        int minStars = (int) ratingBar.getRating();
        if (minStars > 0) {
            filters.put("stars", minStars);
        }

        return filters;
    }

    private void update() {
        runOnUiThread(() -> gameAdapter.setGames(games));
        updatePlayerUI();
    }
}
