# Study Group Finder — Development Plan

**Audience:** an AI coding agent building this app from scratch.
**Scope:** architecture, data model, file structure, screen implementation (including all easy/medium features folded in as core), cross-cutting UI requirements (screen-size adaptability, loading/empty/error/offline states), and feature scoping. Report writing, video, and submission packaging are explicitly out of scope for this document.
**Team context:** the team has prior experience with Room and SharedPreferences (from a previous Android project) and no prior backend experience. Every choice below is made to minimize new concepts introduced, while still meeting course requirements (persistent local data, external data source/API, device capability integration).

---

## 1. Architecture decision summary

| Concern | Decision | Why |
|---|---|---|
| Language | Kotlin | Required-adjacent by course, more concise than Java, coroutines make async Firestore/location calls readable |
| UI toolkit | Traditional XML views + ViewBinding | Already decided; ViewBinding is simpler than DataBinding and avoids `findViewById` boilerplate |
| Architecture pattern | MVVM | Already decided; ViewModel + LiveData, Repository layer in between |
| Navigation | Jetpack Navigation Component, single Activity | Standard, handles back stack and fragment transactions for you; Safe Args gives type-safe argument passing (e.g. sessionId) between fragments |
| Cloud backend | **Firebase** (Auth + Firestore + Storage) accessed directly from the Android client via the official SDK | No server code to write or host. This is the single biggest concept-reduction: you never build or deploy a backend service |
| Local persistence | **Room** (already known) for an offline cache + **SharedPreferences** (already known) for small app-level flags | Reuses what the team already knows instead of introducing something new for this layer |
| REST API requirement | One deliberate Retrofit call to **Firestore's public REST endpoint** for the pre-login "browse communities" screen | Satisfies the course's REST API technique requirement with a small, well-contained, justified use — see §7.1 |
| Push notifications on edits | **Out of scope for this plan.** Core behavior uses local scheduled reminders (WorkManager) + Firestore realtime listeners while a screen is open | Server-triggered push (FCM + Cloud Functions) is a real backend-service decision, not a mechanical addition — see §11.3 |
| Location proximity sort | **Core feature** — one-time `FusedLocationProviderClient.getCurrentLocation()` fetch + client-side Haversine distance calculation | Well-documented permission flow that adds real value to the browse loop (a delivery-app-style "near me" sort), so it's built in rather than deferred — see §7.2 |

> **Verifying this plan against the original idea:** §13 is a line-by-line checklist traced back to the team's own feature list. Three things there are worth knowing up front — the plan **adds** two features nobody asked for (CSV/PDF export, leave-session), **splits** one field the spec implies but the schema was missing (`courseCategory`, for the "course type" chips), and **relocates** continue-from-last from the History row to past-session detail where the spec actually puts it. Everything else maps one-to-one.

**The one sentence version:** everything the app does day-to-day talks to Firestore through the official SDK (no server code), one specific screen calls Firestore's REST endpoint directly with Retrofit to satisfy the REST requirement, every easy/medium extra (block user, activity graph, proximity sort, invite by ID, overlap hiding, study materials, CSV/PDF export, continue-from-last) is built into the core screens below, and only the handful of things that genuinely require a new backend-hosting decision or a new library/architecture choice (Cloud Functions, QR scanning, true device calendar import, an AI chatbot) are out of scope — see §11.

---

## 2. Full tech stack

| Layer | Choice | Notes |
|---|---|---|
| Auth | Firebase Authentication — email/password provider | Simplest provider to implement; Google Sign-In can be added later as a one-line addition if time allows, not required |
| Database | Cloud Firestore | Document model maps naturally onto Community/Session/Membership entities; realtime listeners give you "live updates while a screen is open" for free |
| File storage (Avatar) | **Cloudinary** | Used specifically for profile pictures to ensure unique, overwritable storage via User UID as `public_id` |
| File storage (Materials) | Firebase Storage | Study material attachments (§7.5) |
| Local cache | Room | Caches last-fetched sessions/communities/profile so screens aren't blank when offline. Already known from the midterm project. **Read §2.2 before writing repository code** — Room and the Firestore SDK both cache, and the division of labour has to be deliberate |
| Local flags | SharedPreferences | Last selected community ID, sort/filter preference, "seen onboarding" flag — anything small that doesn't need query capability |
| Networking (REST) | Retrofit + Moshi (or Gson) | Used specifically for the Firestore REST endpoint call in §7.1. Not used elsewhere — everywhere else uses the Firebase SDK directly |
| Async | Kotlin Coroutines (`viewModelScope`, `lifecycleScope`) + `kotlinx-coroutines-play-services` for `.await()` on Firebase `Task<T>` objects | Avoids introducing RxJava; coroutines are the modern default and pair naturally with LiveData |
| Image loading | Glide | Loads profile pictures/avatars from Storage URLs with caching, placeholders, error states — a couple lines per usage |
| Local notifications | WorkManager (`OneTimeWorkRequest`) | Schedules the "session starting soon" reminder; survives process death, no server needed |
| Camera/photo | System camera Intent (`MediaStore.ACTION_IMAGE_CAPTURE`) + Photo Picker (`ActivityResultContracts.PickVisualMedia`) | Deliberately not CameraX — CameraX is for building a custom in-app camera UI, which you don't need. Launching the system camera app is much less code |
| Navigation | Jetpack Navigation Component + Safe Args | Single Activity, one NavHostFragment, all screens are fragments/destinations |
| Location | `FusedLocationProviderClient` (Google Play Services location) | One-time `getCurrentLocation()` fetch for the Home proximity sort (§7.2), not continuous tracking — simpler than a live subscription |
| File picker (study materials) | System document picker Intent (`ACTION_OPEN_DOCUMENT`) | Reuses the same Storage-upload pattern already needed for profile photos (§7.5) |
| File sharing (CSV/PDF export) | `FileProvider` + `Intent.ACTION_SEND` | One-time manifest/XML config step, then plain string/`PdfDocument` output shared straight from the app — no export library needed (§7.6) |

### What was deliberately left out (and why)
- **No custom backend server** (Node/Express, etc.) — would require choosing a hosting platform, writing API routes, and managing a database. Firestore replaces all of that.
- **No Cloud Functions in the core plan** — real backend-service decision-making, deferred (§11).
- **No Jetpack Compose** — already decided against.
- **No RxJava** — coroutines cover everything needed with less new syntax.
- **No CameraX, no ML Kit, no ZXing** — profile/material photos use the simplest possible approach (system camera Intent, Photo Picker, document picker); ZXing-based QR scanning is deferred as a hard additional feature (§11.2).
- **No dependency-injection framework (Hilt/Koin)** — for a project this size, manually constructing repositories in a small `ServiceLocator` object (see §5) is enough and avoids a whole new concept (DI) for a team already absorbing Firebase.
- **No `CalendarContract` device calendar access** — the "hide overlapping sessions" feature is core (§7.2) and uses in-app data only; true device calendar import is deferred (§11.1).
- **No Gemini/AI chatbot integration in core** — genuinely optional, deferred as lowest priority (§11.4).

### 2.1 Screen-size adaptability & state-handling conventions

These are cross-cutting requirements (per the Scope line in §0) that apply to every screen, not a one-time task — call them out explicitly so they aren't only remembered as a single Phase 6 line item:

- **Screen-size adaptability:** build layouts with `ConstraintLayout` and dimension resources (`dimens.xml`) rather than hardcoded values, so nothing needs a rewrite for different screen sizes; use `sw600dp` alternate layouts only where a screen genuinely benefits from a wider layout (e.g. Home list could show 2 columns on a tablet-width device), not as a blanket requirement. Verify each screen in both portrait and landscape, and on at least a small-phone and a tablet-width emulator profile, before considering it done.
- **Loading / empty / error / offline states:** every screen that fetches data (Firestore query, REST call, or Room read) needs all four states designed and implemented, not just the happy path — a loading spinner/skeleton while the fetch is in flight, an empty-state view with a short message when the query legitimately returns nothing, an error state with a retry action when a fetch fails, and an offline state that falls back to the Room cache (§2) with a subtle "showing cached data" indicator rather than a blank screen.
- Treat both of the above as a per-screen checklist item during implementation (§7) and QA (§10 Phase 6), not a separate pass done once at the end.

### 2.2 Room vs Firestore's own offline cache — who is source of truth

The Firestore SDK **already has offline persistence enabled by default**: it caches every document a query has seen and serves reads from that cache when the device is offline. So the Room layer in §2 is, strictly speaking, redundant with it. Keeping both is still the right call — the course requires demonstrable local persistence, and Room is what the team already knows — but the overlap has to be resolved explicitly or two developers will write two different caching strategies into the same repository.

**The rule for this project:**

