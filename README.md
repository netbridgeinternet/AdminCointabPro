# Admin CointabPro

Admin companion app for the CointabPro kiosk system. Connects to Google Drive to monitor photos captured by kiosk devices.

## Features
- Google Sign-In & Drive integration
- Shared folder management (CointabPro Photos/)
- QR code pairing with kiosk devices
- Device dashboard with last photo preview
- Photo gallery per device
- Power-efficient Drive polling for new photos
- Service Account support for kiosk uploads
- Configurable sync frequency

## Setup
1. Create a Google Cloud project with Drive API enabled
2. Add your `google-services.json` to the `app/` directory
3. Replace `YOUR_SERVER_CLIENT_ID` in `DriveManager.kt` with your OAuth web client ID
4. Build and install

## Pairing
1. Sign in to Google Drive on the Admin app
2. The app creates a "CointabPro Photos" folder
3. Show the QR code to pair with a CointabPro kiosk
4. The kiosk scans the QR code and uploads photos to the shared folder
