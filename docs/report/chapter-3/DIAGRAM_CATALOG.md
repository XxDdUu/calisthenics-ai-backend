# DANH MỤC VÀ ĐẶC TẢ CHI TIẾT SƠ ĐỒ THIẾT KẾ HỆ THỐNG
# CHƯƠNG 3: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG - CALISTHENICS AI

> **Tài liệu đặc tả kiến trúc thị giác (Visual Architecture Catalog)**  
> **Dự án:** Hệ thống Hỗ trợ và Phân tích Kỹ thuật Tập luyện Calisthenics Thông minh (Calisthenics AI)  
> **Thư mục lưu trữ:** `docs/report/chapter-3/`  
> **Định dạng sẵn có:** Mã nguồn (`sources/`), Vector đồ họa (`svg/`), Ảnh phân giải cao 300 DPI (`png/`)

---

## 1. BẢNG TỔNG HỢP DANH MỤC SƠ ĐỒ CHƯƠNG 3 (DIAGRAM CATALOG MATRIX)

| Số hiệu hình | Tên sơ đồ (Vietnamese & English) | Mục đích thiết kế | Minh chứng mã nguồn / CSDL | Trạng thái hiện thực |
|---|---|---|---|---|
| **Hình 3.1** | **Sơ đồ ngữ cảnh hệ thống**<br/>*(System Context Diagram)* | Xác định ranh giới hệ thống, các tác nhân (Client, Coach, Admin) và các dịch vụ ngoài (Google OAuth2, Cloudflare Tunnel, SMTP). | `SecurityConfig.java`, `docker-compose.yml`, `cloudflared.service`, `GoogleAuthService.java` | **Đã hiện thực 100%** |
| **Hình 3.2** | **Sơ đồ Use Case tổng quát**<br/>*(General Use Case Diagram)* | Phân rã 20 ca sử dụng nghiệp vụ cốt lõi gắn liền với 3 vai trò và các quan hệ `<<include>>`, `<<extend>>`. | 26 Controllers (`AuthController`, `WorkoutSessionController`, `AnalysisController`, v.v.) | **Đã hiện thực 100%** |
| **Hình 3.3** | **Kiến trúc phân tầng tổng thể**<br/>*(Overall System Architecture)* | Mô tả phân tầng chi tiết: Android (Presentation, ViewModel, Data) $\leftrightarrow$ Ingress $\leftrightarrow$ Spring Boot 3 $\leftrightarrow$ FastAPI AI $\leftrightarrow$ PostgreSQL 16. | `NavGraph.kt`, `TokenManager.kt`, `AiServiceClient.java`, `yolo.py`, `analyzers/*` | **Đã hiện thực 100%** |
| **Hình 3.4** | **Sơ đồ triển khai hệ thống**<br/>*(System Deployment Diagram)* | Kiến trúc vật lý và mạng ảo: Thiết bị Android, Cloudflare Edge, VPS Ubuntu host `fourier`, 3 Docker containers và persistent volumes. | `docker-compose.yml`, `Dockerfile`, `VPS_AUDIT.md`, `systemd cloudflared` | **Đã hiện thực 100%** |
| **Hình 3.5** | **Sơ đồ thực thể liên kết CSDL**<br/>*(Database Entity Relationship Diagram - ERD)* | Mô tả toàn bộ 22 bảng CSDL quan hệ chuẩn hóa, kiểu dữ liệu, khóa chính PK, khóa ngoại FK và lực lượng quan hệ (Cardinalities). | Flyway Migrations `V1__init_schema.sql` đến `V22__add_media_and_equipment_to_exercises.sql` | **Đã hiện thực 100%** |
| **Hình 3.6** | **Sơ đồ tuần tự xác thực người dùng**<br/>*(Authentication Sequence Diagram)* | Mô tả quy trình Đăng nhập, mã hóa BCrypt, cấp phát cặp JWT (Access 60m / Refresh 30d), lưu trữ an toàn và cơ chế tự động làm mới token khi gặp HTTP 401. | `AuthController.java`, `JwtTokenProvider.java`, `AuthInterceptor.kt`, `TokenAuthenticator.kt` | **Đã hiện thực 100%** |
| **Hình 3.7** | **Sơ đồ tuần tự thực hiện buổi tập**<br/>*(Workout Session Sequence Diagram)* | Luồng ghi nhận phiên tập: Start/Resume, ghi log chi tiết từng hiệp (Sets), kích hoạt đếm giờ nghỉ (Rest Timer), tính Volume Load và tự động xác lập kỷ lục PR. | `PersistentWorkoutSessionController.java`, `PersistentWorkoutSessionServiceImpl.java`, `PersonalRecordService.java` | **Đã hiện thực 100%** |
| **Hình 3.8** | **Sơ đồ tuần tự phân tích tư thế bằng AI**<br/>*(AI Analysis Sequence Diagram)* | Luồng xử lý video end-to-end: Android upload $\rightarrow$ Backend trung chuyển $\rightarrow$ FastAPI trích xuất 5 FPS $\rightarrow$ YOLOv8 trích xuất 17 keypoints $\rightarrow$ EMA smoothing $\rightarrow$ Analyzers chấm điểm $\rightarrow$ Lưu JSONB. | `AnalysisController.java`, `AiServiceClient.java`, `analysis.py`, `analyzer.py`, `planche.py` | **Đã hiện thực 100%** |
| **Hình 3.9** | **Lưu đồ giải thuật phân tích kỹ thuật bài tập**<br/>*(Exercise Form Analysis Flowchart)* | Lưu đồ chi tiết giải thuật AI: Kiểm tra ảnh hợp lệ $\rightarrow$ YOLOv8 $\rightarrow$ Kiểm tra người $\rightarrow$ Lọc tin cậy ($c \ge 0.5$) $\rightarrow$ EMA ($\alpha=0.65$) $\rightarrow$ Đánh giá góc cơ sinh học $\rightarrow$ Phát hiện lỗi sai $\rightarrow$ Chấm điểm (0-100). | `app/biomechanics/angles.py`, `smoothing.py`, `confidence.py`, `planche.py`, `pull_up.py` | **Đã hiện thực 100%** |
| **Hình 3.10** | **Sơ đồ hoạt động quản lý bài tập của Admin**<br/>*(Admin Exercise Management Activity Diagram)* | Quy trình quản trị danh mục bài tập: Xem danh sách phân trang, tìm kiếm/lọc, thêm mới, sửa đổi thông tin, bật/tắt kích hoạt và cơ chế xóa mềm an toàn khi có ràng buộc. | `AdminExerciseController.java`, `AdminExerciseServiceImpl.java`, `CreateAdminExerciseRequest.java` | **Đã hiện thực 100%** |

