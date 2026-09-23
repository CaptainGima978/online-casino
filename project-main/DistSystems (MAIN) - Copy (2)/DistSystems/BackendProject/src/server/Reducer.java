package server;

import domain.Game;
import util.Constants;

import java.io.*;
import java.net.*;
import java.util.*;

public class Reducer {

    // Timeout (ms) μέχρι ο Reducer να στείλει το μερικό αποτέλεσμα
    // αν κάποιος Worker δεν έχει απαντήσει (π.χ. έπεσε).
    private static final long REDUCE_TIMEOUT_MS = 5000;

    // --- Κοινές δομές για SEARCH queries ---
    private static final Map<String, List<Game>> queryResults = new HashMap<>();
    private static final Map<String, Integer> queryCount = new HashMap<>();
    // lock object ανά query: ο collector thread κάνει wait(), οι worker threads
    // notify()
    private static final Map<String, Object> queryLocks = new HashMap<>();

    // --- Κοινές δομές για PROFIT queries ---
    private static final Map<String, Map<String, Double>> profitResults = new HashMap<>();
    private static final Map<String, Integer> profitCount = new HashMap<>();
    private static final Map<String, Object> profitLocks = new HashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Constants.REDUCER_PORT;
        ServerSocket server = new ServerSocket(port);
        System.out.println("Reducer Online (Port " + port + ")...");
        while (true) {
            Socket s = server.accept();
            new Thread(() -> {
                try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

                    String cmd = (String) in.readObject();

                    if (cmd.equals("PARTIAL_RESULT")) {
                        String qId = (String) in.readObject();
                        List<Game> partial = (List<Game>) in.readObject();
                        int totalWorkers = in.readInt();

                        Object lock;

                        synchronized (queryResults) {
                            // Πρώτος Worker που φτάνει για αυτό το query:
                            // δημιούργησε lock + collector thread
                            if (!queryLocks.containsKey(qId)) {
                                queryLocks.put(qId, new Object());
                                queryResults.put(qId, new ArrayList<>());
                                queryCount.put(qId, 0);

                                // Ο collector thread περιμένει ως REDUCE_TIMEOUT_MS
                                // ή μέχρι να ειδοποιηθεί ότι όλοι απάντησαν.
                                final String fQId = qId;
                                final int fTotal = totalWorkers;
                                new Thread(() -> collectSearch(fQId, fTotal)).start();
                            }

                            lock = queryLocks.get(qId);

                            // Προσθέτουμε τα αποτελέσματα του Worker (dedup)
                            List<Game> currentList = queryResults.get(qId);
                            for (Game newGame : partial) {
                                boolean exists = false;
                                for (Game existing : currentList) {
                                    if (existing.getGameName().equals(newGame.getGameName())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    currentList.add(newGame);
                                }
                            }

                            int count = queryCount.get(qId) + 1;
                            queryCount.put(qId, count);

                            // Αν απάντησαν ΟΛΟΙ οι Workers, ξύπνα τον collector αμέσως
                            if (count >= totalWorkers) {
                                synchronized (lock) {
                                    lock.notifyAll();
                                }
                            }
                        }

                    } else if (cmd.equals("PARTIAL_PROFIT_RESULT")) {
                        String qId = (String) in.readObject();
                        Map<String, Double> partial = (Map<String, Double>) in.readObject();
                        int totalWorkers = in.readInt();

                        Object lock;

                        synchronized (profitResults) {
                            if (!profitLocks.containsKey(qId)) {
                                profitLocks.put(qId, new Object());
                                profitResults.put(qId, new HashMap<>());
                                profitCount.put(qId, 0);

                                final String fQId = qId;
                                final int fTotal = totalWorkers;
                                new Thread(() -> collectProfit(fQId, fTotal)).start();
                            }

                            lock = profitLocks.get(qId);

                            // Merge partial map
                            for (Map.Entry<String, Double> e : partial.entrySet()) {
                                profitResults.get(qId).put(e.getKey(), e.getValue());
                            }

                            int count = profitCount.get(qId) + 1;
                            profitCount.put(qId, count);

                            if (count >= totalWorkers) {
                                synchronized (lock) {
                                    lock.notifyAll();
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    /**
     * Collector thread για SEARCH queries.
     * Κάνει wait() στο lock του query για REDUCE_TIMEOUT_MS.
     * Ξυπνά είτε λόγω timeout (Worker έπεσε) είτε λόγω notify() (όλοι απάντησαν).
     * Σε κάθε περίπτωση στέλνει ό,τι έχει συγκεντρωθεί.
     */
    private static void collectSearch(String qId, int totalWorkers) {
        Object lock;
        synchronized (queryResults) {
            lock = queryLocks.get(qId);
        }

        synchronized (lock) {
            // Έλεγχος αν ήδη πλήρες (race: μπορεί να έχουν έρθει όλοι πριν μπούμε εδώ)
            synchronized (queryResults) {
                Integer count = queryCount.get(qId);
                if (count != null && count >= totalWorkers) {
                    sendSearchResult(qId);
                    return;
                }
            }

            try {
                lock.wait(REDUCE_TIMEOUT_MS); // Κοιμάται — ξυπνά με notify() ή timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Ξύπνησε (είτε πλήρες είτε timeout) — στείλε ό,τι έχουμε
        sendSearchResult(qId);
    }

    /** Collector thread για PROFIT queries — ίδια λογική. */
    private static void collectProfit(String qId, int totalWorkers) {
        Object lock;
        synchronized (profitResults) {
            lock = profitLocks.get(qId);
        }

        synchronized (lock) {
            synchronized (profitResults) {
                Integer count = profitCount.get(qId);
                if (count != null && count >= totalWorkers) {
                    sendProfitResult(qId);
                    return;
                }
            }

            try {
                lock.wait(REDUCE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        sendProfitResult(qId);
    }

    /**
     * Αφαιρεί τα δεδομένα του query και τα στέλνει στον Master (REDUCER_RESULT).
     * Το remove() επιστρέφει null αν ήδη στάλθηκε — double-send protection.
     */
    private static void sendSearchResult(String qId) {
        List<Game> finalRes;
        synchronized (queryResults) {
            finalRes = queryResults.remove(qId);
            queryCount.remove(qId);
            queryLocks.remove(qId);
        }
        if (finalRes == null)
            return; // Ήδη στάλθηκε

        try (Socket ms = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                ObjectOutputStream outM = new ObjectOutputStream(ms.getOutputStream())) {
            outM.writeObject("REDUCER_RESULT");
            outM.writeObject(qId);
            outM.writeObject(finalRes);
            outM.flush();
            System.out.println("[Reducer] SEARCH result sent for query " + qId
                    + " (" + finalRes.size() + " games)");
        } catch (IOException e) {
            System.err.println("[Reducer] Failed to send SEARCH result to Master: " + e.getMessage());
        }
    }

    /** Αφαιρεί τα δεδομένα και στέλνει στον Master (REDUCER_PROFIT_RESULT). */
    private static void sendProfitResult(String qId) {
        Map<String, Double> finalProfitMap;
        synchronized (profitResults) {
            finalProfitMap = profitResults.remove(qId);
            profitCount.remove(qId);
            profitLocks.remove(qId);
        }
        if (finalProfitMap == null)
            return; // Ήδη στάλθηκε

        try (Socket ms = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                ObjectOutputStream outM = new ObjectOutputStream(ms.getOutputStream())) {
            outM.writeObject("REDUCER_PROFIT_RESULT");
            outM.writeObject(qId);
            outM.writeObject(finalProfitMap);
            outM.flush();
            System.out.println("[Reducer] PROFIT result sent for query " + qId);
        } catch (IOException e) {
            System.err.println("[Reducer] Failed to send PROFIT result to Master: " + e.getMessage());
        }
    }
}