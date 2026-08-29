# Study Group Finder — Hướng dẫn kỹ thuật

Ứng dụng Android tìm và tổ chức nhóm học theo community (trường/khoa). Tài liệu này nói **dùng kỹ thuật gì, ở phần nào, và triển khai ra sao**.

- Đặc tả đầy đủ: [`updated_study_group_finder_dev_plan-1.md`](updated_study_group_finder_dev_plan-1.md) — mọi mục §x.y bên dưới đều trỏ về file đó.
- Checklist đối chiếu tính năng gốc của nhóm: §13 của dev plan.

---

## 1. Trạng thái hiện tại: đây là **sườn**, chưa phải app chạy được

Repo hiện có đầy đủ **cấu trúc** — package, model, DAO, repository, ViewModel, Fragment, navigation graph — nhưng **thân hàm là `TODO()`**. Build xanh, navigation đã được kiểm chứng, nhưng chạy lên sẽ crash ngay ở màn hình đầu vì `SplashFragment` gọi vào `TODO()`.

Đó là chủ ý: sườn để cả nhóm chia việc song song mà không đụng nhau, không phải để demo.

### Điều kiện tiên quyết trước khi code logic

| # | Việc | Vì sao bắt buộc |
|---|---|---|
| 1 | Tạo Firebase project, bật **Authentication (Email/Password)**, **Firestore**, **Storage** | Không có thì mọi call Firebase fail runtime |
| 2 | Tạo account Cloudinary, lấy `cloud_name`, `api_key`, `api_secret` | Lưu trữ ảnh profile (§1.0) |
| 3 | Tải `google-services.json` vào `app/` | Thiếu file này thì plugin ở bước 4 làm **fail build** |
| 4 | Bỏ comment `alias(libs.plugins.google.services)` trong [`app/build.gradle.kts`](app/build.gradle.kts), thêm plugin vào `libs.versions.toml` + root `build.gradle.kts` | Không có plugin thì `google-services.json` không bao giờ được đọc → `Default FirebaseApp is not initialized` |
| 5 | Dán **Firestore rules** (§4) và **Storage rules** (§4.1) vào Console | Storage mặc định khoá; Firestore rules mặc định chặn hết |
| 6 | Điền thông tin Cloudinary vào [`CloudinaryConfig.kt`](app/src/main/java/com/studyfinder/app/util/CloudinaryConfig.kt) | Ảnh profile sẽ không upload được nếu thiếu |
| 7 | Thay `{PROJECT_ID}` trong [`PublicCommunityApi.kt`](app/src/main/java/com/studyfinder/app/data/remote/rest/PublicCommunityApi.kt) bằng project ID thật | REST call §7.1 |
| 8 | Seed document mẫu: `communities`, `sessions` (có sẵn `memberUids`, `endTime`, `courseCategory`), `users` | Không có data thì không phân biệt được empty state với bug |

### Chạy app

```bash
# Git Bash (KHÔNG dùng `bash` trong PowerShell — đó là WSL, không thấy adb)
./scripts/start-android.sh          # build + install + launch + stream logcat
./scripts/start-android.sh :clean   # thêm wipe emulator data
```

---

## 2. Kiến trúc

**MVVM một chiều, Firestore là nguồn chân lý duy nhất.**

```
Firestore  ──►  Repository  ──►  Room  ──►  ViewModel  ──►  Fragment
   ▲                │
   └──── ghi ───────┘
```

Bốn quy tắc không được vi phạm (§2.2):

1. **Mọi write đi vào Firestore.** UI và ViewModel **không bao giờ** ghi vào Room.
2. **Room chỉ là projection read-only**, do repository ghi ngay sau khi fetch thành công. Nó là snapshot để vẽ lần đầu, không phải database để suy luận.
3. **Screen observe Room** qua `Flow`, không observe Firestore trực tiếp — trừ Session Detail (§7.3) cần realtime, và ở đó snapshot vẫn ghi xuyên qua Room trên đường lên UI.
4. **Không tắt offline persistence của Firestore.** Nó lo *write* queue khi offline; Room lo *cái gì được render*.

> Hệ quả một dòng: nếu screen hiện data cũ, bug luôn ở write-through của repository, không bao giờ ở UI.

**Không dùng:** Compose (dùng XML + ViewBinding), Hilt/Koin (dùng `ServiceLocator`), RxJava (dùng Coroutines), backend server (dùng Firestore SDK), Cloud Functions, `collectionGroup` query.

---

## 3. Bản đồ file