---

## 2. ĐẶC TẢ CHI TIẾT TỪNG SƠ ĐỒ (DETAILED SPECIFICATIONS)

### 2.1. Hình 3.1: Sơ đồ ngữ cảnh hệ thống (System Context Diagram)
- **Tập tin nguồn:** `sources/system-context.mmd`
- **Tập tin xuất bản:** `svg/system-context.svg`, `png/system-context.png`
- **Ý nghĩa & Bản chất:**  
  Sơ đồ thể hiện hệ thống Calisthenics AI dưới góc nhìn hộp đen (Black-box View) ở mức khái quát cao nhất. Xác lập rõ ràng ranh giới giữa hệ sinh thái phần mềm Calisthenics AI với 3 nhóm tác nhân người dùng và 3 hệ thống/dịch vụ bên ngoài.
- **Minh chứng mã nguồn:**
  - `com.calisthenics.backend.security.SecurityConfig`: Cấu hình nhận diện 3 Roles (`ROLE_USER`/`ROLE_CLIENT`, `ROLE_COACH`, `ROLE_ADMIN`).
  - `com.calisthenics.backend.security.GoogleAuthService`: Kết nối Google Identity API xác thực Google ID Token SSO.
  - Cấu hình Cloudflare Tunnel trên máy chủ VPS Ubuntu (`cloudflared.service`, Tunnel ID: `d7223a50-bdad-4eaf-82be-e7a149c7e21a`, Hostname: `api.sky-enterprise.id.vn`).
  - Cấu hình SMTP Mail trong `application.yml` (`spring.mail.host=smtp.gmail.com`, cổng 587) gửi OTP khôi phục mật khẩu.
