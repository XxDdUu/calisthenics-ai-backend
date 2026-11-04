# MASTER PROMPT CHO GPT WORD: TẠO BÁO CÁO HỌC THUẬT ĐỒ ÁN TỐT NGHIỆP
# DỰ ÁN: HỆ THỐNG CALISTHENICS AI - HỖ TRỢ VÀ PHÂN TÍCH TẬP LUYỆN THÔNG MINH

> **HƯỚNG DẪN SỬ DỤNG CHO NGƯỜI DÙNG:**  
> Sao chép toàn bộ nội dung từ dòng `--- START OF PROMPT ---` đến hết tài liệu này và dán trực tiếp vào **GPT Word** (hoặc ChatGPT Plus / Claude 3.5 Sonnet / Gemini Pro với prompt tạo báo cáo). Tài liệu này đã chứa đầy đủ toàn bộ kiến thức kỹ thuật xác thực, kiến trúc mã nguồn thực tế, công thức toán học và cấu trúc báo cáo học thuật chuẩn mực, giúp AI xuất ra một bản báo cáo tốt nghiệp đại học chuyên ngành Công nghệ Thông tin / Khoa học Máy tính hoàn chỉnh, chuyên sâu và tuyệt đối không bịa đặt (hallucination).

---

--- START OF PROMPT ---

Bạn là một **Giáo sư Hướng dẫn, Chuyên gia Kiến trúc Phần mềm Cấp cao (Senior Software Architect) và Chuyên gia Soạn thảo Văn bản Học thuật**.

Nhiệm vụ của bạn là: Sử dụng toàn bộ khối tri thức kỹ thuật xác thực (Knowledge Base) được cung cấp dưới đây để viết một **BÁO CÁO ĐỒ ÁN TỐT NGHIỆP ĐẠI HỌC TOÀN DIỆN** (hoặc Luận văn Cử nhân / Kỹ sư Công nghệ Thông tin) với tiêu đề:

> **"NGHIÊN CỨU VÀ PHÁT TRIỂN HỆ THỐNG HỖ TRỢ VÀ PHÂN TÍCH KỸ THUẬT TẬP LUYỆN CALISTHENICS THỜI GIAN THỰC SỬ DỤNG TRÍ TUỆ NHÂN TẠO"**  
> *(CALISTHENICS AI: AN END-TO-END SMART WORKOUT TRACKING AND REAL-TIME BIOMECHANICAL ANALYSIS PLATFORM)*

---

## 1. NGUYÊN TẮC BẮT BUỘC KHI SOẠN THẢO (CORE GUIDELINES)

1. **Văn phong học thuật chuẩn mực (Academic & Formal Tone)**:
   - Sử dụng tiếng Việt học thuật, trang trọng, lập luận gãy gọn, logic, chuẩn văn bản đồ án tốt nghiệp đại học của các trường kỹ thuật hàng đầu (như ĐH Bách Khoa, ĐH Khoa học Tự nhiên, ĐH Công nghệ).
   - Tuyệt đối tránh ngôn từ cảm tính, quảng cáo, sáo rỗng hoặc khẩu ngữ.
   - Các thuật ngữ kỹ thuật chuyên ngành tiếng Anh phổ biến cần được giữ nguyên hoặc kèm theo chú giải (ví dụ: *Pose Estimation, Scapular Protraction, Latency, WebSocket STOMP, Dependency Injection, Ingress*).
2. **Tuân thủ triệt để thực tế mã nguồn (Zero Hallucination)**:
   - CHỈ sử dụng các công nghệ, tên lớp (classes), tên bảng cơ sở dữ liệu, đường dẫn API và số liệu kiểm thử ĐÃ ĐƯỢC CUNG CẤP trong phần Knowledge Base.
   - KHÔNG ĐƯỢC bịa đặt các tính năng chưa tồn tại (như cảm biến IoT gắn trên người, mô hình 3D Mesh reconstruction phức tạp, hoặc hệ thống thanh toán quốc tế).
   - Các thông tin cá nhân cần điền thêm (tên sinh viên, MSSV, tên giảng viên hướng dẫn, tên trường/khoa) phải được đánh dấu rõ ràng bằng thẻ: `[CẦN BỔ SUNG: ... ]`.