- **Firestore is the only source of truth.** Every write goes to Firestore. Nothing is ever written to Room by the UI or a ViewModel.
- **Room is a read-only projection**, written exclusively by the repository layer, immediately after a successful Firestore fetch. Think of it as a snapshot for first paint, not a database the app reasons about.
- **Screens observe Room** (`Flow`-returning DAO queries), never the Firestore query directly — except where a screen needs live updates (§7.3 session detail), which uses `addSnapshotListener` and writes each snapshot through to Room on the way to the UI. This gives one consistent pattern: *Firestore → repository → Room → UI*.
- **Do not disable Firestore's own persistence.** It is what makes writes queue while offline and replay on reconnect; Room cannot do that. The offline story is: Firestore persistence handles *writes*, Room handles *what the screen renders*.

A one-line consequence worth internalising: if a screen looks stale, the bug is always in the repository's write-through, never in the UI.

---

## 3. Data model

### 3.1 Firestore collections (source of truth)

**`communities/{communityId}`**
| Field | Type | Notes |
|---|---|---|
| name | string | |
| city | string | for filtering during community selection |
| verified | boolean | true = requires matching email domain to join |
| domainWhitelist | array\<string\> | e.g. `["university.edu.vn"]`, only checked if `verified == true` |
| createdAt | timestamp | |

*Publicly readable (see §4) — this is the collection used by the REST demo screen.*

**`users/{uid}`** (document ID = Firebase Auth UID)
| Field | Type | Notes |
|---|---|---|
| name | string | |
| studentId | string | |
| communityId | string | ref to `communities/{id}` |
| department | string | |
| major | string | |
| admissionYear | string | admission year |
| bio | string | |
| photoUrl | string | Storage download URL |
| createdAt | timestamp | |

