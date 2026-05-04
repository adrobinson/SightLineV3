# SightLine — Indoor Navigation for the Visually Impaired

SightLine is an Android proof-of-concept application investigating the feasibility of AI-assisted indoor navigation for blind and visually impaired users. It addresses the indoor navigation problem — the unreliability of GPS inside buildings — by combining QR-code-based localisation, a graph-driven BFS pathfinding algorithm, and Google Gemini multimodal scene descriptions to provide a fully audio-guided navigation experience on a standard smartphone, without GPS or dedicated hardware sensors.

> **Note:** This is a research prototype, not a production safety system.

---

## Features

- **QR localisation** — the camera continuously scans for QR codes attached to physical locations. When one is recognised, the app immediately knows where the user is.
- **Voice-guided pathfinding** — the user speaks a destination; BFS finds the shortest route through a pre-loaded location graph and announces each checkpoint step-by-step via text-to-speech.
- **AI scene description** — tapping *Describe* sends a camera frame to Gemini 2.5 Flash, which returns a concise, non-visual spatial description read aloud to the user.
- **Voice queries** — tapping *Ask* lets the user ask a free-form question about what is in front of them; Gemini answers in the context of any active route.
- **Haptic feedback** — a short vibration confirms every successful camera capture.
- **Accessible UI** — large, high-contrast buttons with icon and label text; all state changes (localisation, route progress, arrival, errors) are announced via TTS.

---

## Architecture

The project follows **MVVM** with a clear separation between camera, graph, LLM, and navigation concerns.

```
sightlinev3/
├── MainActivity.kt                  # Compose UI + camera lifecycle
├── camera/
│   └── QrAnalyzer.kt               # ML Kit barcode analyser
├── graph/
│   ├── Graph.kt                    # Data models (Node, Edge, Graph, GraphDto)
│   ├── GraphRepository.kt          # Loads graph.json from assets
│   ├── GraphService.kt             # BFS pathfinding + node lookup
│   ├── GraphViewModel.kt           # State: current node, route, step index
│   ├── GraphViewModelFactory.kt    # Factory for ViewModel injection
│   ├── PathStep.kt                 # Enriched step data class
│   └── RouteState.kt               # Sealed class: Idle / Active / Reached / Error
├── llm/
│   ├── LlmService.kt               # Interface + Firebase AI model setup
│   └── GeminiLlmService.kt         # Concrete implementation
├── navigation/
│   ├── NavigationViewModel.kt      # HintState flow, calls LlmService
│   └── NavigationViewModelFactory.kt
├── test/
│   ├── GraphServiceTest.kt         # Unit tests for BFS and node lookup
│   └── GraphViewModelTest.kt       # Unit tests for ViewModel state logic
└── ui/theme/                       # Compose colour/typography/theme
```

---

## Setup

> ⚠️ **The app will not build or run without a Firebase project and a `google-services.json` file.** This is not optional — Firebase AI Logic is how the app communicates with Gemini, and the config file is how the SDK knows which project to connect to. Follow the Firebase setup steps below before attempting to build.

### 1 — Firebase Setup (required)

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project (or use an existing one).
2. Inside the project, add an **Android app** with the package name `com.example.sightlinev3`.
3. Enable **Firebase AI Logic** (Vertex AI for Firebase) in the console — see the [Firebase AI Logic getting started guide](https://firebase.google.com/docs/ai-logic/get-started).
4. Download the generated **`google-services.json`** file and replace the existing **`google-services.json`** in the `app/` directory of the project. The build will fail if this file is not replaced.
> The app uses the **Gemini Developer API backend** (free tier). This does not require a billing account for research use, but note the [free-tier data governance caveat](#limitations) regarding prompt data.

### 2 — Other Prerequisites

- Android Studio Hedgehog or later
- Android device / emulator running **API 26+ (Android 8.0 Oreo)** — the minimum supported SDK
- Active internet connection for Gemini AI features (BFS navigation and QR scanning work offline)

### Location Graph

The app reads its map from `app/src/main/assets/graph.json`. The expected schema is:

```json
{
  "nodes": {
    "01": { "id": "01", "name": "Main Entrance", "type": "entrance", "aliases": ["front door", "reception"] },
    "02": { "id": "02", "name": "Hallway",       "type": "hallway",  "aliases": ["corridor"] }
  },
  "edges": [
    { "from": "01", "to": "02", "description": "Walk straight ahead through the glass doors" },
    { "from": "02", "to": "01", "description": "Turn around and exit through the glass doors behind you" }
  ]
}
```

Key points about the schema:

- **`aliases`** — additional spoken names the voice matcher will accept (e.g. "lounge", "front room", and "sitting room" all resolving to the Living Room node). Include natural, conversational phrasings.
- **Bidirectional edges** — each physical connection needs two edge entries (one per direction) so that Gemini receives the correct directional description depending on which way the user is travelling.
- **`description`** on an edge — the visual cue injected into Gemini's prompt when the user arrives at the destination node of that edge. Write these from the perspective of someone looking in the direction of travel.

Each physical location must have a QR code printed and fixed to the wall whose raw value matches the node's `id`. QR codes are reliably detected at up to ~3 metres in normal indoor lighting.

### Build & Run

```bash
# Clone the repo
git clone <repo-url>
cd sightlinev3

# Open in Android Studio and sync Gradle, then run on device
```

Permissions requested at runtime: `CAMERA`.

---

## Testing

A suite of **37 JUnit unit tests** was written and all pass. Because `GraphService` and `GraphViewModel` have no Android framework dependencies, these run as standard JVM tests without a device or emulator.

Run all tests with:

```bash
./gradlew test
```

Automated UI or integration testing (Espresso / Jetpack Compose test APIs) was not implemented, as mocking physical QR codes, a live camera feed, and cloud AI responses in an automated environment was not feasible within project scope.

---

## Key Dependencies

| Library | Purpose |
|---|---|
| Jetpack Compose | Declarative UI |
| CameraX | Camera preview, image capture, image analysis |
| Google ML Kit — Barcode Scanning | QR code detection |
| Firebase AI (Gemini 2.5 Flash) | Multimodal scene descriptions |
| Kotlin Coroutines / Flow | Async state management |
| `kotlinx.serialization` | JSON deserialisation of the graph file |
| Android TextToSpeech | Audio output |
| Android SpeechRecognizer | Voice input (STT) |
