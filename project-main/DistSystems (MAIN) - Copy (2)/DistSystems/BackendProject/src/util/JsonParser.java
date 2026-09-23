package util;

import domain.Game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonParser {

    public static Game parseGame(String fileName) {
        String json = "";
        try {
            json = Files.readString(Path.of("./asset/" + fileName + "/" + fileName + ".json"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] parts = json.replaceAll("[{}\"]", "").split(",");
        String id = "", gameName = "", providerName = "", gameLogo = "logo.png", riskLevel = "low", theme = "",
                hashKey = "key123";
        int stars = 0, noOfVotes = 0;
        double minBet = 0.0, maxBet = 100.0;

        for (String part : parts) {
            String[] kv = part.split(":");
            if (kv.length < 2)
                continue;
            switch (kv[0].trim().toLowerCase()) {
                case "id":
                    id = kv[1].trim();
                    break;
                case "gamename":
                    gameName = kv[1].trim();
                    break;
                case "providername":
                    providerName = kv[1].trim();
                    break;
                case "gamelogo":
                    gameLogo = kv[1].trim();
                    break;
                case "risklevel":
                    riskLevel = kv[1].trim();
                    break;
                case "stars":
                    stars = Integer.parseInt(kv[1].trim());
                    break;
                case "noofvotes":
                    noOfVotes = Integer.parseInt(kv[1].trim());
                    break;
                case "minbet":
                    minBet = Double.parseDouble(kv[1].trim());
                    break;
                case "maxbet":
                    maxBet = Double.parseDouble(kv[1].trim());
                    break;
                case "theme":
                    theme = kv[1].trim();
                    break;
                case "hashkey":
                    hashKey = kv[1].trim();
                    break;
            }
        }
        return new Game(id, gameName, providerName, stars, noOfVotes, gameLogo, minBet, maxBet, riskLevel, theme,
                hashKey);
    }

}