```
app/src/main/java/com/studyfinder/app/
├── StudyFinderApp.kt              Application — init ServiceLocator + notification channel
├── ServiceLocator.kt              DI thủ công, dựng repository một lần
├── MainActivity.kt                Activity duy nhất: NavHostFragment + bottom nav
│
├── model/                         Data class dùng trực tiếp bởi UI (không có domain layer riêng)
│   ├── Enums.kt                   TagType, CourseCategory, ExpectationLevel, SessionMode,
│   │                              SessionStatus, MemberStatus, InboxType, SessionSort, SessionViewMode
│   ├── Session.kt                 Session, SessionMember, BusyInterval
│   ├── Community.kt               Community, Course, CampusLocation
│   ├── UserProfile.kt
│   └── InboxItem.kt
│
├── data/
│   ├── local/                     Room — cache offline
│   │   ├── AppDatabase.kt
│   │   ├── converter/Converters.kt        List<String> <-> String (Room không lưu list)
│   │   ├── dao/Daos.kt                    4 DAO, mọi read trả Flow
│   │   └── entity/Entities.kt             4 entity
│   ├── remote/
│   │   ├── firestore/
│   │   │   ├── FirestoreRefs.kt           MỘT nơi biết layout collection + tên field
│   │   │   └── FirestoreMappers.kt        doc <-> model <-> entity
│   │   └── rest/                          Retrofit — CHỈ dùng cho §7.1
│   │       ├── PublicCommunityApi.kt
│   │       ├── RetrofitClient.kt
│   │       └── dto/FirestoreRestDto.kt    DTO cho wire format của Firestore REST
│   └── repository/
│       ├── AuthRepository.kt      §7.0
│       ├── CommunityRepository.kt §7.1
│       ├── SessionRepository.kt   §3.1 — mọi transaction membership
│       ├── ProfileRepository.kt   §7.7 — profile, ảnh, block list, activity graph
│       └── InboxRepository.kt     §7.5/§7.8 — fan-out
│
├── ui/
│   ├── common/StateRenderer.kt    4 state: loading/empty/error/offline
│   ├── auth/                      Splash, Login, Signup, ForgotPassword + AuthViewModel
│   ├── community/                 CommunitySelection + ViewModel + Adapter
│   ├── home/                      Home + ViewModel + SessionListAdapter
│   ├── sessiondetail/             SessionDetail + ViewModel + MemberAvatarAdapter
│   ├── sessioncreate/             CreateSession + ViewModel
│   ├── sessionmanage/             SessionManage, InviteByStudentId + ViewModel + 2 Adapter
│   ├── mysessions/                MySessions + ViewModel + List/Calendar Adapter
│   ├── history/                   History + ViewModel + Adapter
│   ├── inbox/                     Inbox + ViewModel + Adapter
│   └── profile/                   Profile + ViewModel + ActivityGraphView (custom View)
│
├── notification/                  ReminderWorker (WorkManager), NotificationHelper
└── util/                          UiState, DateTimeUtils, LocationUtils, OverlapUtils,
                                   HistoryExporter, Extensions

app/src/main/res/
├── navigation/nav_graph.xml       14 destination, 24 action — toàn bộ flow
├── menu/bottom_nav_menu.xml       ID trùng destination ID (để NavigationUI tự wire)
├── layout/                        fragment_*.xml (14), item_*.xml (6), activity_main.xml
├── values/                        strings, colors, themes (Material3), dimens
└── xml/file_paths.xml             FileProvider cho export CSV/PDF
```

---

## 4. Flow điều hướng

Start destination là `splashFragment`. Nó đọc auth + community state rồi rẽ **ba hướng** và tự pop khỏi back stack (§7.0) — vì Firebase giữ session qua lần mở app, nên start destination không thể là giá trị tĩnh trong XML.

```
splashFragment ─┬─ chưa đăng nhập ──────────────► loginFragment
                ├─ đã login, chưa có community ─► communitySelectionFragment
                └─ đã login, có community ──────► homeFragment

loginFragment ──┬─► signupFragment ──► communitySelectionFragment
                ├─► forgotPasswordFragment
                ├─► communitySelectionFragment      (account mới)
                └─► homeFragment                    (user cũ)

communitySelectionFragment ──► homeFragment          (isEditMode = false)
                             popBackStack()          (isEditMode = true, gọi từ Profile)

┌─ bottom nav ───────────────────────────────────────────────────────┐
│ homeFragment   mySessionsFragment   inboxFragment   profileFragment │
└────────────────────────────────────────────────────────────────────┘

homeFragment ──┬─► sessionDetailFragment (viewMode = LIVE)
               └─► createSessionFragment (nút + FAB)

sessionDetailFragment ──┬─► sessionManageFragment      (host)
                        ├─► profileFragment(uid)       (tap avatar → read-only)
                        └─► createSessionFragment(prefillFromSessionId)
                                                       (continue from last, ở PAST mode)

createSessionFragment ──► sessionDetailFragment        (popUpTo chính nó, inclusive)

sessionManageFragment ──┬─► inviteByStudentIdFragment
                        └─► profileFragment(uid)

mySessionsFragment ──┬─► sessionDetailFragment (LIVE)
                     └─► historyFragment

historyFragment ──► sessionDetailFragment (viewMode = PAST)

inboxFragment ──┬─► sessionDetailFragment   (nút "Details" trên invite row)
                └─► sessionManageFragment   (join_request row, host-facing)

profileFragment ──┬─► communitySelectionFragment(isEditMode = true)
                  └─► loginFragment          (sign out, popUpTo nav_graph inclusive)
```