3. **Định dạng sẵn sàng cho Microsoft Word**:
   - Sử dụng phân cấp tiêu đề Markdown rõ ràng: `#` (Tên Chương), `##` (Mục cấp 1), `###` (Mục cấp 2), `####` (Mục cấp 3).
   - Công thức toán học biểu diễn bằng chuẩn LaTeX/KaTeX: sử dụng `$...$` cho công thức nội dòng và `$$...$$` cho phương trình độc lập.
   - Tất cả các bảng biểu và hình vẽ phải có chú thích rõ ràng theo quy chuẩn học thuật: `Bảng X.Y: Tên bảng` (nằm phía trên bảng) và `Hình X.Y: Tên hình` (nằm phía dưới hình hoặc sơ đồ).
   - Trích dẫn tài liệu tham khảo theo chuẩn quốc tế **IEEE** dạng `[1]`, `[2]`, ...

---

## 2. CƠ SỞ TRI THỨC KỸ THUẬT XÁC THỰC CỦA DỰ ÁN (PROJECT KNOWLEDGE BASE)

### 2.1. Bản chất hệ thống và công nghệ nền tảng
- **Dự án**: Calisthenics AI - Nền tảng huấn luyện thể thao thông minh kết hợp phân tích thị giác máy tính.
- **Thành phần 1: Backend Nghiệp vụ (`calisthenics-ai-backend`)**:
  - Ngôn ngữ: Java 21 LTS.
  - Framework: Spring Boot 3.2.12.
  - An toàn thông tin: Spring Security 6, Stateless JWT (JJwt 0.12.5), Google OAuth2 ID Token Verification (`google-api-client` 2.4.0), Password mã hóa chuẩn BCrypt.
  - Giao tiếp thời gian thực: Spring WebSocket với giao thức STOMP (`/ws`, `/topic/*`, `/app/*`).
  - Tầng dữ liệu: Spring Data JPA, Hibernate 6.4.10, Flyway Database Migration (22 phiên bản V1 đến V22), Driver PostgreSQL 16.
  - Giao tiếp liên dịch vụ: Spring 6 `RestClient` gọi sang AI Microservice qua HTTP REST (`http://localhost:8000`).
  - Kiểm thử: JUnit 5, Mockito, Spring Boot Test $\rightarrow$ **164/164 bài kiểm thử passed 100%**.
- **Thành phần 2: Dịch vụ Trí tuệ Nhân tạo & Cơ sinh học (`calisthenics-ai-service`)**:
  - Ngôn ngữ: Python 3.11+.
  - Web Framework: FastAPI (Uvicorn ASGI server).
  - Thị giác máy tính: Ultralytics YOLOv8 Pose (`yolov8n-pose.pt`) nhận diện 17 điểm then chốt COCO keypoints (tọa độ $x, y$ và độ tin cậy $c$).
  - Thư viện tính toán: NumPy, OpenCV, Pydantic v2.
  - Giải thuật tiền xử lý:
    - Bộ lọc thời gian hàm mũ: $\hat{P}_t = \alpha P_t + (1 - \alpha) \hat{P}_{t-1}$ với $\alpha = 0.65$.
    - Lọc điểm khớp theo ngưỡng tin cậy tối thiểu: $c_{\text{min}} = 0.5$.
    - Tính góc khớp 2D: $\theta = |\operatorname{atan2}(C_y - B_y, C_x - B_x) - \operatorname{atan2}(A_y - B_y, A_x - B_x)| \times \frac{180^\circ}{\pi}$.
    - Tính độ nghiêng đường thẳng so với phương ngang/đứng: $\text{Tilt} = |\operatorname{atan2}(\Delta y, \Delta x)| \times \frac{180^\circ}{\pi}$.
  - Bộ 6 phân tích chuyên biệt (Analyzers):
    1. `PlancheAnalyzer` (`planche.py`): Nhận diện 4 cấp độ (Tuck, Advanced Tuck, Straddle, Full), kiểm tra khóa khớp khuỷu ($\ge 165^\circ$), độ đổ vai ($\ge 15^\circ$), độ nghiêng thân người ($\le 15^\circ$).
    2. `FrontLeverAnalyzer` (`front_lever.py`): Nhận diện 4 cấp độ, kiểm tra tay thẳng, độ nghiêng thân người song song mặt đất ($\le 12^\circ$).
    3. `HandstandAnalyzer` (`handstand.py`): Kiểm tra độ thẳng hàng trục Cổ tay-Vai-Hông-Cổ chân ($165^\circ - 180^\circ$), đếm rep Handstand Push-up (HSPU) qua máy trạng thái Start $\rightarrow$ Eccentric ($\le 90^\circ$) $\rightarrow$ Concentric.
    4. `PullUpAnalyzer` (`pull_up.py`): Máy trạng thái Dead hang ($> 160^\circ$) $\rightarrow$ Ascending $\rightarrow$ Top (cằm qua xà, khuỷu $< 70^\circ$) $\rightarrow$ Descending. Kiểm tra tính đối xứng cơ thể giữa 2 tay ($|\theta_L - \theta_R| \le 15^\circ$).
    5. `DipAnalyzer` (`dips.py`): Đếm rep Dips, kiểm tra khóa khuỷu trên đỉnh và độ sâu gập khuỷu $\le 90^\circ$.
    6. `MuscleUpAnalyzer` (`muscle_up.py`): Phân tích pha bốc nổ, pha chuyển tiếp (transition), phát hiện lỗi nguy hiểm "Chicken Wing" (lên lệch tay $> 0.2$s hoặc lệch góc $> 25^\circ$).
  - Kiểm thử: Pytest $\rightarrow$ **104/104 bài kiểm thử passed trong 6.70s**.
