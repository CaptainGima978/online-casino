package gr.distsystems.fruiting.util;

import gr.distsystems.fruiting.R;

public class GameThemeManager {
    private static final int[] FRUIT_THEME = {
            R.drawable.ic_plum,
            R.drawable.ic_lemon,
            R.drawable.ic_orange,
            R.drawable.ic_watermellon,
            R.drawable.ic_cherries,
            R.drawable.ic_coin,
            R.drawable.ic_seven,
            R.drawable.ic_jackpot_crown
    };
    private static final int[] NATURE_THEME = {
            R.drawable.ic_tree,
            R.drawable.ic_flower,
            R.drawable.ic_bluegem,
            R.drawable.ic_greenrock,
            R.drawable.ic_ancientcoin,
            R.drawable.ic_book,
            R.drawable.ic_totem,
            R.drawable.ic_jackpot_dragon
    };
    private static final int[] VEGAS_THEME = {
            R.drawable.ic_jack,
            R.drawable.ic_queen,
            R.drawable.ic_king,
            R.drawable.ic_ace,
            R.drawable.ic_bluechip,
            R.drawable.ic_goldenchip,
            R.drawable.ic_dice,
            R.drawable.ic_jackpot_vegas
    };
    private static final int[] GEMS_THEME = {
            R.drawable.ic_yellowgem,
            R.drawable.ic_bluegem1,
            R.drawable.ic_greengem,
            R.drawable.ic_purplegem,
            R.drawable.ic_redgem,
            R.drawable.ic_luckycoin,
            R.drawable.ic_goldbar,
            R.drawable.ic_jackpot_gems
    };

    public static int[] getTheme(String themeName) {
        if (themeName == null) return FRUIT_THEME;
        switch (themeName.toLowerCase()) {
            case "fruit":
                return FRUIT_THEME;
            case "nature":
                return NATURE_THEME;
            case "vegas":
                return VEGAS_THEME;
            case "gems":
                return GEMS_THEME;
            default:
                return FRUIT_THEME;
        }
    }
}
