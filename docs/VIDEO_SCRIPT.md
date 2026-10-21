# Demo Video — Script & Recording Guide (3:00)

The assignment asks for **a 3-minute video: a walkthrough of the source code plus a live demonstration**. The plan below fits both into 3:00, with every second assigned. The narration is about 430 words, which matches a calm speaking pace of about 145 words per minute.

---

## Part 1 — Prepare (15 minutes, once)

### Software
| Tool | Why | Free option |
|---|---|---|
| Screen recorder | Capture screen + microphone | **OBS Studio** (best), or Windows **Xbox Game Bar** (`Win + Alt + R`, records one window only), or **Clipchamp** (built into Windows 11) |
| Video editor | Cut mistakes, speed up waits, add titles | **Clipchamp** (Windows 11), **DaVinci Resolve**, **Shotcut** |
| Microphone | Clear voice matters more than video quality | Any headset mic beats a laptop mic |

### OBS settings (if you use OBS)
1. **Settings → Video**: Base and Output resolution **1920×1080**, FPS **30**.
2. **Settings → Output**: Recording format **mp4**, encoder **Hardware (NVENC/QSV/AMF)** if available, otherwise x264.
3. **Settings → Audio**: Mic/Aux = your microphone. In the mixer, right-click the mic → **Filters → Noise Suppression (RNNoise)**.
4. **Sources**: add **Display Capture** (whole screen). That's all you need.
5. Test 10 seconds and play it back to check that sound and text are readable.

### Make the screen readable on video
- IntelliJ: **View → Appearance → Enter Presentation Mode** (big font), or *Settings → Editor → Font* → size **18–20**.
- Close notifications: Windows **Focus Assist / Do Not Disturb** on, and quit Discord, mail and similar apps.
- Terminal font size 18+.
- Hide desktop clutter and use a plain wallpaper.

### Prepare the project before recording
```bash
mvnw.cmd clean package          # builds both JARs and runs the tests
```
- Open **3 terminals** (Windows Terminal tabs or side-by-side windows), each `cd`'d into the project folder.
- Pre-type (but do not run) the commands so you only press Enter on camera:
  - Terminal 1: `java -jar TCPServer/target/TCPServer.jar`
  - Terminals 2 to 4: `java -jar TCPClient/target/TCPClient.jar localhost 3000`
- In IntelliJ, open these tabs **in this order** (left to right) so you can click through them:
  1. `docs/images/class-diagram.png`
  2. `ChatServer.java`
  3. `ClientHandler.java`
  4. `ChatClient.java`
  5. `ServerController.java`
  6. `server.properties`
- **Window layout for the demo:** server window on the left half, then three client windows stacked/tiled on the right. Practice it once. *(Win + ← / Win + → snaps windows.)*

---

## Part 2 — The script

Format: **⏱ time — 🎬 what's on screen — 🎙 what you say.**
Speak naturally; you don't have to read word for word. Hitting the **topics** in each block matters more than exact wording.

---

### ⏱ 0:00 – 0:12 · Intro
🎬 GitHub repo page (README with screenshots), or the server and client windows side by side.

🎙 *"Hi, I'm [your name]. This is my multi-client chat application, built with Java TCP sockets, virtual threads and JavaFX. I'll walk through the code first, then show it running live."*

---

### ⏱ 0:12 – 0:32 · Architecture
🎬 `class-diagram.png`. Point the mouse at the three packages: model, view, controller.

🎙 *"The project is a Maven multi-module build: a shared protocol module, the server, and the client. Both apps follow Model-View-Controller. The model handles sockets and threads and has zero JavaFX imports. It reports events through a listener interface, and the controller forwards them to the JavaFX view. To prove the separation, the same server model can also run without a window."*

---

### ⏱ 0:32 – 0:55 · Server: virtual threads
🎬 `ChatServer.java`. Scroll to `start()` (line ~76, the `newThreadPerTaskExecutor` line), then to `acceptLoop` (line ~119).

🎙 *"In ChatServer.start, I bind a ServerSocket and create an executor that starts one virtual thread per client. The accept loop logs 'Waiting for client', accepts a connection, and hands it to a ClientHandler on its own virtual thread. Virtual threads let me write simple blocking code that still scales to thousands of clients."*

---

### ⏱ 0:55 – 1:15 · Server: chat rules
🎬 Same file: `join()` (line ~144), then `handleMessage()` (line ~175).

