package console;

import domain.Game;
import domain.Player;
import util.Constants;

import java.io.*;
import java.net.*;
import java.util.*;

public class DummyPlayerConsole {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String host = Constants.MASTER_IP;
        int port = Constants.MASTER_PORT;

        Player player = new Player("Big Dawg", "bigdawg123");
        System.out.println(player.getUsername() + " welcome to the casino!");

        while (true) {
            System.out.println("\n--- PLAYER MENU ---");
            System.out.println("Current Balance: " + player.getBalance() + " FUN");
            System.out.println("1. Search Games");
            System.out.println("2. Add Balance ");
            System.out.println("3. Play domain.Game");
            System.out.println("4. Exit");
            System.out.print("Select option: ");

            String input = scanner.nextLine();

            if (input.equals("4"))
                break;

            try (Socket s = new Socket(host, port);
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

                if (input.equals("1")) {
                    // --- SEARCH ---
                    out.writeObject("SEARCH");

                    Map<String, Object> filters = new HashMap<>();
                    System.out.print("Enter Risk Level (low/medium/high): ");
                    filters.put("riskLevel", scanner.nextLine());
                    System.out.print("Enter Minimum Bet: ");
                    filters.put("minBet", Double.parseDouble(scanner.nextLine()));
                    System.out.print("Enter Minimum Stars: ");
                    filters.put("stars", Integer.parseInt(scanner.nextLine()));

                    out.writeObject(filters);
                    out.flush();

                    Object response = in.readObject();
                    if (response instanceof List) {
                        List<Game> games = (List<Game>) response;
                        if (games.isEmpty()) {
                            System.out.println("No games match your filters.");
                        } else {
                            System.out.println("Found " + games.size() + " games:");
                            for (Game g : games) {
                                System.out.println("- " + g.getGameName() + " | Risk: " + g.getRiskLevel()
                                        + " | MinBet: " + g.getMinBet() + " | Stars: " + g.getStars());
                            }
                        }
                    } else {
                        System.out.println("server.Master Response: " + response);
                    }

                } else if (input.equals("2")) {
                    // --- ADD_BALANCE ---
                    out.writeObject("ADD_BALANCE");
                    System.out.print("Enter amount to deposit: ");
                    double amountToAdd = Double.parseDouble(scanner.nextLine());

                    out.writeObject(player.getUsername());
                    out.writeDouble(amountToAdd);
                    out.flush();

                    System.out.println("server.Master Response: " + in.readObject());

                    player.setBalance(player.getBalance() + amountToAdd);
                    System.out.println("Local balance updated successfully!");
                } else if (input.equals("3")) {
                    // --- PLAY GAME ---
                    System.out.print("Enter domain.Game Name to Play: ");
                    String gameName = scanner.nextLine();
                    System.out.print("Enter Bet Amount (0 to exit game): ");
                    double bet = Double.parseDouble(scanner.nextLine());

                    if (bet == 0) {
                        break;
                    } else if (bet > player.getBalance()) {
                        System.out.println("Insufficient balance. Add balance first!");
                    } else {
                        out.writeObject("PLAY");
                        out.writeObject(player.getUsername());
                        out.writeObject(gameName);
                        out.writeDouble(bet);
                        out.flush();

                        Object response = in.readObject();
                        double win = (response instanceof Double) ? (double) response : -1.0;

                        if (response instanceof Double && win >= 0) {
                            System.out.println("You won: " + win + " FUN!");
                            player.setBalance(player.getBalance() + (win - bet));
                            player.setPlayerProfit(player.getPlayerProfit() + (win - bet));
                            System.out.println("Updated Balance: " + player.getBalance() + " FUN");
                        } else {
                            System.out.println("Play failed. Your bet was not processed.");
                        }
                    }
                } else {
                    System.out.println("Invalid choice. Please select 1, 2, 3, or 4.");
                }

            } catch (Exception e) {
                System.err.println("Connection Error: " + e.getMessage());
                System.out.println("Check if server.Master is running on port " + port);
            }
        }

        System.out.println("Closing application...");
        scanner.close();
    }
}