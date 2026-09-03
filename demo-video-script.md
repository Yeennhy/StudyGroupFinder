# Kịch bản quay video demo — Study Group Finder

> File này được viết bằng cách đọc trực tiếp source code hiện tại (không dựa vào `README.md` / `updated_study_group_finder_dev_plan-1.md` vì hai file đó là tài liệu lập kế hoạch, đã **lỗi thời** so với code thật — commit gần nhất `295035f`). Mọi tên nút, text, đường dẫn file đều lấy từ code thật tại thời điểm viết (2026-09-03). Nếu sau này code đổi, phần nào lệch thì sửa lại phần đó, đừng tin mù quáng vào file này mãi.

**Cách dùng:** mỗi mục có 3 phần — **Chuẩn bị** (cần setup gì trước khi bấm quay), **Quay gì** (checklist thao tác + cái camera phải thấy), **Lưu ý** (điều KHÔNG được nói/quay vì sai với thực tế code, tránh bị hỏi vặn khi bảo vệ). Tick `[ ]` → `[x]` khi đã quay xong đoạn đó.

---

## 0. Chuẩn bị môi trường trước khi quay (làm 1 lần)

- [ ] Build debug (`BuildConfig.DEBUG = true`) — seeding chỉ chạy ở debug build.
- [ ] `google-services.json` đã có trong `app/`, project Firebase `studygroupfinder-42da7` đã bật Auth (Email/Password), Firestore, và Supabase Storage bucket `materials` đã tạo (Cloudinary cũng đã điền `CloudinaryConfig.kt`).
- [ ] Firestore rules thật (§4 dev-plan) đã dán vào Console — **không quay bước này**, chỉ cần đảm bảo app không bị `PERMISSION_DENIED` khi quay.
- [ ] Cài app lần đầu → `MainActivity` tự seed data (Toast "Seeding database…" → "Seeding complete!"). Seed **KHÔNG có nút bấm thủ công**, tự chạy 1 lần theo `SharedPreferences` flag.
- [ ] Tạo **2 tài khoản thật** qua flow Sign Up (seed users như `u-host-power`, `u-mem-rach`... chỉ là document Firestore giả, **không phải Firebase Auth thật**, không đăng nhập được) — 1 tài khoản chính để quay hầu hết flow, 1 tài khoản phụ để quay các case cần "người thứ hai" (join, được invite, thấy session bị block...).
- [ ] Đăng nhập tài khoản chính **ít nhất 1 lần trước khi quay chính thức** — seed cá nhân hoá (`seedCurrentUserExtras`) chỉ chạy sau lần login đầu tiên, cần tài khoản đó join community **HCMUS** để khớp với dữ liệu seed sẵn (session `s-arts-workshop`, `s-english-club`, `s-my-hosted`...).
- [ ] Chưa có session nào bị **Cancelled** trong data seed — nếu muốn quay case "Cancelled by Host", phải tự hủy 1 session sống trên camera trước (xem mục C4).
- [ ] Cài đặt sẵn 1 app đọc PDF/ảnh trên máy quay, để demo mở file material / xem PDF export không bị lỗi "No application found".

### ⚠️ Bug seed đã xác nhận trong code — ảnh hưởng trực tiếp tới việc quay bằng 2 tài khoản

`DataSeeder.kt:469` ghi `FirestoreRefs.session("s-my-hosted").set(...)` **đè toàn bộ document**, với `hostUid` = uid của bất kỳ tài khoản nào vừa lần đầu chạy `seedCurrentUserExtras`. Hàm này chạy lại **mỗi khi `MainActivity` được tạo mới** (app bị kill rồi mở lại, hoặc bị hệ thống thu hồi do thiếu RAM) **trong khi** tài khoản đó **lần đầu tiên** là user đang đăng nhập trên máy đó — không chỉ đúng 1 lần duy nhất lúc cài app.

Hệ quả: nếu trong lúc quay, **tài khoản phụ vô tình là người đầu tiên trải qua 1 lần cold-start** sau khi tài khoản chính đã "chiếm" session này, `s-my-hosted` sẽ bị ghi đè sang tài khoản phụ làm host — tài khoản chính mất quyền host mà không có lỗi hay cảnh báo nào hiện ra, chỉ tự nhiên thấy nút hành động đổi khác đi. Vì buổi quay dài, việc app bị kill ngoài ý muốn (chuyển app xem note, RAM thấp, thiết bị tự thu hồi...) là chuyện bình thường, nên **không nên đặt cược cả case 6 của C3 và toàn bộ C4 vào document seed sẵn này.**

**Cách né (bắt buộc áp dụng, không phải tuỳ chọn):** ở mục **C3 case 6** và **C4**, dùng **1 session GATED tự tạo sống** (Create Session, để tài khoản chính làm host) thay vì mở `s-my-hosted`, rồi cho tài khoản phụ bấm "Request to Join" thật trên session đó để tạo pending request. Cách này vừa né hẳn bug, vừa là bằng chứng thuyết phục hơn (chứng minh transaction thật đang chạy, không phải dữ liệu dựng sẵn).

Lý do bắt buộc chứ không phải tuỳ chọn: mục **B1** phía dưới có 1 bước chủ đích **kill app rồi mở lại** trong lúc 1 **tài khoản throwaway thứ ba** (dùng để demo Sign Up) đang đăng nhập lần đầu — đúng điều kiện kích hoạt bug. Tới lúc quay xong B1, `s-my-hosted` gần như chắc chắn đã bị ghi đè sang tài khoản throwaway đó rồi, nên **đừng mở lại `s-my-hosted` ở C3/C4 nữa** — chỉ dùng nó để lướt qua ở mục A4 (xem, không thao tác).

---

## PHẦN A — Nền tảng & hạ tầng (Khôi)

### A1. Navigation flow: navbar + app header đổi theo từng screen

**Chuẩn bị:** không cần setup gì thêm, chỉ cần app đã đăng nhập.

