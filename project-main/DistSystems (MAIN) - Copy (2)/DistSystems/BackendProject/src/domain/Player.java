package domain;

import java.io.Serializable;

public class Player implements Serializable {
    private String username;
    private String password;
    private double balance;
    private double playerProfit;

    public Player(String name, String password) {
        this.username = name;
        this.password = password;
        this.balance = 0;
        this.playerProfit = 0;
    }

    public Player(String username, String password, double balance, double playerProfit) {
        this.username = username;
        this.password = password;
        this.balance = balance;
        this.playerProfit = playerProfit;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String name) {
        this.username = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getPlayerProfit() {
        return playerProfit;
    }

    public void setPlayerProfit(double playerProfit) {
        this.playerProfit = playerProfit;
    }

}