**Đã kiểm chứng:** 14/14 destination tới được, 24/24 action hợp lệ và đều được gọi trong code, không có action mồ côi. Safe Args validate chiều còn lại tại compile-time — sai tên action hoặc sai kiểu argument là **lỗi build**, không phải crash runtime.

### Bốn argument quyết định hành vi screen

| Argument | Ở đâu | Ý nghĩa |
|---|---|---|
| `viewMode: SessionViewMode` | `sessionDetailFragment` | `LIVE` = bình thường. `PAST` = "past view mode" của spec (từ History) — tắt hết nút action, hiện nút "continue from last" |
| `uid: String?` | `profileFragment` | `null` = self view (sửa được, có sign out). Non-null = read-only view của người khác (chỉ có nút Block) |
| `isEditMode: Boolean` | `communitySelectionFragment` | `false` = lần đầu, xong đi Home. `true` = sửa từ Profile, xong pop back |
| `prefillFromSessionId: String?` | `createSessionFragment` | Non-null = đến từ "continue from last", điền sẵn field + mời lại member cũ |

---

## 5. Kỹ thuật: dùng ở đâu và triển khai thế nào

### 5.1 Kotlin Coroutines
**Dùng ở:** toàn bộ tầng data và ViewModel.
**Triển khai:** `viewModelScope.launch {}` trong ViewModel, `suspend fun` trong repository, `Flow` cho stream. `kotlinx-coroutines-play-services` cho `.await()` trên `Task<T>` của Firebase — đó là lý do có dependency này, không có nó phải viết callback lồng nhau.
```kotlin
val snapshot = FirestoreRefs.session(id).get().await()   // cần play-services
```
Không dùng RxJava.

### 5.2 MVVM + ViewModel + LiveData/Flow
**Dùng ở:** mỗi screen một ViewModel.
**Triển khai:** `by viewModels()` trong Fragment. ViewModel giữ **toàn bộ** filter/sort state (xem `HomeViewModel.Filters`) để rotate không mất. Fragment chỉ observe và render — không có business logic trong Fragment.

### 5.3 ViewBinding
**Dùng ở:** mọi Fragment và Activity.
**Triển khai:** pattern `_binding` nullable + `binding` non-null getter, gán `null` trong `onDestroyView()` — bắt buộc, không thì leak View sau khi Fragment bị destroy mà ViewModel còn sống.
```kotlin
private var _binding: FragmentHomeBinding? = null
private val binding get() = _binding!!
override fun onDestroyView() { super.onDestroyView(); _binding = null }
```

### 5.4 Navigation Component + Safe Args
**Dùng ở:** toàn bộ điều hướng. Một Activity, một `NavHostFragment`, 14 fragment destination.
**Triển khai:** khai báo `<argument>` trong `nav_graph.xml`, gọi qua class `XxxFragmentDirections` được generate, nhận bằng `by navArgs()`. **Không** truyền dữ liệu qua Bundle thủ công.
- Bottom nav: ID trong `bottom_nav_menu.xml` **trùng** destination ID → `setupWithNavController(navController)` là toàn bộ phần wiring, không cần click listener.
- Ẩn/hiện bottom nav bằng `addOnDestinationChangedListener` (xem `MainActivity`).
- `popUpTo` + `popUpToInclusive` để nút Back không quay lại màn login sau khi đăng nhập.
- Enum truyền được qua Safe Args: `app:argType="com.studyfinder.app.model.SessionViewMode"`.

