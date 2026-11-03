# Calisthenics AI Backend Service

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-brightgreen.svg)](https://swagger.io/)

A production-ready Spring Boot 3 microservice powering the Calisthenics AI ecosystem. Provides JWT authentication, user profile management, exercise catalog reference, workout session logging, media file storage, and seamless proxy integration with the Python FastAPI `calisthenics-ai-service` for computer vision pose estimation & form analysis.

---

## 📁 Repository Structure

```
calisthenics-ai-backend/
├── src/main/java/com/calisthenics/backend/
│   ├── CalisthenicsBackendApplication.java   # Spring Boot Entrypoint
│   ├── config/                                # Security, OpenAPI, RestClient, & CORS configs
│   ├── security/                              # JWT Token Provider, Filters, Auth Controller & DTOs
│   ├── user/                                  # User Entity, Service, Repository & Controller
│   ├── exercise/                              # Exercise Catalog, Categories & Difficulty models
│   ├── analysis/                              # Pose analysis persistence & AI microservice client
│   ├── workout/                               # Workout Sessions & Log Set/Rep tracking
│   ├── workoutplan/                           # Workout Plan creation & Weekday day scheduling
│   ├── coachclient/                           # Coach-Client relationships & Plan assignments
│   ├── chat/                                  # Real-time WebSocket STOMP & REST chat service
│   └── storage/                               # Local file storage & download controller
│
├── src/main/resources/
│   ├── db/migration/                          # Flyway SQL database schema & seed migrations
│   │   ├── V1__init_schema.sql
│   │   └── V2__seed_exercises.sql
│   └── application.yml                        # Environment configuration properties
│
├── docker-compose.yml                         # Full stack compose setup (Postgres + AI + Backend)
├── Dockerfile                                 # Multi-stage JDK 21 container build
├── .env.example                               # Environment configuration template
└── README.md
```

---

## ⚡ Tech Stack & Architecture

- **Java 21 & Spring Boot 3.2.5**
- **Spring Security 6** with JWT stateless bearer tokens and **Firebase Authentication Admin SDK**
- **Spring WebSocket & STOMP Message Broker** for real-time bidirectional chat
- **Spring Data JPA & Hibernate**
- **Flyway Database Migration**
- **PostgreSQL 16**
- **Springdoc OpenAPI 3 / Swagger UI**
- **RestTemplate / HTTP Client** for FastAPI computer vision integration

---

## 🚀 Quick Start

### 1. Requirements

- JDK 21+
- Maven 3.9+
- PostgreSQL 16 (or Docker)

### 2. Environment Configuration

Copy `.env.example` to `.env` or set environment variables:

```bash
cp .env.example .env
```

To configure Firebase Authentication:
1. Download your service account JSON file from Firebase Console (`Project Settings` > `Service Accounts`).
2. Place the file at `src/main/resources/firebase-service-account.json` or update `FIREBASE_CREDENTIALS_PATH`.

To send password reset OTP emails, set `MAIL_USERNAME` and `MAIL_APP_PASSWORD` in the environment (or in an untracked local `.env` file used by Docker Compose). For `mvn spring-boot:run`, export the variables in the process environment. For Gmail, create an App Password and use the Gmail account as `MAIL_USERNAME`; do not use the account's normal password. Gmail SMTP (`smtp.gmail.com:587`) is the default. Set `MAIL_HOST` and `MAIL_PORT` when using another SMTP provider. Never commit these credentials.

### 3. Build & Run Locally

```bash
# Build project
mvn clean package

# Run local Spring Boot server
mvn spring-boot:run
```

Access Swagger UI Interactive Documentation at: `http://localhost:8080/swagger-ui.html`

A ready-to-import Postman collection is also available at [`postman/Calisthenics AI Backend.postman_collection.json`](postman/Calisthenics%20AI%20Backend.postman_collection.json) with pre-configured environments, auto-chaining token scripts, and complete endpoint coverage.

---

## 🐳 Docker Deployment

To launch the complete infrastructure (PostgreSQL database, AI microservice, and Spring Boot backend):

```bash
docker-compose up --build -d
```

---

## 📡 API Reference Summary

### 🔑 Authentication (`/api/v1/auth`)
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/login` - Authenticate and retrieve JWT token
- `POST /api/v1/auth/firebase` - Authenticate & auto-provision user using Firebase ID Token
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/forgot-password` - Request an email OTP (response does not disclose whether the email is registered)
- `POST /api/v1/auth/verify-otp` - Verify the six-digit OTP and obtain a short-lived reset authorization token
- `POST /api/v1/auth/reset-password` - Set a new password with the reset authorization token

Password reset uses one-time 6-digit OTPs that expire after five minutes. A valid OTP grants a reset token that expires after five minutes; successful password reset invalidates that token and the OTP. Failed OTP checks are limited to five attempts. OTP request throttling allows three requests per email per 15 minutes; reset submissions are limited to five per reset token per 15 minutes. HTTP rate-limit buckets are held in process memory, so they reset on application restart and are not shared across replicas; use a shared store before horizontal scaling. Flyway applies `V14__create_password_reset_otps.sql` automatically at startup.

Example request sequence:

```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com"}'

curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","otp":"123456"}'

curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H 'Content-Type: application/json' \
  -d '{"resetToken":"<reset-token>","newPassword":"NewPassword123!","confirmPassword":"NewPassword123!"}'
```

### 👤 User Management (`/api/v1/users`)
- `GET /api/v1/users/me` - Get current user profile
- `PUT /api/v1/users/me` - Update profile information
- `GET /api/v1/users` - Admin user list

### 🤸 Exercises (`/api/v1/exercises`)
- `GET /api/v1/exercises` - List all exercises (Planche, Handstand, etc.)
- `GET /api/v1/exercises/{id}` - Get exercise details by ID
- `GET /api/v1/exercises/code/{code}` - Get exercise details by code
- `POST /api/v1/exercises` - Admin create exercise

### 🤖 AI Form Analysis (`/api/v1/analysis`)
- `POST /api/v1/analysis/image` - Analyze pose image frame & store score
- `POST /api/v1/analysis/video` - Analyze video timeline hold duration & joint angles
- `GET /api/v1/analysis/history` - User's historical analysis records
- `GET /api/v1/analysis/{id}` - Retrieve analysis report details

### 🏋️ Workouts (`/api/v1/workouts`)
- `POST /api/v1/workouts` - Log new workout session with exercise logs
- `GET /api/v1/workouts` - Get workout session history
- `GET /api/v1/workouts/{id}` - Get workout session detail
- `DELETE /api/v1/workouts/{id}` - Delete workout log

### 📅 Workout Plans (`/api/v1/workout-plans`)
- `GET /api/v1/workout-plans` - List the authenticated user's plans, including each day’s ordered exercises
- `POST /api/v1/workout-plans` - Create a plan and its day/exercise assignments atomically
- `PUT /api/v1/workout-plans/{planId}` - Replace a plan’s day/exercise configuration atomically
- `GET /api/v1/workout-plans/{planId}/days/{dayId}/exercises` - List catalog exercises assigned to a plan day
- `POST /api/v1/workout-plans/{planId}/days/{dayId}/exercises` - Assign an existing catalog exercise to a day
- `PUT /api/v1/workout-plans/{planId}/days/{dayId}/exercises/{assignmentId}` - Update assignment targets and order
- `DELETE /api/v1/workout-plans/{planId}/days/{dayId}/exercises/{assignmentId}` - Remove an assignment without deleting its catalog exercise

A day accepts an arbitrary `exercises` array. Each entry is a `WorkoutPlanExercise` assignment, so workout-specific sets, reps/duration, rest, notes, and ordering never modify the reusable catalog `Exercise`.

```json
{
  "name": "Push Pull Legs",
  "splitType": "PPL",
  "days": [
    {
      "dayOfWeek": "MONDAY",
      "sessionType": "PUSH",
      "name": "Push Day",
      "exercises": [
        {
          "exerciseId": 12,
          "sets": 4,
          "reps": 8,
          "durationSeconds": null,
          "restSeconds": 120,
          "displayOrder": 1,
          "notes": "Target solid pause on lockout."
        },
        {
          "exerciseId": 13,
          "sets": 3,
          "reps": null,
          "durationSeconds": 10,
          "restSeconds": 90,
          "displayOrder": 2,
          "notes": "Hold the position with proper form."
        }
      ]
    }
  ]
}
```

Plans can contain one to seven distinct weekdays, which keeps existing full-week plans valid while allowing focused plans. Exercise lists are returned in `displayOrder`; plan create/update normalizes their stored order to contiguous `1..n`. On an update, send the assignment `id` returned by the API for unchanged exercises to retain that assignment while reordering or editing it. Omitting `id` remains supported for new exercises and older clients.

### 🤝 Coach–Client Sharing (`/api/v1/coach-client-relationships`)

All accounts keep the existing `ROLE_USER` role. A user can send a connection
request, and relationship participation determines Coach/Client permissions:

- `POST /api/v1/coach-client-relationships` - Legacy Coach-initiated request with `{"clientId": 42}`
- `GET /api/v1/coaches` - Discover other authenticated-user profiles as potential Coaches
- `GET /api/v1/coaches/{id}` - View a Coach profile
- `POST /api/v1/coach-client-relationships/requests` - A Client sends a request with `{"coachId": 42}`
- `GET /api/v1/coach-client-relationships` - List relationships for the authenticated user
- `POST /api/v1/coach-client-relationships/{id}/accept` - Accept as the requested Client
- `POST /api/v1/coach-client-relationships/{id}/reject` - Reject as the requested Client
- `POST /api/v1/coach-client-relationships/{id}/cancel` - Cancel a pending request or end an active relationship
- `POST /api/v1/coach-client-relationships/{id}/assignments` - Assign a Coach-owned plan to a Client (active relationship required)
- `GET /api/v1/coach-client-relationships/{id}/assignments` - List assignments as the Coach
- `PUT /api/v1/coach-client-relationships/{id}/assignments/{assignmentId}` - Update assignment dates/status/notes as the Coach
- `GET /api/v1/clients/me/workout-plan-assignments` - List assignments for the authenticated Client, including history

Plans are shared by reference, not copied: the assigned plan remains owned and
editable by the Coach. Ending a relationship ends its active assignments but
retains the assignment records and plan references for the Client's history.
The service serializes relationship changes by locking the Client's user row;
PostgreSQL partial unique indexes also enforce one active Coach per Client and
prevent duplicate pending/active pairs.

### 💬 Real-Time Coach–Client Chat (`/api/v1/chat` & WebSocket `/ws`)

The chat service provides 1-on-1 real-time messaging between Coaches and Clients. Chat access requires an **active coach–client relationship** between the two users.

#### 1. REST API Endpoints (`/api/v1/chat`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/chat/conversations` | List all conversations for current user (sorted by `lastMessageAt DESC`, includes unread counts) |
| `POST` | `/api/v1/chat/conversations` | Get or create a conversation with another user |
| `GET` | `/api/v1/chat/conversations/{conversationId}/messages` | Paginated message history for a conversation |
| `POST` | `/api/v1/chat/conversations/{conversationId}/messages` | Send a new message via REST |
| `POST` | `/api/v1/chat/conversations/{conversationId}/read` | Mark all messages in a conversation as read |
| `GET` | `/api/v1/chat/unread-count` | Get total unread message count across all conversations |
| `GET` | `/api/v1/chat/presence` | Get online/offline presence and last seen for conversation participants |

##### Create or Get Conversation Request
`POST /api/v1/chat/conversations`
```json
{
  "otherUserId": 2
}
```

##### List Messages Query Parameters
`GET /api/v1/chat/conversations/{conversationId}/messages?page=0&size=20&direction=asc`
- `page`: Page index (default: `0`)
- `size`: Page size (default: `20`)
- `direction`: Sort order: `asc` (chronological / oldest first) or `desc` (newest first, default: `asc`)

##### Send Message Request via HTTP
`POST /api/v1/chat/conversations/{conversationId}/messages`
```json
{
  "content": "Great workout today!"
}
```

#### 2. WebSocket & STOMP Protocol (`/ws`)

The application exposes a full-duplex STOMP over WebSocket broker for instant message delivery and live updates.

##### Endpoints & Prefixes
- **Raw WebSocket Endpoint** (Native Android / OkHttp / iOS): `ws://<host>:<port>/ws`
- **SockJS Fallback Endpoint** (Browsers / Web apps): `http://<host>:<port>/ws`
- **Application Destination Prefix** (Client to Server): `/app`
- **Broker Destinations** (Topic / Queue Subscriptions): `/topic`, `/queue`
- **User Destination Prefix**: `/user`

##### Authentication
Authenticate using the JWT Bearer token via either:
1. **HTTP Handshake Header**: `Authorization: Bearer <jwt-token>`
2. **Handshake Query Parameter**: `ws://<host>:<port>/ws?token=<jwt-token>`
3. **STOMP `CONNECT` Frame Header**:
   ```
   CONNECT
   accept-version:1.2,1.1,1.0
   heart-beat:10000,10000
   Authorization: Bearer <jwt-token>
   ```

##### Subscriptions (Server to Client)
- **Conversation Topic**: `/topic/conversations/{conversationId}`
  - Subscribe to receive real-time messages for an open conversation.
  - Automatically verifies that the connecting user is an authorized participant in the conversation.
- **Personal Notifications Queue**: `/user/queue/messages`
  - Subscribe to receive real-time incoming messages from any conversation the user participates in (useful for background notifications and chat badges).
- **Presence Updates Queue**: `/user/queue/presence`
  - Subscribe to receive real-time online/offline status updates and last-seen timestamps of your chat partners.

##### Sending Messages & Heartbeats via WebSocket (Client to Server)
- **Send Message**: Send a STOMP `SEND` frame to `/app/chat.send` with JSON body:
  ```json
  {
    "conversationId": 1,
    "content": "Hey coach, ready for today's session!"
  }
  ```
- **Presence Heartbeat**: Send an empty STOMP `SEND` frame to `/app/presence.heartbeat` periodically (e.g., every 25 seconds) to maintain active online status.

##### Broadcast Message Format (`ChatMessageResponse`)
Messages broadcast over `/topic/conversations/{id}` and `/user/queue/messages` use the following schema:
```json
{
  "id": 101,
  "conversationId": 1,
  "senderId": 5,
  "senderName": "Coach John",
  "senderAvatarUrl": "https://example.com/avatar.jpg",
  "content": "Hey coach, ready for today's session!",
  "messageType": "TEXT",
  "createdAt": "2026-10-28T10:00:00Z"
}
```

##### Mark Conversation Read
When opening a conversation, call `POST /api/v1/chat/conversations/{conversationId}/read` to clear unread counts.

### 📁 File Storage (`/api/v1/storage`)
- `POST /api/v1/storage/upload` - Upload raw image/video media file
- `GET /api/v1/storage/files/{fileName}` - Serve/download uploaded file

---

## 📄 License

Apache 2.0 License.