**Quay gì:**
- [ ] Mở lần lượt 4 tab bottom nav: **Home / My Sessions / Inbox / Profile** — cho thấy tab đang chọn có pill nền vàng + chữ đậm, 3 tab còn lại thường.
- [ ] Từ Home bấm avatar góc header → nhảy sang Profile (bottom nav vẫn hiện vì vẫn là self-view).
- [ ] Từ Home/My Sessions/Inbox bấm icon **đồng hồ cát** (hourglass) trên header → mở History; quay cả 3 nơi để chứng minh nút này xuất hiện ở nhiều screen, không riêng 1 chỗ.
- [ ] Mở 1 session bất kỳ → header đổi thành nút **Back** (mũi tên), tiêu đề "Session Details", **không còn bottom nav** (vì đây không phải 1 trong 4 tab gốc).
- [ ] Từ Session Detail (vai trò host) bấm "Manage Session" → header vẫn Back, tiêu đề "Manage Session"; thử back ra khi **đang có thay đổi chưa lưu** → dialog "Discard Changes?" xuất hiện (đây là back-button custom, không phải back mặc định).
- [ ] Vào Profile xem người khác (bấm avatar 1 thành viên trong session) → header đổi: có nút Back, **ẩn avatar/nút sign-out**, icon bên phải đổi thành **block** (hoặc ẩn nếu đã block rồi) — quay để đối chiếu rõ với self-view.
- [ ] Vào Community Selection từ Profile (bấm mũi tên community) → header có nút Back vì đây là edit-mode; nếu vào lần đầu từ Splash thì **không có** nút Back (bắt buộc chọn community).

**Lưu ý:**
- Tiêu đề header là **string hardcode trong code Fragment**, không phải lấy từ `nav_graph.xml` (nav_graph có field label riêng nhưng không được dùng) — không cần giải thích chi tiết trên video, chỉ cần quay đúng tiêu đề hiển thị.
- **Không có icon "bút chì sửa" nào nằm trên app header** — nút sửa nằm ngay trên trang (Profile: `btnEditDetails` cạnh avatar; Session Manage: icon bút chì trên card thông tin session). Đừng chỉ vào header nói "đây là nút edit".

---

### A2. Firebase DB setup + Firestore rules/constraints

**Chuẩn bị:** mở sẵn Firebase Console tab Firestore (Data + Rules), tab Authentication.

**Quay gì (đây là phần quay màn hình Console, không phải app):**
- [ ] Console → Authentication → tab Sign-in method → cho thấy Email/Password đã Enable.
- [ ] Console → Firestore Database → tab Data → mở nhanh qua các collection: `communities`, `sessions`, `sessions/{id}/members` (subcollection), `users`, `users/{uid}/inbox`, `users/{uid}/blocked` — chỉ cần lướt qua cho thấy cấu trúc đúng với thiết kế.
- [ ] Console → Firestore → tab Rules → cuộn qua toàn bộ nội dung rules đã dán (§4 dev-plan), dừng lại nhấn mạnh 2 đoạn quan trọng để giải thích miệng:
  - Đoạn `joinsSelf()`/`leavesSelf()` — lý do người **không phải host** vẫn được tự sửa `joinedCount`/`memberUids` của chính họ khi join/leave.
  - Đoạn `allow create` trên `users/{uid}/inbox` — lý do người khác (host mời, hệ thống thông báo) được phép **ghi vào inbox của mình**, dù không sở hữu document đó.
- [ ] (Tuỳ chọn nếu còn thời gian) mở tab Storage cho Firebase Storage rules — lưu ý: **ảnh profile đi qua Cloudinary, tài liệu học đi qua Supabase Storage**, Firebase Storage trong project gần như không được dùng cho 2 tính năng chính này (SDK có include nhưng dead code) — nếu quay phần này, nói rõ Firebase Storage rules chỉ là phần hạ tầng dự phòng, luồng ảnh/tài liệu thật đi qua Cloudinary/Supabase.

**Lưu ý:**
- Đây **không phải feature trong app**, không có cách "demo" bằng thao tác chạm — chỉ quay được màn hình Console. Đừng cố tìm cách bấm nút trong app để chứng minh rules, giải thích bằng lời trong lúc lướt Console là đủ.

---

### A3. Splash screen + logo app

**Chuẩn bị:** đăng xuất trước, sau đó tắt hẳn app (swipe khỏi recent apps) để splash chạy lại từ đầu — nếu chỉ mở lại app đang chạy nền, Android có thể khôi phục thẳng vào Home mà không qua Splash.

**Quay gì:**
- [ ] Từ màn hình chủ máy, bấm icon app → quay cận icon launcher (glyph hình mũ cử nhân) trước khi app mở.
- [ ] App mở ra → quay full Splash: nền giấy chấm bi màu be, card bo góc trắng ở giữa, chữ **"StudyCohort"** (40sp đậm, màu xanh theme), tagline **"Study smarter, together"**, icon mũ cử nhân 100dp, progress bar đang xoay bên dưới.
- [ ] Quay case 1 (chưa đăng nhập → Splash tự chuyển sang **Login**) — làm được ngay, không cần tài khoản nào, có thể quay **luôn ở đầu buổi** trước khi đăng nhập tài khoản chính lần nào.
- [ ] Quay case 3 (đã đăng nhập, **đã có community** → Splash tự chuyển thẳng vào **Home**) — dùng tài khoản chính, làm được bất cứ lúc nào tài khoản chính đang đăng nhập, kể cả tận dụng lần mở app đầu tiên trong buổi quay.
- [ ] Case 2 (đã đăng nhập nhưng **chưa chọn community**) — **không quay riêng ở đây**, tài khoản có trạng thái này chỉ tồn tại trong khoảng ngắn ngay sau khi Sign Up xong (mục B1) và trước khi join community. Note lại, tới lúc quay B1 thì lồng luôn: sau bước "đăng nhập lại bằng tài khoản vừa tạo", **kill app rồi mở lại** để bắt Splash tự route sang Community Selection — đó chính là case 2, không cần dựng riêng.
- [ ] Bấm nút Back ở Home ngay sau khi splash chuyển xong (case 3) → chứng minh **không quay lại được Splash** (đã bị pop khỏi back stack).

**Lưu ý:** icon launcher và icon trên splash dùng chung 1 path vẽ (glyph mũ cử nhân) — nên quay 2 cái cạnh nhau/nối tiếp để nhấn mạnh tính đồng bộ thương hiệu, đây là điểm cộng dễ thấy.

---

### A4. Seed data

**Chuẩn bị:** đã login tài khoản chính ít nhất 1 lần (xem mục 0).