- **Thành phần 3: Ứng dụng Di động Client (`calisthenics-ai-android`)**:
  - Ngôn ngữ: Kotlin 1.9+.
  - Giao diện: Jetpack Compose, Material 3, Navigation Compose.
  - Kiến trúc: MVVM, Clean Architecture, StateFlow, Coroutines.
  - Dependency Injection: Google Hilt (`@HiltViewModel`, `@Inject`).
  - Mạng: Retrofit 2, Gson, OkHttp Client kèm `AuthInterceptor` (Bearer token & auto refresh token), STOMP client qua WebSocket.
  - Kiểm thử: Local Unit Tests $\rightarrow$ **31/31 bài kiểm thử passed**.
- **Thành phần 4: Cơ sở dữ liệu & Hạ tầng**:
  - PostgreSQL 16 Alpine với 22 Flyway migrations (từ V1 đến V22).
  - Triển khai VPS Ubuntu 22.04 LTS qua Docker Compose, tích hợp Cloudflare Tunnel (`api.sky-enterprise.id.vn`) bảo vệ cổng an toàn và định tuyến vào `localhost:8082`.

---

## 3. CẤU TRÚC CHI TIẾT CỦA BÁO CÁO CẦN SINH RA

Báo cáo tốt nghiệp cần được viết hoàn chỉnh theo bố cục 6 chương chuẩn hóa sau đây:

### TRANG THỦ TỤC ĐẦU BÁO CÁO
- **TRANG BÌA**: Tên trường `[CẦN BỔ SUNG: Tên Trường]`, Tên khoa `[CẦN BỔ SUNG: Khoa CNTT]`, Tên đề tài, Sinh viên thực hiện `[CẦN BỔ SUNG: Họ tên sinh viên, MSSV]`, Giảng viên hướng dẫn `[CẦN BỔ SUNG: Học hàm, học vị, Họ tên GVHD]`, Địa danh và năm bảo vệ `[CẦN BỔ SUNG: Tháng/Năm]`.
- **LỜI CAM ĐOAN**: Lời cam đoan tính trung thực, không sao chép của đề tài.
- **LỜI CẢM ƠN**: Lời tri ân gửi đến thầy cô hướng dẫn, bộ môn và gia đình.
- **TÓM TẮT ĐỒ ÁN (ABSTRACT)**:
  - Bản tóm tắt tiếng Việt (khoảng 300 - 450 từ): Nêu rõ bối cảnh Calisthenics, vấn đề chấn thương và thiếu chuẩn hóa, giải pháp kết hợp Spring Boot + YOLOv8 Pose + Android Jetpack Compose, kết quả đạt được (164 test backend, 104 test AI, 31 test android).
  - Bản tóm tắt tiếng Anh (Abstract) (khoảng 300 - 450 từ): Tương đương bản tiếng Việt với văn phong học thuật tiếng Anh chuẩn xác.
