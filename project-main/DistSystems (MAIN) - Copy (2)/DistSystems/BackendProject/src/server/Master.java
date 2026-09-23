package server;

import domain.Game;
import domain.Player;
import util.Constants;

import java.io.*;
import java.net.*;
import java.util.*;

public class Master {
    private static List<WorkerHolder> workerPorts = new ArrayList<>();
    private static Map<String, Player> registeredPlayers = new HashMap<>();
    private static final Map<String, ObjectOutputStream> activeClients = new HashMap<>();
    // Αποθηκεύει queryId -> replicaIdx για να γνωρίζει ο Master ποιο replica να
    // ενημερώσει μετά το PLAY_RESULT
    private static final Map<String, Integer> playReplicaMap = new HashMap<>();
    // Timeout (ms) για σύνδεση με Worker — αν δεν απαντήσει εντός 3s θεωρείται down
    private static final int WORKER_TIMEOUT_MS = 3000;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: java Master <ip1>-<port1> <ip2>-<port2>...");
            return;
        }

        for (String arg : args) {
            String[] parts = arg.split("-");
            String hostIp = parts[0];
            int port = Integer.parseInt(parts[1]);
            workerPorts.add(new WorkerHolder(hostIp, port));
            System.out.println("Registered Worker: " + hostIp + ":" + port);
        }

        int port = Constants.MASTER_PORT;
        ServerSocket server = new ServerSocket(port);
        System.out.println("Master Server Online (Port " + port + ")...");
        while (true) {
            Socket s = server.accept();
            new Thread(() -> handleClient(s)).start();
        }
    }

    private static void handleClient(Socket s) {
        ObjectOutputStream out = null;
        boolean keepAlive = false;
        try {
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            out = new ObjectOutputStream(s.getOutputStream());

            String action = (String) in.readObject();

            if (action.equals("ADD_GAME")) {
                Game g = (Game) in.readObject();
                routeRequest(g.getGameName(), "ADD", g, out);
            } else if (action.equals("REMOVE_GAME")) {
                String name = (String) in.readObject();
                routeRequest(name, "REMOVE", name, out);
            } else if (action.equals("DETAILS")) {
                String name = (String) in.readObject();
                routeRequest(name, "DETAILS", name, out);
            } else if (action.equals("EDIT_GAME")) {
                String name = (String) in.readObject();
                String risk = (String) in.readObject();
                double minBet = (Double) in.readObject();
                double maxBet = (Double) in.readObject();
                routeRequest(name, "EDIT", name, out, risk, minBet, maxBet);
            } else if (action.equals("GET_GAME_PROFIT")) {
                String name = (String) in.readObject();
                routeRequest(name, "GET_GAME_PROFIT", name, out);
            } else if (action.equals("GET_PLAYER_PROFIT")) {
                StringBuilder sb = new StringBuilder();
                double total = 0.0;
                for (Map.Entry<String, Player> entry : registeredPlayers.entrySet()) {
                    String username = entry.getKey();
                    double profit = entry.getValue().getPlayerProfit();
                    sb.append("\"").append(username).append("\": ");
                    sb.append(profit >= 0 ? "+" + profit : profit).append(", \n");
                    total += profit;
                }
                sb.append("\"Total\": ").append(total >= 0 ? "+" + total : total).append(" FUN");
                out.writeObject(sb.toString());
            } else if (action.equals("GET_PROVIDER_PROFIT")) {
                String providerName = (String) in.readObject();
                Map<String, Object> f = new HashMap<>();
                f.put("providerName", providerName);
                String queryId = UUID.randomUUID().toString();
                f.put("queryID", queryId);
                f.put("totalWorkers", workerPorts.size());

                synchronized (activeClients) {
                    activeClients.put(queryId, out);
                }
                keepAlive = true;

                for (WorkerHolder w : workerPorts) {
                    try (Socket ws = new Socket(w.ip, w.port);
                            ObjectOutputStream outW = new ObjectOutputStream(ws.getOutputStream());
                            ObjectInputStream inW = new ObjectInputStream(ws.getInputStream())) {
                        outW.writeObject("GET_PROVIDER_PROFIT_MAP");
                        outW.writeObject(f);
                        outW.flush();
                    } catch (Exception e) {
                        System.err.println("Worker " + w.port + " unreachable during broadcast.");
                    }
                }
            } else if (action.equals("SEARCH")) {
                Map<String, Object> f = (Map<String, Object>) in.readObject();
                String queryId = UUID.randomUUID().toString();
                f.put("queryID", queryId);
                f.put("totalWorkers", workerPorts.size());

                synchronized (activeClients) {
                    activeClients.put(queryId, out);
                }
                keepAlive = true;

                for (WorkerHolder w : workerPorts) {
                    try (Socket ws = new Socket(w.ip, w.port);
                            ObjectOutputStream outW = new ObjectOutputStream(ws.getOutputStream());
                            ObjectInputStream inW = new ObjectInputStream(ws.getInputStream())) {
                        outW.writeObject("SEARCH_MAP");
                        outW.writeObject(f);
                        outW.flush();
                    } catch (Exception e) {
                        System.err.println("Worker " + w.port + " unreachable during search broadcast.");
                    }
                }
            } else if (action.equals("REDUCER_RESULT")) {
                String queryId = (String) in.readObject();
                List<Game> finalList = (List<Game>) in.readObject();

                ObjectOutputStream clientOut;
                synchronized (activeClients) {
                    clientOut = activeClients.remove(queryId);
                }
                if (clientOut != null) {
                    clientOut.writeObject(finalList);
                    clientOut.flush();
                    clientOut.close();
                }
            } else if (action.equals("REDUCER_PROFIT_RESULT")) {
                String queryId = (String) in.readObject();
                Map<String, Double> resultMap = (Map<String, Double>) in.readObject();

                StringBuilder sb = new StringBuilder();
                double total = 0.0;
                for (Map.Entry<String, Double> e : resultMap.entrySet()) {
                    sb.append("\"").append(e.getKey()).append("\": ");
                    sb.append(e.getValue() >= 0 ? "+" + e.getValue() : e.getValue()).append(", \n");
                    total += e.getValue();
                }
                sb.append("\"Total\": ").append(total >= 0 ? "+" + total : total).append(" FUN");

                ObjectOutputStream clientOut;
                synchronized (activeClients) {
                    clientOut = activeClients.remove(queryId);
                }
                if (clientOut != null) {
                    clientOut.writeObject(sb.toString());
                    clientOut.flush();
                    clientOut.close();
                }
            } else if (action.equals("PLAY")) {
                String username = (String) in.readObject();
                String gameName = (String) in.readObject();
                double bet = in.readDouble();

                String queryId = UUID.randomUUID().toString();
                queryId = username + "-" + bet + "-" + queryId;
                synchronized (activeClients) {
                    activeClients.put(queryId, out);
                }
                keepAlive = true;

                // Υπολόγισε primary/replica πριν στείλεις, ώστε μετά το PLAY_RESULT
                // να ξέρουμε ποιο replica να ενημερώσουμε (SYNC_PROFIT)
                if (!workerPorts.isEmpty()) {
                    int primaryIdx = Math.abs(gameName.hashCode()) % workerPorts.size();
                    int replicaIdx = (primaryIdx + 1) % workerPorts.size();
                    synchronized (playReplicaMap) {
                        playReplicaMap.put(queryId, replicaIdx);
                    }
                }
                routeRequest(gameName, "PLAY", queryId, null, gameName, bet);
            } else if (action.equals("PLAY_RESULT")) {
                String queryId = (String) in.readObject();
                Double result = (Double) in.readObject();
                String gameName = (String) in.readObject(); // Worker στέλνει και το gameName
                double betPaid = in.readDouble(); // bet — primitive double (writeDouble)
                // profitDelta = bet - win (θετικό = κέρδος για το σπίτι)
                double profitDelta = betPaid - result;

                String[] parts = queryId.split("-");
                String username = parts[0];
                Double bet = Double.parseDouble(parts[1]);

                // Ενημέρωσε balance/profit παίκτη στον Master (in-memory)
                synchronized (registeredPlayers) {
                    Player p = registeredPlayers.get(username);
                    if (p != null) {
                        p.setPlayerProfit(p.getPlayerProfit() + (result - bet));
                        p.setBalance(p.getBalance() + result - bet);
                    }
                }

                // === ACTIVE REPLICATION: Sync profit στο Replica ===
                // Ο Primary Worker έχει ήδη ενημερώσει το gameProfit.
                // Ενημερώνουμε τώρα ΚΑΙ το Replica με το ίδιο delta.
                int replicaIdx;
                synchronized (playReplicaMap) {
                    Integer ri = playReplicaMap.remove(queryId);
                    replicaIdx = (ri != null) ? ri : -1;
                }
                if (replicaIdx >= 0 && replicaIdx < workerPorts.size()) {
                    WorkerHolder replica = workerPorts.get(replicaIdx);
                    try {
                        sendSyncProfit(replica.ip, replica.port, gameName, profitDelta);
                    } catch (IOException e) {
                        System.err.println(
                                "[WARN] Sync profit to replica " + replica.port + " failed: " + e.getMessage());
                    }
                }

                // Απάντησε στον Client
                ObjectOutputStream clientOut;
                synchronized (activeClients) {
                    clientOut = activeClients.remove(queryId);
                }
                if (clientOut != null) {
                    clientOut.writeObject(result);
                    clientOut.flush();
                    clientOut.close();
                }
            } else if (action.equals("ADD_BALANCE")) {
                String name = (String) in.readObject();
                double amount = in.readDouble();
                Player p = registeredPlayers.get(name);
                if (p == null) {
                    out.writeObject("Error! No registered player found.");

                } else {
                    p.setBalance(p.getBalance() + amount);
                    out.writeObject("Balance updated");
                }
            } else if (action.equals("GET_BALANCE")) {
                String name = (String) in.readObject();
                Player p = registeredPlayers.get(name);
                out.writeObject(p != null ? p.getBalance() : 0.0);
            } else if (action.equals("REGISTER")) {
                String name = (String) in.readObject();
                String password = (String) in.readObject();
                if (registeredPlayers.containsKey(name)) {
                    out.writeObject("REGISTER_FAIL");
                    out.writeObject("Username already exists.");
                } else {
                    Player p = new Player(name, password);
                    registeredPlayers.put(name, p);
                    out.writeObject("REGISTER_SUCCESS");
                    out.writeObject(p.getBalance());
                }
            } else if (action.equals("LOGIN")) {
                String name = (String) in.readObject();
                String pass = (String) in.readObject();
                if (registeredPlayers.containsKey(name) && registeredPlayers.get(name).getPassword().equals(pass)) {
                    out.writeObject("LOGIN_SUCCESS");
                    out.writeObject(registeredPlayers.get(name).getBalance());
                } else {
                    out.writeObject("LOGIN_FAIL");
                    out.writeObject("Invalid credentials.");
                }
            }

            if (!keepAlive) {
                out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!keepAlive) {
                try {
                    if (out != null)
                        out.close();
                    s.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Δρομολογεί ένα αίτημα στον κατάλληλο Worker με βάση consistent hashing.
     * Αν ο Primary αποτύχει (IOException / timeout), κάνει αυτόματο failover στο
     * Replica.
     * Για mutating commands (ADD, EDIT, REMOVE) κάνει replication στο Replica όταν
     * ο Primary είναι ΟΚ.
     * Για read-only commands (DETAILS, GET_GAME_PROFIT, PLAY) χρησιμοποιεί το
     * Replica ΜΟΝΟ σε failure.
     */
    private static void routeRequest(String routingKey, String cmd, Object data,
            ObjectOutputStream clientOut, Object... extra) throws Exception {
        if (workerPorts.isEmpty())
            return;

        int primaryIdx = Math.abs(routingKey.hashCode()) % workerPorts.size();
        int replicaIdx = (primaryIdx + 1) % workerPorts.size();

        WorkerHolder primary = workerPorts.get(primaryIdx);
        WorkerHolder replica = workerPorts.get(replicaIdx);

        Object response = null;
        boolean primarySuccess = false;

        // --- Δοκίμασε τον Primary ---
        if (!primary.isDown) {
            try {
                response = sendToWorker(primary.ip, primary.port, cmd, data, extra);
                primarySuccess = true;
                primary.isDown = false; // Επιστροφή σε online κατάσταση (αν ήταν θεωρητικά down)
            } catch (IOException e) {
                // Timeout ή Connection Refused → ο κόμβος έπεσε
                primary.isDown = true;
                System.out.println("[FAILOVER] Primary Worker " + primary.port
                        + " unreachable. Routing to Replica " + replica.port);
            }
        } else {
            System.out.println("[SKIP] Primary Worker " + primary.port
                    + " already marked down. Going directly to Replica " + replica.port);
        }

        if (primarySuccess) {
            // Primary ΟΚ → κάνε replication για mutating commands
            boolean isMutating = cmd.equals("ADD") || cmd.equals("EDIT") || cmd.equals("REMOVE");
            if (isMutating) {
                try {
                    sendToWorker(replica.ip, replica.port, cmd, data, extra);
                    replica.isDown = false;
                    System.out.println("[REPLICATED] " + cmd + " synced to Replica " + replica.port);
                } catch (IOException e) {
                    replica.isDown = true;
                    System.err.println("[WARN] Replication to Replica " + replica.port + " failed: " + e.getMessage());
                }
            }
            // Επέστρεψε απάντηση στον Client (για async PLAY, clientOut==null)
            if (clientOut != null)
                clientOut.writeObject(response);
        } else {
            // Primary DOWN → Failover στο Replica
            try {
                response = sendToWorker(replica.ip, replica.port, cmd, data, extra);
                replica.isDown = false;
                if (clientOut != null)
                    clientOut.writeObject(response);
            } catch (IOException e) {
                replica.isDown = true;
                System.err.println("[ERROR] Both Primary " + primary.port
                        + " and Replica " + replica.port + " are unreachable.");
                if (clientOut != null)
                    clientOut.writeObject("Error: All nodes for this data are unreachable.");
            }
        }
    }

    /**
     * Στέλνει ένα αίτημα σε έναν Worker με socket timeout ώστε να ανιχνεύεται
     * γρήγορα αν ο κόμβος είναι down, χωρίς να κρέμεται η εφαρμογή.
     */
    private static Object sendToWorker(String ip, int port, String cmd, Object data, Object... extra) throws Exception {
        Socket ws = new Socket();
        ws.connect(new InetSocketAddress(ip, port), WORKER_TIMEOUT_MS); // connection timeout
        ws.setSoTimeout(WORKER_TIMEOUT_MS); // read timeout
        try (ObjectOutputStream outW = new ObjectOutputStream(ws.getOutputStream());
                ObjectInputStream inW = new ObjectInputStream(ws.getInputStream())) {
            outW.writeObject(cmd);
            if (data != null)
                outW.writeObject(data);
            for (Object e : extra) {
                if (e instanceof Double)
                    outW.writeDouble((Double) e);
                else
                    outW.writeObject(e);
            }
            outW.flush();
            return inW.readObject();
        } finally {
            try {
                ws.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Στέλνει SYNC_PROFIT στο Replica Worker ώστε να ενημερώσει το gameProfit
     * του παιχνιδιού κατά το profitDelta (bet - win).
     * Καλείται μόνο μετά από επιτυχές PLAY_RESULT — δηλαδή μόνο όταν το primary
     * έχει ήδη επεξεργαστεί το παιχνίδι.
     */
    private static void sendSyncProfit(String ip, int port, String gameName, double profitDelta)
            throws IOException, ClassNotFoundException {
        Socket ws = new Socket();
        ws.connect(new InetSocketAddress(ip, port), WORKER_TIMEOUT_MS);
        ws.setSoTimeout(WORKER_TIMEOUT_MS);
        try (ObjectOutputStream outW = new ObjectOutputStream(ws.getOutputStream());
                ObjectInputStream inW = new ObjectInputStream(ws.getInputStream())) {
            outW.writeObject("SYNC_PROFIT");
            outW.writeObject(gameName);
            outW.writeDouble(profitDelta);
            outW.flush();

            inW.readObject();
        } finally {
            try {
                ws.close();
            } catch (IOException ignored) {
            }
        }
    }
}

class WorkerHolder {
    String ip;
    int port;
    // Χρησιμοποιείται από τον Master για να αποφύγει αχρείαστα timeouts
    // όταν ο κόμβος είναι ήδη γνωστό ότι είναι down.
    volatile boolean isDown = false;

    public WorkerHolder(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}