🎙 *"When a client joins with an empty username, it becomes a guest in read-only mode. In handleMessage, the allUsers command is answered only to the sender. A read-only user gets an error, and a normal message is stamped with the sender's name and the time, then broadcast to everyone."*

---

### ⏱ 1:15 – 1:35 · Protocol + client + controller
🎬 Quick taps: `ClientHandler.send` (the `synchronized` block), then `ChatClient.send` (line ~88), then `ServerController` (`Platform.runLater`, line ~59).

🎙 *"Each packet is one line of text with tab-separated fields, escaped so multi-line messages are safe. Writes are synchronized so two broadcasts never mix bytes. On the client, end and bye disconnect and send BYE to the server. And because network events arrive on background threads, the controller uses Platform.runLater to update JavaFX safely."*

---

### ⏱ 1:35 – 1:42 · Configuration
🎬 `server.properties`.

🎙 *"The IP and port are read from a properties file at runtime, so I can redeploy without recompiling."*

---

### ⏱ 1:42 – 2:45 · Live demo (the most important part)
🎬 Terminals + app windows. Do these actions **in this order**:

| ⏱ | Action | 🎙 Say |
|---|---|---|
| 1:42 | Terminal 1 → Enter (start server) | *"I start the server. The log shows 'Server started' and 'Waiting for client'."* |
| 1:48 | Terminal 2 → Enter → type **alice** → Join | *"A client starts with the server IP and port as arguments. I log in as Alice. The status shows Online with a green circle, and the server says Welcome Alice."* |
| 1:58 | Terminal 3 → Enter → **bob** → Join | *"Bob joins, and each user gets a random color in the server's list."* |
| 2:04 | Terminal 4 → Enter → leave username **empty** → Join | *"This third user leaves the name empty, so they're in read-only mode."* |
| 2:10 | Alice types `Hi Bob!` + **Enter**; Bob clicks **SEND** with `Hey Alice` | *"Messages go to everyone instantly, with the sender and time. Enter or the SEND button both work."* |
| 2:20 | Guest types `hello` + Enter | *"The guest tries to send, and it's blocked."* |
| 2:25 | Bob types `allUsers` | *"allUsers returns the active users, and only Bob sees the list."* |
| 2:32 | Alice types `bye` | *"Alice types bye. Her status turns Offline and red, and the server and Bob are notified."* |
| 2:40 | Bob clicks **LEAVE** | *"The LEAVE button does the same."* |

---

### ⏱ 2:45 – 3:00 · Tests + outro
🎬 Terminal running `mvnw.cmd test` (pre-recorded and sped up 4× in the editor) ending on **BUILD SUCCESS**, then the GitHub page.

🎙 *"Finally, 48 automated tests run both models over real sockets with no UI, including 200 concurrent clients. The code, UML diagrams and JARs are on GitHub. Thanks for watching!"*

---

## Part 3 — Recording tips

1. **Record in segments**, not one take: (A) code walkthrough, (B) live demo, (C) tests. Then join them in the editor. Re-recording a 40-second segment is much easier than redoing 3 minutes.
2. **Rehearse twice with a timer.** If you are over time, cut words, not features. The demo is what gets graded.
3. **Move the mouse slowly** and point at what you are talking about. Use **Ctrl + G** in IntelliJ to jump to a line.
4. **Speed up dead time** in the editor (builds, typing) to 2–4×.
5. **Add 2–3 text captions** in the editor at key moments: *"Virtual thread per client"*, *"READ-ONLY MODE"*, *"allUsers → only the requester"*. Graders skim, and captions make each feature obvious.
6. **Audio:** quiet room, mic 10–15 cm from your mouth. Do a test and listen with headphones.
7. **Export:** 1080p, MP4 (H.264), 30 fps.
8. **Upload:** YouTube as **Unlisted** (or Google Drive with "anyone with the link"), then put the link in the README's *Deliverables* section. Open the link in a private window to check that it works.

## Part 4 — Final checklist before you submit

- [ ] Under or exactly 3:00
- [ ] Code walkthrough shows: virtual threads, broadcast, allUsers, read-only, Platform.runLater, properties file
- [ ] Demo shows: server log (Started / Waiting / Welcome), random user colors, Online + circle, Enter and SEND, read-only blocked, allUsers, bye, LEAVE
- [ ] Client started with `<ServerIPAddress> <PortNumber>` visible in the terminal
- [ ] Voice is clear; no notifications popped up
- [ ] Link works in a private browser window