*Note: `blockedUserIds` is deliberately **not** a field on this document. Any signed-in user can read any user doc (that's what makes member lists and read-only profile views work), so a block list stored here would be publicly readable — everyone could see who blocked whom. It lives in a private subcollection instead:*

**`users/{uid}/blocked/{blockedUid}`** (subcollection — readable only by the owner)
| Field | Type | Notes |
|---|---|---|
| createdAt | timestamp | document ID is the blocked user's uid; no other fields needed |

*Loaded once at app start into an in-memory `Set<String>` on the repository, since it's small and read on every list render — see §7.7.*

**`sessions/{sessionId}`**
| Field | Type | Notes |
|---|---|---|
| communityId | string | |
| hostUid | string | |
| courseId | string | pick from a per-community predefined list, not freeform (keeps filtering usable) |
| courseName | string | |
| courseCategory | string enum | the **subject area** — `physics` \| `calculus` \| `dsa` \| … — seeded per community alongside the course list. Distinct from `courseId`: the spec asks for filter chips on *course type*, which is a category spanning many course IDs, so it cannot be derived from `courseId` at query time. Set automatically from the picked course, never typed by the user |
| tagType | string enum | `normal` \| `midterm` \| `final` |
| expectationLevel | string enum | `pass` \| `overachieving` — **must be an enum, not freeform text**, since Home sorts/filters by it |
| title | string | |
| description | string | |
| goals | string | |
| locationName | string | pick from a predefined campus location list rather than freeform address (simpler than geocoding) |
| lat, lng | number | drives the proximity sort on Home — see §7.2 |
| startTime | timestamp | |
| endTime | timestamp | **required** — the overlap check in §7.2 cannot work without it. Collect it on the Create Session form as a duration picker (30m/1h/1.5h/2h/custom) and store the computed `endTime`, so the form stays simple but queries get a real interval |
| capacity | number | |
| joinedCount | number | maintained via transaction, see below — avoids an expensive count query. Always equals `memberUids.size()` |
| memberUids | array\<string\> | **uids with `status == accepted` or `admin`.** This is the denormalised field that makes "which sessions did I join?" a flat query — see the note below. Written in the same transaction as `joinedCount` |
| mode | string enum | `open` \| `gated` |
| status | string enum | `upcoming` \| `cancelled` — **no `completed` value.** Nothing in a serverless app can flip a doc to "completed" when its end time passes (there is no cron or Cloud Function in the core plan), so "past vs upcoming" is derived client-side by comparing `endTime` to now. A stored `completed` state would silently be wrong for every session |
| materialUrls | array\<string\> | Storage download URLs for attached study materials — see §7.5 |
| createdAt / updatedAt | timestamp | |

> **Why `memberUids` exists — read this before touching §7.6 or §7.7.**
> Membership lives in the `members` subcollection below, but Firestore **cannot query upwards**: there is no way to ask "give me every session whose `members` subcollection contains my uid" with an ordinary query. The two options are a `collectionGroup("members")` query — which needs its own composite index *and* a separate `match /{path=**}/members/{uid}` security-rules block that the rules in §4 would not otherwise cover — or denormalising the accepted-member uids onto the parent document.
> This plan takes the second option. `whereArrayContains("memberUids", uid)` is one flat query, one single-field index, and no extra rules surface. It is what powers My Sessions, History (§7.6), the activity graph (§7.7), and the overlap check (§7.2) — four features that would otherwise have no working query at all.
> The cost is that `memberUids` must be kept in lockstep with the subcollection. Every write that changes membership does both in one transaction; there is no code path anywhere that touches only one of them. Firestore's `array-contains` cap is one such clause per query, which is fine here, and the 1 MiB document limit means a session would need tens of thousands of members before size became a concern.

**`sessions/{sessionId}/members/{uid}`** (subcollection)
| Field | Type | Notes |
|---|---|---|
| status | string enum | `invited` \| `pending` \| `accepted` \| `admin` |
| joinedAt | timestamp | |

*`invited` is what makes the spec's **"Accept Invite"** button on Session Detail (§7.3) possible. Without it, the only record of an invitation is an inbox item, and Session Detail would have to scan the whole inbox looking for one matching this session just to decide which button to draw. With it, the screen reads a single document — `members/{myUid}` — and the status alone tells it which of Join / Request to join / Accept Invite / Leave to show. `invited` deliberately does **not** count toward `joinedCount` or `memberUids`; an invitation is not a membership until accepted.*

*Every membership change is a single Firestore transaction that touches **both** this subcollection document **and** the parent session's `joinedCount` + `memberUids`. Never one without the other — that invariant is what keeps the X/Y capacity count correct without a server, and the security rules in §4 are written to enforce it.*

**The six membership transactions, precisely:**

| Action | Who runs it | Subcollection write | Parent session write |
|---|---|---|---|
| Join an `open` session | the joiner | create `members/{uid}` with `status = accepted` | `joinedCount += 1`, `memberUids` += own uid |
| Request to join a `gated` session | the requester | create `members/{uid}` with `status = pending` | **none** — a pending request is not a member yet |
| Host invites someone by student ID (§7.5) | the host | create `members/{uid}` with `status = invited` | **none** — an invitation is not a membership yet |
| Invitee accepts (§7.3, §7.8) | the invitee | update own `status` `invited` → `accepted` | `joinedCount += 1`, `memberUids` += own uid |
| Host approves a pending request | the host | update that member's `status` → `accepted` | `joinedCount += 1`, `memberUids` += that uid |
| Leave / host removes a member | the member or the host | delete `members/{uid}` | `joinedCount -= 1`, `memberUids` -= that uid |

*Cap overflow rule: if `joinedCount >= capacity`, the transaction rejects with a "session full" result rather than silently switching to gated. Note this is enforced **twice** — once client-side for a good error message, and once in the security rules (§4), so a modified client cannot overfill a session.*

*The gated row is the reason a pending request must not touch `memberUids`: `memberUids` means "accepted members", and everything downstream (My Sessions, capacity, overlap detection) depends on that meaning holding.*

**`users/{uid}/inbox/{itemId}`** (subcollection — merges "invites" and "notifications" into one screen per your earlier decision)
| Field | Type | Notes |
|---|---|---|
| type | string enum | `invite` \| `join_request` \| `system` |
| sessionId | string | nullable for pure system messages |
| fromUid | string | nullable |
| message | string | |
| read | boolean | |
| createdAt | timestamp | |

### 3.2 Room entities (local cache mirror)
Mirror only what's needed offline: `SessionEntity`, `CommunityEntity`, `MySessionEntity` (a lightweight join-style cache of sessions the current user has joined), and a single-row `ProfileEntity` for the current user. Each has a matching DAO with `@Insert(onConflict = REPLACE)` upsert methods and `Flow`-returning queries so the UI updates automatically when the repository refreshes the cache.

**Mapping approach:** don't build a separate "domain model" layer. Keep one plain Kotlin data class per entity (e.g. `Session`) used directly by the UI, plus a small `toEntity()` / `toRoomModel()` extension function where a Room-specific shape is needed. This is a deliberate simplification — a full domain/data/UI three-layer split adds more indirection than a 2-4 person, 2.5-week project needs.

---

## 4. Firestore security rules

Set these directly in the Firebase Console's Rules tab — **no Firebase CLI needed**, no new tool to install.

These rules are not a sketch: two of the app's core flows are *impossible* under naive rules, and getting them wrong produces `PERMISSION_DENIED` at runtime rather than a compile error, which is a miserable thing to debug two days before a deadline. The two flows are called out inline below.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn()   { return request.auth != null; }
    function isSelf(uid)  { return signedIn() && request.auth.uid == uid; }

    // Used only from the `members` subcollection, where the parent session is
    // a different document and must actually be fetched. Inside the session
    // document's own rules use `resource.data.hostUid` directly — a get() on
    // the very document being written is a wasted billed read, and rules are
    // capped at 10 get()/exists() calls per single-document request.
    function isHostOf(sessionId) {
      return signedIn() && request.auth.uid ==
        get(/databases/$(database)/documents/sessions/$(sessionId)).data.hostUid;
    }

    function myMemberDoc(sessionId) {
      return /databases/$(database)/documents/sessions/$(sessionId)/members/$(request.auth.uid);
    }
    function wasInvitedTo(sessionId) {
      return exists(myMemberDoc(sessionId))
        && get(myMemberDoc(sessionId)).data.status == 'invited';
    }

    function changedKeys() {
      return request.resource.data.diff(resource.data).affectedKeys();
    }

    // A non-host may only ever move the membership counters, and only for
    // themselves — never anyone else's, and never past capacity. This is the
    // parent-doc half of the join/leave transaction in §3.1.
    function joinsSelf() {
      return changedKeys().hasOnly(['joinedCount', 'memberUids', 'updatedAt'])
        && !(request.auth.uid in resource.data.memberUids)
        && request.resource.data.memberUids.hasAll(resource.data.memberUids)
        && request.resource.data.memberUids.size() == resource.data.memberUids.size() + 1
        && request.auth.uid in request.resource.data.memberUids
        && request.resource.data.joinedCount == resource.data.joinedCount + 1
        && request.resource.data.joinedCount <= resource.data.capacity
        && request.resource.data.status == 'upcoming'
        // Open sessions anyone may join; gated ones only with an invitation
        // the host actually issued (§3.1). This is the rules-level half of
        // the gate — the UI half is the button state machine in §7.3.
        && (resource.data.mode == 'open' || wasInvitedTo(sessionId));
    }

    function leavesSelf() {
      return changedKeys().hasOnly(['joinedCount', 'memberUids', 'updatedAt'])
        && request.auth.uid in resource.data.memberUids
        && resource.data.memberUids.hasAll(request.resource.data.memberUids)
        && request.resource.data.memberUids.size() == resource.data.memberUids.size() - 1
        && !(request.auth.uid in request.resource.data.memberUids)
        && request.resource.data.joinedCount == resource.data.joinedCount - 1;
    }

    // ---- communities: public read powers the pre-login REST screen (§7.1) ----
    match /communities/{communityId} {
      allow read:  if true;
      allow write: if false;  // seeded manually via the console
    }

    // ---- users ----
    match /users/{uid} {
      // Any signed-in user can read any profile — member lists and the
      // read-only profile view (§7.7) both need this.
      allow read:           if signedIn();
      allow create, update: if isSelf(uid);
      allow delete:         if false;

      // Private to the owner. This is why the block list is a subcollection
      // and not a field on the user doc (§3.1) — the doc above is world-
      // readable to signed-in users, so a field here would leak who blocked whom.
      match /blocked/{blockedUid} {
        allow read, write: if isSelf(uid);
      }

      // ⚠️ FLOW 1 THAT NAIVE RULES BREAK — inbox fan-out.
      // Invite-by-student-ID, approve-request, edit-session and cancel-session
      // (§7.5) all write into *someone else's* inbox. A plain
      // `allow write: if isSelf(uid)` makes every one of those fail, which
      // kills the entire Inbox screen (§7.8).
      // So: the owner reads and mutates their own inbox, but ANY signed-in
      // user may CREATE an item in another user's inbox. Create is the only
      // cross-user permission in these rules, and the payload is constrained
      // so it cannot be used to forge a sender or spam large documents.
      match /inbox/{itemId} {
        allow read, update, delete: if isSelf(uid);
        allow create: if signedIn()
          && request.resource.data.fromUid == request.auth.uid
          && request.resource.data.type in ['invite', 'join_request', 'system']
          && request.resource.data.read == false
          && request.resource.data.message is string
          && request.resource.data.message.size() <= 500;
      }
    }

    // ---- sessions ----
    match /sessions/{sessionId} {
      allow read: if signedIn();

      // A host cannot create a session owned by someone else, and must start
      // as their own first member so joinedCount/memberUids are consistent
      // from the very first write (§7.4).
      allow create: if signedIn()
        && request.resource.data.hostUid == request.auth.uid
        && request.resource.data.joinedCount == 1
        && request.resource.data.memberUids == [request.auth.uid]
        && request.resource.data.status == 'upcoming';

      // ⚠️ FLOW 2 THAT NAIVE RULES BREAK — joining.
      // `allow update: if request.auth.uid == resource.data.hostUid` looks
      // right, but the join transaction (§3.1) increments joinedCount on THIS
      // document while being run by the joiner, not the host. Host-only update
      // means the whole transaction rolls back and nobody but the host can
      // ever join a session.
      // Note this reads hostUid off the document directly rather than calling
      // isHostOf() — we are already inside that document.
      allow update: if (signedIn() && request.auth.uid == resource.data.hostUid)
        || joinsSelf()
        || leavesSelf();

      allow delete: if signedIn() && request.auth.uid == resource.data.hostUid;

      match /members/{uid} {
        allow read: if signedIn();

        // Self-created membership is capped at 'pending' (gated) or
        // 'accepted' (open). Without this split a user could simply write
        // themselves in as 'admin' or self-approve past the gated flow.
        // Only the host can mint an 'invited' row (§7.5 invite by student ID).
        allow create: if isHostOf(sessionId)
          || (isSelf(uid)
              && request.resource.data.status in ['pending', 'accepted']);

        // The host may promote anyone; a user may promote ONLY themselves and
        // ONLY along the one legal edge invited -> accepted. Everything else
        // (pending -> accepted, anything -> admin) stays host-only.
        allow update: if isHostOf(sessionId)
          || (isSelf(uid)
              && resource.data.status == 'invited'
              && request.resource.data.status == 'accepted');

        allow delete: if isSelf(uid) || isHostOf(sessionId);
      }
    }
  }
}
```

**Two notes to save you an afternoon:**

- **Collection-group queries are not covered by these rules.** `match /sessions/{sessionId}/members/{uid}` does *not* grant access to a `collectionGroup("members")` query — that needs its own `match /{path=**}/members/{uid}` block plus a collection-group index. The core plan is designed to never need one (that is exactly what `memberUids` in §3.1 buys you), so if you find yourself reaching for `collectionGroup`, re-read §3.1 first.
- **Test the rules before building screens on them.** The Firebase Console's Rules Playground lets you simulate an authenticated write against a real document path in about thirty seconds. Simulate at minimum:

  | # | Simulated write | Expected |
  |---|---|---|
  | 1 | non-host increments `joinedCount` + adds self to `memberUids` on an open session | ✅ allow |
  | 2 | same write when `joinedCount` already equals `capacity` | ❌ deny |
  | 3 | non-host does the same on a **gated** session with no invitation | ❌ deny |
  | 4 | invited user updates own `members/{uid}` `invited` → `accepted` | ✅ allow |
  | 5 | user updates own `members/{uid}` `pending` → `accepted` | ❌ deny |
  | 6 | user creates own `members/{uid}` with `status: "admin"` | ❌ deny |
  | 7 | user A creates a doc in user B's `inbox` with `fromUid = A` | ✅ allow |
  | 8 | user A creates a doc in user B's `inbox` with `fromUid = C` | ❌ deny |
  | 9 | user A reads user B's `blocked` subcollection | ❌ deny |
  | 10 | non-host edits a session's `title` | ❌ deny |

  Rows 1, 4 and 7 are the three flows that naive rules break; rows 2, 5, 6, 8 and 9 are the privilege-escalation and privacy holes. Ten minutes here saves a day in Phase 4.

### 4.1 Firebase Storage rules

Storage has a **completely separate rules engine** from Firestore and defaults to denying everything once the free trial window closes. Both the profile photo upload (§7.7) and the study-material upload (§7.5) fail without this, and it is easy to forget because §4 above looks like it covers "the rules".

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {

    match /users/{uid}/profile/{fileName} {
      allow read:  if request.auth != null;
      allow write: if request.auth != null
        && request.auth.uid == uid
        && request.resource.size < 5 * 1024 * 1024
        && request.resource.contentType.matches('image/.*');
    }

    match /sessions/{sessionId}/materials/{fileName} {
      allow read:  if request.auth != null;
      allow write: if request.auth != null
        && request.resource.size < 10 * 1024 * 1024;
    }
  }
}
```