- **Dòng dữ liệu & Luồng tương tác:**
  - Client / Coach / Admin tương tác thông qua Ứng dụng Android.
  - Ứng dụng Android gửi HTTPS REST và WSS STOMP qua mạng Cloudflare Edge, Cloudflare Tunnel chuyển tiếp vào cổng nội bộ `8082`.
  - Backend Spring Boot điều phối lưu trữ vào PostgreSQL 16 (cổng 5432), tệp phương tiện vào `/app/uploads`, và chuyển tiếp các tác vụ nặng về thị giác máy tính sang FastAPI AI Service (cổng 8000).

---

### 2.2. Hình 3.2: Sơ đồ Use Case tổng quát (General Use Case Diagram)
- **Tập tin nguồn:** `sources/use-case.puml`, `sources/use-case.mmd`
- **Tập tin xuất bản:** `svg/use-case.svg`, `png/use-case.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả đầy đủ các chức năng nghiệp vụ của hệ thống được phân quyền cho 3 tác nhân. Mọi Use Case trên sơ đồ đều được hỗ trợ bởi các Controller và Service thực tế trong mã nguồn, không vẽ các tính năng dự kiến hoặc chưa hoàn thiện.
- **Phân rã các gói chức năng:**
  1. *Phân hệ Xác thực & Hồ sơ cá nhân (UC01 - UC04)*: Đăng ký, đăng nhập Local/Google SSO, quản lý thông số hình thể ban đầu, khôi phục mật khẩu bằng OTP 6 chữ số.
  2. *Phân hệ Thư viện & Giáo án tập luyện (UC05 - UC08)*: Tra cứu bài tập phân loại theo động tác và cấp độ, HLV thiết kế giáo án mẫu (`workout_plans`), gán giáo án cho học viên với lịch biểu linh hoạt, học viên xem lịch tập trong ngày.
  3. *Phân hệ Thực hiện Tập luyện & Phân tích AI (UC09 - UC12)*: Thực hiện phiên tập trực tiếp (ghi log sets, reps, weight, RPE), đồng hồ đếm ngược thời gian nghỉ (`<<include>> UC10`), phân tích video/ảnh kỹ thuật bằng AI (`<<extend>> UC11`), xem lịch sử và biểu đồ khối lượng Volume.
  4. *Phân hệ Theo dõi Chỉ số & Tương tác (UC13 - UC17)*: Ghi nhận số đo cơ thể (`client_body_measurements`), tự động xác lập và chia sẻ kỷ lục cá nhân (`<<extend>> UC14`), quản lý mối quan hệ Coach - Client, nhắn tin thời gian thực qua giao thức STOMP WebSocket, HLV nhận xét buổi tập.
  5. *Phân hệ Quản trị Hệ thống (UC18 - UC20)*: Admin quản trị danh mục bài tập chuẩn (CRUD, Active/Deactive, Soft delete), quản lý người dùng, giám sát các chỉ số sức khỏe của máy chủ qua Actuator.

---

### 2.3. Hình 3.3: Kiến trúc phân tầng tổng thể hệ thống (Overall System Architecture)
- **Tập tin nguồn:** `sources/architecture.mmd`
- **Tập tin xuất bản:** `svg/architecture.svg`, `png/architecture.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả kiến trúc phân tầng chuẩn mực theo mô hình phân tán 3 tầng (3-Tier Distributed Architecture). Thể hiện sự phân tách trách nhiệm rõ ràng (Separation of Concerns) giữa tầng giao diện khách (Android), tầng cổng mạng (Cloudflare Ingress), tầng nghiệp vụ & điều phối dữ liệu (Spring Boot 3), tầng trí tuệ nhân tạo (FastAPI) và tầng lưu trữ bền vững (PostgreSQL).
- **Minh chứng mã nguồn:**
  - *Android Data Layer*: `TokenManager.kt` dùng `EncryptedSharedPreferences`, `AuthInterceptor.kt` tự động tiêm `Bearer token`, `TokenAuthenticator.kt` tự động bắt mã 401 để refresh token và replay request. `ChatWebSocketClient.kt` thiết lập kết nối STOMP.
  - *Backend Filter & Security*: `JwtAuthenticationFilter.java` giải mã token, `SecurityConfig.java` kiểm tra quyền theo tiền tố `ROLE_`.
  - *Backend Service Layer*: `AiServiceClient.java` sử dụng `RestTemplate` gửi Multipart request tới Python AI service tại `http://ai-service:8000`.
  - *AI Biomechanics Engine*: `app/biomechanics/angles.py` tính góc vector 2D, `smoothing.py` cài đặt bộ lọc EMA $\alpha=0.65$, `analyzers/` chứa các giải thuật chuyên biệt cho Planche, Front Lever, Handstand, Pull-up, Dips, Muscle-up.