- **DANH MỤC TỪ VIẾT TẮT**: Bảng tra cứu các từ viết tắt: AI, API, DTO, EMA, FCM, FSM, HSPU, HTTP, JPA, JSON, JWT, MVVM, NFR, ORM, PR, RBAC, REST, RPE, SSO, STOMP, UI/UX, VPS, WSS, YOLO.
- **DANH MỤC BẢNG BIỂU & DANH MỤC HÌNH VẼ**: Liệt kê đánh số.

---

### CHƯƠNG 1: TỔNG QUAN VÀ BỐI CẢNH ĐỀ TÀI
- **1.1. Bối cảnh thực tiễn**:
  - Sự bùng nổ của bộ môn Calisthenics và Street Workout trên toàn cầu và tại Việt Nam.
  - Phân tích tính phức tạp về cơ sinh học: Các động tác đòn bẩy dài (Leverages) đòi hỏi lực siết cơ bụng, khóa khớp khuỷu tay, mở vai và sự phối hợp thần kinh - cơ bắp cao độ.
  - Vấn đề thực tế: Tỷ lệ chấn thương cao do tập sai kỹ thuật (viêm gân cơ nhị đầu, rách sụn viền khớp vai); sự thiếu hụt công cụ định lượng khách quan cho huấn luyện viên khi dạy học viên trực tuyến.
- **1.2. Mục tiêu nghiên cứu và giải pháp đề xuất**:
  - Xây dựng hệ thống hoàn chỉnh từ Mobile App đến Backend và AI Microservice.
  - Đề xuất giải thuật nhận diện lỗi sai kỹ thuật và đếm rep tự động dựa trên mô hình Pose Estimation và hình học động lực học 2D.
- **1.3. Đối tượng và phạm vi nghiên cứu**:
  - Đối tượng: Người tập Calisthenics tự do (Client), Huấn luyện viên cá nhân (Coach) và Nhà quản trị (Admin).
  - Phạm vi bài tập tập trung: 6 bài tập cốt lõi (Planche, Front Lever, Handstand & HSPU, Pull-up, Dips, Muscle-up).
- **1.4. Đóng góp của đề tài**:
  - Đóng góp về mặt học thuật: Chuẩn hóa bộ quy tắc cơ sinh học tính toán (Computational Biomechanical Rules) cho các kỹ thuật Calisthenics đỉnh cao.
  - Đóng góp về mặt ứng dụng: Một nền tảng thực tế có thể thương mại hóa, có độ tin cậy cao, đã qua kiểm thử tự động nghiêm ngặt.
- **1.5. Cấu trúc của báo cáo đồ án**: Tóm tắt nội dung chính của 5 chương tiếp theo.

---

### CHƯƠNG 2: CƠ SỞ LÝ THUYẾT VÀ CÔNG NGHỆ NỀN TẢNG
- **2.1. Cơ sở cơ sinh học trong Calisthenics**:
  - Phân tích động tác tĩnh (Isometric Holds) vs. động tác động (Dynamic Repetitions).
  - Tầm quan trọng của góc khóa khớp (Joint Lockout) và độ co rút xương bả vai (Scapular Retraction & Protraction).
- **2.2. Thị giác máy tính và Ước lượng tư thế người (Human Pose Estimation)**:
  - Khái quát về bài toán Pose Estimation trong thể thao.
  - So sánh các phương pháp (OpenPose, MediaPipe, YOLO-Pose).
  - Kiến trúc mạng nơ-ron **Ultralytics YOLOv8 Pose**: Cấu trúc Backbone, Neck, Head; cơ chế Anchor-free và tối ưu hóa suy luận thời gian thực.
  - Định nghĩa tập dữ liệu COCO với 17 điểm then chốt hình thể người.
- **2.3. Công nghệ Backend hiện đại**:
  - Ngôn ngữ Java 21 LTS với các tính năng Virtual Threads, Pattern Matching, Record patterns.
  - Khung ứng dụng **Spring Boot 3.2.12**: Kiến trúc hướng dịch vụ, quản lý vòng đời Bean, Inversion of Control (IoC), Dependency Injection (DI).
  - Cơ chế bảo mật Spring Security 6: Luồng xử lý Filter Chain, xác thực phi trạng thái bằng JSON Web Token (JWT) và Google OAuth2 ID Token.
  - Giao tiếp thời gian thực: Giao thức WebSocket và phân tầng tin nhắn **STOMP (Simple Text Oriented Messaging Protocol)** theo mô hình Publish/Subscribe.
