package server;

import domain.Game;
import util.Constants;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class Worker implements Runnable {
    private String hostIp;
    private int port;
    private Map<String, Game> games = new HashMap<>();

    public Worker(String hostIp, int port) {
        this.hostIp = hostIp;
        this.port = port;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java Worker <host_ip> <port>");
            return;
        }
        String hostIp = args[0];
        int port = Integer.parseInt(args[1]);
        new Worker(hostIp, port).run();
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName(hostIp))) {
            System.out.println("Worker started on " + hostIp + ":" + port);
            while (true) {
                Socket s = server.accept();
                new Thread(() -> handleRequest(s)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRequest(Socket s) {
        try (ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            String cmd = (String) in.readObject();
            if (cmd.equals("ADD")) {
                Game g = (Game) in.readObject();
                String safeName = g.getGameName().toLowerCase().trim().replace(" ", "_");

                try {
                    String imagePath = "asset/" + safeName + "/" + safeName + ".png";
                    File imageFile = new File(imagePath);

                    if (imageFile.exists()) {
                        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                        g.setImageBytes(imageBytes);
                        System.out.println("Image loaded successfully from: " + imagePath);
                    } else {

                        System.out.println("Warning: Image file not found at " + imagePath);
                    }
                } catch (IOException e) {
                    System.out.println("Error reading image: " + e.getMessage());
                }
                if (!g.hasValidInfo()) {
                    out.writeObject("Invalid game info");
                    return;
                }
                if (games.containsKey(g.getGameName())) {
                    out.writeObject("Game " + g.getGameName() + " already exists on Worker " + port);
                    return;
                }
                synchronized (games) {
                    games.put(g.getGameName(), g);
                }
                out.writeObject("Game " + g.getGameName() + " Added to Worker " + port);
            } else if (cmd.equals("REMOVE")) {
                String name = (String) in.readObject();
                synchronized (games) {
                    if (games.containsKey(name)) {
                        if (games.get(name).isAvailable()) {
                            games.get(name).setAvailable(false);
                            out.writeObject("Game " + name + " set to unavailable on Worker " + port);
                        } else {
                            games.get(name).setAvailable(true);
                            out.writeObject("Game " + name + " set to available on Worker " + port);
                        }
                    } else {
                        out.writeObject("Game " + name + " not found on Worker " + port);
                    }
                }
            } else if (cmd.equals("DETAILS")) {
                String name = (String) in.readObject();
                synchronized (games) {
                    if (games.containsKey(name)) {
                        Game g = games.get(name);
                        String details = "\nGame: " + g.getGameName();
                        details += "\nProvider: " + g.getProviderName();
                        details += "\nRisk Level: " + g.getRiskLevel();
                        details += "\nMin Bet: " + g.getMinBet();
                        details += "\nMax Bet: " + g.getMaxBet();
                        details += "\nStars: " + g.getStars();
                        details += "\nNumber of Votes: " + g.getNoOfVotes();
                        details += "\nAvailable: " + (g.isAvailable() ? "Yes" : "No");
                        out.writeObject(details);
                    } else {
                        out.writeObject("Game " + name + " not found on Worker " + port);
                    }
                }
            } else if (cmd.equals("EDIT")) {
                String name = (String) in.readObject();
                String risk = (String) in.readObject();
                double minBet = in.readDouble();
                double maxBet = in.readDouble();

                synchronized (games) {
                    if (games.containsKey(name)) {
                        Game g = games.get(name);
                        Game updated = new Game(g);
                        updated.setRiskLevel(risk);
                        updated.setMinBet(minBet);
                        updated.setMaxBet(maxBet);
                        updated.calculateDetails();
                        if (!updated.hasValidInfo()) {
                            out.writeObject("Invalid game info");
                            return;
                        }
                        games.put(name, updated);
                        out.writeObject("Game " + name + " updated, Worker " + port);
                    } else {
                        out.writeObject("Game " + name + " not found on Worker " + port);
                    }
                }
            } else if (cmd.equals("GET_GAME_PROFIT")) {
                String name = (String) in.readObject();
                synchronized (games) {
                    if (games.containsKey(name)) {
                        Game g = games.get(name);
                        out.writeObject(g.getGameProfit());
                    } else {
                        out.writeObject("Game " + name + " not found on Worker " + port);
                    }
                }
            } else if (cmd.equals("GET_PROVIDER_PROFIT_MAP")) {
                Map<String, Object> f = (Map<String, Object>) in.readObject();
                String queryId = (String) f.get("queryID");
                int totalWorkers = (int) f.get("totalWorkers");
                String providerName = (String) f.get("providerName");

                Map<String, Double> partialProfitMap = new HashMap<>();
                synchronized (games) {
                    for (Game g : games.values()) {
                        if (g.getProviderName().equalsIgnoreCase(providerName)) {
                            partialProfitMap.put(g.getGameName(), g.getGameProfit());
                        }
                    }
                }

                try {
                    sendProviderProfitToReducer(queryId, partialProfitMap, totalWorkers);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (cmd.equals("SEARCH_MAP")) {
                Map<String, Object> f = (Map<String, Object>) in.readObject();
                String queryId = (String) f.get("queryID");
                int totalWorkers = (int) f.get("totalWorkers");
                List<Game> found = new ArrayList<>();
                synchronized (games) {
                    for (Game g : games.values()) {
                        boolean match = true;
                        if (f.containsKey("stars") && g.getStars() < (int) f.get("stars"))
                            match = false;
                        if (f.containsKey("betCategory")
                                && !String.valueOf(f.get("betCategory")).equals(g.getBetCategory()))
                            match = false;
                        if (f.containsKey("minBet") && g.getMinBet() > (double) f.get("minBet"))
                            match = false;
                        if (f.containsKey("maxBet") && g.getMaxBet() < (double) f.get("maxBet"))
                            match = false;
                        if (f.containsKey("riskLevel")
                                && !String.valueOf(f.get("riskLevel")).equalsIgnoreCase(g.getRiskLevel()))
                            match = false;

                        if (match && g.isAvailable()) {
                            found.add(g);
                        }
                    }
                }

                try {
                    sendToReducer(queryId, found, totalWorkers);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (cmd.equals("PLAY")) {
                String querryId = (String) in.readObject();
                String name = (String) in.readObject();
                double bet = in.readDouble();

                double win = playGame(name, bet);

                System.out.println(
                        "Worker " + port + ": Player played game " + name + " with bet " + bet + ", win: " + win);

                // === ΕΝΗΜΕΡΩΣΗ MASTER (PLAY_RESULT) ===
                // Στέλνουμε ΚΑΙ gameName + bet ώστε ο Master να υπολογίσει
                // το profitDelta = bet - win και να ενημερώσει το Replica (SYNC_PROFIT).
                try (Socket sMaster = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                        ObjectOutputStream outM = new ObjectOutputStream(sMaster.getOutputStream());
                        ObjectInputStream inM = new ObjectInputStream(sMaster.getInputStream())) {
                    outM.writeObject("PLAY_RESULT");
                    outM.writeObject(querryId);
                    outM.writeObject(win); // το κέρδος του παίκτη
                    outM.writeObject(name); // gameName — χρειάζεται για SYNC_PROFIT
                    outM.writeDouble(bet); // bet — χρειάζεται για profitDelta
                    outM.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                out.writeObject("PLAY_ACK");
            } else if (cmd.equals("SYNC_PROFIT")) {
                // === ACTIVE REPLICATION: Ο Master ζητά να ενημερωθεί το gameProfit ===
                // Αυτή η εντολή φτάνει ΜΟΝΟ στο Replica, αφού ο Primary έχει παίξει.
                // Εφαρμόζουμε το ίδιο delta atomically ώστε να μην υπάρχει race condition.
                String name = (String) in.readObject();
                double profitDelta = in.readDouble();

                synchronized (games) {
                    Game g = games.get(name);
                    if (g != null) {
                        // Το synchronized(g) εξασφαλίζει ότι αν τρέχουν ταυτόχρονα
                        // πολλά SYNC_PROFIT για το ίδιο παιχνίδι, εφαρμόζονται σειριακά.
                        synchronized (g) {
                            g.setGameProfit(g.getGameProfit() + profitDelta);
                        }
                        System.out.println("[SYNC] Worker " + port + ": gameProfit of '" + name
                                + "' updated by delta " + profitDelta
                                + " -> new profit: " + g.getGameProfit());
                    } else {
                        System.err.println("[SYNC] Worker " + port + ": game '" + name + "' not found for sync.");
                    }
                }
                // ACK: conferma al Master che il sync è avvenuto
                out.writeObject("SYNC_ACK");
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double playGame(String name, double bet) throws Exception {
        Game g;
        synchronized (games) {
            g = games.get(name);
        }
        if (g == null)
            return -1;

        try (Socket s = new Socket(Constants.SRG_IP, Constants.SRG_PORT);
                ObjectOutputStream outSRG = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream inSRG = new ObjectInputStream(s.getInputStream())) {
            outSRG.writeObject(g.getHashKey());
            int R = inSRG.readInt();
            if (!SRGServer.sha256(R + g.getHashKey()).equals(inSRG.readObject()))
                return -1;

            double mult = (R % 100 == 0) ? g.getJackpot() : getMultiplier(g, R % 10);
            double win = bet * mult;
            synchronized (g) {
                g.setGameProfit(g.getGameProfit() + (bet - win));
            }
            return win;
        }
    }

    private double getMultiplier(Game g, int idx) {
        if (g.getRiskLevel().equalsIgnoreCase("low"))
            return Game.LOW_RISK[idx];
        if (g.getRiskLevel().equalsIgnoreCase("medium"))
            return Game.MED_RISK[idx];
        return Game.HIGH_RISK[idx];
    }

    private void sendToReducer(String queryId, List<Game> data, int totalWorkers) throws IOException {
        try (Socket s = new Socket(Constants.REDUCER_IP, Constants.REDUCER_PORT);
                ObjectOutputStream outR = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream inR = new ObjectInputStream(s.getInputStream())) {
            outR.writeObject("PARTIAL_RESULT");
            outR.writeObject(queryId);
            outR.writeObject(data);
            outR.writeInt(totalWorkers);
            outR.flush();
        }
    }

    private void sendProviderProfitToReducer(String queryId, Map<String, Double> profitMap, int totalWorkers)
            throws IOException {
        try (Socket s = new Socket(Constants.REDUCER_IP, Constants.REDUCER_PORT);
                ObjectOutputStream outR = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream inR = new ObjectInputStream(s.getInputStream())) {
            outR.writeObject("PARTIAL_PROFIT_RESULT");
            outR.writeObject(queryId);
            outR.writeObject(profitMap);
            outR.writeInt(totalWorkers);
            outR.flush();
        }
    }
}