---

### 2.4. Hình 3.4: Sơ đồ triển khai hệ thống (Deployment Diagram)
- **Tập tin nguồn:** `sources/deployment.puml`, `sources/deployment.mmd`
- **Tập tin xuất bản:** `svg/deployment.svg`, `png/deployment.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả cấu hình triển khai thực tế trên môi trường sản xuất (Production VPS). Minh họa cách thức đóng gói ứng dụng di động dạng tệp APK chạy trên smartphone Android API 26+, kết nối qua mạng biên Cloudflare, và hạ tầng ảo hóa Docker Compose trên máy chủ ảo cá nhân Ubuntu 22.04 LTS (Host name: `fourier`).
- **Minh chứng hạ tầng:**
  - Cấu hình tệp `docker-compose.yml`:
    - Dịch vụ `postgres`: Image `postgres:16-alpine`, cổng nội bộ 5432, phân vùng dữ liệu bền vững `postgres_data:/var/lib/postgresql/data`.
    - Dịch vụ `ai-service`: Build từ Dockerfile Python 3.11-slim, cổng 8000, tải mô hình trọng số `yolov8n-pose.pt`.
    - Dịch vụ `backend`: Build từ OpenJDK 21, cổng 8080 (ánh xạ cổng máy chủ `8082:8080`), phân vùng tệp media `media_uploads:/app/uploads`.
  - Dịch vụ hệ điều hành `cloudflared.service`: Tạo đường hầm mã hóa TLS Zero Trust từ mạng biên Cloudflare trỏ trực tiếp vào `http://localhost:8082`, không cần mở bất kỳ cổng firewall công khai nào trên VPS.

---

### 2.5. Hình 3.5: Sơ đồ Thực thể - Quan hệ Cơ sở dữ liệu (Database ERD)
- **Tập tin nguồn:** `sources/database-erd.mmd`
- **Tập tin xuất bản:** `svg/database-erd.svg`, `png/database-erd.png`
- **Ý nghĩa & Bản chất:**  
  Biểu diễn chính xác lược đồ CSDL quan hệ chuẩn hóa gồm 22 thực thể được tạo ra qua 22 migrations của Flyway (`V1__init_schema.sql` đến `V22__add_media_and_equipment_to_exercises.sql`).
- **Chi tiết các thực thể trọng yếu:**
  - `users`: Bảng người dùng cơ sở, phân loại quyền (`ROLE_USER`, `ROLE_COACH`, `ROLE_ADMIN`), hỗ trợ `google_id` và `firebase_uid`.
  - `client_profiles` & `coach_profiles`: Mở rộng 1-1 với `users`, lưu thông số thể hình và năng lực HLV.
  - `exercises`: Danh mục bài tập chuẩn với trường `code` duy nhất, phân loại nhóm cơ (`PUSH`, `PULL`, `CORE`), độ khó, dụng cụ (`equipment`) và đường dẫn video mẫu (`media_url`).
  - `workout_plans` $\rightarrow$ `workout_plan_days` $\rightarrow$ `workout_plan_exercises`: Cấu trúc phân cấp 3 tầng của giáo án tập luyện chuẩn mực.
  - `coach_client_relationships` $\rightarrow$ `workout_plan_assignments`: Quản lý việc ký hợp đồng huấn luyện và phân bổ giáo án có thời hạn.
  - `workout_sessions` $\rightarrow$ `workout_session_exercises` $\rightarrow$ `workout_session_sets`: Cấu trúc lưu vết buổi tập thực tế bền vững. Trường `version` trong `workout_session_exercises` hỗ trợ khóa lạc quan (Optimistic Locking) chống xung đột cập nhật đồng thời.
  - `analyses`: Lưu trữ kết quả phân tích AI kèm dữ liệu tọa độ bán cấu trúc `jsonb keypoints_json`.
  - `client_body_measurements`: Lưu trữ số đo cơ thể theo thời gian với độ chính xác cao `NUMERIC(5, 1)`. Chỉ mục hỗn hợp `idx_client_body_measurements_client_date` trên `(client_id, recorded_date)` tối ưu hóa việc vẽ biểu đồ tiến độ.
  - `chat_conversations` $\rightarrow$ `chat_messages`: Hệ thống tin nhắn thời gian thực hỗ trợ `client_message_id` duy nhất chống trùng lặp tin nhắn (Idempotency).