### 5.5 Firebase Authentication
**Dùng ở:** §7.0 — Splash routing, Login, Signup, ForgotPassword; `AuthRepository`.
**Triển khai:**
- `signInWithEmailAndPassword` / `createUserWithEmailAndPassword`
- Thứ tự signup **quan trọng**: tạo account → ghi `users/{uid}` → `sendEmailVerification()` → điều hướng. Ghi user doc *trước khi* navigate, vì mọi screen sau đó đều đọc nó.
- `sendPasswordResetEmail()` — 2 dòng SDK, và là thứ giám khảo thử đầu tiên.
- `user.isEmailVerified` gate việc join **verified** community (§7.1) — nếu không, ai cũng đăng ký `abc@university.edu.vn` mà không sở hữu email đó.
- Sign out: clear Auth → **wipe Room** (đang giữ data của user khác) → clear SharedPreferences → navigate popUpTo cả graph.
- Phân biệt 4 `FirebaseAuthException` code: sai password, email không tồn tại, email sai format, lỗi mạng. "Có lỗi xảy ra" là không đủ.

### 5.6 Cloud Firestore
**Dùng ở:** mọi CRUD. `FirestoreRefs` là nơi duy nhất biết đường dẫn collection.

**a) Query + composite index**
Home (§7.2) lọc `communityId` + `tagType` + `courseCategory` + `orderBy(startTime)`. Firestore cần composite index cho tổ hợp filter — lần đầu chạy, Console hiện link tạo index một click. **Chip không chọn phải không thêm clause nào**, đừng thêm clause "match everything", nếu không số index cần tạo bùng nổ.

**b) `array-contains` — trái tim của 4 tính năng**
Firestore **không query ngược được** từ subcollection lên parent. Không có cách nào hỏi "session nào có `members/{myUid}`" bằng query thường. Nên `memberUids` được denormalize lên session document:
```kotlin
FirestoreRefs.sessions()
    .whereArrayContains("memberUids", uid)
    .orderBy("startTime")
```
Đây là query duy nhất chạy My Sessions, History, activity graph và overlap check. **Không dùng `collectionGroup("members")`** — rules §4 không cấp quyền cho collection-group query (cần block `match /{path=**}/members/{uid}` riêng + index riêng).

**c) Transaction — invariant quan trọng nhất của project**
Mỗi thay đổi membership ghi **cả hai**: document `members/{uid}` **và** `joinedCount` + `memberUids` của session cha. Không bao giờ một cái mà thiếu cái kia. Sáu transaction (§3.1):

| Hành động | Ai chạy | Subcollection | Session cha |
|---|---|---|---|
| Join session `open` | người join | tạo `members/{uid}` = `accepted` | `joinedCount +1`, `memberUids +uid` |
| Xin vào session `gated` | người xin | tạo `members/{uid}` = `pending` | **không đổi** |
| Host mời theo student ID | host | tạo `members/{uid}` = `invited` | **không đổi** |
| Người được mời accept | người đó | `invited` → `accepted` | `joinedCount +1`, `memberUids +uid` |
| Host duyệt request | host | `pending` → `accepted` | `joinedCount +1`, `memberUids +uid` |
| Leave / host remove | member hoặc host | xoá `members/{uid}` | `joinedCount -1`, `memberUids -uid` |

`invited` và `pending` **không** tính vào `joinedCount`/`memberUids` — `memberUids` nghĩa là "accepted members", và mọi thứ downstream phụ thuộc vào nghĩa đó.

**d) Realtime listener**
Session Detail (§7.3) mở `addSnapshotListener` khi visible. Đây là cái làm edit/cancel của host hiện ra ngay cho người đang xem — **thay thế cho push notification**, không cần FCM.

**e) Create bằng WriteBatch, không phải add-rồi-patch**
Rules yêu cầu `hostUid == auth.uid`, `joinedCount == 1`, `memberUids == [hostUid]` **ngay lúc create**. Tạo ID client-side rồi ghi session doc + host member row trong một `WriteBatch`:
```kotlin
val ref = FirestoreRefs.sessions().document()   // ID sinh client-side
db.batch().apply { set(ref, payload); set(memberRef, hostRow) }.commit()
```
Nếu tách 2 write, crash giữa 2 bước để lại session không có host.

### 5.7 Firestore Security Rules (§4)
**Dùng ở:** Firebase Console → Firestore → Rules. Không cần Firebase CLI.
**Triển khai:** dán nguyên §4. Hai chỗ mà rules "hiển nhiên" sẽ làm **gãy app**, và lỗi hiện ra là `PERMISSION_DENIED` runtime chứ không phải compile error:

1. **Join.** `allow update: if auth.uid == resource.data.hostUid` trông đúng, nhưng transaction join tăng `joinedCount` trên document đó **do người join chạy, không phải host**. Host-only update ⇒ rollback toàn bộ ⇒ **không ai join được ngoài host**. Fix: hàm `joinsSelf()` / `leavesSelf()` cho phép non-host chỉ dịch counter cho chính mình, và không quá `capacity` (capacity được enforce ở **rules**, không chỉ ở client).
2. **Inbox fan-out.** Invite / approve / edit / cancel đều ghi vào inbox **của người khác**. `allow write: if auth.uid == uid` chặn hết ⇒ chết cả màn Inbox. Fix: owner có `read/update/delete`, còn **mọi user đã đăng nhập có `create`** — quyền cross-user duy nhất trong rules, với payload bị ràng buộc (`fromUid == auth.uid`, `read == false`, message ≤ 500 ký tự).

Hai lỗ leo thang quyền cũng đã bịt: user không tự ghi `status: "admin"`, và không tự duyệt `pending → accepted` (chỉ cạnh `invited → accepted` là hợp lệ cho chính mình).

### 5.8 Firebase Storage (§4.1)
**Dùng ở:** ảnh profile (§7.7), tài liệu học (§7.5).
**Triển khai:** rules **riêng biệt** với Firestore, dán ở Console → Storage → Rules. Đường dẫn `users/{uid}/profile/{file}` và `sessions/{sessionId}/materials/{file}`. Cap size + content type trong rules.
**Giới hạn đã biết, nên ghi vào báo cáo:** Storage rules **không đọc được** Firestore, nên "chỉ host được upload material" *không* biểu diễn được ở đây — chỉ enforce ở UI, rules chỉ chặn size/type. Muốn chặt phải dùng Cloud Function (ngoài scope, §11.3).

### 5.9 Room
**Dùng ở:** cache offline (§2.2). 4 entity, 4 DAO.
**Triển khai:**
- `@Insert(onConflict = REPLACE)` cho upsert, query trả `Flow<T>` để UI tự cập nhật.
- `@TypeConverters` bắt buộc: Room không lưu `List<String>` — `memberUids` và `materialUrls` được join bằng ký tự **ASCII unit separator (U+001F)**; uid và Storage URL không bao giờ chứa nó, khác với dấu phẩy.
- Timestamp đã normalize thành `Long` epoch-millis trong entity, nên không cần converter cho thời gian.
- `fallbackToDestructiveMigration()` — cache là đồ bỏ được, đổi schema thì drop, không cần viết migration.
- DAO nào cũng có `clear()` để sign out wipe sạch.
- **KSP, không phải kapt** (nhanh gấp đôi, và là processor được support).

### 5.10 Retrofit + Moshi — REST call duy nhất (§7.1)
**Dùng ở:** danh sách "browse all communities" lần đầu ở Community Selection. **Đây là yêu cầu external API của môn học** — đừng âm thầm đổi sang Firestore SDK.
**Triển khai:** đây là phần dễ bị đánh giá nhẹ nhất, cần chừa thời gian thật. Firestore REST **không** trả JSON phẳng, nó trả *typed wire format*:
```json
{ "documents": [ {
    "name": "projects/xxx/databases/(default)/documents/communities/abc123",
    "fields": {
      "name":     { "stringValue": "FPT University HCM" },
      "verified": { "booleanValue": true },
      "domainWhitelist": { "arrayValue": { "values": [ { "stringValue": "fpt.edu.vn" } ] } }
    } } ] }
```
Nên cần bộ DTO wrapper (đã có sẵn trong [`dto/FirestoreRestDto.kt`](app/src/main/java/com/studyfinder/app/data/remote/rest/dto/FirestoreRestDto.kt)). Hai chi tiết dễ sập:
- **document ID không phải field** — nó là segment cuối của `name`: `name.substringAfterLast('/')`
- **field vắng thì mất khỏi map `fields`**, không phải null → mọi property phải nullable + có default

Map về `Community` ngay ở biên repository, để không tầng nào khác thấy `stringValue`.
`converter-moshi` **một mình không đủ** — phải có `moshi` + `moshi-kotlin-codegen` (KSP), nếu không runtime throw "reflective adapter missing".

### 5.11 WorkManager (§8)
**Dùng ở:** nhắc "session sắp bắt đầu".
**Triển khai:** `OneTimeWorkRequest` với initial delay, `enqueueUniqueWork` theo tên `reminder_$sessionId` (schedule 2 lần thì replace chứ không nhân bản). Cancel theo đúng tên đó khi user leave (§7.3). Sống qua process death, không cần server.