**Quay gì:**
- [ ] Mở Home, lướt qua community **HCMUS** — cho thấy nhiều session với đủ loại tag (Normal/Midterm/Final) và mức kỳ vọng (Pass/Casual/Overachieving).
- [ ] Mở Community Selection → lướt qua **5 community**: HCMUS, FPT-HCM, HUST (3 community có tick verified), OPEN-STUDY-DN, OPEN-STUDY-CT (2 community không verified, không có tick) — quay rõ sự khác biệt icon tick.
- [ ] Mở 1 community bất kỳ trong Manage/Create Session → dropdown campus location cho thấy nhiều địa điểm trong 1 community (lưu ý: hiện tại dropdown là 4 địa điểm mock cố định, không đổi theo community — xem phần Lưu ý).
- [ ] Mở session `s-phys-final-full` ("Physics 2 Final Cram (FULL)") → chứng minh case **session đầy** (4/4).
- [ ] Mở session `s-arts-workshop` → chứng minh case **có người bị block** trong roster.
- [ ] Mở session `s-my-hosted` ("My Hosted Session (test)") → tài khoản chính đang là **host**, có 1 request `PENDING` từ Carol chờ duyệt.
- [ ] Mở session `s-english-club` → tài khoản chính đang ở trạng thái **INVITED** (chưa accept).
- [ ] Mở Inbox → cho thấy cả 3 loại thông báo cùng lúc: `invite` (từ s-english-club), `join_request` (từ s-my-hosted), `system` ("Welcome to Study Group Finder!") — đây là bằng chứng rõ nhất data seed đa dạng, nên quay kỹ đoạn này.

**Lưu ý:**
- Campus location trong Create/Edit Session **hiện đang là 4 địa điểm hardcode giống nhau cho mọi community** (Main Library / Study Room A / Hall B / Campus Cafe), dù Firestore đã seed địa điểm riêng theo từng community (ví dụ "Ta Quang Buu Library" cho HUST). Nếu muốn video "sạch", chỉ nói chung chung "chọn địa điểm từ danh sách có sẵn", đừng khẳng định danh sách đổi theo community đang chọn.
- Không có session nào seed sẵn ở trạng thái **Cancelled** — phải tự hủy sống trên camera (mục C4) nếu cần quay case này.
- Các user seed (`u-host-power`, `u-mem-rach`...) chỉ là document Firestore, **không đăng nhập được** — đừng thử login bằng các tên này.

---

### A5. Animation, loading/empty/error/offline state

**Chuẩn bị:** chuẩn bị sẵn công tắc Wifi/dữ liệu di động để bật/tắt nhanh khi quay offline; chuẩn bị 1 filter chip chắc chắn ra 0 kết quả (để quay empty).

**Quay gì:**
- [ ] **Loading**: quay lúc mới mở Home/Profile/History (spinner loading xuất hiện rất ngắn — có thể cần quay chậm/replay để bắt kịp).
- [ ] **Empty**: trên Home, bấm 1 chip filter khiến không còn session nào khớp → card "Nothing here yet"-style hiện ra; hoặc mở My Sessions bằng tài khoản phụ chưa join gì → thấy "You haven't joined any sessions yet. Find one on the Home tab."
- [ ] **Offline** (quan trọng, đúng thứ tự để trigger được): mở Home/Session Detail/Session Manage **khi còn mạng** cho load xong 1 lần (để nhận ít nhất 1 snapshot thật từ server) → **sau đó mới tắt Wifi/mạng** → quay banner xám "Showing saved data — you're offline" xuất hiện phía trên, data cũ vẫn hiển thị bên dưới.
- [ ] **Filter chip mở/đóng (animation)**: trên Home bấm nút mũi tên (`btnExpandFilters`) cạnh nút Sort → quay hiệu ứng 3 hàng chip (loại session/loại môn/mức kỳ vọng) trượt lên + fade in so le nhau, và icon mũi tên xoay 90°; bấm lại để quay chiều đóng lại (fade out nhanh hơn).
- [ ] **Chuyển màn hình (fade-through)**: chuyển qua lại vài tab bottom nav, quay hiệu ứng fade nhẹ giữa các screen (Material `MaterialFadeThrough`), áp dụng ở hầu hết screen chính trừ nhóm auth (Splash/Login/Signup/Forgot Password/Success).

**Lưu ý:**
- **Error state** rất khó tự chủ động trigger (chỉ xảy ra khi Firestore thật sự lỗi, ví dụ thiếu composite index) — nếu không dựng được lỗi thật, có thể bỏ qua đoạn quay error, chỉ cần mô tả bằng lời + screenshot layout (`layout_state_error.xml`) là đủ, đừng cố giả lập sai cách trên camera.
- Offline sẽ **không** hiện nếu mở màn hình đó **ngay từ lúc đang offline với cache trống** (lúc đó sẽ ra Loading/Error, không phải Offline) — phải mở lúc còn mạng trước, đúng thứ tự nêu trên.
- Create Session và Invite-by-Student-ID **không dùng bộ 4-state chung** này (chỉ có loading overlay riêng khi submit) — đừng tìm banner offline ở 2 màn đó.

---

### A6. Set up report LaTeX

**Quay gì:** không có gì để quay trong app. `report/` hiện chỉ có `report/README.md`, đang chờ template chính thức của trường, chưa dựng file `.tex` nào. Nếu cần 1 cảnh cho mục này trong video, quay nhanh nội dung `report/README.md` (giải thích đang chờ template) là đủ, không cần dựng thêm.

---

## PHẦN B — Auth & Profile (Khôi)

### B1. Sign Up

**Quay gì:**
- [ ] Từ Login bấm link **Sign up** → mở form với 5 field: Full Name, Student ID, Email Address, Password, Confirm Password.
- [ ] Bỏ trống 1 field rồi bấm **Sign Up** → lỗi "All fields are required".
- [ ] Điền password và confirm password khác nhau → lỗi "Confirmation doesn't match password".
- [ ] Điền đủ + đúng → bấm **Sign Up** → Toast "Account created!" → tự động điều hướng sang **Success screen** ("Account Created Successfully" / "Welcome to StudyCohort!" / nút "Back to Login").
- [ ] Bấm "Back to Login" → quay lại **màn Login** (không tự vào thẳng app).
- [ ] Đăng nhập lại bằng tài khoản vừa tạo → vì `communityId` còn trống → Splash tự đưa sang **Community Selection**.
- [ ] **Nối luôn case 2 của mục A3 tại đây**: kill hẳn app (swipe khỏi recent apps) rồi mở lại → quay Splash tự route thẳng sang Community Selection lần nữa (lần này đi từ cold-start, không phải điều hướng trong app) — đây là bằng chứng rõ nhất cho "đã đăng nhập nhưng chưa có community". Không cần dựng case này ở đâu khác.
- [ ] (Tuỳ chọn) Kiểm tra hộp mail vừa đăng ký → cho thấy email xác thực thật đã được gửi (Firebase `sendEmailVerification`).
- [ ] Thoát khỏi Community Selection **mà không join community nào** (tài khoản throwaway này không cần dùng nữa) — chuyển sang B2, nơi lần đăng nhập cuối cùng sẽ là tài khoản chính.

