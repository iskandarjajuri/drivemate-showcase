# backend / バックエンド

[日本語](#日本語) ・ [English](#english)　｜　[← README](../README.md)

<a id="日本語"></a>

## 日本語

Firebase の **Firestore Security Rules** と、チャットまわりの **Cloud Functions（TypeScript）** です。
すべてエミュレータで動作確認できます（実際の Firebase には接続しません）。

| パス | 内容 |
|---|---|
| `firestore.rules` | 名前付きデータベース `drivemate-database` 用のルール |
| `firestore.indexes.json` | 複合インデックス |
| `functions/src/translate/` | チャットの自動翻訳（日本語 ⇄ インドネシア語）。外部 LLM の API キーは Secret Manager から取得し、**未設定なら無効** |
| `functions/src/chat/` | スレッド集計・未読数・ディレクトリ同期・プッシュ通知 |
| `functions/src/push/` | プッシュ通知の送信（トークン管理を含む） |
| `functions/test/` | ユニットテストとルールテスト |

- 関数はすべて **Firestore トリガー** です。HTTPS の呼び出し口（callable）はなく、アプリから直接は呼びません。
- 翻訳の結果は、メッセージのドキュメントに `translations.{ja|id}`・`translationStatus` などとして書き込まれます。
  デモでは、この形のデータを **あらかじめシード**しています（`tools/seed/seed.mjs`）。
- テスト：`npm ci && npm test`（エミュレータ不要のユニットテスト）。

## English

Firebase **Firestore Security Rules** and the chat-related **Cloud Functions (TypeScript)**. Everything can be exercised
on the emulators (it never connects to a real Firebase project).

| Path | Contents |
|---|---|
| `firestore.rules` | Rules for the named database `drivemate-database` |
| `firestore.indexes.json` | Composite indexes |
| `functions/src/translate/` | Automatic chat translation (Japanese ⇄ Indonesian). The external LLM API key comes from Secret Manager; **disabled when it is not set** |
| `functions/src/chat/` | Thread bookkeeping, unread counts, directory sync, push notifications |
| `functions/src/push/` | Sending push notifications (including token handling) |
| `functions/test/` | Unit tests and rules tests |

- Every function is a **Firestore trigger**. There are no HTTPS callables; the app never calls a function directly.
- The translation result is written onto the message document as `translations.{ja|id}`, `translationStatus`, etc. The
  demo **pre-seeds** data of exactly this shape (`tools/seed/seed.mjs`).
- Tests: `npm ci && npm test` (unit tests that need no emulator).