### 5.12 FusedLocationProviderClient (§7.2)
**Dùng ở:** sort theo khoảng cách trên Home — kiểu app giao đồ ăn, "cách 0.3 km".
**Triển khai:** `getCurrentLocation()` **một lần**, không subscribe liên tục (đơn giản hơn nhiều). Rồi tính Haversine client-side trong `LocationUtils` với `lat`/`lng` đã có sẵn trong kết quả query — không geocoding, không request thêm. Xin `ACCESS_FINE_LOCATION` khi user bấm "sort by distance"; **bị từ chối thì im lặng quay về sort theo thời gian**, không hiện error dialog.

### 5.13 Camera / Photo Picker / Document Picker (§7.7, §7.5)
**Dùng ở:** ảnh profile (2 nguồn theo spec: camera và gallery), tài liệu học.
**Triển khai:** **không dùng CameraX** — CameraX để tự dựng UI camera trong app, ở đây không cần.
- Camera: `ActivityResultContracts.TakePicture` (system camera Intent), cần `CAMERA` permission.
- Gallery: `ActivityResultContracts.PickVisualMedia` — **không cần runtime permission nào** trên API 33+, đó chính là lý do chọn nó thay vì gallery intent thủ công.
- Bottom sheet 2 lựa chọn "Take photo" / "Choose from gallery" — hai launcher khác nhau sau một nút.
- Tài liệu: `ACTION_OPEN_DOCUMENT` (Storage Access Framework), cũng **không cần permission**.
- Cả ba đi cùng một đường upload lên Storage.

### 5.14 RecyclerView + ListAdapter/DiffUtil
**Dùng ở:** 6 adapter — community, session, member avatar, pending request, calendar day, inbox.
**Triển khai:** `ListAdapter` + `DiffUtil.ItemCallback` (so `id` cho `areItemsTheSame`, so cả object cho `areContentsTheSame`) → animation và hiệu năng miễn phí, không tự gọi `notifyDataSetChanged`.
`SessionListAdapter.Row` bọc `Session` cùng các annotation Home tính client-side (`distanceKm`, `overlapsAvailability`, `containsBlockedUser`).

### 5.15 Calendar view — tự dựng, không thư viện (§7.6)
**Dùng ở:** My Sessions, toggle giữa list và calendar (spec yêu cầu **cả hai**).
**Triển khai:** `CalendarView` built-in của Android **không** render được marker theo ngày, nên nó là công cụ sai dù tên nghe hợp. Cách không thêm thư viện: `RecyclerView` + `GridLayoutManager(context, 7)` render một tháng ô ngày, mỗi ô là `item_calendar_day.xml` có nhãn ngày + dot bật/tắt theo `Map<LocalDate, List<Session>>`. Tap một ngày thì filter list bên dưới.
Ship **list trước** (must-have), calendar là toggle làm sau (should-have). Cả hai render từ cùng một list đã fetch → toggle không tốn query nào.

### 5.16 Custom View — activity graph (§7.7)
**Dùng ở:** heatmap kiểu GitHub trên Profile.
**Triển khai:** `ActivityGraphView` extends `View`, override `onMeasure` (7 hàng × N cột tuần) và `onDraw` (một rounded rect mỗi ngày, màu chia bucket theo số session). Không thư viện chart — nó chỉ là lưới hình chữ nhật màu.
**Chú ý:** lấy data từ **cùng kết quả query My Sessions**, không phải `collectionGroup("members")` (rules không cho). Đặt query ở `SessionRepository` để cả hai ViewModel observe.

### 5.17 FileProvider + ACTION_SEND (§7.6)
**Dùng ở:** export CSV/PDF của History. **Không có trong spec gốc** — cắt đầu tiên nếu thiếu thời gian.
**Triển khai:** CSV là string phân cách; PDF dùng `android.graphics.pdf.PdfDocument` built-in — không thư viện export. Ghi vào cache dir, share bằng `content://` URI qua `FileProvider` (đã config sẵn ở manifest + `res/xml/file_paths.xml`). `file://` URI thẳng sẽ throw `FileUriExposedException`.

### 5.18 Glide
**Dùng ở:** avatar member, ảnh profile.
**Triển khai:** `Glide.with(view).load(photoUrl).placeholder(...).error(...).circleCrop().into(imageView)` — vài dòng mỗi chỗ dùng, tự lo cache.

### 5.19 Runtime permissions (§9)
| Permission | Cho | Cách xin |
|---|---|---|
| `CAMERA` | ảnh profile | `RequestPermission`, chỉ xin khi bấm "take photo", có rationale nếu từng bị từ chối |
| `POST_NOTIFICATIONS` (API 33+) | reminder | xin một lần, tốt nhất ngay sau khi join session đầu tiên |
| `ACCESS_FINE_LOCATION` | proximity sort | xin khi bấm "sort by distance", từ chối thì fallback im lặng |
| — | Photo Picker, document picker | **không cần permission** |