**Lưu ý:** Sau khi Sign Up, app **tự động sign-out** rồi mới ra Success screen — không có chuyện signup xong vào thẳng Community Selection luôn (dù trong code có 1 action nav thừa không dùng tới). Kịch bản đúng là: **Sign up → Success → Back to Login → tự đăng nhập lại → Community Selection.**

---

### B2. Sign In

**Quay gì:**
- [ ] Nhập sai password → lỗi **"The password you entered is incorrect."** hiện dưới field password.
- [ ] Nhập email không tồn tại → lỗi **"Account currently unavailable or does not exist."** hiện dưới field email.
- [ ] Nhập email sai định dạng → lỗi **"Please enter a valid email address."**
- [ ] Bật chế độ máy bay rồi thử đăng nhập → lỗi mạng hiện ra (message gốc từ Firebase SDK, không có string custom riêng).
- [ ] **Đăng nhập đúng bằng TÀI KHOẢN CHÍNH** (không phải account throwaway của B1 nữa) → Splash đưa thẳng vào Home vì đã có community từ trước. **Từ đây tới hết Part B, C, D, mặc định luôn là tài khoản chính đang đăng nhập**, trừ khi ghi chú "tài khoản thứ hai/phụ" rõ ràng.

**Lưu ý:** đăng nhập thành công **không có bước "đồng bộ toàn bộ dữ liệu vào Room" riêng biệt** — Room được các repository tự ghi dần khi từng screen mở Flow tương ứng, không có màn hình loading tổng nào để quay riêng cho việc này.

---

### B3. Forgot Password

**Quay gì:**
- [ ] Từ Login bấm **Forgot Password** → form chỉ có 1 field Email Address, nút **"Send Recovery Email"**.
- [ ] Bỏ trống → lỗi "Email is required".
- [ ] Nhập email hợp lệ → bấm gửi → Toast "Recovery email sent!" → Success screen ("Recovery Email Sent" / "Don't forget to check for spam!" / "Back to Login").
- [ ] (Tuỳ chọn) Mở hộp mail cho thấy email reset password thật của Firebase đã tới.

**Lưu ý:** đây dùng thẳng `sendPasswordResetEmail` của Firebase — **không có màn hình "nhập mật khẩu mới" trong app** (`fragment_new_password.xml` tồn tại trong resource nhưng không được nối vào nav_graph, là file mồ côi). Việc đổi mật khẩu thật sự diễn ra ở trang web do Firebase host, ngoài app — đừng cố tìm màn "New Password" trong app để quay, nó không hoạt động.

---

> **B4 (Sign Out) đã dời xuống cuối file** (mục F) — làm cảnh kết ở cuối toàn bộ video, sau khi đã quay xong Part C và D. Lý do: sign out xong sẽ phải đăng nhập tài khoản khác để chứng minh Room bị xoá sạch, mà **mọi mục sau đây (B5, B6, Part C, Part D) đều cần tài khoản chính đang đăng nhập liên tục** — quay Sign Out ở đây sẽ làm đứt mạch toàn bộ phần còn lại của video.

### B5. Profile — self view

**Quay gì:**
- [ ] Mở Profile, quay đủ 4 card: Avatar/Name/Bio, Community, Personal Details (Department/Major/Admission Year), Session Activity (activity graph).
- [ ] Bấm icon bút chì (`btnEditDetails`) → các field chuyển sang chế độ chỉnh sửa (EditText hiện ra), nút "Save Changes" xuất hiện.
- [ ] Sửa Bio/Department/Major/Admission Year → bấm **Save Changes** → quay lại chế độ đọc, data đã cập nhật, thoát app mở lại vẫn giữ nguyên (chứng minh ghi Firestore thật, không phải chỉ đổi UI tạm).
- [ ] Bấm icon bút chì nhỏ trên avatar (`btnEditAvatar`) → dialog "Change Photo" / "Select your profile picture source" với 2 lựa chọn **Camera** và **Gallery**.
  - [ ] Chọn **Camera** → xin quyền CAMERA (nếu lần đầu) → mở app camera hệ thống → chụp → ảnh preview lên avatar ngay (chưa upload).
  - [ ] Chọn **Gallery** → mở Android Photo Picker (không cần xin quyền storage) → chọn ảnh → preview lên avatar.
  - [ ] Bấm **Save Changes** → ảnh mới upload lên Cloudinary, avatar cập nhật thật trên Firestore.
- [ ] Cuộn tới card **Session Activity** → quay activity graph dạng heatmap kiểu GitHub (ô màu theo số session mỗi ngày, 90 ngày gần nhất), legend "Less → More".
- [ ] Nếu tài khoản đang demo **chưa verify email**: quay banner "Your account has not been verified yet" + link "Resend verification email" → bấm gửi lại → xác nhận email tới hộp thư.
- [ ] Bấm mũi tên bên card Community → chuyển sang Community Selection ở **edit mode** (có nút Back, khác với lần chọn đầu tiên).

**Lưu ý:** ở card Community, tên hiển thị hiện đang là **ID thô của community** (ví dụ chữ "HCMUS"), chưa resolve ra tên đầy đủ ("University of Science, VNU-HCM") — cứ quay bình thường, không cần né, nhưng đừng thuyết minh "đây là tên đầy đủ community" vì thực tế đang hiện ID.

---

### B6. Profile — xem người khác + Block

**Chuẩn bị:** vào 1 session có thành viên **khác** `u-blocked-sample` — ví dụ session do `u-host-power` host, hoặc bất kỳ session nào có `u-alice`/`u-bob`/`u-carol`/`u-dan`/`u-erin` trong roster. **Không dùng `u-blocked-sample`/session `s-arts-workshop` cho bước Block sống** — người này đã bị seed tự động block sẵn cho tài khoản chính ngay từ lần đăng nhập đầu tiên (`seedCurrentUserExtras`), nên bấm vào avatar họ sẽ thấy nút **"Unblock" có sẵn**, không quay được thao tác Block từ đầu nữa.

