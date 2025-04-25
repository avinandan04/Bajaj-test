import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.*;

public class Main {
    static final String INIT_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
    static final String NAME = "John Doe";
    static final String REGNO = "REG12347";
    static final String EMAIL = "john@example.com";

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Create request payload
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("name", NAME);
        requestMap.put("regNo", REGNO);
        requestMap.put("email", EMAIL);

        // 2. Send initial POST to /generateWebhook
        String response = sendJsonPost(INIT_URL, mapper.writeValueAsString(requestMap), null);
        JsonNode root = mapper.readTree(response);

        // Print the raw JSON response
        System.out.println("Response JSON: " + root.toString());

        String webhook = root.get("webhook").asText();
        String token = root.get("accessToken").asText();
        JsonNode users = root.get("data").get("users");

        // 3. Process users for mutual followers
        Map<Integer, Set<Integer>> followMap = new HashMap<>();
        if (users != null) {
            for (JsonNode user : users) {
                JsonNode idNode = user.get("id");
                if (idNode != null) {
                    int id = idNode.asInt();
                    Set<Integer> follows = new HashSet<>();
                    JsonNode followsNode = user.get("follows");
                    if (followsNode != null) {
                        followsNode.forEach(f -> follows.add(f.asInt()));
                    }
                    followMap.put(id, follows);
                } else {
                    // Print the whole user object if "id" is missing
                    System.err.println("User missing 'id': " + user.toString());
                }
            }
        } else {
            System.err.println("Missing 'users' array in the response.");
        }

        Set<List<Integer>> result = new HashSet<>();
        for (Map.Entry<Integer, Set<Integer>> entry : followMap.entrySet()) {
            int id = entry.getKey();
            for (int followed : entry.getValue()) {
                if (followMap.containsKey(followed) && followMap.get(followed).contains(id) && id < followed) {
                    result.add(Arrays.asList(id, followed));
                }
            }
        }

        // 4. Prepare outcome JSON
        Map<String, Object> outcomeMap = new HashMap<>();
        outcomeMap.put("regNo", REGNO);
        outcomeMap.put("outcome", result);

        String outcomeJson = mapper.writeValueAsString(outcomeMap);

        // 5. Retry posting outcome up to 4 times
        boolean success = false;
        for (int i = 0; i < 4 && !success; i++) {
            try {
                sendJsonPost(webhook, outcomeJson, token);
                System.out.println(" Outcome successfully posted!");
                success = true;
            } catch (IOException e) {
                System.err.println(" Post failed, retrying... Attempt " + (i + 1));
                Thread.sleep(1000);
            }
        }

        if (!success) {
            System.err.println(" Failed to post outcome after 4 attempts.");
        }
    }

    static String sendJsonPost(String urlStr, String jsonPayload, String token) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) {
            conn.setRequestProperty("Authorization", token);
        }
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes());
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return br.lines().collect(Collectors.joining());
        }
    }
}