**Mọi permission đều phải có đường từ chối không crash, không dead-end.**

### 5.20 Screen-size adaptability + 4 UI state (§2.1)
**Dùng ở:** mọi screen. Đây là **rubric**, không phải polish cuối.
**Triển khai:**
- `ConstraintLayout` + `dimens.xml` thay vì số cứng → đổi kích thước là override `values-sw600dp`, không phải viết lại layout.
- Layout `sw600dp` thay thế **chỉ ở nơi thực sự có lợi** (Home 2 cột trên tablet), không làm đại trà.
- Mỗi screen fetch data phải có **cả bốn**: loading, empty (message thật), error (retry chạy được), offline (fallback Room + hint "showing cached data"). Dùng `StateRenderer` để không phải viết 4 block visibility bằng tay ở mỗi screen rồi lệch nhau.

---

## 6. Checklist triển khai theo phase

Nhãn: **[M]** must-have (không cắt) · **[S]** should-have · **[N]** nice-to-have (cắt tự do)

### Phase 1 — Setup
- [ ] **[M]** Firebase project: bật Auth (Email/Password), Firestore, Storage
- [ ] **[M]** `google-services.json` vào `app/`, bật plugin `google-services`
- [ ] **[M]** Build xanh với plugin đã bật
- [ ] **[M]** Sườn navigation chạy (Splash → Login hiện ra được)

### Phase 2 — Rules + tầng data
- [ ] **[M]** **Dán rules §4 và §4.1 vào Console TRƯỚC KHI viết screen**
- [ ] **[M]** Test 10 case Rules Playground (bảng ở §4) — đủ 10, không bỏ case nào
- [ ] **[M]** Seed data mẫu có sẵn `memberUids`, `endTime`, `courseCategory`, `materialUrls`
- [ ] **[M]** Room entity + DAO + Converters chạy được (test đọc/ghi)
- [ ] **[M]** `FirestoreRefs` + `FirestoreMappers` hoàn chỉnh
- [ ] **[M]** Repository nối Firestore → Room write-through

### Phase 3 — Auth + core loop
- [ ] **[M]** Splash rẽ đúng 3 hướng (§7.0)
- [ ] **[M]** Login, Signup, Forgot password
- [ ] **[M]** Email verification gửi được, `isEmailVerified` gate verified community
- [ ] **[M]** Sign out clear Room (login account thứ 2, xác nhận không lẫn data)
- [ ] **[M]** **Community Selection với REST call** (xem network call, không chỉ xem list)
- [ ] **[M]** Search as you type + filter by city
- [ ] **[M]** Join free-for-all và verified community (cả case domain sai)
- [ ] **[M]** Home list + sort theo time
- [ ] **[M]** Session Detail render header/body/member
- [ ] **[M]** Create Session tạo được, host thành `admin`, X/Y = 1/Y

### Phase 4 — Membership loop
- [ ] **[M]** Join session `open` — **test bằng account thứ 2**, X/Y tăng cho mọi người
- [ ] **[M]** Request to join session `gated`
- [ ] **[M]** Host approve → member vào list, X/Y tăng
- [ ] **[M]** Host reject → request mất, X/Y không đổi
- [ ] **[M]** Leave session, X/Y giảm, reminder bị cancel
- [ ] **[M]** Host remove member, X/Y giảm, người bị remove nhận inbox item
- [ ] **[M]** Edit time → mọi accepted member nhận thông báo
- [ ] **[M]** Edit location → mọi accepted member nhận thông báo
- [ ] **[M]** Cancel session → fan-out, doc **không bị xoá**
- [ ] **[M]** Join khi đã full → chặn, nút hiện "Session full"
- [ ] **[M]** My Sessions (list) + History bằng `whereArrayContains`
- [ ] **[S]** Invite by student ID → có **cả** member doc `invited` **và** inbox item
- [ ] **[S]** Attach study material
- [ ] **[S]** Continue from last (điền sẵn + mời lại member cũ)

### Phase 5 — Profile + Inbox
- [ ] **[M]** Profile hiện đủ: community, department, major, khóa tuyển, name, studentID, bio
- [ ] **[M]** Self view sửa được, persist qua restart
- [ ] **[M]** Read-only view từ member list — **ẩn hết control edit**
- [ ] **[M]** Inbox list, invite row có **Accept + Details**
- [ ] **[M]** Accept join ngay tại chỗ, không rời screen
- [ ] **[M]** Thông báo edit/cancel/remove từ Phase 4 về đúng đây
- [ ] **[S]** Upload ảnh profile từ **gallery**
- [ ] **[S]** Upload ảnh profile từ **camera**
- [ ] **[S]** Đổi community từ Profile
- [ ] **[N]** Activity graph
- [ ] **[N]** Block user, hiệu ứng thấy được trên Home

