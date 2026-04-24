package com.quiz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class LeaderboardApp {

    private static final String REG_NO = "RA2311030010093";
    private static final String GET_URL_TEMPLATE = "https://devapigw.vidalhealthtpa.com/srm-quiz-task/quiz/messages?regNo=%s&poll=%d";
    private static final String POST_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task/quiz/submit";
    
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        System.out.println("Starting Quiz Leaderboard System...");
        System.out.println("Registration Number: " + REG_NO);

        Set<String> processedEvents = new HashSet<>();
        Map<String, Integer> participantScores = new HashMap<>();

        for (int pollIndex = 0; pollIndex < 10; pollIndex++) {
            System.out.println("\n--- Polling API: " + pollIndex + " ---");
            try {
                String url = String.format(GET_URL_TEMPLATE, REG_NO, pollIndex);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = null;
                for (int retry = 0; retry < 3; retry++) {
                    try {
                        response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        break;
                    } catch (Exception e) {
                        if (retry == 2) throw e;
                        System.err.println("  [Retry] Fetch failed for poll " + pollIndex + ". Retrying in 2s...");
                        Thread.sleep(2000);
                    }
                }

                if (response != null && response.statusCode() == 200) {
                    QuizResponse quizResponse = gson.fromJson(response.body(), QuizResponse.class);
                    if (quizResponse != null && quizResponse.events != null) {
                        for (Event event : quizResponse.events) {
                            String uniqueKey = event.roundId + "|" + event.participant;
                            if (processedEvents.contains(uniqueKey)) {
                                System.out.println("  [Duplicate] Ignored event: " + uniqueKey + " (Score: " + event.score + ")");
                            } else {
                                processedEvents.add(uniqueKey);
                                int currentScore = participantScores.getOrDefault(event.participant, 0);
                                participantScores.put(event.participant, currentScore + event.score);
                                System.out.println("  [New] Processed event: " + uniqueKey + " (Score: " + event.score + ")");
                            }
                        }
                    } else {
                        System.out.println("  No events found in this poll.");
                    }
                } else {
                    System.err.println("  Failed to fetch poll " + pollIndex + ". Status code: " + response.statusCode());
                }

                // Sleep for 5 seconds between polls, but skip sleeping after the 9th (last) poll
                if (pollIndex < 9) {
                    System.out.println("  Waiting 5 seconds before next poll...");
                    Thread.sleep(5000);
                }

            } catch (Exception e) {
                System.err.println("Error during poll " + pollIndex + ": " + e.getMessage());
            }
        }

        System.out.println("\n--- All Polls Completed ---");

        // Generate and sort leaderboard
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : participantScores.entrySet()) {
            leaderboard.add(new LeaderboardEntry(entry.getKey(), entry.getValue()));
        }

        // Sort descending by score, then ascending by participant name
        leaderboard.sort((e1, e2) -> {
            int scoreCompare = Integer.compare(e2.totalScore, e1.totalScore); // Descending
            if (scoreCompare == 0) {
                return e1.participant.compareTo(e2.participant); // Ascending alphabetically
            }
            return scoreCompare;
        });

        System.out.println("Final Leaderboard:");
        for (LeaderboardEntry entry : leaderboard) {
            System.out.println("  " + entry.participant + ": " + entry.totalScore);
        }

        // Submit the final payload
        submitLeaderboard(leaderboard);
    }

    private static void submitLeaderboard(List<LeaderboardEntry> leaderboard) {
        System.out.println("\n--- Submitting Final Leaderboard ---");
        try {
            SubmitRequest submitRequest = new SubmitRequest(REG_NO, leaderboard);
            String jsonPayload = gson.toJson(submitRequest);
            System.out.println("Payload:\n" + jsonPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(POST_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = null;
            for (int retry = 0; retry < 3; retry++) {
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    break;
                } catch (Exception e) {
                    if (retry == 2) throw e;
                    System.err.println("  [Retry] Submission failed. Retrying in 2s...");
                    Thread.sleep(2000);
                }
            }

            if (response != null) {
                System.out.println("Response Status: " + response.statusCode());
                System.out.println("Full Submit Response: " + response.body());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("\nSUCCESS: Leaderboard submitted successfully!");
                } else {
                    System.out.println("\nFAILURE: Leaderboard submission failed.");
                    SubmitResponse submitResponse = gson.fromJson(response.body(), SubmitResponse.class);
                    if (submitResponse != null && submitResponse.message != null) {
                        System.out.println("Message: " + submitResponse.message);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error submitting leaderboard: " + e.getMessage());
        }
    }

    // --- Models for Gson serialization/deserialization ---

    static class QuizResponse {
        String regNo;
        String setId;
        int pollIndex;
        List<Event> events;
    }

    static class Event {
        String roundId;
        String participant;
        int score;
    }

    static class SubmitRequest {
        String regNo;
        List<LeaderboardEntry> leaderboard;

        SubmitRequest(String regNo, List<LeaderboardEntry> leaderboard) {
            this.regNo = regNo;
            this.leaderboard = leaderboard;
        }
    }

    static class LeaderboardEntry {
        String participant;
        int totalScore;

        LeaderboardEntry(String participant, int totalScore) {
            this.participant = participant;
            this.totalScore = totalScore;
        }
    }

    static class SubmitResponse {
        boolean isCorrect;
        boolean isIdempotent;
        int submittedTotal;
        int expectedTotal;
        String message;
    }
}
