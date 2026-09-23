package gr.distsystems.fruiting.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Adapter for the slot machine reels.
 */
public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.SymbolViewHolder> {
    private final int[] symbols;

    public ReelAdapter(int[] symbols) {
        this.symbols = symbols;
    }

    @NonNull
    @Override
    public SymbolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView imageView = new ImageView(parent.getContext());
        // Set height to fit approximately 3 symbols per reel view height
        float density = parent.getContext().getResources().getDisplayMetrics().density;
        int heightPx = (int) (120 * density);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = (int) (16 * density);
        imageView.setPadding(padding, padding, padding, padding);
        return new SymbolViewHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull SymbolViewHolder holder, int position) {
        holder.imageView.setImageResource(symbols[position % symbols.length]);
    }

    @Override
    public int getItemCount() {
        return Integer.MAX_VALUE; // Infinite scroll simulation
    }

    public static class SymbolViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public SymbolViewHolder(View itemView) {
            super(itemView);
            imageView = (ImageView) itemView;
        }
    }
}
