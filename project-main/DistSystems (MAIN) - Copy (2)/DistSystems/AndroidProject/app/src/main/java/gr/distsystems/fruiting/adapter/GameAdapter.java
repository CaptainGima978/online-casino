package gr.distsystems.fruiting.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import gr.distsystems.fruiting.R;
import domain.Game;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

    private List<Game> games = new ArrayList<>();
    private List<Game> gamesFull = new ArrayList<>();
    private OnGameClickListener listener;
    
    // Cache bitmaps to avoid re-decoding them while scrolling
    private final LruCache<String, Bitmap> bitmapCache;

    public interface OnGameClickListener {
        void onPlayClick(Game game);
    }

    public GameAdapter() {
        // Use 1/8th of available memory for bitmap cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void setOnGameClickListener(OnGameClickListener listener) {
        this.listener = listener;
    }

    public void setGames(List<Game> games) {
        this.games = new ArrayList<>(games);
        this.gamesFull = new ArrayList<>(games);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Game game = games.get(position);
        holder.textViewGameName.setText(game.getGameName());
        holder.textViewProvider.setText(game.getProviderName());
        holder.textViewBetCategory.setText(game.getBetCategory());
        holder.textViewRiskValue.setText(game.getRiskLevel());
        holder.textViewStars.setText(String.format("★ %.1f (%d)", (float)game.getStars(), game.getNoOfVotes()));
        holder.textViewBetRange.setText(String.format("Bet: %.2fFUN - %.2fFUN", game.getMinBet(), game.getMaxBet()));
        holder.textViewJackpot.setText(String.format("Jackpot: %.0fx", game.getJackpot()));

        String cacheKey = game.getGameName() + "_" + game.getProviderName();
        Bitmap cachedBitmap = bitmapCache.get(cacheKey);

        if (cachedBitmap != null) {
            holder.imageViewGameLogo.setImageBitmap(cachedBitmap);
        } else {
            byte[] imageBytes = game.getImageBytes();
            if (imageBytes != null && imageBytes.length > 0) {
                // Decoding on main thread is still not ideal, but with caching it happens only once
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap != null) {
                    bitmapCache.put(cacheKey, bitmap);
                    holder.imageViewGameLogo.setImageBitmap(bitmap);
                } else {
                    holder.imageViewGameLogo.setImageResource(R.drawable.ic_launcher_background);
                }
            } else {
                holder.imageViewGameLogo.setImageResource(R.drawable.ic_launcher_background);
            }
        }

        holder.buttonPlay.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayClick(game);
            }
        });
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    public void filter(String text) {
        games.clear();
        if (text == null || text.isEmpty()) {
            games.addAll(gamesFull);
        } else {
            text = text.toLowerCase();
            for (Game item : gamesFull) {
                if (item.getGameName().toLowerCase().contains(text) || item.getProviderName().toLowerCase().contains(text)) {
                    games.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        TextView textViewGameName, textViewProvider, textViewBetCategory, textViewRiskValue, textViewStars, textViewBetRange, textViewJackpot;
        ImageView imageViewGameLogo;
        Button buttonPlay;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewGameName = itemView.findViewById(R.id.textViewGameName);
            textViewProvider = itemView.findViewById(R.id.textViewProvider);
            textViewBetCategory = itemView.findViewById(R.id.textViewBetCategory);
            textViewRiskValue = itemView.findViewById(R.id.textViewRiskValue);
            textViewStars = itemView.findViewById(R.id.textViewStars);
            textViewBetRange = itemView.findViewById(R.id.textViewBetRange);
            textViewJackpot = itemView.findViewById(R.id.textViewJackpot);
            imageViewGameLogo = itemView.findViewById(R.id.imageViewGameLogo);
            buttonPlay = itemView.findViewById(R.id.buttonPlay);
        }
    }
}