- **2.4. Công nghệ Cơ sở dữ liệu quan hệ và Quản lý phiên bản**:
  - Hệ quản trị CSDL PostgreSQL 16: Hỗ trợ dữ liệu quan hệ mạnh mẽ kèm cấu trúc bán cấu trúc JSONB.
  - Công cụ quản lý migration **Flyway**: Đảm bảo tính nhất quán lược đồ dữ liệu qua các môi trường phát triển, thử nghiệm và production.
- **2.5. Công nghệ Phát triển Ứng dụng Di động Android**:
  - Ngôn ngữ Kotlin và lập trình bất đồng bộ với Coroutines & Flow.
  - Bộ công cụ thiết kế giao diện khai báo **Jetpack Compose** và hệ thống thiết kế Material Design 3.
  - Kiến trúc MVVM (Model - View - ViewModel) và Clean Architecture.
  - Thư viện Dependency Injection **Google Dagger Hilt**.
- **2.6. Hạ tầng Triển khai và Mạng an toàn**:
  - Containerization với Docker và Docker Compose.
  - Giải pháp mạng biên **Cloudflare Tunnel (Zero Trust Ingress)**: Bảo vệ máy chủ gốc không cần mở port công khai trên Firewall của VPS.

---

### CHƯƠNG 3: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG
- **3.1. Phân tích yêu cầu hệ thống**:
  - Bảng tổng hợp tác nhân (Actors): Client, Coach, Admin.
  - Sơ đồ ca sử dụng tổng quát (General Use Case Diagram).
  - Đặc tả chi tiết các Use Case chính: Đăng ký/Đăng nhập, Phân tích bài tập qua video, Thực hiện phiên tập luyện (Live Session), Quản lý giáo án, Nhắn tin thời gian thực, Theo dõi số đo cơ thể.
- **3.2. Kiến trúc tổng thể hệ thống**:
  - Mô hình kiến trúc phân tán 3 tầng (3-Tier Distributed Architecture): Mobile Client $\leftrightarrow$ Spring Boot API Gateway/Business Logic $\leftrightarrow$ Python AI Microservice $\leftrightarrow$ PostgreSQL 16.
  - Sơ đồ triển khai và luồng tương tác giữa các thành phần.
- **3.3. Thiết kế Cơ sở dữ liệu quan hệ (Database Design)**:
  - Phân tích chi tiết quá trình tiến hóa lược đồ từ V1 đến V22.
  - Bảng đặc tả chi tiết các thực thể trọng yếu:
    - Bảng `users`, `clients`, `coaches`, `admins`
    - Bảng `exercises`, `workout_plans`, `workout_plan_exercises`, `workout_plan_assignments`
    - Bảng `workouts`, `workout_sessions`, `workout_session_sets`
    - Bảng `analyses` (lưu trữ JSONB góc khớp và form breakdown)
    - Bảng `client_body_measurements` (số đo cơ thể độ chính xác cao `NUMERIC(5, 1)`)
    - Bảng `chat_conversations`, `chat_messages`, `user_presence`
  - Sơ đồ thực thể liên kết (ERD) hoàn chỉnh với đầy đủ khóa chính (PK), khóa ngoại (FK) và quan hệ 1-1, 1-N, N-N.
  - Chiến lược lập chỉ mục (Indexing Strategy) nhằm tối ưu hiệu năng truy vấn: `idx_workout_sessions_client_date`, `idx_client_body_measurements_client_date`, `idx_chat_messages_conv_created`.
- **3.4. Thiết kế Giao diện lập trình ứng dụng (API Design)**:
  - Chuẩn thiết kế RESTful API (HTTP Verbs, Mã trạng thái HTTP 200, 201, 400, 401, 403, 404, 500).
  - Bảng danh mục đầy đủ 26 REST Controllers và các Endpoint chính.
  - Thiết kế kênh WebSocket STOMP: Điểm kết nối `/ws`, Kênh gửi `/app/chat.send`, Kênh nhận `/topic/conversation.{id}`.