**Quay gì:**
- [ ] Từ danh sách thành viên trong Session Detail, bấm avatar 1 người **chưa từng bị block** (ví dụ `u-host-power`) → mở Profile **read-only**: không có bottom nav, có nút Back, **không có** icon bút chì/nút save/mũi tên community, icon bên phải header là **block**.
- [ ] Bấm icon block → dialog "Block User" / "Are you sure you want to block this user?" → bấm **Block**.
- [ ] Quay ngay: header 3 card chuyển màu xám lạnh, hiện chữ "You blocked this user", nút **Unblock** xuất hiện.
- [ ] Quay sang Home → tìm 1 session có chứa người vừa bị block → card session đó bị làm xám (alpha thấp), có label nhỏ **"Contains a blocked user"**.
- [ ] Bấm Unblock để khôi phục lại trạng thái ban đầu cho phần quay sau không bị ảnh hưởng.
- [ ] (Tuỳ chọn, không cần thao tác gì) Mở sẵn session `s-arts-workshop` để chỉ ra case này **đã tự xám sẵn từ đầu** nhờ seed data tự block `u-blocked-sample` — dùng để nói thêm rằng hiệu ứng xám không phụ thuộc việc vừa bấm Block ở trên, mà là logic chung áp dụng mọi lúc.

---

## PHẦN C — Vòng đời Session: Create/Manage/Edit/Detail (Vĩ)

### C1. Create Session

**Quay gì:**
- [ ] Từ Home bấm nút **FAB (+)** → mở Create Session.
- [ ] Điền lần lượt: Course ID, Course Name (gõ tay), chọn **Course Category** từ dropdown (Physics/Calculus/DSA/Programming/English/Arts/Social/Other), Title, Description, Goals.
- [ ] Bật/tắt toggle **Open to All** ↔ **Only Requests** (đây chính là Open vs Gated).
- [ ] Chọn **Campus Location** từ dropdown.
- [ ] Chọn **Preparing for**: Normal/Midterm/Final.
- [ ] Chọn **Expectation Level**: Pass/Casual/Overachieving.
- [ ] Thêm 1-2 **tag** tự do (bấm "Add Tag" → nhập → chip xuất hiện, có thể xoá bằng nút x trên chip).
- [ ] Bấm chọn **Date** (Material date picker) và **Time** (Material time picker, đồng hồ 12h).
- [ ] **Kéo slider Duration** (đúng cái slider user nhắc tới) — quay rõ 7 nấc: 30m / 1h / 1h30 / 2h / 2h30 / 3h / 3h30, label đổi theo từng nấc kéo.
- [ ] Chỉnh **Capacity** bằng nút +/- (mặc định 4, tối thiểu 2).
- [ ] Bỏ trống Title, bấm Create → Toast "Please enter a title" (test validate).
- [ ] Điền đủ, bấm **Create** → điều hướng sang **Success screen** ("Session Created!" / "Your study group is now live." / nút "View My Sessions").
- [ ] Bấm nút trên Success → landing về **Home** (xem Lưu ý).
- [ ] Mở lại session vừa tạo → quay X/Y hiển thị **1/Y** (chỉ có host), role là host → nút hành động là "Manage Session".

**Lưu ý:** dù nút Success ghi "View My Sessions", bấm vào thực tế điều hướng về **Home**, không phải tab My Sessions — đừng thuyết minh sai theo label nút.

---

### C2. Continue from Last / "Pick up Session" (từ History)

**Chuẩn bị:** cần 1 session **đã kết thúc** (finished/past) trong History của tài khoản demo — session `PAST` mode.

**Quay gì:**
- [ ] Mở History → bấm vào 1 session cũ → vào Session Detail ở **PAST view mode** — quay rõ: mọi nút hành động khác biến mất, chỉ còn nút **"Pick up Session"**.
- [ ] Bấm "Pick up Session" → dialog xác nhận "Pick up Session?" / "A new session will be created with the same details. All previous members will be automatically invited." → bấm **Continue**.
- [ ] Quay Create Session mở ra với **field đã điền sẵn** (course, title, description, goals, preparing-for, expectation, campus location, capacity, gated/open, tags) — **riêng Date/Time/Duration để trống**, phải tự chọn lại (nhấn mạnh điểm này khi quay).
- [ ] Chọn ngày giờ mới → bấm Create → Success screen.
- [ ] Mở Inbox bằng **tài khoản thứ hai** (người từng là member của session cũ) → quay item **invite mới** vừa được tự động gửi tới họ cho session mới.

---

### C3. Session Details — ma trận nút hành động dưới cùng

Đây là màn hình quan trọng nhất để chứng minh logic phân nhánh — quay đủ **8 trạng thái** dưới đây, mỗi trạng thái 1 clip ngắn cho thấy rõ text nút + trạng thái bật/tắt màu:

| # | Trạng thái | Session để mở (data seed sẵn) | Text nút | Bấm được? |
|---|---|---|---|---|
| 1 | Session mở, chưa tham gia | bất kỳ session OPEN nào chưa join | **"Join Session"** | Có |
| 2 | Session Gated (private) | `s-calc-help` / `s-mobile-sync` / `s-dsa-gated-mid` | **"Request to Join"** | Có |
| 3 | Session đầy | `s-phys-final-full` | **"Session Full"** (nút xám mờ) | Không |
| 4 | Có người bị block trong roster | `s-arts-workshop` (dùng tài khoản đã block `u-blocked-sample`) | **"Contains Blocked User"** (nút xám mờ) | Không |
| 5 | Đang là member (đã accepted) | join thử 1 session rồi mở lại từ My Sessions | **"Leave Session"** | Có |
| 6 | Đang là host | session vừa Create ở C1 (khuyên dùng — xem cảnh báo bug seed ở mục 0) | **"Manage Session"** | Có |
| 7 | Được mời (chưa accept) | `s-english-club` | **"Accept Invite"** | Có |
| 8 | Đã cancel (host huỷ) | tự huỷ 1 session sống trước (xem C4) | **"Cancelled by Host"** (nút xám mờ) | Không |
| 9 | Xem từ History (PAST mode) | bất kỳ session cũ | **"Pick up Session"** | Có |

**Quay thêm 2 case biên (thứ tự ưu tiên logic, rất đáng nói khi bảo vệ đồ án):**
- [ ] Case member đã join **trước khi** session đầy → dù session sau đó đầy thêm người khác, member cũ **vẫn thấy "Leave Session"**, không bị chuyển sang "Session Full" (member check được ưu tiên hơn full check).
- [ ] Case được mời (`invited`) nhưng session đó đã đầy/có blocked user trước khi kịp accept → nút hiện **"Session Full"/"Contains Blocked User"** thay vì "Accept Invite" — invite bị "khoá" lại.