### Phase 6a — Robustness (must-have, đây là rubric)
- [ ] **[M]** Loading state trên **mọi** screen fetch data
- [ ] **[M]** Empty state có message thật
- [ ] **[M]** Error state có retry chạy được
- [ ] **[M]** Offline state fallback Room + hint "showing cached data"
- [ ] **[M]** Mọi permission có đường từ chối không crash

### Phase 6b — Adaptability
- [ ] **[S]** Portrait + landscape mọi screen
- [ ] **[S]** Emulator small phone + tablet-width

### Phase 6c — Extras
- [ ] **[S]** My Sessions calendar view toggle
- [ ] **[S]** Proximity sort + nhãn khoảng cách
- [ ] **[S]** Local reminder notification
- [ ] **[N]** Auto-hide/grey session trùng giờ
- [ ] **[N]** Export CSV/PDF (**không có trong spec — cắt đầu tiên**)

### Phase 7 — Buffer
- [ ] **[M]** Chạy hết §13 của dev plan trên máy thật, **account thứ 2**, cài mới
- [ ] **[M]** Fix bug
- [ ] **[N]** Chọn 1 món từ §11 nếu còn thời gian thật

---

## 7. Ba yêu cầu môn học — phải sống sót qua mọi lần cắt scope

| Yêu cầu | Thoả bằng | Ở phase |
|---|---|---|
| **Persistent local data** | Room cache, demo được bằng cách tắt mạng mà screen vẫn có data | 2 + 6a |
| **External data source / API** | Retrofit gọi Firestore REST ở Community Selection (§7.1) | 3 |
| **Device capability** | Camera cho ảnh profile (§7.7) **và/hoặc** location cho proximity sort (§7.2) — giữ **ít nhất một**, camera rẻ hơn | 5 hoặc 6c |

---

## 8. Cạm bẫy đã biết

Ba lỗi đầu **chỉ lộ ra ở account thứ hai**, không phải account host. Test một account sẽ thấy mọi thứ ổn.

| Triệu chứng | Nguyên nhân thật | Xem |
|---|---|---|
| Join fail, `PERMISSION_DENIED` | Rules cho update session chỉ host — nhưng người join là người tăng `joinedCount` | §4 FLOW 2 |
| Invite / approve fail, `PERMISSION_DENIED` | Ghi vào inbox **người khác**; rules cần exception `create`-only | §4 FLOW 1 |
| My Sessions / History / activity graph rỗng | Query ngược từ subcollection — không tồn tại. Phải dùng `memberUids` | §3.1 |
| Đếm X/Y sai | Có code path chỉ ghi `members` mà không ghi counter (hoặc ngược lại) | §3.1 |
| Chip "course type" không lọc được | Thiếu field `courseCategory`; `courseId` không suy ra được category | §3.1 |
| Overlap check không chạy | Thiếu `endTime` — không có interval thì không so được | §3.1 |
| Upload ảnh/tài liệu fail | Quên Storage rules (khác hoàn toàn Firestore rules) | §4.1 |
| Ai cũng thấy được mình block ai | `blockedUserIds` để làm field trên user doc (world-readable). Phải là subcollection | §3.1 |
| Screen hiện data cũ | Write-through của repository, **không bao giờ** là UI | §2.2 |
| `Default FirebaseApp is not initialized` | Thiếu `google-services.json` hoặc chưa bật plugin | mục 1 |
| Moshi throw "reflective adapter missing" | Có `converter-moshi` nhưng thiếu `moshi-kotlin-codegen` | §5.10 |
| `bash: adb: command not found` | `bash` trong PowerShell là **WSL**, không phải Git Bash | mục 1 |

---

## 9. Quy ước code

- **Enum lưu bằng string `wire`, không bao giờ bằng ordinal** — ordinal vỡ ngay khi ai đó đổi thứ tự constant, và Firestore Console phải đọc được bằng mắt.
- **Timestamp trong model/entity là `Long` epoch-millis**, convert sang `Timestamp` của Firestore chỉ ở `FirestoreMappers`.
- **Không có domain layer riêng.** Một data class mỗi entity, dùng trực tiếp bởi UI; mapping chỉ nằm trong `FirestoreMappers`.
- **Tên field Firestore chỉ viết một lần**, trong `FirestoreRefs.Field`.
- **Mỗi hàm `TODO()` có mã mục §x.y** trong message hoặc KDoc — biết ngay phải đọc đâu trong dev plan.
