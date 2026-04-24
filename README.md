# Quiz Leaderboard System

This is a backend integration Java application that simulates consuming API responses from an external validator (a quiz show system) and processing them correctly.

This solution demonstrates handling of duplicate data in distributed systems, ensuring idempotent processing and accurate aggregation.

## Objective
The application achieves the following:
1. **Polls** the validator GET API 10 times to collect event data (with a strict 5-second delay between polls).
2. **Deduplicates** API responses properly to prevent score inflation.
3. **Aggregates** the scores per participant only from unique events.
4. **Sorts** the generated leaderboard in descending order based on total score.
5. **Submits** the final leaderboard to the validator POST API exactly once.

## Architecture

- **Data Fetch Layer**: HTTP Client polls the external API with automatic retry logic for fault tolerance.
- **Processing Layer**:
  - Deduplication using a `HashSet`.
  - Aggregation using a `HashMap`.
- **Output Layer**:
  - Leaderboard sorting.
  - Submission via POST API with retry safety.

This mimics a real backend ingestion pipeline handling duplicate distributed data.

## Deduplication Strategy

Each event is uniquely identified using:

`roundId + "|" + participant`

This ensures that even if the same API response appears across multiple polls, it is processed only once.

Example:
Poll 1 → R1 + Alice → processed  
Poll 3 → R1 + Alice → ignored (duplicate)

## Time Complexity

- **Polling**: O(10) (Constant time for 10 API calls)
- **Event Processing**: O(n) (Where n is the number of total events)
- **Sorting**: O(p log p) (Where p is the number of unique participants)

Overall: O(n + p log p)

## Prerequisites
- **Java**: Version 11 or higher
- **Maven**: For dependency management and building the project

## Setup & Run

1. **Clone the repository**:
   ```bash
   git clone <your-repository-url>
   cd quiz-leaderboard-system
   ```

2. **Compile and Execute**:
   You can run the application directly using Maven:
   ```bash
   mvn clean compile exec:java
   ```

## Sample Output

```text
--- Polling API: 0 ---
  [New] Processed event: R1|Alice (Score: 120)
  [New] Processed event: R1|Bob (Score: 95)
  Waiting 5 seconds before next poll...

--- Polling API: 2 ---
  [Duplicate] Ignored event: R1|Alice (Score: 120)
  [New] Processed event: R2|Charlie (Score: 110)
  Waiting 5 seconds before next poll...

...

--- All Polls Completed ---
Final Leaderboard:
  Bob: 295
  Alice: 280
  Charlie: 260

--- Submitting Final Leaderboard ---
Response Status: 201
Full Submit Response: {"regNo":"RA2311030010093","totalPollsMade":10,"submittedTotal":835,"attemptCount":1}

SUCCESS: Leaderboard submitted successfully!
```