**Quay thêm các phần khác trên màn Session Detail:**
- [ ] Card **Pending Requests** hiện trên Session Detail (chỉ hiện với host, session gated có người xin vào) — bấm vào 1 hàng chỉ mở **Profile người đó**, **không có nút Approve/Reject ở đây** (nhấn mạnh: duyệt phải qua Manage Session).
- [ ] (Host) bấm **"Invite more students"** → sang màn tìm theo Student ID (xem C5).
- [ ] Upload material: bấm nút upload (chỉ host thấy, session chưa kết thúc) → chọn file bất kỳ → quay row file mới xuất hiện trong danh sách.
- [ ] Bấm vào 1 row material → file mở bằng app ngoài (PDF viewer/trình duyệt); nếu máy không có app phù hợp → Toast "No application found to open this file".
- [ ] (Host) bấm nút xoá (x) trên 1 material → dialog "Delete Material?" → xác nhận → row biến mất ngay.
- [ ] (Host) bấm **"Mark Finished"** trên 1 session → dialog xác nhận → session chuyển trạng thái Finished.

**Lưu ý:**
- `containsBlockedUser` tính theo **danh sách block của người đang xem**, không phải global — muốn demo case 4, đúng tài khoản đang xem phải là người đã block `u-blocked-sample` trước (seed tự làm điều này cho tài khoản chính sau lần login đầu).
- Không quay nhầm nút Approve/Reject vào card Pending Requests **trên Session Detail** — nó không tồn tại ở đó, chỉ có ở Session Manage.

---

### C4. Session Manage (host)

**Chuẩn bị:** dùng session **GATED tự tạo sống** ở C1 (không dùng `s-my-hosted` — xem cảnh báo bug seed ở mục 0), cho **tài khoản phụ** bấm "Request to Join" session đó trước (từ Home, dùng tài khoản phụ) để có sẵn 1 pending request thật khi vào Manage.

**Quay gì:**
- [ ] Từ session đang làm host (session vừa Create ở C1) bấm **"Manage Session"**.
- [ ] Card **Pending Requests**: quay request của tài khoản phụ → bấm **Approve** → member được thêm ngay lập tức (không cần bấm Save), X/Y tăng.
- [ ] Tạo/duyệt thêm 1 request khác rồi bấm **Reject** → request biến mất, X/Y không đổi.
- [ ] Card danh sách thành viên: bấm nút kick (x) trên 1 hàng → dialog "Remove Member?" → xác nhận → **row biến mất khỏi UI ngay nhưng CHƯA ghi Firestore** (giải thích: đây là thay đổi "staged", chỉ thật sự xoá khi bấm Save).
- [ ] Bấm icon bút chì trên card thông tin session → mở dialog **Session Edit** (modal 95% màn hình) → sửa vài field (ví dụ đổi Title, kéo lại Duration slider, đổi Campus Location) → bấm **Save Changes** trong dialog → dialog đóng, nhưng **vẫn chưa ghi Firestore thật**.
- [ ] Toggle **Open to All ↔ Only Requests** ngay trên Manage screen — cũng chỉ là staged.
- [ ] Bấm nút **Save** ở cuối màn Session Manage → dialog xác nhận "Save Changes?" → xác nhận → lúc này mới thật sự ghi Firestore **cùng lúc** cả phần sửa session lẫn phần xoá member đã stage — quay Toast/fan-out thông báo tới các member còn lại ("The session details have been updated.") xuất hiện trong Inbox của tài khoản thứ hai.
- [ ] Test back-out: sửa 1 field rồi bấm Back (hệ thống hoặc header) mà **không** bấm Save → dialog "Discard Changes?" với 2 lựa chọn Save/Discard.
- [ ] Bấm **"Cancel Session"** (nút đỏ) → dialog "Cancel Session?" / "This will notify all members and stop any further activity." → xác nhận → app tự thoát về Home, và session này giờ dùng để quay case #8 ở mục C3 ("Cancelled by Host").
- [ ] Kiểm tra Inbox tài khoản thứ hai → thấy thông báo "The session has been cancelled by the host."

**Lưu ý:** màn Session Manage **không có khu vực upload material** dù code có sẵn hàm xử lý — đừng tìm nút upload ở màn này, upload chỉ làm được từ Session Detail.

---

### C5. Invite by Student ID

**Quay gì:**
- [ ] Từ Session Detail hoặc Session Manage bấm dòng "Invite more students" → mở màn tìm kiếm.
- [ ] Để trống ô search → quay danh sách gợi ý các thành viên **cùng community**.
- [ ] Gõ 1 phần student ID/tên → quay kết quả lọc dần theo từng ký tự (prefix search).
- [ ] Gõ chuỗi vô nghĩa → quay empty state "No student found with that ID."
- [ ] Bấm chọn 1 người → quay điều hướng sang **Success screen** ("Invitation Sent!" / "The student has been notified." / nút "Back to Management").
- [ ] Bấm nút trên Success → landing về **Home** (xem Lưu ý, giống case Create Session).
- [ ] Mở Inbox tài khoản thứ hai → thấy item **invite** mới với 2 nút **Accept** / **Details**.

**Lưu ý:** nút Success ghi "Back to Management" nhưng thực tế bấm vào lại về **Home**, không quay lại Session Manage — đừng thuyết minh theo label nút.

---

## PHẦN D — Khám phá & tổ chức (chia đều)

### D1. Home — filter/search/sort/khoảng cách/trạng thái xám

**Chuẩn bị:** bật quyền vị trí (Location) cho app trước khi quay phần sort theo khoảng cách.

**Quay gì:**
- [ ] Quay 3 hàng filter chip: **loại session** (All/Midterm/Final/Review), **loại môn** (8 category), **mức kỳ vọng** (All/Pass/Casual/Overachieving) — bấm thử vài chip, quay danh sách lọc lại theo Firestore query thật (không phải lọc tại chỗ).
- [ ] Bấm nút mũi tên cạnh Sort để **đóng/mở cả 3 hàng chip cùng lúc** (đã quay animation ở mục A5, ở đây chỉ cần nhắc lại nhanh nếu muốn nối cảnh).
- [ ] Gõ vào ô tìm kiếm ("Search for name, course ID, etc.") 1 từ khớp tên/course ID/course name → quay kết quả lọc ngay (client-side, có debounce ~350ms).
- [ ] Bấm nút **Sort** → mở popup menu 4 lựa chọn: **Soonest time / Name (A–Z) / Name (Z–A) / Nearest location**.
  - [ ] Chọn "Nearest location" → xin quyền vị trí lần đầu → danh sách sắp theo khoảng cách, mỗi card hiện nhãn khoảng cách kiểu app giao đồ ăn (ví dụ "300m", "2.4km").
  - [ ] Test case từ chối quyền vị trí → Toast "Location unavailable — sorted by time instead", sort âm thầm quay lại theo thời gian, không có dialog lỗi to.
