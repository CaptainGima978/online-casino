package domain;

import java.io.Serializable;
import java.util.Objects;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id, gameName, providerName, riskLevel, hashKey, betCategory, theme;
    private int stars, noOfVotes;
    private double minBet, maxBet, jackpot, gameProfit;
    private boolean available = true;

    private byte[] imageBytes;

    public static final double[] LOW_RISK = { 0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5 };
    public static final double[] MED_RISK = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5 };
    public static final double[] HIGH_RISK = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5 };

    public Game(String id, String gameName, String providerName, int stars, int noOfVotes, String gameLogo,
                double minBet, double maxBet, String riskLevel, String theme, String hashKey) {
        this.id = id;
        this.gameName = gameName;
        this.providerName = providerName;
        this.riskLevel = riskLevel;
        this.theme = theme;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.hashKey = hashKey;

        this.gameProfit = 0;

        this.calculateDetails();
        this.available = true;
    }

    public Game(Game other) {
        this.id = other.id;
        this.gameName = other.gameName;
        this.providerName = other.providerName;
        this.riskLevel = other.riskLevel;
        this.theme = other.theme;
        this.hashKey = other.hashKey;
        this.betCategory = other.betCategory;
        this.stars = other.stars;
        this.noOfVotes = other.noOfVotes;
        this.minBet = other.minBet;
        this.maxBet = other.maxBet;
        this.jackpot = other.jackpot;
        this.gameProfit = other.gameProfit;
        this.available = other.available;
    }

    public void calculateDetails() {
        if (this.minBet >= 5.0)
            this.betCategory = "$$$";
        else if (this.minBet >= 1.0)
            this.betCategory = "$$";
        else
            this.betCategory = "$";

        if (this.riskLevel.equalsIgnoreCase("low"))
            this.jackpot = 10;
        else if (this.riskLevel.equalsIgnoreCase("medium"))
            this.jackpot = 20;
        else
            this.jackpot = 40;
    }

    public boolean hasValidInfo() {
        return id != null && !id.isEmpty() &&
                gameName != null && !gameName.isEmpty() &&
                providerName != null && !providerName.isEmpty() &&
                riskLevel != null && !riskLevel.isEmpty() &&
                hashKey != null && !hashKey.isEmpty() &&
                stars >= 1 &&
                noOfVotes >= 0 &&
                minBet >= 0.1 && maxBet > minBet;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getHashKey() {
        return hashKey;
    }

    public void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }

    public String getBetCategory() {
        return betCategory;
    }

    public void setBetCategory(String betCategory) {
        this.betCategory = betCategory;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public int getNoOfVotes() {
        return noOfVotes;
    }

    public void setNoOfVotes(int noOfVotes) {
        this.noOfVotes = noOfVotes;
    }

    public double getMinBet() {
        return minBet;
    }

    public void setMinBet(double minBet) {
        this.minBet = minBet;
    }

    public double getMaxBet() {
        return maxBet;
    }

    public void setMaxBet(double maxBet) {
        this.maxBet = maxBet;
    }

    public double getJackpot() {
        return jackpot;
    }

    public void setJackpot(double jackpot) {
        this.jackpot = jackpot;
    }

    public double getGameProfit() {
        return gameProfit;
    }

    public void setGameProfit(double gameProfit) {
        this.gameProfit = gameProfit;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }
}