---

### 2.6. Hình 3.6: Sơ đồ tuần tự xác thực người dùng (Authentication Sequence Diagram)
- **Tập tin nguồn:** `sources/authentication-sequence.puml`, `sources/authentication-sequence.mmd`
- **Tập tin xuất bản:** `svg/authentication-sequence.svg`, `png/authentication-sequence.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả chi tiết 3 giai đoạn của vòng đời xác thực trong ứng dụng:
  1. *Giai đoạn 1: Đăng nhập & Cấp phát Token*: Người dùng nhập mật khẩu, Backend kiểm tra mã băm BCrypt qua `AuthenticationManager`. Khi hợp lệ, `JwtTokenProvider` tạo Access Token (hạn dùng 60 phút) và Refresh Token (hạn dùng 30 ngày). Android lưu an toàn vào `EncryptedSharedPreferences` qua `TokenManager`.
  2. *Giai đoạn 2: Truy cập tài nguyên bảo vệ*: `AuthInterceptor` tự động đính kèm `Authorization: Bearer <accessToken>`. Backend `JwtAuthenticationFilter` phân giải Claims và gán `SecurityContext`.
  3. *Giai đoạn 3: Tự động làm mới Token (Transparent 401 Refresh)*: Khi Access Token hết hạn, `TokenAuthenticator` chặn mã lỗi HTTP 401, tự động gọi API `POST /api/v1/auth/refresh-token` bằng Refresh Token để nhận cặp token mới, cập nhật bộ nhớ máy và tự động phát lại (replay) request ban đầu mà người dùng không bị gián đoạn trải nghiệm.

---

### 2.7. Hình 3.7: Sơ đồ tuần tự thực hiện buổi tập (Workout Session Sequence Diagram)
- **Tập tin nguồn:** `sources/workout-session-sequence.puml`, `sources/workout-session-sequence.mmd`
- **Tập tin xuất bản:** `svg/workout-session-sequence.svg`, `png/workout-session-sequence.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả chu trình một buổi tập thực tế của học viên từ khi mở giáo án đến khi hoàn tất:
  1. *Khởi tạo hoặc tiếp tục (Start / Resume)*: Gọi `POST /api/v1/workout-sessions`. Hệ thống kiểm tra xem có buổi tập nào đang ở trạng thái `IN_PROGRESS` hay không để khôi phục hoặc tạo mới phiên tập kèm danh sách các bài tập và hiệp tập tương ứng.
  2. *Ghi nhận từng hiệp tập (Log Set)*: Khi học viên hoàn thành một hiệp (ví dụ: 10 lần kéo xà với RPE 8), gọi `PUT /api/v1/workout-sessions/{sId}/exercises/{eId}/sets/{setId}`. Backend cập nhật `is_completed = true`. Android tự động kích hoạt đồng hồ đếm ngược thời gian nghỉ (Rest Countdown Timer: 90s).
  3. *Kết thúc buổi tập & Tự động ghi nhận kỷ lục (Finish & PR Detection)*: Gọi `POST /api/v1/workout-sessions/{sessionId}/finish`. Backend tính tổng thời gian, tính tổng khối lượng tải trọng tập luyện (Volume Load), cập nhật trạng thái `COMPLETED`, và kích hoạt `PersonalRecordService`. Dịch vụ so sánh thành tích từng hiệp với bảng `personal_records`; nếu vượt kỷ lục cũ (Max Reps hoặc Max Hold Time), tự động chèn bản ghi kỷ lục mới và trả về cờ `newPrAchieved = true`.

---