- [ ] Bật toggle **"Hide Conflicts"** → các session trùng lịch với session mình đã join bị **ẩn hẳn** khỏi danh sách (khác với việc chỉ làm xám).
- [ ] Tắt "Hide Conflicts" lại → quay card session trùng lịch hiện lại ở dạng **xám mờ + label "Overlaps your schedule"**.
- [ ] Quay lần lượt 2 case xám còn lại: card session đầy → label **"Session full"**; card có blocked user → label **"Contains a blocked user"** (thứ tự ưu tiên: full > blocked > overlap nếu một session dính nhiều điều kiện cùng lúc).

**Lưu ý:** 2 toggle pill "Grind"/"Casual" trên UI đang ẩn (`visibility=gone`), không hoạt động — đừng tìm hoặc nhắc tới chúng trong video.

---

### D2. Community — REST API + verified badge

**Quay gì:**
- [ ] Vào Community Selection **ngay từ đầu, chưa gõ gì vào ô search** → đây là lúc app gọi **HTTPS REST trực tiếp tới Firestore REST API** (không qua SDK) — nếu có công cụ network inspector (ví dụ Logcat lọc theo Retrofit/OkHttp, hoặc Charles Proxy) thì bật lên quay song song để chứng minh có request HTTP thật ra ngoài.
- [ ] Quay lưới 2 cột community, chỉ ra rõ 3 card có **icon tick nhỏ** cạnh nút "Join Community" (HCMUS, FPT-HCM, HUST) so với 2 card không có tick (OPEN-STUDY-DN, OPEN-STUDY-CT).
- [ ] Gõ vào ô search "Search for your college" → quay kết quả lọc theo tên (từ lúc này đã chuyển qua Firestore SDK, không còn REST — có thể nói miệng điểm này).
- [ ] Bấm 1 city-chip (danh sách chip được dựng động từ dữ liệu REST, ví dụ "Ho Chi Minh City", "Hanoi", "Da Nang") → danh sách lọc theo thành phố.
- [ ] Thử **join community verified** bằng tài khoản **chưa verify email** → lỗi "Verify your email address before joining a verified community."
- [ ] Thử join bằng tài khoản đã verify nhưng **email sai domain** (ví dụ gmail.com trong khi HCMUS whitelist `hcmus.edu.vn`/`fitus.edu.vn`) → lỗi "Your email domain isn't on this community's allowed list."
- [ ] Join thành công 1 community **không verified** (OPEN-STUDY-DN) → không cần qua bất kỳ check nào ở trên, vào thẳng.

**Lưu ý:** hàng chip filter tĩnh cũ (All/Universities/STEM/Arts/Social) đang bị ẩn trong layout — đừng tìm, hàng chip city mới là hàng thật đang hoạt động.

---

### D3. My Sessions — List View / Calendar View (Vĩ)

**Quay gì:**
- [ ] Mở tab My Sessions, mặc định **List View** → quay các session được nhóm theo ngày với header **"Today"**, **"Tomorrow"**, hoặc `"Wednesday, Sep 9"` cho các ngày xa hơn.
- [ ] Bấm segment **"Calendar View"** → quay lưới lịch tháng, header tuần **M T W T F S S** (thứ Hai đứng đầu, đúng thứ tự thực tế bất kể locale), ô ngoài tháng hiện mờ hơn.
- [ ] Quay rõ: ngày có session bận → **cả vòng tròn số ngày** tô màu **xanh dương**; ngày đang chọn để xem → **cả vòng tròn** tô màu **vàng** (không phải chấm nhỏ, mà là tô nguyên số).
- [ ] Bấm 1 ngày khác trên lịch → danh sách session bên dưới lịch đổi theo ngày vừa chọn.
- [ ] Bấm mũi tên trái/phải cạnh tiêu đề tháng → chuyển tháng trước/sau, quay tiêu đề `"September 2026"` đổi theo.
- [ ] Mở lại tab lần đầu (fresh) → chứng minh app **tự nhảy tới tháng có session gần nhất** và tự chọn sẵn ngày đó, không cần người dùng tự tìm.

---

### D4. Inbox — 3 loại thông báo + reminder WorkManager

**Quay gì:**
- [ ] Quay danh sách Inbox với các **pill ngày** chen giữa ("Today"/"Yesterday"/ngày cụ thể) phân tách các nhóm thông báo.
- [ ] Item loại **Invite** (icon phong bì): quay 2 nút **Accept** và **Details** — bấm **Accept** ngay tại Inbox (không cần rời màn hình) → item cập nhật trạng thái đã accept; bấm **Details** ở 1 item khác → mở Session Detail (LIVE mode).
- [ ] Item loại **Join Request** (icon chuông, dành cho host): chỉ có 1 nút **Details** → bấm vào → điều hướng thẳng sang **Session Manage** (không phải Session Detail) để host duyệt/từ chối ở đó.
- [ ] Item loại **System** (thông báo chung, ví dụ "Welcome to Study Group Finder!"): không có nút nào, bấm cả dòng chỉ để đánh dấu đã đọc — quay icon chuyển từ đậm sang mờ (alpha) sau khi đọc.
- [ ] Join 1 session bất kỳ (mode Open) → chờ tới gần giờ session (hoặc set giờ session cách hiện tại ~16 phút để test nhanh) → quay notification hệ thống xuất hiện, nội dung **"Starts in about 15 minutes."**, bấm vào notification → deep-link thẳng vào đúng Session Detail đó.
- [ ] Rời (Leave) session đó trước giờ hẹn → xác nhận reminder job bị huỷ (không có notification xuất hiện nữa) — có thể demo bằng cách join rồi leave nhanh, quay log/toast xác nhận hành động.

**Lưu ý:**
- Nút của Join Request row ghi **"Details"**, không phải "Manage" — đừng thuyết minh nhầm tên nút.
- Reminder chạy hoàn toàn **local (WorkManager, không có push/FCM)**. Nếu host là người bấm Approve cho người khác, job reminder được đặt lịch trên **máy của host**, không tự đẩy notification sang máy người được duyệt — nếu muốn quay case reminder cho tài khoản thứ hai, tài khoản đó phải **tự** join/accept invite trên chính máy của họ, không dựa vào hành động của host.
- Timestamp trong Inbox hiện đầy đủ ngày giờ (`"MMM d, yyyy, h:mm a"`), không phải dạng tương đối "2 giờ trước".

---

### D5. History — dashed/dimmed cancelled + export PDF (Vĩ)

