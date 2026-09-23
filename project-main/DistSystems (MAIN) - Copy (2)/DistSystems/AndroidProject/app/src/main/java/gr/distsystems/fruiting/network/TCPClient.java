package gr.distsystems.fruiting.network;

import gr.distsystems.fruiting.util.Constants;
import domain.Game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class TCPClient {

    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds

    private Socket createSocket() throws IOException {
        Socket socket = new Socket();
        socket.setKeepAlive(true);
        socket.setSoTimeout(READ_TIMEOUT);
        socket.connect(new InetSocketAddress(Constants.SERVER_IP, Constants.SERVER_PORT), CONNECTION_TIMEOUT);
        return socket;
    }

    public void registerPlayer(String username, String password, NetworkCallback<Double> callback) {
        new Thread(() -> {
            try (Socket s = createSocket();
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()))) {
                
                out.writeObject("REGISTER");
                out.writeObject(username);
                out.writeObject(password);
                out.flush();

                try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(s.getInputStream()))) {
                    Object response = in.readObject();

                    if ("REGISTER_SUCCESS".equals(response)) {
                        Double balance = (Double) in.readObject();
                        callback.onSuccess(balance);
                    } else if ("REGISTER_FAILED".equals(response)) {
                        String errorMessage = (String) in.readObject();
                        callback.onError(errorMessage);
                    }
                }
            } catch (Exception e) {
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void loginPlayer(String username, String password, NetworkCallback<Double> callback) {
        new Thread(() -> {
            try (Socket s = createSocket();
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()))) {
                
                out.writeObject("LOGIN");
                out.writeObject(username);
                out.writeObject(password);
                out.flush();

                try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(s.getInputStream()))) {
                    Object response = in.readObject();

                    if ("LOGIN_SUCCESS".equals(response)) {
                        Double balance = (Double) in.readObject();
                        callback.onSuccess(balance);
                    } else if ("LOGIN_FAILED".equals(response)) {
                        String errorMessage = (String) in.readObject();
                        callback.onError(errorMessage);
                    }
                }
            } catch (Exception e) {
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void searchGames(Map<String, Object> filters, NetworkCallback<List<Game>> callback) {
        new Thread(() -> {
            try (Socket s = createSocket();
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

                out.writeObject("SEARCH");
                out.writeObject(filters);
                out.flush();

                try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(s.getInputStream()))) {
                    Object response = in.readObject();

                    if (response instanceof List) {
                        callback.onSuccess((List<Game>) response);
                    } else {
                        callback.onError("Wrong data format from the server.");
                    }
                }
            } catch (Exception e) {
                callback.onError("Connection error: " + e.getMessage());
            }
        }).start();
    }

    public void playGame(String playerName, String gameName, double bet, NetworkCallback<Double> callback) {
        new Thread(() -> {
            try (Socket s = createSocket();
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

                out.writeObject("PLAY");
                out.writeObject(playerName);
                out.writeObject(gameName);
                out.writeDouble(bet);
                out.flush();

                try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(s.getInputStream()))) {
                    Object response = in.readObject();

                    if (response instanceof Double) {
                        double winAmount = (Double) response;
                        if (winAmount >= 0) {
                            callback.onSuccess(winAmount);
                        } else {
                            callback.onError("Bet failed. Check your balance.");
                        }
                    } else {
                        callback.onError("Unexpected response from the server.");
                    }
                }
            } catch (Exception e) {
                callback.onError("Connection error: " + e.getMessage());
            }
        }).start();
    }

    public void addBalance(String username, double amountToAdd, NetworkCallback<String> callback) {
        new Thread(() -> {
            try (Socket s = createSocket();
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

                out.writeObject("ADD_BALANCE");
                out.writeObject(username);
                out.writeDouble(amountToAdd);
                out.flush();

                try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(s.getInputStream()))) {
                    Object response = in.readObject();

                    if (response instanceof String) {
                        callback.onSuccess((String) response);
                    } else {
                        callback.onError("Error updating balance.");
                    }
                }
            } catch (Exception e) {
                callback.onError("Connection error: " + e.getMessage());
            }
        }).start();
    }

    public void getBalance(String username, NetworkCallback<Double> callback) {
        new Thread(() -> {
            try (Socket s = createSocket();
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(s.getOutputStream()))) {
                
                out.writeObject("GET_BALANCE");
                out.writeObject(username);
                out.flush();

                try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(s.getInputStream()))) {
                    Double response = (Double) in.readObject();
                    callback.onSuccess(response);
                }
            } catch (Exception e) {
                callback.onError("Connection Error: " + e.getMessage());
            }
        }).start();
    }
}
