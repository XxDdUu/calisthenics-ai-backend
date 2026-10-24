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

### 3. Build & Run Locally

```bash
# Build project
mvn clean package

# Run local Spring Boot server
mvn spring-boot:run
```

Access Swagger UI Interactive Documentation at: `http://localhost:8080/swagger-ui.html`

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

### 📁 File Storage (`/api/v1/storage`)
- `POST /api/v1/storage/upload` - Upload raw image/video media file
- `GET /api/v1/storage/files/{fileName}` - Serve/download uploaded file

---

## 📄 License

Apache 2.0 License.