### 2.8. Hình 3.8: Sơ đồ tuần tự phân tích tư thế bằng AI (AI Analysis Sequence Diagram)
- **Tập tin nguồn:** `sources/ai-analysis-sequence.puml`, `sources/ai-analysis-sequence.mmd`
- **Tập tin xuất bản:** `svg/ai-analysis-sequence.svg`, `png/ai-analysis-sequence.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả toàn bộ luồng truyền tải dữ liệu và tính toán phân tích tư thế bài tập từ video:
  1. Học viên chọn tệp video (ví dụ bài tập Planche) và bấm "Phân tích AI".
  2. Android gửi Multipart request `POST /api/v1/analysis/video` đến Spring Boot Backend.
  3. Backend lưu tệp video vào kho lưu trữ an toàn `/app/uploads/videos/`, sau đó `AiServiceClient` chuyển tiếp tệp video sang FastAPI AI Service (`POST http://ai-service:8000/api/v1/analysis/video?sample_fps=5`).
  4. FastAPI sử dụng OpenCV trích xuất khung hình định kỳ ở tần số lấy mẫu 5 FPS.
  5. Mỗi khung hình được đưa qua mô hình YOLOv8-pose (`yolov8n-pose.pt`) để phát hiện người và trích xuất 17 điểm then chốt COCO.
  6. Điểm khớp được lọc ngưỡng tin cậy ($c \ge 0.5$) và làm mượt theo thời gian bằng bộ lọc EMA ($\alpha = 0.65$).
  7. Dữ liệu khớp đã làm mượt được chuyển vào bộ phân tích chuyên biệt (`PlancheAnalyzer`): tính góc khóa khuỷu ($\theta_{\text{elbow}}$), góc đổ vai ($\phi_{\text{lean}}$), độ nghiêng thân người ($\gamma_{\text{tilt}}$), xác định cấp độ tiến trình (Tuck / Straddle / Full), và đánh giá thời gian giữ chuẩn (Valid Hold).
  8. Kết thúc video, AI Service tổng hợp thời gian giữ chuẩn, danh sách lỗi kỹ thuật, điểm số form (0-100), và nhận xét phản hồi dạng JSON trả về cho Backend.
  9. Backend lưu kết quả vào bảng `analyses` trong PostgreSQL và trả về cho Android hiển thị màn hình báo cáo phân tích chi tiết.

---

### 2.9. Hình 3.9: Lưu đồ giải thuật phân tích kỹ thuật bài tập (Exercise Form Analysis Flowchart)
- **Tập tin nguồn:** `sources/ai-analysis-flowchart.mmd`
- **Tập tin xuất bản:** `svg/ai-analysis-flowchart.svg`, `png/ai-analysis-flowchart.png`
- **Ý nghĩa & Bản chất:**  
  Lưu đồ thuật toán chi tiết bên trong dịch vụ AI, thể hiện tường minh tất cả các rẽ nhánh logic và các điều kiện bất thường:
  - *Kiểm tra dữ liệu đầu vào*: Nếu tệp rỗng hoặc sai định dạng $\rightarrow$ trả về HTTP 400 Bad Request.
  - *Kiểm tra phát hiện người*: Nếu không có người trong ảnh $\rightarrow$ trả về trạng thái `NO_PERSON_DETECTED`, điểm số = 0, phản hồi rõ ràng không gây hiểu lầm.
  - *Kiểm tra độ tin cậy khớp*: Nếu $c_{\text{avg}} < 0.5$ hoặc các khớp then chốt bị che khuất $\rightarrow$ trả về trạng thái `LOW_CONFIDENCE`, điểm số = 0, cảnh báo thiếu sáng hoặc bị che khuất.
  - *Làm mượt tín hiệu*: Áp dụng công thức EMA $P_t' = 0.65 P_t + 0.35 P_{t-1}'$ loại bỏ rung lắc camera.
  - *Rẽ nhánh động tác*:
    - **Planche**: Đánh giá 3 tiêu chuẩn vàng: Khóa khuỷu $\ge 165^\circ$, Đổ vai $\ge 15^\circ$, Độ lệch thân so với phương ngang $\le 15^\circ$.
    - **Pull-up**: Máy trạng thái 4 pha (Dead Hang $\rightarrow$ Ascending $\rightarrow$ Top cằm qua xà $\rightarrow$ Descending), kiểm tra đối xứng 2 tay $|\theta_L - \theta_R| \le 15^\circ$.
    - **Các bài tập khác (Front Lever, Handstand, Dips, Muscle-up)**: Đánh giá góc mở nách, độ sâu Dip, và phát hiện lỗi lệch tay "Chicken-Wing".
  - *Chấm điểm & Phản hồi*: Trừ điểm dựa trên số lượng và mức độ nghiêm trọng của lỗi sai, cộng điểm cho thời gian giữ chuẩn; tùy chọn vẽ khung xương (draw overlay) trước khi đóng gói JSON.

