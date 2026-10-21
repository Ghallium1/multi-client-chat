---
title: "Building a Multi-Client Chat in Java: Raw TCP Sockets, Virtual Threads and JavaFX"
published: false
tags: java, networking, javafx, concurrency
cover_image: (upload docs/images/screenshot-chat.png)
---

# Building a Multi-Client Chat in Java: Raw TCP Sockets, Virtual Threads and JavaFX

*How I built a group chat from scratch on top of `java.net.Socket`, why I chose virtual threads, and the problems I ran into along the way.*

> 🔗 **Source code:** [GitHub repository](https://github.com/Ghallium1/multi-client-chat) · 🎥 **Demo:** [3-minute video](YOUR_VIDEO_LINK)

![The finished app: server on the left, clients on the right](YOUR_SCREENSHOT_LINK)

---

## The assignment

For my Programming Paradigms course, the brief was short but dense: build a **group chat** where many clients connect to one **central server** over **TCP**, using **JavaFX** for the interface. The requirements included:

- Users log in with a username. Without one, they're stuck in **READ-ONLY MODE**.
- Typing `allUsers` returns the list of connected users **to that user only**.
- `end`, `bye` or a **LEAVE** button disconnects cleanly.
- The server shows a live, color-coded list of users and an activity log.
- **Virtual threads** for concurrency.
- The IP and port come from a **config file at runtime**.
- **Strict separation** between the networking logic and the UI.

That last point shaped the whole design, so let's start there.

---

## 1. Architecture: keep the sockets away from the buttons

I split the project into three Maven modules:

```
chat-common   → protocol + configuration (no UI)
TCPServer     → model / view / controller
TCPClient     → model / view / controller
```

Inside each app, the **model** owns the sockets, threads and chat rules, and it **does not import a single JavaFX class**. It reports what happens through a small listener interface, the classic Observer pattern:

```java
public interface ServerListener {
    void onLog(LogType type, String message);
    void onClientConnected(String username, boolean readOnly);
    void onClientDisconnected(String username);
}
```

The **controller** implements that interface and forwards each event to the **view**. The view is a plain JavaFX class with methods like `appendLog(...)` and `addUser(...)`. It never sees a socket.

The real test of a separation like this is whether you can swap the view. So I wrote a second one:

```java
public final class ConsoleServerView implements ServerListener {
    @Override
    public void onLog(LogType type, String message) {
        System.out.printf("%s  %-10s %s%n", LocalTime.now().format(TIME), type, message);
    }
    // ...
}
```

`java -jar TCPServer.jar --nogui` runs the exact same `ChatServer` with console output. No model code changed, and that was the goal.

![Class diagram](YOUR_CLASS_DIAGRAM_LINK)

---

## 2. Designing a protocol: TCP is a stream, not a message queue

The first trap in socket programming is assuming that one `write()` on one side equals one `read()` on the other. It doesn't. TCP delivers a **stream of bytes**, so the application has to define where one message ends and the next begins. This is called **framing**.

I picked the simplest framing that works: **one packet per line**, with tab-separated fields.

```
CHAT<TAB>14:05<TAB>alice<TAB>Hello everyone!<LF>
```

With `BufferedReader.readLine()`, that gives me exactly one packet per call. But what if a user types a message that contains a newline, or a tab? It would break the framing. The fix is **escaping**: inside a field, `\`, tab, CR and LF become `\\`, `\t`, `\r` and `\n`.

```java
static String escape(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        switch (c) {
            case '\\' -> out.append("\\\\");
            case '\t' -> out.append("\\t");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            default -> out.append(c);
        }
    }
    return out.toString();
}
```

The complete protocol fits in one enum: `HELLO`, `MESSAGE` and `BYE` from client to server, and `WELCOME`, `REJECTED`, `CHAT`, `INFO`, `USERS` and `ERROR` back. Being human-readable pays off: you can debug the server with nothing but `telnet localhost 3000`.

**Why not Java serialization (`ObjectOutputStream`)?** It ties both ends to Java and to identical class versions. It is also a well-known security risk, because deserializing untrusted network data has caused many real vulnerabilities. Plain text has neither problem.

---

## 3. Concurrency: one virtual thread per client

The classic "thread per connection" server is the easiest concurrent code to read:

```java
while ((line = in.readLine()) != null) {   // blocks until the client sends something
    handle(line);
}
```

The problem has always been cost. A platform thread reserves around 1 MB of stack, and a chat server spends most of its time **waiting** on idle clients. Thread pools and NIO selectors solve that, but they turn simple code into callbacks and state machines.

**Virtual threads**, final since Java 21, remove the trade-off. They are scheduled by the JVM, not the OS. When one blocks on a socket read, it is unmounted from its carrier thread, which goes off to run another virtual thread. You keep the simple blocking code, and the cost per client drops to a few hundred bytes.

In my server, that's one line:

```java
clientThreads = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("client-", 1).factory());
```

and the accept loop just hands every socket to it:

```java
while (!socket.isClosed()) {
    listener.onLog(LogType.SYSTEM, "Waiting for client...");
    Socket client = socket.accept();
    clientThreads.execute(new ClientHandler(client, this));
}
```

Naming the threads (`client-1`, `client-2`, …) is a small thing that makes thread dumps and debugger views much easier to read.

---

## 4. The bugs that concurrency invites

Giving every client its own thread means several threads touch shared state at once. I hit three classic hazards:

### a) Interleaved writes
Alice's handler thread broadcasts her message to Bob while Charlie's handler thread broadcasts **his** message to Bob at the same moment. If both write to Bob's socket at once, the bytes can interleave and corrupt both packets. The fix is to serialize writes per connection:

```java
void send(Packet packet) {
    String line = Protocol.encode(packet);
    synchronized (writeLock) {
        out.write(line);
        out.write('\n');
        out.flush();
    }
}
```

### b) Modifying the user list while broadcasting
A broadcast loops over all clients. If someone disconnects mid-loop, you get a `ConcurrentModificationException`, or worse, a silent bug. My `ClientRegistry` is `synchronized`, and broadcasts iterate over a **snapshot copy**, so the lock is never held during slow network writes:

```java
private void broadcast(Packet packet) {
    for (ClientHandler client : registry.snapshot()) {
        client.send(packet);
    }
}
```

### c) Updating JavaFX from the wrong thread
JavaFX, like most UI toolkits, is **single-threaded**: only the *JavaFX Application Thread* may touch the scene graph. But my model's events fire on network threads. Calling `listView.getItems().add(...)` from there leads to random `IllegalStateException`s or corrupted rendering. This is exactly why the controller exists:

```java
@Override
public void onClientConnected(String username, boolean readOnly) {
    Platform.runLater(() -> view.addUser(username, readOnly));
}
```

The model stays thread-agnostic, and the controller marshals every event onto the UI thread.

---

## 5. Making the rules un-bypassable

**READ-ONLY MODE** was an interesting design question. The obvious approach is to disable the text box. But a client is just a program on someone else's machine, and anyone can write their own client and send a `MESSAGE` anyway.

So the rule is enforced in **two places**:

1. **The client** blocks the message locally, for instant feedback in the UI.
2. **The server** checks again and refuses, for actual security:

```java
if (sender.isReadOnly()) {
    sender.send(Packet.of(PacketType.ERROR, READ_ONLY_REFUSAL));
    listener.onLog(LogType.WARNING, "Blocked a message from read-only user " + sender.username());
    return true;
}
```

The same principle applies to everything that matters. The **server** stamps the time (so all clients agree), validates and de-duplicates usernames, and interprets `allUsers`, so the reply can go only to the sender:

```java
if (command.isPresent()) {
    if (command.get().isDisconnect()) return false;          // end / bye
    sender.send(new Packet(PacketType.USERS, registry.usernames()));   // only to the requester
    return true;
}
```

---

## 6. Configuration without recompiling

The IP and port are read from a `.properties` file at startup, with a simple override chain:

1. defaults bundled in the JAR,
2. a `server.properties` next to the JAR (or `-Dchat.config=path`),
3. for the client, command-line arguments: `java -jar TCPClient.jar 192.168.1.20 3000`.

Moving the server to another port is a one-line text edit and a restart.

---

## 7. Packaging JavaFX into a runnable JAR

Since Java 11, JavaFX is no longer part of the JDK, so a plain `java -jar` fails unless JavaFX is bundled. I used the **maven-shade-plugin** to build a single "fat" JAR per app. One trick took me a while to find: **the main class must not extend `javafx.application.Application`**. When it does, the Java launcher insists on finding JavaFX as named modules and refuses to start. The fix is a tiny launcher class:

```java
public final class TCPClient {
    public static void main(String[] args) {
        var config = ClientConfig.resolve(args);   // validate <ServerIPAddress> <PortNumber>
        Application.launch(ClientApp.class, args);
    }
}
```

This also gave me a good place to validate the command-line arguments and print a usage message before any window opens.

---

## 8. Testing a network app without clicking around

Because the models don't depend on JavaFX, I could test them **for real**: real `ServerSocket`, real `Socket`s, port 0 (so the OS picks a free port), and no window. There are 48 tests in total, including:

- broadcast reaches everyone, with the server's timestamp (a fixed `Clock` makes it deterministic),
- `allUsers` goes **only** to the requester,
- a read-only guest is refused,
- duplicate usernames are rejected, ignoring case,
- a client that vanishes without saying `BYE` is cleaned up,
- **200 concurrent clients** join and all receive a broadcast,
- the client model talks end to end to the server model.

```java
@Test
void servesManyConcurrentClients() throws Exception {
    for (int i = 0; i < 200; i++) {
        TestClient client = TestClient.join(port, "user" + i);
        assertEquals(PacketType.WELCOME, client.next().type());
    }
    clients.get(0).say("hello everyone");
    for (TestClient client : clients) {
        assertEquals("hello everyone", client.nextIgnoringInfo().field(2));
    }
}
```

Writing these tests caught a real ordering subtlety: messages a client hasn't read yet are still buffered in its socket after the server closes the connection, so "closed" doesn't mean "nothing left to read".

---

## What I learned

- **TCP gives you a stream; you design the messages.** Framing and escaping are the first things to get right.
- **Virtual threads make simple code scalable.** I wrote straightforward blocking I/O and still served hundreds of clients.
- **Concurrency bugs come from shared state.** Lock the writes, snapshot the lists, and keep UI updates on the UI thread.
- **Never trust the client.** Every rule that matters is enforced on the server.
- **Separation of concerns pays off immediately.** It made the console mode and the headless tests almost free.

### What I'd add next
Private messages (`@bob hi`), TLS with `SSLSocket`, persistent chat history, and a typing indicator.

---

*Thanks for reading! The full source code, UML diagrams and runnable JARs are on [GitHub](https://github.com/Ghallium1/multi-client-chat). Feedback welcome in the comments.*
