# マイルストーン / Milestones

[日本語](#日本語) ・ [English](#english)　｜　[← README](../README.md) ・ [アーキテクチャ / Architecture](architecture.md)

---

<a id="日本語"></a>

## 日本語

個人開発プロジェクトとしての **工程表（マイルストーン）** です。
「いつまでに何を、どうなれば完了とするか」を明確にし、状況を **ありのまま** に見える化します。

### この表の読み方

- **状況**：✅ 完了　🔄 進行中　📅 予定
- **完了条件**（Definition of Done）：何が満たされたら完了とするか。**証跡**：その根拠（コード・テスト・動画など）。
- **時期**：元プロジェクトの履歴で確認できる着手月（`YYYY年M月`）のみ記載します。予定の項目には、**確約できない期限を書きません**。
- 数値はすべて実測です（2026年10月28日時点）。推測の数字は載せません。デモの画面・動画はすべて **デモデータ（架空）** です。

### 工程表（一覧）

| # | マイルストーン | 状況 | 時期 |
|---|---|:---:|---|
| M0 | 基盤：KMP 共有モジュール＋ Clean Architecture ＋ MVVM | ✅ 完了 | 2025年12月〜 |
| M1 | ドライバーの日次業務（出退勤打刻・車両点検・安全申告・SOP・勤務履歴） | ✅ 完了 | 2025年12月〜2026年2月 |
| M2 | 駐在員（Expat）：ホームとメッセージ | ✅ 完了（Android） | 2025年12月〜 |
| M3 | 多言語（日本語／インドネシア語／英語）と端末言語への自動追従 | ✅ 完了 | 2026年2月〜 |
| M4 | チャットと自動翻訳（インドネシア語 ⇄ 日本語） | ✅ 実装済み（デモは翻訳を事前保存） | 2026年4月〜5月 |
| M5 | 品質保証：自動テスト・アーキテクチャ検査・CI | 🔄 進行中 | 2026年5月〜 |
| M6 | ショーケースの公開（このリポジトリ・デモ動画・ドキュメント） | 🔄 進行中 | 2026年10月 |
| M7 | iOS（SwiftUI）の拡充 | 🔄 進行中（本リポジトリには含まない） | 2026年4月〜 |
| M8 | 技術的負債の返済（型付きエラー、コメントの英語化 ほか） | 📅 予定 | 未定 |
| M9 | Compose Multiplatform による共有 UI | 📅 予定（構想段階） | 未定 |

### 各マイルストーンの詳細

#### M0　基盤 ✅
- **目的**：Android と iOS で業務ロジックを一度だけ書く土台をつくる。
- **完了条件**：ドメイン層がプラットフォームに依存しない。依存の向きが「プレゼンテーション → ドメイン ← データ」で一方向。
- **証跡**：[`docs/architecture.md`](architecture.md)、`./scripts/check-architecture.sh`（5 つの規則がすべて合格）、`shared` のテスト **539 件・失敗 0**。
- **課題**：共有ドメインに管理者向けユースケースが残っている（画面は含めていない）。

#### M1　ドライバーの日次業務 ✅
- **目的**：ドライバーが 1 日の業務（打刻 → 点検 → 安全申告 → 退勤）を迷わず終えられるようにする。
- **完了条件**：エミュレータ上で、ログイン → 出勤打刻（GPS）→ 車両点検 → 安全申告（12 ステップ）→ 退勤が、実際の Security Rules を通して最後まで完了する。
- **証跡**：デモ動画と画面（`docs/media/`）。点検・申告は「1 日 1 回」をドキュメント ID と Security Rules で保証。
- **確認できたこと**：Tap In／Tap Out の位置は逆ジオコーディングされ、出退勤イベントの `locationName` に住所が保存される（デモでは公共のランドマークの住所）。
- **課題**：逆ジオコーディングは端末側のサービスに依存する。失敗時の挙動は、このショーケースでは **未検証**。

#### M2　駐在員（Expat）：ホームとメッセージ ✅（Android）
- **目的**：駐在員が、担当ドライバーの状況を一目で把握し、すぐに連絡できるようにする。
- **完了条件**：ホーム（要確認の件数・お知らせ）とメッセージが動作する。
- **証跡**：デモ動画・画面。
- **課題**：デモで見せているのは Expat の **ホームとメッセージのみ**。承認（レビュー）ワークフローのコードはリポジトリにあるが、デモでは実演していない。

#### M3　多言語と端末言語への自動追従 ✅
- **目的**：既定では端末の言語設定に自然に従う（アプリ内の「アプリの言語」設定は初期値が「システムに従う」）。
- **完了条件**：端末の言語を日本語 → インドネシア語に切り替えると、アプリの表示も追従する。
- **証跡**：デモ動画（端末の設定画面で言語を切り替える様子）。文言は EN／ID／JA の 3 言語。
- **課題**：コード内コメントの一部がインドネシア語（M8 で英語化を予定）。

#### M4　チャットと自動翻訳 ✅（実装済み・デモは事前保存）
- **目的**：日本語の駐在員とインドネシア語のドライバーが、互いの母語で会話できるようにする。
- **完了条件**：メッセージ保存時に翻訳が付与され、読み手の言語で表示される。
- **証跡**：翻訳パイプライン（`backend/functions`）とそのテスト。デモでは **原文と翻訳をあらかじめ Firestore に保存**（ライブ翻訳ではありません）。
- **課題**：ライブ翻訳には外部 LLM の API キーが必要で、このデモでは実演していない。

#### M5　品質保証 🔄
- **目的**：変更しても壊れないことを、自動で確かめられるようにする。
- **完了条件（現時点の到達点）**：
  - `shared` のテスト **539 件**、`composeApp` のユニットテスト **302 件**、バックエンドのユニットテスト **69 件**、Security Rules のテスト **407 件**。いずれも失敗 0（合計 1,317 件）。
  - アーキテクチャ検査スクリプトが合格。
  - GitHub Actions のワークフローを用意（リポジトリ公開後に初回実行。結果は実行後に追記します）。
- **未達**：Compose UI・iOS の自動テストは薄い。テストカバレッジ率は **未計測**。
- **次の一手**：公開後に CI を有効化し、結果をこの表へ反映する。

#### M6　ショーケースの公開 🔄
- **目的**：実装・設計・品質への姿勢を、第三者が確認できる形で公開する。
- **完了条件**：リポジトリ公開、README（日本語・英語）、デモ動画、ドキュメント、機密情報の検査が完了している。
- **証跡**：本リポジトリ。社名・個人情報・認証情報が含まれないことを、公開前に自動検査で確認。

#### M7　iOS（SwiftUI）の拡充 🔄
- **目的**：iOS でも Android と同等の体験を提供する。
- **現状（正直に）**：iOS は **Android より範囲が狭い**。同等とは言えない状態のため、本リポジトリには含めていない。
- **証跡**：共有モジュールの Kotlin が iOS ターゲット（`iosArm64`・`iosSimulatorArm64`）でコンパイルできる（Linux で確認）。Swift／Xcode のビルドは未検証。
- **次の一手**：機能差分の一覧化 → 優先度の高い画面から順に追加。

#### M8　技術的負債の返済 📅
- **ログイン失敗の分類**：現在はエラー **メッセージの文字列** で分類している（`AuthViewModel.kt` の `mapAuthError`）。
  SDK のメッセージが変わると壊れやすいため、データ層で **型付きエラー** に変換する。
- コメント・テスト名の英語化。コンポーネントごとのテスト追加。

#### M9　Compose Multiplatform による共有 UI 📅
- 構想段階。最初の候補は、汎用的なフォーム画面（点検リスト）。**着手の約束はしていません。**

### 進め方（報連相・見える化・カイゼン）

- **報連相**（報告・連絡・相談）：状況が変わったら、この表を更新し、**証跡へのリンク**を添えて報告します。
- **見える化**：進捗はこの文書と [GitHub の Milestones](https://github.com/iskandarjajuri/drivemate-showcase/milestones) で公開し、「完了」には必ず根拠を添えます。
- **カイゼン**：完了のたびに「うまくいったこと／課題」を振り返り、課題は M8 に積みます。例：デモ作成中に、
  ログイン失敗の分類が SDK メッセージ依存で壊れていることを発見し、判定を修正してテストを追加しました。
- **品質を最優先**：機能の数よりも、安定性と画面の分かりやすさを優先します。

---

<a id="english"></a>

## English

The project's **milestone plan** (a Japanese-style *kōtei-hyō* schedule). It states what must be true for each step to
count as done, and shows the status **as it is**.

### How to read this

- **Status:** ✅ done　🔄 in progress　📅 planned
- **Definition of Done** says what must be true for completion; **Evidence** is the proof (code, tests, videos).
- **When:** only start months that can be confirmed from the original project's history (`Month YYYY`). Planned items carry
  **no deadlines I cannot keep**.
- All numbers are measured (as of 28 October 2026). No guessed figures. Every screen and video in the demo is **demo data
  (fictional)**.

### Schedule at a glance

| # | Milestone | Status | When |
|---|---|:---:|---|
| M0 | Foundation: KMP shared module + Clean Architecture + MVVM | ✅ Done | Dec 2025 → |
| M1 | Driver daily work (clock in/out, vehicle checklist, safety declaration, SOP, work history) | ✅ Done | Dec 2025 – Feb 2026 |
| M2 | Expatriate: Home and Messages | ✅ Done (Android) | Dec 2025 → |
| M3 | Multilingual (Japanese / Indonesian / English) following the device language | ✅ Done | Feb 2026 → |
| M4 | Chat with automatic translation (Indonesian ⇄ Japanese) | ✅ Implemented (demo uses pre-seeded translations) | Apr – May 2026 |
| M5 | Quality: automated tests, architecture check, CI | 🔄 In progress | May 2026 → |
| M6 | Publish the showcase (this repository, demo videos, docs) | 🔄 In progress | Oct 2026 |
| M7 | iOS (SwiftUI) expansion | 🔄 In progress (not included here) | Apr 2026 → |
| M8 | Pay down technical debt (typed errors, English comments, …) | 📅 Planned | TBD |
| M9 | Shared UI with Compose Multiplatform | 📅 Planned (idea stage) | TBD |

### Details

#### M0　Foundation ✅
- **Purpose:** a base where business logic is written once for Android and iOS.
- **Definition of Done:** the domain layer has no platform dependency; dependencies point one way (presentation → domain ← data).
- **Evidence:** [`docs/architecture.md`](architecture.md); `./scripts/check-architecture.sh` (all 5 rules pass); **539 `shared`
  tests, 0 failures**.
- **Issue:** the shared domain still contains admin-oriented use cases (no admin screens are included).

#### M1　Driver daily work ✅
- **Purpose:** let a driver finish the day (clock in → inspect → safety declaration → clock out) without getting lost.
- **Definition of Done:** on the emulator, sign-in → clock-in (GPS) → vehicle checklist → 12-step safety declaration →
  clock-out all complete through the real Security Rules.
- **Evidence:** demo videos and screenshots (`docs/media/`). "Once per day" is enforced by the document id and the rules.
- **Verified:** the Tap In / Tap Out position is reverse-geocoded and the address is stored in the attendance event's `locationName` (in the demo, the address of a public landmark).
- **Issue:** reverse geocoding depends on a device-side service. Behaviour on failure is **not verified** in this showcase.

#### M2　Expatriate: Home and Messages ✅ (Android)
- **Purpose:** let an expatriate see the drivers' status at a glance and reach them quickly.
- **Definition of Done:** Home (items needing attention, announcements) and Messages work.
- **Evidence:** demo videos and screenshots.
- **Issue:** the demo shows the expatriate **Home and Messages only**. The approval (review) workflow code is in the repository but is not demonstrated.

#### M3　Following the device language ✅
- **Purpose:** by default the app follows the device's language setting (the in-app "App language" setting starts at "Follow system").
- **Definition of Done:** switching the device language from Japanese to Indonesian switches the app too.
- **Evidence:** a demo video that changes the system language in the device's Settings. Strings exist in EN, ID and JA.
- **Issue:** some code comments are in Indonesian (English translation planned in M8).

#### M4　Chat with automatic translation ✅ (implemented; demo is pre-seeded)
- **Purpose:** let a Japanese expatriate and an Indonesian driver talk in their own languages.
- **Definition of Done:** a translation is attached when a message is stored and shown in the reader's language.
- **Evidence:** the translation pipeline (`backend/functions`) and its tests. In the demo, **both the original and the
  translation are pre-seeded in Firestore** (this is not live translation).
- **Issue:** live translation needs an external LLM API key, which is not exercised in this demo.

#### M5　Quality 🔄
- **Purpose:** be able to confirm automatically that changes do not break things.
- **Reached so far:**
  - **539** `shared` tests, **302** `composeApp` unit tests, **69** backend unit tests and **407** Security Rules tests — 0 failures in all (1,317 in total).
  - The architecture check passes.
  - A GitHub Actions workflow is provided (first run after the repository is public; results will be added here).
- **Not reached:** automated tests for Compose UI and iOS are thin. Coverage percentage is **not measured**.
- **Next:** enable CI once public and reflect the results in this table.

#### M6　Publish the showcase 🔄
- **Purpose:** make the implementation, design and quality practices verifiable by third parties.
- **Definition of Done:** repository public; README (Japanese and English), demo videos, docs, and a secrets/PII scan completed.
- **Evidence:** this repository. Before publishing, automated scans confirmed no company names, personal data or credentials.

#### M7　iOS (SwiftUI) expansion 🔄
- **Purpose:** offer the same experience on iOS as on Android.
- **Where it stands (honestly):** iOS is **narrower than Android**. Because it is not equivalent, it is not included here.
- **Evidence:** the shared module's Kotlin compiles for the iOS targets (`iosArm64`, `iosSimulatorArm64`) — verified on Linux. A Swift/Xcode build is not verified.
- **Next:** list the feature gaps, then add screens in priority order.

#### M8　Technical debt 📅
- **Login-failure classification:** it currently inspects the error **message text** (`mapAuthError` in `AuthViewModel.kt`),
  which breaks when SDK messages change. Convert to **typed errors** in the data layer.
- Translate comments and test names to English; add component-level tests.

#### M9　Shared UI with Compose Multiplatform 📅
- Idea stage; the first candidate is a generic form screen (the checklist). **No commitment to start.**

### How the work is run (hō-ren-sō, visibility, kaizen)

- **Hō-ren-sō** (report, inform, consult): when status changes, update this table and attach a **link to the evidence**.
- **Visibility (*mieruka*):** progress is public in this document and in [GitHub Milestones](https://github.com/iskandarjajuri/drivemate-showcase/milestones); every "done" carries its proof.
- **Kaizen (continuous improvement):** after each milestone, review what worked and what did not, and queue issues in M8.
  Example: while making the demo, I found that login-failure classification broke on SDK message changes, fixed the
  detection and added tests.
- **Quality first:** stability and clarity of the screens take priority over the number of features.