*Known limitation, worth stating in the report rather than hiding: Storage rules cannot read Firestore documents, so "only the host may attach materials" is **not** expressible here — it is enforced in the UI only, and these rules fall back to size and type caps. Locking it down properly would require a Cloud Function, which is out of scope (§11.3).*

---

## 5. Android project structure

```
app/
└── src/main/
    ├── java/com/studyfinder/app/
    │   ├── StudyFinderApp.kt                 # Application class
    │   ├── ServiceLocator.kt                 # manual DI: builds repositories once, no framework
    │   │
    │   ├── data/
    │   │   ├── local/
    │   │   │   ├── AppDatabase.kt
    │   │   │   ├── dao/
    │   │   │   │   ├── SessionDao.kt
    │   │   │   │   ├── CommunityDao.kt
    │   │   │   │   ├── MySessionDao.kt
    │   │   │   │   └── ProfileDao.kt
    │   │   │   ├── converter/
    │   │   │   │   └── Converters.kt      # @TypeConverter: Timestamp <-> Long,
    │   │   │   │                          # List<String> <-> comma-joined String
    │   │   │   │                          # (Room stores neither natively)
    │   │   │   └── entity/
    │   │   │       ├── SessionEntity.kt
    │   │   │       ├── CommunityEntity.kt
    │   │   │       ├── MySessionEntity.kt
    │   │   │       └── ProfileEntity.kt
    │   │   ├── remote/
    │   │   │   ├── firestore/
    │   │   │   │   ├── FirestoreRefs.kt      # central place for collection() references
    │   │   │   │   └── FirestoreMappers.kt   # doc -> model conversion helpers
    │   │   │   └── rest/
    │   │   │       ├── PublicCommunityApi.kt # Retrofit interface, see §7.1
    │   │   │       └── RetrofitClient.kt
    │   │   └── repository/
    │   │       ├── AuthRepository.kt
    │   │       ├── CommunityRepository.kt
    │   │       ├── SessionRepository.kt
    │   │       ├── ProfileRepository.kt
    │   │       └── InboxRepository.kt
    │   │
    │   ├── model/                            # shared plain Kotlin data classes
    │   │   ├── Session.kt
    │   │   ├── Community.kt
    │   │   ├── UserProfile.kt
    │   │   └── InboxItem.kt
    │   │
    │   ├── ui/
    │   │   ├── auth/          (LoginFragment, SignupFragment, AuthViewModel)
    │   │   ├── community/     (CommunitySelectionFragment, CommunityViewModel)
    │   │   ├── home/          (HomeFragment, HomeViewModel, SessionListAdapter)
    │   │   ├── sessiondetail/ (SessionDetailFragment, SessionDetailViewModel)
    │   │   ├── sessioncreate/ (CreateSessionFragment, CreateSessionViewModel)
    │   │   ├── sessionmanage/ (SessionManageFragment, SessionManageViewModel — approve/edit/cancel, invite by ID, material upload)
    │   │   ├── mysessions/    (MySessionsFragment, MySessionsViewModel)
    │   │   ├── history/       (HistoryFragment, HistoryViewModel — includes CSV/PDF export)
    │   │   ├── inbox/         (InboxFragment, InboxViewModel)
    │   │   ├── profile/       (ProfileFragment, ProfileViewModel, ActivityGraphView.kt — custom View, GitHub-style heatmap grid, §7.7)
    │   │   └── common/        (shared adapters, loading/empty/error state views)
    │   │
    │   ├── notification/
    │   │   ├── ReminderWorker.kt              # WorkManager job, fires local notification
    │   │   └── NotificationHelper.kt          # channel setup, builder helpers
    │   │
    │   ├── util/
    │   │   ├── Result.kt                      # sealed class: Loading / Success / Error
    │   │   ├── DateTimeUtils.kt
    │   │   ├── LocationUtils.kt               # Haversine distance calc, §7.2
    │   │   ├── HistoryExporter.kt             # CSV string builder + PdfDocument writer, §7.6
    │   │   └── Extensions.kt
    │   │
    │   └── MainActivity.kt                    # hosts NavHostFragment
    │
    ├── res/
    │   ├── layout/           (fragment_*.xml, item_*.xml, activity_main.xml)
    │   ├── navigation/nav_graph.xml
    │   ├── values/(strings.xml, colors.xml, themes.xml, dimens.xml)
    │   ├── xml/file_paths.xml                 # FileProvider path config, needed for §7.6 export/share
    │   └── drawable/
    │
    ├── google-services.json                   # Firebase config, from console
    └── AndroidManifest.xml
```

No root-level `firebase.json` / `firestore.rules` files needed since rules are managed through the console rather than the CLI.

---

## 6. Gradle dependencies (illustrative — pull latest stable of each)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Required — without it google-services.json is never read and every
    // Firebase call fails at runtime with "Default FirebaseApp is not initialized".
    id("com.google.gms.google-services")
    // Type-safe fragment arguments (sessionId etc.)
    id("androidx.navigation.safeargs.kotlin")
    // Annotation processing for Room and Moshi. KSP, not kapt — it is the
    // supported processor for both now, and roughly twice as fast to build.
    id("com.google.devtools.ksp")
}

android {
    buildFeatures {
        viewBinding = true   // this project is XML views, not Compose (§2)
    }
}

dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:latest"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // Coroutines + Task interop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:latest")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:latest")

    // Room (already familiar)
    implementation("androidx.room:room-runtime:latest")
    implementation("androidx.room:room-ktx:latest")
    ksp("androidx.room:room-compiler:latest")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:latest")
    implementation("androidx.navigation:navigation-ui-ktx:latest")

    // Material Components — the XML theme parent (Theme.Material3.*) and the
    // widgets the screens assume: ChipGroup for Home filters (§7.2),
    // BottomNavigationView, TextInputLayout, MaterialDatePicker/TimePicker (§7.4)
    implementation("com.google.android.material:material:latest")

    // AppCompat + Fragment + ConstraintLayout — the XML/ViewBinding baseline
    implementation("androidx.appcompat:appcompat:latest")
    implementation("androidx.fragment:fragment-ktx:latest")
    implementation("androidx.constraintlayout:constraintlayout:latest")
    implementation("androidx.recyclerview:recyclerview:latest")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:latest")

    // Retrofit — only used for the REST demo call (§7.1)
    implementation("com.squareup.retrofit2:retrofit:latest")
    implementation("com.squareup.retrofit2:converter-moshi:latest")
    // Moshi itself + its codegen. converter-moshi alone does NOT pull in a
    // Kotlin adapter, so without one of these every parse throws at runtime
    // with "Cannot serialize Kotlin type ... reflective adapter missing".
    implementation("com.squareup.moshi:moshi:latest")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:latest")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:latest")

    // Location (proximity sort, §7.2) — one-time fetch only, not continuous tracking
    implementation("com.google.android.gms:play-services-location:latest")

    // Glide
    implementation("com.github.bumptech.glide:glide:latest")

    // Lifecycle / ViewModel / LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:latest")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:latest")
}
```

Also add the `com.google.gms:google-services` and `androidx.navigation:navigation-safe-args-gradle-plugin` classpath entries (or `plugins { ... apply false }` blocks) to the **root** `build.gradle.kts`, and drop `google-services.json` into `app/` before the first build.

---

## 7. Screen-by-screen implementation notes (core screens only)

### 7.0 Auth & app entry routing

Easy to skip because it isn't a "feature", but every screen below assumes a signed-in user with a community already chosen, so the routing that guarantees that has to exist first.

- **Start destination is decided at runtime, not in `nav_graph.xml`.** On launch, `MainActivity` checks `FirebaseAuth.getInstance().currentUser` — Firebase persists the session across restarts, so a returning user must not see Login again. Use a `NavController.graph.setStartDestination(...)` call (or a small `SplashFragment` that immediately navigates and pops itself) to route three ways:
  - no `currentUser` → **Login**
  - signed in but `users/{uid}` has no `communityId` → **Community Selection** (§7.1)
  - signed in with a community → **Home** (§7.2)
- **Signup order:** create the Auth account → write the `users/{uid}` document → send the verification email → route to Community Selection. Writing the user doc *before* navigating matters, because every downstream screen reads it.
- **Email verification:** call `sendEmailVerification()` on signup and check `user.isEmailVerified` before allowing a **verified** community to be joined (§7.1). Unverified users can still browse and use open communities — gating everything behind a verified email makes the app impossible to demo. Add a "resend verification email" action on the profile screen.
- **Forgot password:** `sendPasswordResetEmail()` from a link on the Login screen. Two lines of SDK, and its absence is the single most common thing a grader tries.
- **Sign out:** clear Firebase Auth, wipe the Room cache (it holds another user's data), clear SharedPreferences flags, then navigate to Login popping the entire back stack.
- Auth errors need real messages, not "something went wrong": wrong password, email already in use, malformed email, and network failure are four distinct `FirebaseAuthException` codes and users hit all four.

### 7.1 Community selection (first login / edit from profile) — includes the REST demo
- Two data sources on this screen:
  - **Search/filter as you type** → Firestore SDK query (`whereEqualTo("city", ...)`), since it's fast, real-time, and simple.
  - **Initial "browse all communities" list on first load** → this is the one deliberate call through `PublicCommunityApi` (Retrofit) hitting `GET https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents/communities`. This is unauthenticated, read-only, public data — a safe and honest place to demonstrate REST/Retrofit/JSON parsing for the course requirement without adding risk elsewhere.

**Budget real time for the REST parsing — it is not a flat JSON body.** Firestore's REST API returns its *typed wire format*, where every field is wrapped in an object naming its type. A `communities` document comes back looking like this:

```json
{
  "documents": [
    {
      "name": "projects/xxx/databases/(default)/documents/communities/abc123",
      "fields": {
        "name":     { "stringValue": "FPT University HCM" },
        "city":     { "stringValue": "Ho Chi Minh" },
        "verified": { "booleanValue": true },
        "domainWhitelist": {
          "arrayValue": { "values": [ { "stringValue": "fpt.edu.vn" } ] }
        },
        "createdAt": { "timestampValue": "2026-08-01T00:00:00Z" }
      },
      "createTime": "...", "updateTime": "..."
    }
  ],
  "nextPageToken": "..."
}
```

So you need a small set of DTOs — `CommunityListResponse(documents)`, `FirestoreDocument(name, fields)`, `CommunityFields(name, city, verified, domainWhitelist)`, and typed wrappers `StringValue(stringValue)`, `BoolValue(booleanValue)`, `ArrayValue(arrayValue)` — plus a mapper down to the plain `Community` model in `model/`. Two further details that bite: the document ID is **not** a field, it is the last path segment of `name` (`name.substringAfterLast('/')`), and an absent field is simply **missing from the `fields` map** rather than null, so every DTO property must be nullable with a sensible default. Keep all of this confined to `data/remote/rest/` and map to `Community` at the repository boundary, so the rest of the app never sees the wire format.

- Join flow: if `community.verified`, check the signed-in user's email domain against `domainWhitelist` client-side before writing `communityId` onto the user doc; if not verified, block with an inline error. **This check is only meaningful if the email is verified** — otherwise anyone can sign up as `whatever@university.edu.vn` without owning that address and walk straight into a verified community. See §7.0 for the email-verification gate.

### 7.2 Home / Upcoming sessions
- Firestore query scoped to `communityId`, ordered by `startTime`.
- Sort by time / expectation level → additional `orderBy()` / client-side sort toggle.
- **Sort by distance:** on screen load (or a manual "sort by distance" toggle), call `FusedLocationProviderClient.getCurrentLocation()` once (not a continuous subscription), then compute distance to each session's `lat`/`lng` client-side with a Haversine formula (`LocationUtils.kt`, §5) and sort/annotate the list — a delivery-app-style "near me" sort. Gate this behind the `ACCESS_FINE_LOCATION` runtime permission flow (§9); if denied, fall back to time sort with no error state.
- **Auto-hide sessions that overlap with availability:** the spec's "availability" is fed from two sources — the sessions you have already joined, and (deferred, §11.1) your imported device calendar. Model it as a single `List<BusyInterval>` produced by a `getBusyIntervals()` function on the repository, so the deferred half can later be appended to that list without touching the Home screen at all. In the core plan that function returns exactly one thing: your joined sessions. Compare each candidate session's `[startTime, endTime)` interval against the intervals of sessions the current user has already joined (from the same in-app "My sessions" data used in §7.6, not the device calendar) and grey out or filter candidates that overlap — delivers the "don't show me things I can't attend" value entirely from data you already have in Firestore. The standard interval test is `a.startTime < b.endTime && b.startTime < a.endTime`; this is the reason `endTime` is a required field in §3.1 and not something to leave for later. Make it a **toggle, defaulting to "grey out"** rather than hard-filtering, so a user is never confused by a session that silently isn't in the list.
- Search by course ID → `whereEqualTo("courseId", ...)`.
- **Filter chips, two independent `ChipGroup`s** (this is what the spec asks for, and they are *not* the same field):
  - *Session type* → `whereEqualTo("tagType", ...)` — `normal` / `midterm` / `final`
  - *Course type* → `whereEqualTo("courseCategory", ...)` — physics / calculus / DSA / …, which is why `courseCategory` is its own field in §3.1 rather than something inferred from `courseId`
  Firestore requires composite indexes for combined filters — the console will show a direct link to auto-create the needed index the first time you run a combined query, just click it. Budget for this: `communityId` + `tagType` + `courseCategory` + `orderBy(startTime)` is one index, and each distinct combination of active chips is potentially another, so build the query so unselected chips add no clause at all rather than a "match anything" clause.
- **Blocked-user hiding (spec: "sessions whose member list has this person are hidden/greyed out"):** after each fetch, drop or grey any session whose `memberUids` intersects the in-memory blocked set (§7.7). This is a one-line client-side check *precisely because* `memberUids` lives on the session document (§3.1) — without it you would have to open every session's `members` subcollection just to render the Home list. Default to **greying out with a "contains a blocked user" label** rather than hard-hiding, so capacity counts still make sense.
- Tap a card → navigate to Session Detail with `sessionId` via Safe Args.
- **A `FloatingActionButton` on this screen is the entry point to Create Session** (§7.4) — the spec's "+ button from Home".
- Cache the fetched list into Room after each successful fetch; on screen load, show cached data immediately while the network fetch is in flight (loading/empty/error states per the rubric).

### 7.3 Session detail
- Real-time listener (`addSnapshotListener`) on the session doc while this screen is visible — this is what makes edits/cancellations by the host show up live for anyone currently viewing the screen, without needing push notifications.
- Member list subcollection query for avatars; tap a member → read-only Profile view.
- Action button state machine — one `when` over (am I host?) × (my member status) × (session status) × (is it full?):

The whole thing is driven by one document read — `members/{myUid}` — plus the session's own fields. Evaluate top to bottom and take the first match:

| # | Condition | Button |
|---|---|---|
| 1 | navigated from History / `endTime` in the past | **no action**, "Past session" label — the spec's *past view mode* |
| 2 | `status == cancelled` | **no action**, "Cancelled by host" label |
| 3 | my member status is `admin` (i.e. I am the host) | **Manage** → §7.5 |
| 4 | my member status is `invited` | **Accept Invite** |
| 5 | my member status is `accepted` | **Leave session** — see the note below |
| 6 | my member status is `pending` | **Request pending** (disabled) + *Cancel request* |
| 7 | no member doc, `joinedCount >= capacity` | **Session full** (disabled) |
| 8 | no member doc, `mode == open` | **Join** |
| 9 | no member doc, `mode == gated` | **Request to join** |

- **Accept Invite** runs the invitee transaction from §3.1 (own `status` `invited` → `accepted`, `joinedCount += 1`, own uid into `memberUids`) and marks the matching inbox item read. Row 4 is why `invited` is a member status rather than an inbox-only concept — see §3.1.
- **Leave session — an intentional addition beyond the original spec.** The spec says the button is simply greyed out once you have joined. But §8 schedules a reminder that must be "cancelled if they leave", and a lobby you can never exit is a genuine dead end (a mis-tap on Join is unrecoverable, and the seat stays consumed against `capacity` forever). So row 5 shows **Leave session** instead of a greyed button: confirmation dialog → the delete-membership transaction from §3.1 → cancel the WorkManager reminder by its unique work name → write a `system` inbox item to the host so they know the roster changed. If the team prefers to hold strictly to the original spec, drop row 5 to a disabled "Joined" chip and everything else still works — but then also drop the reminder-cancellation clause in §8.
- The host cannot leave their own session; they cancel it instead (§7.5).
- **Cancel a pending request** is the same transaction minus the counter changes, since a pending request never entered `memberUids` (§3.1).

### 7.4 Create session
- Form with dropdowns for courseId (from a predefined per-community list — see §3.1), tagType, expectationLevel, mode.
- **Time input is a start time + a duration**, not two independent date-times: `MaterialDatePicker` + `MaterialTimePicker` for the start, then a duration dropdown (30m / 1h / 1.5h / 2h / custom). Compute and store `endTime = startTime + duration` (§3.1). This keeps the form to two taps while still producing the real interval that the overlap check (§7.2) and the upcoming/past split (§7.6) both need.
- Validate before submit: start time must be in the future, capacity ≥ 2, title non-empty, `endTime > startTime`.
- **Continue from last:** an optional entry point from History (§7.6) that prefills this form from a past session's fields (course, tag, expectation, location, description/goals, duration) — plain field copy, no new concept. Deliberately does *not* copy the date/time, which must always be re-picked.
- On submit: **one `set()` call**, not `add()` then a follow-up write. Generate the ID client-side with `collection("sessions").document()`, then write the session doc with `hostUid = my uid`, `joinedCount = 1`, `memberUids = [my uid]`, `status = "upcoming"` and the host's `members/{uid}` doc (`status = admin`) in a single `WriteBatch`. The rules in §4 require exactly this shape at creation — a session that is created empty and patched afterwards will be rejected, and a crash between two separate writes would leave a session with no host member.

### 7.5 Session management (host only)
- Pending requests list = subcollection query `where("status", "==", "pending")`.
- Approve = the approve transaction from §3.1 (member status → `accepted`, `joinedCount += 1`, uid appended to `memberUids`), then write an inbox item for that user. Reject = delete the member doc, counters untouched.
- Edit key fields (time/location) → after the Firestore write succeeds, also write a `system` inbox item for every accepted member (client-side fan-out loop — fine at this scale, no Cloud Function needed). Iterate `memberUids` from the session doc; it is exactly the recipient list, already loaded.
- Cancel session → set `status = cancelled`, same inbox fan-out. Do **not** delete the document — History (§7.6) and the members' own records still reference it.
- **Remove member from group** (explicitly in the spec, and easy to overlook because it looks like the mirror of "leave"): a swipe or overflow action on each accepted member in the roster → confirmation → the *same* delete-membership transaction as §3.1's last row, run by the host rather than the member (the rules in §4 allow both) → write a `system` inbox item to the removed user, since they will otherwise just find the session gone from My Sessions with no explanation. The host cannot remove themselves.
- **Invite by student ID** additionally writes `members/{uid}` with `status = invited` (§3.1) — not just the inbox item. The inbox row is the *notification*; the member doc is the *state* that makes the Accept Invite button appear on Session Detail (§7.3). Writing only one of the two is the most likely bug in this screen.

> **All four bullets above write into other users' `inbox` subcollections.** That is a cross-user write, and it is denied by the obvious `allow write: if request.auth.uid == uid` rule. §4 grants a narrow `create`-only exception precisely for this; if invites or approvals start failing with `PERMISSION_DENIED`, the rules are the first place to look, not the transaction code.
- **Invite member by student ID:** a search field queries `users` by `studentId` (`whereEqualTo("studentId", ...)`); on selecting a result, write an item to that user's `users/{uid}/inbox` subcollection with `type = invite` and `sessionId` set. No new library — reuses the inbox schema already in §3.1. Note `studentId` needs its own single-field index, and it should be unique in practice — decide what the UI does if the query returns zero users (most common case: a typo) or more than one.
- **Attach study materials:** document picker (`ACTION_OPEN_DOCUMENT`) → upload to Firebase Storage under `sessions/{id}/materials/` → append the resulting download URL to the session doc's `materialUrls` array. Same upload pattern as the profile photo flow (§7.7), just a different picker and destination path; handle basic file-size/type validation client-side before upload.

### 7.6 My sessions (calendar/list view) & History
- **The query is `sessions.whereArrayContains("memberUids", uid)`** — one flat query against the denormalised field in §3.1, ordered by `startTime`. Firestore cannot search a subcollection from the parent side, so there is no version of this screen that queries `members` directly; if that field is missing or stale, this screen, History, the activity graph (§7.7) and the overlap check (§7.2) all break together. Needs one composite index (`memberUids` array-contains + `startTime` order) — the console offers a one-click link the first time it runs.
- Split the result client-side by `endTime` vs now into "My sessions" (current/future) and "History" (past). Splitting on `endTime` rather than `startTime` means a session in progress right now still shows under "My sessions", which is what a user expects. This is also the source list for the Home overlap-hiding check in §7.2.
- Sessions with `status == cancelled` show in History with a struck-through/greyed treatment rather than disappearing.

**Calendar view *and* list view — the spec asks for both, and the calendar half is real work.** Do not let "(calendar view/list view)" in the screen title hide a day of effort:

- Android's built-in `CalendarView` widget **cannot** render per-date markers, so it cannot show which days have sessions. It is the wrong tool here despite the promising name.
- The no-new-library option, and the recommended one: a `RecyclerView` with `GridLayoutManager(context, 7)` rendering one month of day cells, each cell an `item_calendar_day.xml` with a date label and a dot whose visibility is driven by a `Map<LocalDate, List<Session>>` built once from the query result. Tapping a day filters the list underneath. This reuses the adapter pattern the team is already writing everywhere else, and the same day-cell grid is structurally the activity graph in §7.7 — build one, adapt it for the other.
- Ship the **list view first** and treat the calendar as the toggle added second (it is 🟡 in §10, the list is 🔴). A `MaterialButtonToggleGroup` in the toolbar switches between them; both render from the same already-fetched list, so the toggle costs no extra queries.

**Continue from last (spec placement):** the spec puts this button **inside past-session detail**, not on the History row — so the entry point is the past-view Session Detail screen (§7.3, state-machine row 1), which also gives room for a "this will invite the previous members" confirmation. It navigates to Create Session pre-filled per §7.4, and on submit **also sends an invite to every member of the original session** — iterate the old session's `memberUids`, and for each one run the invite path from §7.5 (member doc with `status = invited` + inbox item) against the newly created session. Skip anyone in the blocked set (§7.7) and skip the host themselves. This is the one place where "continue from last" is more than a field copy, and it is easy to miss when reading the spec quickly.
- **Export history (CSV / PDF)** — ⚠️ *note: this feature appears nowhere in the team's original spec; it was added by an earlier draft of this plan.* It is cheap and it demonstrates file I/O plus an implicit Intent, so it is worth keeping if time allows — but it is 🟢 in §10 and should be the **first thing cut** if the calendar view or any 🔴 item is at risk, precisely because no one asked for it. An export action on History builds the output client-side, no library needed — CSV as a plain delimited string, PDF via Android's built-in `PdfDocument` API for simple text/table drawing — then shares it via `FileProvider` + `Intent.ACTION_SEND` (`HistoryExporter.kt`, §5). The one setup cost is a one-time `FileProvider` manifest entry and `res/xml/file_paths.xml`.

### 7.7 Profile
- Fields per the spec: community, department, major, khóa tuyển (`admissionYear`), name, studentId, bio (§3.1).
- Self view: editable form + photo upload (system camera Intent or Photo Picker → Firebase Storage → write `photoUrl`). Offer **both** sources in a small bottom sheet — "Take photo" and "Choose from gallery" — since the spec names gallery *and* camera explicitly; they are two different launchers (`ActivityResultContracts.TakePicture` and `PickVisualMedia`) behind one button.
- **Community shown here is also the entry point to change it** — tapping it navigates to Community Selection (§7.1) in edit mode, which is the spec's "via community edit in profile". Changing community re-scopes Home, so confirm before writing, and note that sessions already joined in the old community stay in My Sessions.
- **Activity graph:** reuse the exact same `whereArrayContains("memberUids", uid)` result the My Sessions screen already fetches (§7.6) — no second query, and specifically **not** a `collectionGroup("members")` query, which §4 does not grant access to. Bucket those sessions by `startTime.toLocalDate()` client-side, then render a grid of colored `View`s (`ActivityGraphView.kt`, §5) — a GitHub-style heatmap, no charting library needed. Because both screens want the same data, put the query in `SessionRepository` and let both ViewModels observe it.
- Other view (read-only): navigated to from a member list tap.
- **Block user:** a "Block" action on another user's profile writes a document at `users/{myUid}/blocked/{theirUid}` (§3.1 — a private subcollection, not an array field on the public user doc, so the block list is not readable by the person blocked). Load it once at app start into an in-memory `Set<String>` on the repository.
  Per the spec, the effect is on **Home**: any session whose member list contains a blocked user is hidden or greyed out while browsing (§7.2). That check is `session.memberUids.any { it in blockedSet }` — a pure client-side test over data the Home query already returned, which only works because `memberUids` is denormalised onto the session document (§3.1). Do not attempt a Firestore query for this: `not-in` caps at 10 values and cannot express "array has no element in this set" at all.
  Also suppress inbox items where `fromUid` is blocked (§7.8). Do **not** strip blocked users out of a member list you are already viewing in Session Detail — the roster would then disagree with the `X/Y joined` counter, which reads as a bug.

### 7.8 Inbox (merged invites + notifications)
- Single subcollection query on `users/{uid}/inbox`, ordered by `createdAt` desc, rendered in a `RecyclerView`.
- Each item's `type` determines its action buttons. Per the spec an invite row carries **two** buttons:
  - **Accept** → runs the invitee transaction from §3.1 in place, without leaving the screen, then marks the item read
  - **Details** → navigates to Session Detail (§7.3) with the item's `sessionId`, where the same accept action is available as state-machine row 4
  A `join_request` row (host-facing) links to Session Management (§7.5); a `system` row has only "mark read".
- Hide items whose `fromUid` is in the blocked set (§7.7).
- The spec scopes this screen to invites only, and notes it is *"only available if implementing invite by ID"*. This plan merges invites and notifications into one screen, so it also carries the edit/cancel/removal messages fanned out in §7.5 — which means **it is no longer optional**: without it, a member is never told that a session's time changed or that it was cancelled. Treat 🔴, not conditional.

---

## 8. Notifications design (explicitly split, per your earlier note)

| Trigger | Mechanism | New concepts required |
|---|---|---|
| "Session starting soon" reminder | **WorkManager** `OneTimeWorkRequest` scheduled locally when the user joins a session, cancelled if they leave | None — device-local, no backend |
| Live update while viewing a session that changed | **Firestore realtime listener** already open on that screen | None — already using the SDK |
| System push notification when app is backgrounded and someone else edits/cancels your session | **Deferred.** Requires FCM (client, easy) + a server-side trigger (Cloud Functions, genuinely new) | See §11.3 — treat as optional stretch, attempt only with time to spare |

---

## 9. Permissions matrix

| Permission | Used for | Request pattern |
|---|---|---|
| `CAMERA` | Profile photo capture | `ActivityResultContracts.RequestPermission`, request only when the "take photo" button is tapped, show rationale if previously denied |
| `POST_NOTIFICATIONS` (Android 13+) | Local reminder notifications | Request once, ideally right after the user joins their first session |
| `READ_MEDIA_IMAGES` / Photo Picker | Gallery photo selection | Photo Picker (`ActivityResultContracts.PickVisualMedia`) needs **no runtime permission at all** on API 33+, which is why it's preferred over a manual gallery intent |
| `ACCESS_FINE_LOCATION` | Home proximity sort (§7.2) | Standard runtime permission flow; request when the user first taps "sort by distance," fall back to time-sort silently if denied |
| — (document picker) | Study material attach (§7.5) | `ACTION_OPEN_DOCUMENT` via Storage Access Framework needs no runtime permission |
| `READ_CALENDAR` | Only if true device calendar import (§11.1) is implemented — recommended against, see below | Standard runtime permission flow |

---

## 10. Suggested build phases

Rough phase breakdown against the Sept 5 deadline — treat as a starting point, adjust to your actual group size and split.

**Be honest about the scope first.** This plan carries roughly thirty distinct features across ten screens, for a 2–4 person team, in about two and a half weeks of part-time work. That is achievable only if the cut order is decided *now*, while nobody is panicking, rather than at 2 a.m. on Sept 4. So each phase below is marked:

- 🔴 **Must-have** — the app is not a coherent submission without it. Never cut.
- 🟡 **Should-have** — cut only if a 🔴 item is at risk.
- 🟢 **Nice-to-have** — cut freely; these exist to be traded for time.

| # | Phase | Contents |
|---|---|---|
| 1 | **Setup** | 🔴 Firebase project (Auth email/password + Firestore + Storage enabled via console), `google-services.json` wired in, Gradle per §6, Navigation skeleton with empty fragments for every core screen |
| 2 | **Rules + data layer** | 🔴 **Security rules from §4 pasted in and verified in the Rules Playground before any screen is built** — see the note below. 🔴 Firestore seeded with test documents that include `memberUids`, `endTime`, `materialUrls` from the start. 🔴 Room entities/DAOs/converters, repositories |
| 3 | **Auth + core loop** | 🔴 §7.0 routing, login/signup/forgot-password. 🔴 Community Selection **incl. the REST call** (course requirement — never cut). 🔴 Home base list + time sort, Session Detail, Create Session |
| 4 | **Membership loop** | 🔴 Join / request / invite / accept / **leave** / **remove member** transactions end-to-end. 🔴 Session Management (approve/edit/cancel). 🔴 My Sessions + History as **lists**. 🟡 invite by student ID, 🟡 study material upload, 🟡 continue-from-last (incl. re-inviting previous members) |
| 5 | **Profile + Inbox** | 🔴 Profile view/edit (self + read-only other), 🔴 merged Inbox with Accept/Details. 🟡 photo upload (Storage + camera Intent — this is the *device capability* requirement, so keep it unless location below is done instead). 🟡 change community from Profile. 🟢 activity graph, 🟢 block user |
| 6a | **Robustness pass** | 🔴 Loading / empty / error / offline states across all screens (§2.1) + Room cache wiring end-to-end. This is a 🔴 because it is explicitly in the rubric and touches every screen — it is not "polish" |
| 6b | **Adaptability pass** | 🟡 Portrait/landscape + small-phone/tablet-width check across all screens (§2.1) |
| 6c | **Extras** | 🟡 My Sessions **calendar view** toggle (§7.6 — the list ships in Phase 4, this is the second half). 🟡 proximity sort (the other candidate for the *device capability* requirement), 🟡 local reminder notifications, 🟢 auto-hide overlapping sessions, 🟢 CSV/PDF export (**not in the original spec** — cut first) |
| 7 | **Buffer** | 🔴 Bug fixing, the §13 checklist run end-to-end, and a full manual pass on a clean install. 🟢 Then, only if real time is left, one item from §11 |

**Two changes from the original phasing, and why they matter:**

- **Rules moved to Phase 2, before screens.** The join and inbox flows are the two things naive rules silently break (§4). Discovering that in Phase 4, after four screens are written against an assumption that does not hold, costs a day. Verifying four Playground cases in Phase 2 costs ten minutes.
- **The old Phase 6 was doing five large jobs at once** — proximity sort, overlap hiding, notifications, four UI states on *every* screen, and an adaptability pass on *every* screen. Two of those are per-screen sweeps across the whole app, which is why it was the phase most likely to collapse. Split into 6a/6b/6c so the rubric-critical sweep (6a) is protected and the optional Home extras (6c) are the part that gets traded away.

**Course-requirement floor — verify these three survive any cut:**

| Requirement | Satisfied by | Status |
|---|---|---|
| Persistent local data | Room cache (§2.2) + SharedPreferences | 🔴 Phase 2 + 6a |
| External data source / API | Retrofit → Firestore REST on Community Selection (§7.1) | 🔴 Phase 3 |
| Device capability | Camera for profile photo (§7.7) **or** location for proximity sort (§7.2) | 🔴 keep **at least one** — camera is the cheaper of the two |

---

## 11. Deferred features — genuinely hard or require a real architectural decision

Everything easy or medium is now folded into the core plan above (§3–§10). What's left here are the items that need either a new library commitment, a new backend-hosting decision, or open-ended judgment calls that shouldn't be pre-made for you. Only pick these up after the full core plan (phases 1–6) works end-to-end, and treat one as a stretch goal, not an expectation.

### 11.1 True device calendar import — *Medium–Hard, recommend deferring or skipping*
The core plan already ships the simplified version of this (auto-hide overlapping sessions using in-app data, §7.2), which delivers the actual user-facing value. The literal "import my Google/device calendar" feature instead needs `CalendarContract` ContentProvider queries, `READ_CALENDAR` permission, and correct handling of recurring events and timezones — genuinely fiddly, and only worth it if the in-app version proves insufficient and you have real time to spare.

If you do pick it up, it should be a **pure addition to `getBusyIntervals()`** (§7.2) — query `CalendarContract.Instances` for the visible window, map each instance to a `BusyInterval`, concatenate with the joined-session intervals, and return. No screen changes, no data-model changes, and the feature can be abandoned mid-way without leaving anything broken. That is the whole reason §7.2 routes through that function instead of reading joined sessions directly.

### 11.2 QR-based session join — *Medium–Hard*
Needs a barcode-scanning library, which is a real add-a-dependency decision rather than a mechanical extension of something already in the stack. If you pursue this, prefer **ZXing embedded** (`zxing-android-embedded`) over ML Kit — it's a drop-in scanning Activity with much less setup than ML Kit's model-based API, at the cost of slightly less polish. Requires `CAMERA` permission.

### 11.3 Push notification on session edits (server-triggered) — *Hard, requires a real backend decision*
This is the one feature that genuinely needs you to pick and learn a backend-execution service, which is exactly the kind of decision this plan tries to avoid for the core app. If attempted: add FCM to the client (straightforward, a few lines), then write **one small Cloud Function** (Node.js, HTTP or Firestore-trigger type) that fans out an FCM message to a session's accepted members when the doc changes. This reuses your existing Firebase project (no new hosting account), but does introduce Node.js, the Cloud Functions deploy flow, and (depending on plan tier) billing setup. Treat this as optional and time-boxed — the realtime-listener behavior in §8 already covers the "while viewing the screen" case, so this only adds value for backgrounded-app notifications.

### 11.4 AI chatbot (Gemini API) — *Hard, lowest priority*
Requires an API key, a chat UI, and a real decision about calling the Gemini REST API directly from the client via Retrofit (simplest, but embeds the key in the APK — an acceptable trade-off for a course project, just flag it in the report as a known limitation) versus routing through a backend (more secure, but reintroduces exactly the backend-hosting question this plan avoids elsewhere). Only attempt this once every core screen and higher-priority deferred item is done.

---

## 12. Deliberate simplifications — summary

For quick reference, here's everything intentionally kept simple and why, so nobody on the team second-guesses these mid-build:

- No custom backend server — Firestore replaces it entirely for core CRUD.
- No Cloud Functions in the core plan — deferred as one of the hardest remaining items (§11.3).
- **No `collectionGroup` queries** — membership is denormalised onto the session document as `memberUids` (§3.1), which turns four otherwise-impossible screens into one flat `array-contains` query with no extra rules surface.
- **No stored `completed` status** — past vs upcoming is derived from `endTime` client-side, because nothing serverless can flip that flag when the time passes (§3.1).
- **No server-side enforcement of "host only may upload materials"** — Storage rules cannot read Firestore, so this is UI-enforced with size/type caps as the backstop (§4.1). Flag it as a known limitation in the report.
- No domain-model mapping layer — one shared model class per entity, small extension functions where Room needs a different shape.
- No dependency-injection framework — a manual `ServiceLocator` object is enough at this scale.
- No CameraX — system camera Intent is sufficient for "take a profile photo."
- No barcode-scanning library in the core plan — QR-based join deferred (§11.2).
- No Firebase CLI — security rules are edited directly in the console.
- No real device calendar import — the "hide overlapping sessions" value is core and delivered from in-app data instead; true calendar import stays deferred (§11.1).
- No AI/chatbot integration in the core plan — deferred as lowest priority (§11.4).

---

## 13. Spec compliance checklist

Every line below is traced back to the team's original feature list. Tick a box only when the behaviour works **on a device, against real Firestore, as a non-host account** — a passing unit test or a screen that merely renders is not a tick. Run the whole list once in Phase 7 (§10) with a freshly installed app and a second account, because a startling number of these only break for the *second* user.

Legend: 🔴 must-have · 🟡 should-have · 🟢 nice-to-have · ⬜ deferred by design (§11)

### Community selection (§7.1)
- [ ] 🔴 Appears automatically on first login, before Home is reachable
- [ ] 🔴 Reachable again later by tapping community in Profile (§7.7)
- [ ] 🔴 Initial "browse all" list loads **via Retrofit/REST**, not the SDK — *this is the course's API requirement; verify by watching the network call, not just the list*
- [ ] 🔴 Search as you type narrows the list
- [ ] 🔴 Filter by city works
- [ ] 🔴 Joining a **free-for-all** community succeeds
- [ ] 🔴 Joining a **verified** community succeeds when the email domain matches
- [ ] 🔴 Joining a verified community is **blocked with a readable message** when the domain does not match

### Home / Upcoming sessions (§7.2)
- [ ] 🔴 Lists only sessions in *my* community
- [ ] 🔴 Sort by time
- [ ] 🔴 Sort by expectation level
- [ ] 🔴 Search by course ID
- [ ] 🔴 Filter chips — **session type** (normal / midterm / final)
- [ ] 🔴 Filter chips — **course type** (physics / calculus / DSA …), driven by `courseCategory` (§3.1)
- [ ] 🔴 Two filters + a sort applied together return correct results *(composite index created — this is where a missing index shows up)*
- [ ] 🔴 Tapping a card opens Session Detail
- [ ] 🔴 `+` FAB opens Create Session
- [ ] 🟡 Sort by location proximity, with distance shown per card
- [ ] 🟡 Location permission denied → silently falls back to time sort, no crash, no error dialog
- [ ] 🟢 Sessions overlapping my availability are greyed out / hidden
- [ ] 🟢 Sessions containing a blocked user are greyed out / hidden

### Session detail (§7.3)
- [ ] 🔴 Header shows course, tag type, time, location, capacity as **X/Y joined**
- [ ] 🔴 Body shows description, goals/agenda, expectation level, session type
- [ ] 🔴 Member list renders with avatars
- [ ] 🔴 Tapping a member opens their read-only Profile
- [ ] 🔴 Button = **Join** on an open session, and joining increments X/Y for *everyone*
- [ ] 🔴 Button = **Request to join** on a gated session
- [ ] 🔴 Button = **Accept Invite** when invited (§3.1 `invited` status)
- [ ] 🔴 Button = **Manage** when I am the host
- [ ] 🔴 Button disabled / absent when opened from History (past view mode)
- [ ] 🔴 Button reads **Session full** and is disabled at capacity
- [ ] 🔴 Host's edit to time/location appears live while I have this screen open (snapshot listener)
- [ ] 🟡 Attached study materials are listed and open
- [ ] 🟡 *(addition beyond spec, see §7.3)* Leave session works and frees a seat

### Create session (§7.4)
- [ ] 🔴 Reached from Home's `+` button
- [ ] 🔴 Text fields, dropdowns and chips for every session attribute
- [ ] 🔴 Mode chosen at creation: **free-for-all** or **request-gated**
- [ ] 🔴 Start time + duration produce a correct `endTime`
- [ ] 🔴 Created session appears on Home for *another* account in the same community
- [ ] 🔴 Creator is immediately a member with `admin` status and X/Y reads 1/Y

### Session management (§7.5)
- [ ] 🔴 Pending requests list appears **only** for gated sessions
- [ ] 🔴 Approve moves the requester into the member list and increments X/Y
- [ ] 🔴 Reject removes the request without changing X/Y
- [ ] 🔴 Edit session information saves and is visible to others
- [ ] 🔴 Cancel session marks it cancelled everywhere (not deleted)
- [ ] 🔴 **Remove member** works and decrements X/Y
- [ ] 🔴 Editing **time** notifies every accepted member
- [ ] 🔴 Editing **location** notifies every accepted member
- [ ] 🔴 Cancelling notifies every accepted member
- [ ] 🟡 Invite by student ID → target receives an inbox item **and** an Accept Invite button on Session Detail
- [ ] 🟡 Attach study material uploads and appears on Session Detail
- [ ] ⬜ QR join scanner — deferred (§11.2)

### My sessions (§7.6)
- [ ] 🔴 **List view** shows sessions I joined
- [ ] 🔴 Tapping an entry returns to Session Detail
- [ ] 🟡 **Calendar view** toggle, with days that have sessions marked
- [ ] ⬜ Import device calendar — deferred (§11.1)

### History (§7.6)
- [ ] 🔴 Past sessions show time + location + tags
- [ ] 🔴 Tapping opens Session Detail in **past view mode**
- [ ] 🟡 **Continue from last** button inside past-session detail prefills Create Session
- [ ] 🟡 …and sends an invite to the previous session's members
- [ ] 🟢 CSV / PDF export *(not in the original spec — cut first, §7.6)*

### Profile (§7.7)
- [ ] 🔴 Shows community, department, major, khóa tuyển, name, student ID, bio
- [ ] 🔴 Self view allows editing; changes persist across a restart
- [ ] 🔴 Profile picture upload **from gallery**
- [ ] 🔴 Profile picture upload **via device camera**
- [ ] 🔴 Read-only mode when opened from a member list — no edit controls visible
- [ ] 🟢 Activity graph lights up days on which I joined a session
- [ ] 🟢 Block user, and the effect is visible on Home (§7.2)

### Invites / Inbox (§7.8)
- [ ] 🔴 Invites appear in a RecyclerView list
- [ ] 🔴 Each invite row has **Accept** and **Details**
- [ ] 🔴 Accept joins the session without leaving the screen
- [ ] 🔴 Details navigates to Session Detail
- [ ] 🔴 Edit / cancel / removal notifications from §7.5 arrive here

### Cross-cutting (§2.1, §9) — check on *every* screen that fetches
- [ ] 🔴 Loading state
- [ ] 🔴 Empty state with a real message
- [ ] 🔴 Error state with a working retry
- [ ] 🔴 Offline state falls back to the Room cache with a "showing cached data" hint
- [ ] 🟡 Portrait and landscape
- [ ] 🟡 Small phone and tablet-width emulator
- [ ] 🔴 Every runtime permission has a denial path that does not crash or dead-end
- [ ] 🔴 Sign out clears the Room cache — log in as a second account and confirm no data from the first leaks through

### Course requirements (§10) — the three that must survive any cut
- [ ] 🔴 **Persistent local data** — Room cache demonstrably serving a screen with the network off
- [ ] 🔴 **External API** — the Retrofit/REST call in §7.1
- [ ] 🔴 **Device capability** — camera (§7.7) and/or location (§7.2), at least one fully working