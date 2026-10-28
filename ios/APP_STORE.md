# App Store preparation — Lokma Go Restoran

## App record draft

- Display name: Lokma Go Restoran
- Bundle ID: `uz.lokmago.restaurant.ios`
- Version: `1.0.0` (build `1`)
- Primary language: Uzbek
- Category: Food & Drink (secondary: Business, if available)
- The iOS app now authenticates directly with the existing `lakmago-server` API used by the admin panel (`https://apilokma.poppolizol.uz/api/`). It uses the same restaurant login and password, then keeps the session token in Keychain.

## Store materials

- Listing metadata: `ios/AppStore/STORE_LISTING.md`.
- Privacy policy draft: `ios/AppStore/PRIVACY_POLICY_DRAFT.md`.
- Support page draft: `ios/AppStore/SUPPORT_PAGE_DRAFT.md`.
- The icon asset is 1024×1024 PNG with no alpha channel.
- `PrivacyInfo.xcprivacy` declares the account identifier and app-local UserDefaults access. The App Store Connect privacy answers must also include any data the server retains, including relevant account and request log data.

Replace all bracketed placeholders and publish the policy/support pages at public HTTPS URLs. Confirm the legal operator, retention/deletion practices, and privacy contact with the service owner before submission.

## Checks completed

- The public API host `https://apilokma.poppolizol.uz/health` and `/api/time` both returned HTTP 200 over TLS.
- The protected `/api/panel/me` route returned HTTP 401 without a bearer token, as expected.
- The iOS API client has no shared gateway/BFF key. Login is performed by the existing admin-panel API and subsequent requests use the JWT.
- A fresh unsigned Release archive built against the new `main` API contract is at `ios/build/LokmaRestaurant-main.xcarchive`.

## Remaining before App Store submission

- Sign the fresh archive with Apple Distribution before upload.
- Create the App Store Connect app record; provide a non-production restaurant account for review.
- Finalize App Privacy answers and publish privacy/support URLs.
- Capture at least one screenshot at Apple's required iPhone display size with Dynamic Island. The connected iPhone 13 Pro does not provide that display size; CoreSimulator was unavailable when checked.
- Test sign-in, order status transitions, reports, session refresh, and offline/error behavior with a test restaurant.
- Configure `lakmago-server` APNs environment (`APNS_KEY_PATH`, `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_TOPIC`) before relying on order alerts while the app is closed; see `docs/SERVER-PUSH.md`.
