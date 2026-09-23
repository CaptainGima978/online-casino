package gr.distsystems.fruiting.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import domain.Game;
import gr.distsystems.fruiting.R;
import gr.distsystems.fruiting.adapter.ReelAdapter;
import gr.distsystems.fruiting.network.NetworkCallback;
import gr.distsystems.fruiting.network.TCPClient;
import gr.distsystems.fruiting.util.GameThemeManager;

public class PlayActivity extends AppCompatActivity {

    private String username;
    private double balance;
    private double winnings;
    private Game game;
    private TCPClient tcpClient;

    private TextView balanceTv, minBetTv, maxBetTv, winningsTv;
    private EditText betEt;
    private MaterialButton spinBtn;
    private RecyclerView reel1, reel2, reel3;
    private View frame1, frame2, frame3;
    private ReelAdapter adapter1, adapter2, adapter3;

    private int[] symbols;
    private int[] reel1Symbols;
    private int[] reel2Symbols;
    private int[] reel3Symbols;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_play);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tcpClient = new TCPClient();

        // Retrieve data passed from SearchActivity
        username = getIntent().getStringExtra("username");
        balance = getIntent().getDoubleExtra("balance", 0.0);
        game = (Game) getIntent().getSerializableExtra("game");
        winnings = 0;

        if (game == null) {
            Toast.makeText(this, "Game data missing!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        symbols = GameThemeManager.getTheme(game.getTheme());

        initViews();
        setupReels();
        updateUI();

        spinBtn.setOnClickListener(v -> spin());
    }

    private void initViews() {
        balanceTv = findViewById(R.id.balanceTv);
        winningsTv = findViewById(R.id.winningsTv);
        minBetTv = findViewById(R.id.minBetTv);
        maxBetTv = findViewById(R.id.maxBetTv);
        betEt = findViewById(R.id.betEt);
        spinBtn = findViewById(R.id.spinBtn);
        reel1 = findViewById(R.id.reel1);
        reel2 = findViewById(R.id.reel2);
        reel3 = findViewById(R.id.reel3);
        frame1 = findViewById(R.id.frame1);
        frame2 = findViewById(R.id.frame2);
        frame3 = findViewById(R.id.frame3);
    }

    private void setupReels() {
        reel1Symbols = shuffleSymbols(symbols);
        reel2Symbols = shuffleSymbols(symbols);
        reel3Symbols = shuffleSymbols(symbols);

        adapter1 = new ReelAdapter(reel1Symbols);
        adapter2 = new ReelAdapter(reel2Symbols);
        adapter3 = new ReelAdapter(reel3Symbols);

        reel1.setLayoutManager(new LinearLayoutManager(this));
        reel2.setLayoutManager(new LinearLayoutManager(this));
        reel3.setLayoutManager(new LinearLayoutManager(this));

        reel1.setAdapter(adapter1);
        reel2.setAdapter(adapter2);
        reel3.setAdapter(adapter3);

        // Scroll to a middle position to allow "infinite" scrolling in both directions
        int startPos = Integer.MAX_VALUE / 2;
        reel1.scrollToPosition(startPos);
        reel2.scrollToPosition(startPos);
        reel3.scrollToPosition(startPos);
    }

    private int[] shuffleSymbols(int[] original) {
        List<Integer> list = new ArrayList<>();
        for (int s : original)
            list.add(s);
        Collections.shuffle(list);
        int[] shuffled = new int[list.size()];
        for (int i = 0; i < list.size(); i++)
            shuffled[i] = list.get(i);
        return shuffled;
    }

    private int findSymbolIndex(int[] reelSymbols, int targetOriginalIndex) {
        int targetResId = symbols[targetOriginalIndex];
        for (int i = 0; i < reelSymbols.length; i++) {
            if (reelSymbols[i] == targetResId) {
                return i;
            }
        }
        return 0;
    }

    private int adjustScrollForTarget(int current, int baseScroll, int targetIndex, int length) {
        int desiredRem = (targetIndex + 1) % length;
        int p = current + baseScroll;
        int currentRem = p % length;
        int diff = desiredRem - currentRem;
        if (diff < 0) {
            diff += length;
        }
        return baseScroll + diff;
    }

    private boolean isWinCombination(int t1, int t2, int t3) {
        if (t1 == t2 && t2 == t3) return true;
        if (t1 == t2 && t1 < 5) return true;
        if (t2 == t3 && t2 < 5) return true;
        if (t1 == t3 && t1 < 5) return true;
        return false;
    }

    private void updateUI() {
        balanceTv.setText(String.format("Balance: %.2f FUN", balance));
        winningsTv.setText(String.format("Winnings: %.2f FUN", winnings));
        minBetTv.setText(String.format("Min: %.1f", game.getMinBet()));
        maxBetTv.setText(String.format("Max: %.1f", game.getMaxBet()));
        betEt.setHint("Bet (e.g. " + game.getMinBet() + ")");
    }

    private void spin() {
        String betStr = betEt.getText().toString();
        if (betStr.isEmpty()) {
            Toast.makeText(this, "Enter a bet amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double bet;
        try {
            bet = Double.parseDouble(betStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid bet amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bet < game.getMinBet() || bet > game.getMaxBet()) {
            Toast.makeText(this, "Bet must be between " + game.getMinBet() + " and " + game.getMaxBet(),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (bet > balance) {
            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show();
            return;
        }

        spinBtn.setEnabled(false);
        frame1.setVisibility(View.INVISIBLE);
        frame2.setVisibility(View.INVISIBLE);
        frame3.setVisibility(View.INVISIBLE);

        tcpClient.playGame(username, game.getGameName(), bet, new NetworkCallback<Double>() {
            @Override
            public void onSuccess(Double winAmount) {
                runOnUiThread(() -> {
                    double multiplier = winAmount / bet;
                    int target1, target2, target3;
                    Random r = new Random();

                    if (multiplier >= 10) {
                        target1 = 7; target2 = 7; target3 = 7;
                    } else if (multiplier >= 2) {
                        int special = 5 + r.nextInt(2);
                        target1 = special; target2 = special; target3 = special;
                    } else if (multiplier >= 1) {
                        int basic = r.nextInt(5);
                        target1 = basic; target2 = basic; target3 = basic;
                    } else if (multiplier > 0) {
                        int basic = r.nextInt(5);
                        int other = r.nextInt(5);
                        while (other == basic) other = r.nextInt(5);
                        int choice = r.nextInt(3);
                        if (choice == 0) { target1 = basic; target2 = basic; target3 = other; }
                        else if (choice == 1) { target1 = basic; target2 = other; target3 = basic; }
                        else { target1 = other; target2 = basic; target3 = basic; }
                    } else {
                        do {
                            target1 = r.nextInt(8);
                            target2 = r.nextInt(8);
                            target3 = r.nextInt(8);
                        } while (isWinCombination(target1, target2, target3));
                    }

                    int t1Index = findSymbolIndex(reel1Symbols, target1);
                    int t2Index = findSymbolIndex(reel2Symbols, target2);
                    int t3Index = findSymbolIndex(reel3Symbols, target3);

                    int current1 = ((LinearLayoutManager) reel1.getLayoutManager()).findFirstVisibleItemPosition();
                    int current2 = ((LinearLayoutManager) reel2.getLayoutManager()).findFirstVisibleItemPosition();
                    int current3 = ((LinearLayoutManager) reel3.getLayoutManager()).findFirstVisibleItemPosition();

                    int scroll1 = 50 + r.nextInt(20);
                    int scroll2 = 80 + r.nextInt(20);
                    int scroll3 = 110 + r.nextInt(20);

                    scroll1 = adjustScrollForTarget(current1, scroll1, t1Index, reel1Symbols.length);
                    scroll2 = adjustScrollForTarget(current2, scroll2, t2Index, reel2Symbols.length);
                    scroll3 = adjustScrollForTarget(current3, scroll3, t3Index, reel3Symbols.length);

                    reel1.smoothScrollToPosition(current1 + scroll1);
                    reel2.smoothScrollToPosition(current2 + scroll2);
                    reel3.smoothScrollToPosition(current3 + scroll3);

                    boolean show1 = false, show2 = false, show3 = false;
                    if (multiplier >= 1) {
                        show1 = true; show2 = true; show3 = true;
                    } else if (multiplier > 0) {
                        if (target1 == target2) { show1 = true; show2 = true; }
                        else if (target2 == target3) { show2 = true; show3 = true; }
                        else if (target1 == target3) { show1 = true; show3 = true; }
                    }
                    
                    final boolean fShow1 = show1;
                    final boolean fShow2 = show2;
                    final boolean fShow3 = show3;

                    // Simulate a delay for the spin animation to "finish"
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        winnings = winAmount;
                        balance = (balance - bet) + winAmount;
                        updateUI();
                        if (winAmount > 0) {
                            if (fShow1) frame1.setVisibility(View.VISIBLE);
                            if (fShow2) frame2.setVisibility(View.VISIBLE);
                            if (fShow3) frame3.setVisibility(View.VISIBLE);
                            Toast.makeText(PlayActivity.this, "WIN! +" + String.format("%.2f", winAmount) + " FUN",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(PlayActivity.this, "Try again!", Toast.LENGTH_SHORT).show();
                        }
                        spinBtn.setEnabled(true);
                    }, 2000);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(PlayActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    spinBtn.setEnabled(true);
                });
            }
        });
    }
}