- **3.5. Thiết kế Giải thuật Trí tuệ Nhân tạo & Cơ sinh học**:
  - Giải thuật tiền xử lý tọa độ: Trích xuất 17 điểm COCO, lọc độ tin cậy ($c \ge 0.5$), bộ lọc làm mượt hàm mũ EMA ($\alpha = 0.65$).
  - Công thức toán học tính góc khớp giữa 3 điểm tọa độ bất kỳ.
  - Công thức xác định độ nghiêng cơ thể (Tilt angle).
  - Chi tiết giải thuật cho 6 bộ phân tích:
    1. *Planche*: Máy trạng thái phân cấp tiến trình (Tuck $\rightarrow$ Full) và tiêu chí thẳng tay, đổ vai.
    2. *Front Lever*: Tiêu chí độ phẳng thân người và góc mở nách.
    3. *Handstand & HSPU*: Máy trạng thái đếm rep HSPU (Eccentric $\rightarrow$ Concentric).
    4. *Pull-up*: Máy trạng thái đếm rep (Dead hang $\rightarrow$ Chin-over-bar) và thuật toán kiểm tra đối xứng 2 bên tay.
    5. *Dips*: Đếm rep và kiểm tra độ sâu góc khuỷu $\le 90^\circ$.
    6. *Muscle-up*: Nhận diện pha chuyển tiếp và giải thuật phát hiện lỗi "Chicken Wing".

---

### CHƯƠNG 4: HIỆN THỰC HỆ THỐNG
- **4.1. Hiện thực Tầng Dịch vụ Backend (Spring Boot 3)**:
  - Cấu hình bảo mật `SecurityConfig.java`: Cấu hình chuỗi bộ lọc bảo mật, CORS, CSRF, phân quyền RBAC dựa trên tiền tố `ROLE_`.
  - Cơ chế xác thực `JwtAuthenticationFilter` và tích hợp Google OAuth2 ID Token.
  - Hiện thực lớp dịch vụ điều phối phân tích bài tập `AnalysisServiceImpl`: Lưu tệp video an toàn, gọi sang AI Service bằng `RestClient`, lưu kết quả JSONB vào bảng `analyses`.
  - Hiện thực hệ thống Chat thời gian thực `ChatWebSocketController` và quản lý trạng thái hiện diện người dùng `PresenceHeartbeatController`.
  - Xử lý bất đồng bộ và xử lý ngoại lệ tập trung với `@ControllerAdvice` (`GlobalExceptionHandler`).
- **4.2. Hiện thực Dịch vụ Trí tuệ Nhân tạo (FastAPI & YOLOv8 Pose)**:
  - Khởi tạo FastAPI App, tải mô hình trọng số `yolov8n-pose.pt`.
  - Hiện thực pipeline trích xuất video: Lấy mẫu khung hình ở tần số định trước (Sample FPS = 5) để cân bằng giữa độ chính xác và tài nguyên máy tính.
  - Hiện thực module tính toán hình học `angles.py`, `alignment.py`, `smoothing.py`.
  - Cấu trúc phản hồi JSON chuẩn hóa: Điểm số tổng quát, thời gian giữ (Hold time), số lần lặp (Rep count), chi tiết góc khớp từng frame, và danh sách cảnh báo lỗi kỹ thuật.
- **4.3. Hiện thực Ứng dụng Di động Android (Jetpack Compose)**:
  - Cấu trúc dự án theo Clean Architecture (Data, Domain, Presentation).
  - Hiện thực các màn hình chính:
    - Màn hình Đăng nhập / Đăng ký (`LoginScreen`, `RegisterScreen`).
    - Màn hình Trang chủ (`HomeScreen`): Hiển thị bài tập hôm nay, tiến độ tuần.
    - Màn hình Thực hiện buổi tập (`WorkoutSessionScreen`): Ghi nhận từng hiệp, đếm ngược thời gian nghỉ, xem gợi ý kỹ thuật.
    - Màn hình Phân tích AI (`AnalysisScreen`): Quay video hoặc tải video từ thư viện, hiển thị khung xương chuyển động và biểu đồ đánh giá lỗi sai.
    - Màn hình Chỉ số cơ thể (`BodyMetricsScreen`): Biểu đồ biến thiên cân nặng, tỷ lệ mỡ qua các mốc thời gian.
    - Màn hình Chat với HLV (`ChatScreen`): Danh sách tin nhắn, gửi/nhận tức thì qua STOMP.
  - Xử lý vòng đời mạng với Hilt, Retrofit Interceptor và Quản lý phiên làm việc tự động làm mới Token.
