package server;

import util.Constants;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

public class SRGServer {
    private static final Map<String, RandomBuffer> buffers = new HashMap<>();

    public static void main(String[] args) throws IOException {
        int port = Constants.SRG_PORT;
        ServerSocket server = new ServerSocket(port);
        System.out.println("SRG Server Online (Port " + port +  ")...");
        while (true) {
            Socket s = server.accept();
            new Thread(() -> handleWorker(s)).start();
        }
    }

    private static void handleWorker(Socket s) {
        try (ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            String secret = (String) in.readObject();
            
            RandomBuffer buffer;
            synchronized (buffers) {
                if (!buffers.containsKey(secret)) {
                    RandomBuffer newBuffer = new RandomBuffer();
                    buffers.put(secret, newBuffer);
                    new Thread(() -> {
                        try {
                            while (true)
                                newBuffer.produce();
                        } catch (InterruptedException e) {
                        }
                    }).start();
                }
                buffer = buffers.get(secret);
            }

            int number = buffer.consume();
            out.writeInt(number);
            out.writeObject(sha256(number + secret));
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String sha256(String base) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash)
            hex.append(String.format("%02x", b));
        return hex.toString();
    }
}

class RandomBuffer {
    private final Queue<Integer> buffer = new LinkedList<>();

    public synchronized void produce() throws InterruptedException {
        while (buffer.size() == 50)
            wait();
        buffer.add(new Random().nextInt(10000));
        notifyAll();
    }

    public synchronized int consume() throws InterruptedException {
        while (buffer.isEmpty())
            wait();
        int val = buffer.poll();
        notifyAll();
        return val;
    }
}