---

### 2.10. Hình 3.10: Sơ đồ hoạt động quản lý bài tập của Admin (Admin Exercise Management Activity Diagram)
- **Tập tin nguồn:** `sources/admin-exercise-activity.mmd`
- **Tập tin xuất bản:** `svg/admin-exercise-activity.svg`, `png/admin-exercise-activity.png`
- **Ý nghĩa & Bản chất:**  
  Mô tả toàn bộ các luồng thao tác của Quản trị viên trên phân hệ quản lý bài tập (`AdminExerciseController.java`):
  1. Đăng nhập thành công với quyền `ROLE_ADMIN`, mở Bảng điều khiển Quản trị.
  2. Gửi yêu cầu `GET /api/v1/admin/exercises` để tải danh sách phân trang (kèm tìm kiếm, lọc theo nhóm cơ, độ khó, trạng thái kích hoạt).
  3. Lựa chọn các hành động:
     - *Thêm mới bài tập*: Nhập thông tin, kiểm tra tính duy nhất của mã bài tập (`code`), gửi `POST /api/v1/admin/exercises` (trả về 201 Created).
     - *Chỉnh sửa bài tập*: Tải chi tiết và thống kê sử dụng `GET /api/v1/admin/exercises/{id}`, cập nhật dữ liệu qua `PUT /api/v1/admin/exercises/{id}`.
     - *Bật/Tắt kích hoạt*: Gửi `PATCH /api/v1/admin/exercises/{id}/status` với `{ active: true/false }`, cập nhật tức thì trên giao diện bảng.
     - *Xóa bài tập an toàn*: Gửi `DELETE /api/v1/admin/exercises/{id}?softDelete=true`. Hệ thống kiểm tra ràng buộc khóa ngoại; nếu bài tập đã từng được dùng trong giáo án hoặc buổi tập của người dùng, tự động chuyển sang chế độ Xóa mềm (`is_active = false`) để bảo toàn tính toàn vẹn dữ liệu lịch sử.

---

## 3. HƯỚNG DẪN TÍCH HỢP VÀO BÁO CÁO MICROSOFT WORD

1. **Chèn ảnh phân giải cao (PNG)**:
   - Các tệp trong thư mục `png/` đã được render ở độ phân giải cao (tương đương 300 DPI, chiều ngang từ 1100px đến 2500px).
   - Trong Microsoft Word, chọn **Insert > Pictures > This Device...**, trỏ tới thư mục `docs/report/chapter-3/png/` và chọn ảnh tương ứng.
   - Căn lề giữa cho ảnh, chọn chế độ hiển thị **In Line with Text**.
2. **Chèn phụ chú học thuật chuẩn hóa**:
   - Sử dụng chức năng **References > Insert Caption** trong Word để tạo chú thích tự động:
     - `Hình 3.1: Sơ đồ ngữ cảnh hệ thống Calisthenics AI`
     - `Hình 3.2: Sơ đồ Use Case tổng quát hệ thống`
     - `Hình 3.3: Kiến trúc phân tầng tổng thể hệ thống`
     - `Hình 3.4: Sơ đồ triển khai hệ thống`
     - `Hình 3.5: Sơ đồ Thực thể - Quan hệ Cơ sở dữ liệu (ERD)`
     - `Hình 3.6: Sơ đồ tuần tự xác thực người dùng & Quản lý Token`
     - `Hình 3.7: Sơ đồ tuần tự thực hiện buổi tập và ghi nhận kỷ lục`
     - `Hình 3.8: Sơ đồ tuần tự phân tích tư thế bài tập bằng AI`
     - `Hình 3.9: Lưu đồ giải thuật phân tích kỹ thuật bài tập bằng AI`
     - `Hình 3.10: Sơ đồ hoạt động quản lý danh mục bài tập của Quản trị viên`
3. **Sử dụng tệp Vector (SVG)**:
   - Microsoft Word phiên bản 2019, 2021 và Microsoft 365 hỗ trợ chèn trực tiếp tệp SVG từ thư mục `svg/`. Tệp SVG giữ nguyên độ sắc nét tuyệt đối khi phóng to/thu nhỏ ở bất kỳ kích thước trang in nào.