- **4.4. Hiện thực Triển khai Hạ tầng và Bảo mật**:
  - Viết tệp `docker-compose.yml` đóng gói dịch vụ PostgreSQL và Backend.
  - Cấu hình Cloudflare Tunnel thông qua dịch vụ `cloudflared.service` trên Ubuntu VPS, chuyển tiếp an toàn tên miền `api.sky-enterprise.id.vn` về máy chủ nội bộ.

---

### CHƯƠNG 5: THỬ NGHIỆM VÀ ĐÁNH GIÁ KẾT QUẢ
- **5.1. Kế hoạch và Phương pháp thử nghiệm**:
  - Quy trình kiểm thử tự động (Unit Test, Integration Test) và kiểm thử chấp nhận người dùng (UAT).
  - Môi trường thử nghiệm phần cứng và phần mềm.
- **5.2. Kết quả kiểm thử tự động của hệ thống**:
  - **Kiểm thử Backend**: Thực thi lệnh `mvn test` $\rightarrow$ **164/164 bài kiểm thử thành công 100%**, không có bất kỳ lỗi nào.
  - **Kiểm thử AI Service**: Thực thi lệnh `pytest tests/ -q` $\rightarrow$ **104/104 bài kiểm thử thành công trong 6.70 giây**, bao phủ trọn vẹn toàn bộ các hàm toán học, bộ lọc EMA và 6 bộ phân tích bài tập.
  - **Kiểm thử Android App**: Thực thi lệnh `./gradlew testDebugUnitTest` $\rightarrow$ **31/31 bài kiểm thử thành công**, đóng gói APK `./gradlew assembleDebug` thành công.
  - Bảng tổng hợp kết quả kiểm thử toàn hệ thống (Tổng cộng **299 bài kiểm thử tự động passed**).
- **5.3. Đánh giá hiệu năng và độ trễ (Performance & Latency Evaluation)**:
  - Thời gian xử lý khung hình AI: Trung bình 80ms - 120ms trên CPU tiêu chuẩn, sẵn sàng đạt 30 FPS khi hỗ trợ tăng tốc GPU.
  - Thời gian phản hồi của các REST API: Thời gian trung bình dưới 50ms đối với các tác vụ CRUD thông thường.
  - Băng thông và tải bộ nhớ của ứng dụng di động: Tối ưu bộ nhớ nhờ cơ chế giải phóng tài nguyên của Jetpack Compose và CameraX.
- **5.4. Đánh giá độ chính xác của các giải thuật cơ sinh học**:
  - Đánh giá khả năng phân loại cấp độ Planche và Front Lever (Tuck vs. Full).
  - Đánh giá độ nhạy của giải thuật phát hiện lỗi "Chicken Wing" trong Muscle-up.
  - Đánh giá tính ổn định của góc khớp sau khi qua bộ lọc làm mượt EMA so với tín hiệu thô.
- **5.5. Đánh giá trải nghiệm người dùng và giao diện**:
  - Khảo sát sự thuận tiện của quy trình HLV giao bài và học viên nộp video phân tích.
  - Đánh giá độ trực quan của biểu đồ theo dõi số đo cơ thể và kỷ lục cá nhân.

---

### CHƯƠNG 6: KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN
- **6.1. Những kết quả đã đạt được**:
  - Xây dựng thành công hệ sinh thái trọn vẹn gồm Mobile App (Android), Backend (Spring Boot 3), AI Microservice (FastAPI/YOLOv8) và CSDL PostgreSQL.
  - Hiện thực thành công 26 REST Controller, hệ thống WebSocket Chat thời gian thực, và 22 migrations quản lý toàn vẹn dữ liệu.
  - Hoàn thiện 6 bộ phân tích cơ sinh học chuyên sâu cho các kỹ thuật Calisthenics khó.
  - Hệ thống đạt độ ổn định cao với 299 bài kiểm thử tự động thành công tuyệt đối.
- **6.2. Các hạn chế kỹ thuật thực tế (Honest Limitations)**:
  - Giới hạn của mô hình ước lượng tư thế 2D: Hiện tượng co ngắn phối cảnh khi chuyển động lệch mặt phẳng camera, không đo lường được lực xoắn khớp nội tại.
  - Yêu cầu nghiêm ngặt về góc đặt camera (Side-view cho Planche/Front Lever, Front-view cho Pull-up).
  - Ảnh hưởng của điều kiện ánh sáng và trang phục rộng đến độ tin cậy của các điểm khớp ngoại vi.
