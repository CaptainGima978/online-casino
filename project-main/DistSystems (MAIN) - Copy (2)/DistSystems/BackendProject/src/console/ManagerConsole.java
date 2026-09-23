package console;

import domain.Game;
import util.Constants;
import util.JsonParser;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ManagerConsole {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- Manager Console ---");
            System.out.println("1. Add New Game");
            System.out.println("2. Remove Game / Reset Available)");
            System.out.println("3. Show Game Details");
            System.out.println("4. Edit Game");
            System.out.println("5. Print Profit/Loss per Game");
            System.out.println("6. Print Profit/Loss per Provider");
            System.out.println("7. Print Profit/Loss per Player");
            System.out.println("0. Exit");
            System.out.print("Choice: ");
            String input = sc.nextLine();

            if (input.equals("0"))
                break;

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                continue;
            }

            try (
                    Socket masterServer = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                    ObjectOutputStream out = new ObjectOutputStream(masterServer.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(masterServer.getInputStream())) {

                switch (choice) {
                    case 1:
                        System.out.println(
                                "To add a game, the game folder must contain the game json file and game logo (optional). "
                                        +
                                        "\nGame folder must be inside the asset folder.\n");
                        System.out.print("Enter Game Name: ");
                        String name = sc.nextLine();
                        Game g = JsonParser.parseGame(name);
                        out.writeObject("ADD_GAME");
                        out.writeObject(g);
                        break;
                    case 2:
                        System.out.print("Enter Game Name to Remove: ");
                        String rName = sc.nextLine();
                        out.writeObject("REMOVE_GAME");
                        out.writeObject(rName);
                        break;
                    case 3:
                        System.out.print("Enter Game Name to Show Details: ");
                        String sName = sc.nextLine();
                        out.writeObject("DETAILS");
                        out.writeObject(sName);
                        break;
                    case 4:
                        System.out.print("Enter Game Name to Edit: ");
                        String eName = sc.nextLine();
                        System.out.print("Enter Risk Level (low/medium/high): ");
                        String risk = sc.nextLine();
                        System.out.println("Enter New Min Bet: ");
                        double minBet = sc.nextDouble();
                        System.out.println("Enter New Max Bet: ");
                        double maxBet = sc.nextDouble();
                        out.writeObject("EDIT_GAME");
                        out.writeObject(eName);
                        out.writeObject(risk);
                        out.writeObject(minBet);
                        out.writeObject(maxBet);
                        break;
                    case 5:
                        System.out.println("Enter Game Name to Get Profit: ");
                        String gpName = sc.nextLine();
                        out.writeObject("GET_GAME_PROFIT");
                        out.writeObject(gpName);
                        break;
                    case 6:
                        System.out.println("Enter Provider Name to Get Profit: ");
                        String pName = sc.nextLine();
                        out.writeObject("GET_PROVIDER_PROFIT");
                        out.writeObject(pName);
                        break;
                    case 7:
                        out.writeObject("GET_PLAYER_PROFIT");
                        break;
                    default:
                        System.out.println("Invalid choice. Please select a valid option.");
                        continue;
                }
                out.flush();
                Object response = in.readObject();
                System.out.println("Server Response: " + response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sc.close();
    }
}