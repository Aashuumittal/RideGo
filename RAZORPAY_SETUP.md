# Razorpay Setup Guide for RideGo

This project keeps the existing Spring Boot + MongoDB + static HTML/CSS/JS architecture. Razorpay is integrated from the Spring Boot backend using REST calls and from the static passenger dashboard using Razorpay Checkout.

## 1. Razorpay account creation

1. Go to https://razorpay.com and create a business account.
2. Complete email and phone verification.
3. Open the Razorpay Dashboard.
4. Keep the account in Test Mode while developing locally.

## 2. Test mode setup

1. In the Razorpay Dashboard, switch the environment toggle to Test Mode.
2. Use Razorpay test cards, UPI, and wallet values from the Razorpay documentation.
3. Do not use live keys for local testing.

## 3. API key generation

1. In the Razorpay Dashboard, open Account & Settings.
2. Go to API Keys.
3. Click Generate Test Key.
4. Copy the Key ID and Key Secret immediately.
5. Store them as environment variables. Do not commit real secrets.

## 4. Environment variables

Set these before starting Spring Boot:

```bash
export ORS_API_KEY=your_openrouteservice_key
export RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxxxx
export RAZORPAY_KEY_SECRET=your_razorpay_test_secret
export RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
```

Windows PowerShell:

```powershell
$env:ORS_API_KEY="your_openrouteservice_key"
$env:RAZORPAY_KEY_ID="rzp_test_xxxxxxxxxxxxxx"
$env:RAZORPAY_KEY_SECRET="your_razorpay_test_secret"
$env:RAZORPAY_WEBHOOK_SECRET="your_webhook_secret"
```

## 5. Backend integration steps

1. Spring Boot reads `razorpay.key-id`, `razorpay.key-secret`, and `razorpay.webhook-secret` from `application.properties` environment placeholders.
2. When a completed ride is ready for payment, the passenger calls `POST /api/v1/rides/{rideId}/payments/order`.
3. The backend creates a Razorpay order for the persisted ride fare.
4. After Checkout succeeds, the passenger calls `POST /api/v1/rides/{rideId}/payments/verify` with payment ID, order ID, and signature.
5. The backend verifies the signature, stores the payment in MongoDB, and marks the ride `PAID`.

## 6. Frontend integration steps

1. `user.html` loads `https://checkout.razorpay.com/v1/checkout.js`.
2. Completed unpaid rides display a Pay button.
3. The Pay button creates an order through the backend.
4. Razorpay Checkout opens with the returned order ID, amount, currency, and key ID.
5. On success, the browser posts the Razorpay response to the backend verification endpoint.

## 7. Webhook setup

1. For local webhook testing, expose port `9091` using a tunnel such as ngrok.
2. Example: `ngrok http 9091`.
3. In Razorpay Dashboard, add a webhook URL:
   `https://your-ngrok-domain/api/v1/payments/razorpay/webhook`
4. Select payment/order events needed for reconciliation, such as `payment.captured` and `payment.failed`.
5. Set the same webhook secret in Razorpay and `RAZORPAY_WEBHOOK_SECRET`.
6. The local app verifies the `X-Razorpay-Signature` header before accepting webhook events.

## 8. Local testing process

1. Start MongoDB locally on `mongodb://localhost:27017/ridego`.
2. Export `ORS_API_KEY`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and optionally `RAZORPAY_WEBHOOK_SECRET`.
3. Run the backend:
   `./mvnw spring-boot:run`
4. Open `http://localhost:9091/index.html`.
5. Register a driver with phone and vehicle details.
6. Register/login as a passenger.
7. Enter pickup, destination, vehicle type, and schedule details if needed.
8. Click Preview Route. Confirm map, distance, ETA, and fare estimate appear.
9. Request the ride.
10. Login as matching vehicle-type driver and accept the ride.
11. Complete the ride.
12. Login as the passenger, click Pay, complete Razorpay Checkout with test credentials.
13. Confirm the ride shows `PAID` and driver earnings include only successful payments.
