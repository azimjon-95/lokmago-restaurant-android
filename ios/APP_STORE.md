# App Store preparation — Lokma Go Restaurant

## App record draft

- Display name: Lokma Go Restoran
- Bundle ID: `uz.lokmago.restaurant.ios`
- Version: `1.0.0` (build `1`)
- Primary language: Uzbek
- Category: Food & Drink / Business (choose the closest available in App Store Connect)
- Description: Lokma Go Restoran helps restaurant staff receive and manage customer orders. Sign in with your restaurant account, review incoming orders and customer details, update preparation and delivery status, and view today's sales summary.
- Keywords: restoran, buyurtma, lokma, taom, yetkazib berish

## App privacy draft

The app processes restaurant staff login credentials for authentication, a session token stored in the iOS Keychain, and order/customer details returned by the restaurant API. The app currently includes no ads, third-party analytics, or tracking SDKs. Order and customer data are displayed to authorized staff and are handled by the Lokma Go API; backend retention and deletion controls must be confirmed with the service owner before submitting the privacy answers in App Store Connect.

Before submission, publish a restaurant-app privacy policy that names the legal operator and provides a working privacy contact. The existing customer-app policy does not describe this staff app's order/customer data access and should not be reused without revision.

## Store materials

- Listing metadata draft: `ios/AppStore/STORE_LISTING.md`.
- Privacy policy draft with backend-verification checklist: `ios/AppStore/PRIVACY_POLICY_DRAFT.md`.
- Support page draft: `ios/AppStore/SUPPORT_PAGE_DRAFT.md`.
- Replace every bracketed placeholder and publish the policy/support pages at public HTTPS URLs before submission.

## Remaining release requirements

- The app talks directly to lakmago-server (`https://apilokma.poppolizol.uz/api/`, same login + password as the admin panel). No gateway. Verify `/health` over HTTPS before submission.
- Configure APNs and backend push delivery if background order alerts are required. This first iOS version polls for new orders while the app is open.
- Apple Development signing is configured for device testing.
- Create an Apple Distribution signing identity/profile before exporting an App Store archive.
- Current Release archive is at `ios/build/LokmaRestaurant.xcarchive`; it was built without signing and cannot be uploaded until Distribution signing is configured.
- Optional: run `./ios/sync-config.sh` to take `API_URL` from the ignored `env.properties` (otherwise the default server in `Config.swift` is used).
- Create the App Store Connect app record, verify account ownership and legal details, and answer the App Privacy questionnaire based on the backend's actual practices.
- Capture required iPhone screenshots on a real device and provide public support/privacy URLs.
- Validate login, order transitions, reports, and offline/error behavior against a test restaurant before release.