**Quay gì:**
- [ ] Vào History từ icon đồng hồ cát (Home/My Sessions/Inbox) → header History đổi sang nút Back (không hiện icon đồng hồ cát nữa, tránh vòng lặp).
- [ ] Quay danh sách: session **Finished** có pill "FINISHED" nền xanh nhạt, card nền trắng đặc, không mờ.
- [ ] Quay session **Cancelled** (dùng session đã huỷ ở mục C4) → pill "CANCELLED" nền đỏ nhạt, **toàn bộ card viền nét đứt (dashed) + độ mờ 50%** — quay cận cảnh viền đứt nét để thấy rõ khác biệt với card thường.
- [ ] Bấm vào 1 session cũ → mở Session Detail ở PAST mode (nối sang case #9 ma trận nút ở mục C3 nếu muốn).
- [ ] Bấm nút **EXPORT** (góc trên phải, icon download) → hệ thống mở dialog **Save As** chuẩn Android (Storage Access Framework), tên file gợi ý **"StudySessionHistory.pdf"** → chọn nơi lưu → xác nhận.
- [ ] Quay điều hướng sang **Success screen** ("History Exported!" / "Your session history has been saved as PDF." / nút "Back to History").
- [ ] Mở file PDF vừa lưu bằng app đọc PDF trên máy → quay nội dung: header vàng "Study Session History", ngày xuất, tên/student ID/community, rồi từng session cũ với title/môn/thời gian/địa điểm/badge màu theo trạng thái (xanh lá=Finished, đỏ nhạt=Cancelled)/vài dòng goals.

**Lưu ý:**
- Bấm nút "Back to History" trên Success screen thực tế điều hướng về **Home**, không quay lại màn History — đừng thuyết minh theo đúng label nút.
- Không có tính năng **Share/gửi file** (không có share-sheet `ACTION_SEND`) sau khi export — chỉ có bước Save As hệ thống. Đừng script cảnh "bấm share rồi gửi qua Zalo/Gmail", tính năng đó chưa tồn tại.

---

## PHẦN F — Cảnh kết: Sign Out

Đặt ở cuối cùng sau khi đã quay hết Part C và D, vì đây là hành động duy nhất **phá huỷ trạng thái đăng nhập tài khoản chính** (xoá Room + về Login) — làm nó ở giữa video sẽ cắt đứt mọi mục phía sau cần tài khoản chính.

**Quay gì:**
- [ ] Vào Profile (self-view, tài khoản chính) → bấm icon sign-out góc header → dialog xác nhận "Sign Out" / "Are you sure you want to sign out?" → bấm **Sign Out**.
- [ ] Quay ngay sau đó: app điều hướng thẳng về **Login**, không cho back lại được (đã pop hết stack).
- [ ] Đăng nhập lại bằng **tài khoản thứ hai (phụ)** → chứng minh không còn thấy dữ liệu của tài khoản chính (Room đã bị xoá sạch 4 bảng: session, community, mySession, profile) — ví dụ mở My Sessions phải rỗng/khác hẳn danh sách vừa quay ở Part C/D.

---

## PHỤ LỤC 1 — Bảng session seed sẵn dùng nhanh khi quay

| Session ID | Tình huống | Mode |
|---|---|---|
| `s-phys-final-full` | Đầy chỗ (4/4) | Open |
| `s-arts-workshop` | Có thành viên đã bị block bởi tài khoản chính | Open |
| `s-calc-help`, `s-mobile-sync`, `s-dsa-gated-mid` | Gated, đã có sẵn người xin vào (pending) | Gated |
| `s-english-club` | Tài khoản chính đang ở trạng thái Invited | Open |
| `s-my-hosted` | Tài khoản chính là host, có 1 pending request (Carol) — ⚠️ chỉ dùng để **xem/lướt** (mục A4), **không** dùng làm vehicle chính cho C3 case 6 / C4 (xem cảnh báo bug seed ở mục 0) | Gated |
| `s-overlap-a` / `s-overlap-b` | Trùng lịch nhau (dùng để demo overlap/"Hide Conflicts") | Open |
| `s-social-read` | Session mở, chưa có ai tham gia ngoài host | Open |

Không có session nào seed sẵn ở trạng thái **Cancelled** hoặc **Finished** với data phong phú — 2 trạng thái này nên tự tạo sống trên camera (Mark Finished / Cancel Session) trước khi quay các phần liên quan (D5, case #8 mục C3).

---

## PHỤ LỤC 2 — Tổng hợp những điều KHÔNG nên nói/quay (tránh bị vặn khi bảo vệ)

- Header app **không có** icon bút chì sửa dùng chung — nút sửa nằm riêng trên từng trang (Profile, Session Manage).
- Sign Up **không** vào thẳng Community Selection — phải qua Success screen rồi tự đăng nhập lại.
- Không có màn "nhập mật khẩu mới" trong app (`fragment_new_password.xml` là file mồ côi, không nối route) — reset password xử lý ngoài app qua email Firebase.
- Nút Success sau khi Create Session / gửi Invite / Export PDF đều ghi nhãn gợi ý quay lại đúng chỗ ("View My Sessions", "Back to Management", "Back to History") nhưng **thực tế bấm vào đều về Home** — không thuyết minh đúng theo chữ trên nút.
- Card Pending Requests trên **Session Detail** chỉ để xem, không duyệt được — duyệt/từ chối chỉ làm được ở **Session Manage**.
- Nút hành động trên item Join Request trong Inbox ghi **"Details"**, không phải "Manage".
- Campus Location trong Create/Edit Session hiện dùng chung 4 địa điểm mock, chưa thật sự đổi theo từng community.
- Reminder notification chạy local qua WorkManager, không có FCM/push — hành động của host (approve) không tự đẩy notification sang máy người được duyệt.
- History export PDF **không có** tính năng Share/gửi file, chỉ có Save As hệ thống.
- 2 toggle "Grind"/"Casual" trên Home đang ẩn, không hoạt động — đừng tìm hoặc nhắc tới.
- Card "tên community" trên Profile hiện đang hiện ID thô (ví dụ "HCMUS"), chưa resolve ra tên đầy đủ.
- `s-my-hosted` chỉ đáng tin cậy làm demo "tài khoản chính là host" ở **mục A4** (đầu video, trước khi tài khoản throwaway của B1 cold-start làm nó bị ghi đè). Từ C3/C4 trở đi, coi như document này đã đổi chủ, dùng session tự tạo sống thay thế (xem cảnh báo bug ở mục 0).
- Đừng bấm Block vào `u-blocked-sample` để "demo" — người này đã bị seed tự block sẵn cho tài khoản chính, bấm vào chỉ thấy nút Unblock có sẵn, không quay được thao tác Block thật.
