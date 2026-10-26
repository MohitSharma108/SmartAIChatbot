# SmartAIChatbot

An Android Java/XML chat app using MVVM, Room, and Retrofit. The app talks to a
local Node.js/Express backend, which calls the OpenAI Responses API. The OpenAI
key stays in `backend/.env` and is never bundled into the Android app.

## Backend

Requirements: Node.js 18 or newer.

```bash
cd backend
cp .env.example .env
# Set OPENAI_API_KEY and OPENAI_MODEL in .env
npm install
npm start
```

Useful checks:

```bash
node --check server.js
node --check list-models.js
curl http://localhost:3000/health
```

`npm run models` lists models available to the configured OpenAI project. Do
not expose this development server directly to the public internet without
adding authentication and HTTPS.

## Android app

Open this folder in Android Studio and sync the Gradle project. The project
uses Android Gradle Plugin 8.13.2, compile SDK 34, and Java 8 source
compatibility. Gradle itself should run with a JDK 17-or-newer installation.
From a terminal, use the included Gradle wrapper:

```bash
./gradlew :app:assembleDebug
```

Start the backend before sending a message.

The default emulator URL is `http://10.0.2.2:3000/` (see
`app/src/main/java/com/example/smartaichatbot/network/ApiClient.java`). A
physical device needs the computer's LAN address in `BASE_URL` and the matching
development host in `res/xml/network_security_config.xml`. Production traffic
must use HTTPS.

The archive does not include an Android SDK, Java runtime, Gradle wrapper, or
OpenAI credentials, so an Android build requires a local Android Studio/SDK
installation and a configured `backend/.env`.

## Online website deployment

The repository includes a browser chat website in `website/` and a Render
Blueprint in `render.yaml`. Create a Render Web Service from this repository,
or deploy the Blueprint. Use the repository root as the service root, then set
these environment variables in Render:

```text
OPENAI_API_KEY=your_real_key
OPENAI_MODEL=your_supported_model
```

The service runs `cd backend && npm start` and serves the website and `/chat`
API from the same HTTPS URL. Never commit `backend/.env` or place the API key
in browser JavaScript.