- **6.3. Định hướng phát triển tương lai**:
  - Nghiên cứu tích hợp mô hình 3D Pose Lifting (tái tạo khung xương không gian 3D từ video 2D đơn mắt).
  - Chuyển đổi mô hình YOLOv8 Pose sang TFLite/ONNX để suy luận trực tiếp trên thiết bị (On-Device Inference), giảm tải mạng.
  - Xây dựng thuật toán AI tự động đề xuất lộ trình tập luyện cá nhân hóa dựa trên lịch sử lỗi kỹ thuật.

---

### TÀI LIỆU THAM KHẢO (THEO CHUẨN IEEE)
Tạo danh mục tài liệu tham khảo chuẩn mực với đầy đủ các bài báo khoa học, sách chuyên ngành và tài liệu kỹ thuật cốt lõi:
1. J. Redmon, S. Divvala, R. Girshick, and A. Farhadi, "You Only Look Once: Unified, Real-Time Object Detection," in *IEEE Conference on Computer Vision and Pattern Recognition (CVPR)*, 2016.
2. C. Szegedy et al., "Rethinking the Inception Architecture for Computer Vision," in *IEEE CVPR*, 2016.
3. Ultralytics, "YOLOv8 Pose Estimation Architecture and Documentation," *Ultralytics Open-Source Repository*, 2023.
4. T. Y. Lin et al., "Microsoft COCO: Common Objects in Context," in *European Conference on Computer Vision (ECCV)*, 2014.
5. S. Low, *Overcoming Gravity: A Systematic Approach to Gymnastics and Bodyweight Strength*, 2nd ed., Bradenton, FL: Battle Ground Creative, 2016.
6. R. Walls, *Spring Boot in Action*, Shelter Island, NY: Manning Publications, 2016.
7. Google Android Developers, "Guide to App Architecture and Jetpack Compose," *Google Developers Documentation*, 2024.
8. PostgreSQL Global Development Group, "PostgreSQL 16.0 Documentation," 2023.
9. D. Winter, *Biomechanics and Motor Control of Human Movement*, 4th ed., Hoboken, NJ: John Wiley & Sons, 2009.
10. E. Gamma, R. Helm, R. Johnson, and J. Vlissides, *Design Patterns: Elements of Reusable Object-Oriented Software*, Reading, MA: Addison-Wesley, 1994.

---

### PHỤ LỤC
- **Phụ lục A**: Hướng dẫn cài đặt và thiết lập môi trường phát triển (Yêu cầu Java 21, Maven 3.9+, Python 3.11+, Android Studio Hedgehog+, Docker).
- **Phụ lục B**: Trích lục các đoạn mã nguồn cốt lõi (Core Code Snippets):
  - Lớp `SecurityConfig.java` và cơ chế xác thực JWT.
  - Lớp `PlancheAnalyzer.py` và thuật toán tính góc trong `angles.py`.
  - Lớp `WorkoutSessionViewModel.kt` và xử lý trạng thái giao diện trong Compose.
- **Phụ lục C**: Bảng tổng hợp toàn bộ 26 REST Controller và Endpoint URL.

---

## 4. CHỈ DẪN SINH NỘI DUNG CHI TIẾT CHO AI

Khi thực hiện viết từng phần của báo cáo, bạn hãy:
1. **Viết thật chi tiết, có chiều sâu kỹ thuật**: Giải thích cặn kẽ tại sao lại chọn công nghệ đó, giải quyết bài toán gì, ưu điểm so với giải pháp khác là gì.
2. **Không tóm tắt sơ sài**: Mỗi chương phải được viết với độ dài tương xứng của một luận văn tốt nghiệp (khoảng 15 - 25 trang Word cho mỗi chương lớn).
3. **Trình bày công thức toán học rõ ràng**: Mọi giải thuật tính góc, làm mượt EMA, chuẩn hóa tọa độ đều phải có công thức toán học KaTeX đi kèm giải thích từng ký hiệu.
4. **Sử dụng bảng biểu và sơ đồ Text-based / Mermaid phong phú**: Để người dùng dễ dàng chuyển đổi sang hình ảnh và biểu đồ trực quan trong Microsoft Word.

BÂY GIỜ, HÃY BẮT ĐẦU SOẠN THẢO BÁO CÁO HỌC THUẬT THEO CẤU TRÚC VÀ CÁC NGUYÊN TẮC TRÊN.

--- END OF PROMPT ---
