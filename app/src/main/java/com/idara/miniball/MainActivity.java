package com.idara.miniball;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.content.pm.ActivityInfo;

import java.net.*;
import java.util.*;
import java.io.*;

public class MainActivity extends Activity {

    private GameView gameView;
    private TextView statusText;
    private TextView scoreText;
    private TextView playersText;
    private EditText ipInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 17, 20));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(10, 6, 10, 6);
        topBar.setBackgroundColor(Color.rgb(25, 28, 34));

        Button hostBtn = makeButton("HOST");
        Button joinBtn = makeButton("JOIN");

        ipInput = new EditText(this);
        ipInput.setHint("Host IP");
        ipInput.setText("192.168.1.");
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.LTGRAY);
        ipInput.setTextSize(14);
        ipInput.setPadding(12, 0, 12, 0);
        ipInput.setBackgroundColor(Color.rgb(43, 47, 56));

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(13);
        statusText.setPadding(12, 0, 12, 0);

        playersText = new TextView(this);
        playersText.setText("Players: 0");
        playersText.setTextColor(Color.WHITE);
        playersText.setTextSize(14);
        playersText.setGravity(Gravity.CENTER);

        scoreText = new TextView(this);
        scoreText.setText("0 - 0");
        scoreText.setTextColor(Color.WHITE);
        scoreText.setTextSize(22);
        scoreText.setGravity(Gravity.CENTER);
        scoreText.setTypeface(Typeface.DEFAULT_BOLD);

        topBar.addView(hostBtn, new LinearLayout.LayoutParams(dp(82), dp(44)));
        topBar.addView(joinBtn, new LinearLayout.LayoutParams(dp(82), dp(44)));
        topBar.addView(ipInput, new LinearLayout.LayoutParams(dp(180), dp(44)));
        topBar.addView(statusText, new LinearLayout.LayoutParams(0, dp(44), 1));
        topBar.addView(playersText, new LinearLayout.LayoutParams(dp(120), dp(44)));
        topBar.addView(scoreText, new LinearLayout.LayoutParams(dp(110), dp(44)));

        gameView = new GameView(this, new GameView.HudListener() {
            @Override
            public void onStatus(final String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(s);
                    }
                });
            }

            @Override
            public void onScore(final int red, final int blue) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scoreText.setText(red + " - " + blue);
                    }
                });
            }

            @Override
            public void onPlayers(final int count) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        playersText.setText("Players: " + count);
                    }
                });
            }
        });

        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));

        root.addView(gameView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        hostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gameView.startHost();
            }
        });

        joinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String ip = ipInput.getText().toString().trim();
                gameView.startClient(ip);
            }
        });
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(50, 120, 255));
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameView != null) {
            gameView.stopNetwork();
        }
    }

    public static class GameView extends View implements Runnable {

        interface HudListener {
            void onStatus(String s);
            void onScore(int red, int blue);
            void onPlayers(int count);
        }

        private static final int PORT = 50005;
        private static final int MAX_PLAYERS = 10;

        private final Activity activity;
        private final HudListener hud;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Object lock = new Object();

        private Thread gameThread;
        private Thread netThread;
        private boolean running = true;

        private DatagramSocket socket;
        private InetAddress remoteAddress;
        private int remotePort = PORT;

        private boolean isHost = false;
        private boolean isClient = false;

        private int localPlayerId = -1;

        private final Player[] players = new Player[MAX_PLAYERS];
        private final HashMap<String, Integer> clientMap = new HashMap<>();

        private Ball ball;

        private int redScore = 0;
        private int blueScore = 0;

        private float fieldW = 1400f;
        private float fieldH = 800f;

        private float touchStartX = -1;
        private float touchStartY = -1;
        private float joyX = 0;
        private float joyY = 0;
        private boolean touching = false;

        private long lastNetSend = 0;
        private long lastHelloSend = 0;

        private final int[] playerColors = new int[] {
                Color.rgb(235, 70, 70),
                Color.rgb(70, 145, 255),
                Color.rgb(255, 95, 95),
                Color.rgb(90, 165, 255),
                Color.rgb(210, 50, 50),
                Color.rgb(45, 120, 245),
                Color.rgb(255, 125, 125),
                Color.rgb(120, 185, 255),
                Color.rgb(180, 35, 35),
                Color.rgb(25, 95, 210)
        };

        public GameView(Activity activity, HudListener hud) {
            super(activity);
            this.activity = activity;
            this.hud = hud;

            setFocusable(true);

            for (int i = 0; i < MAX_PLAYERS; i++) {
                players[i] = new Player(i);
            }

            ball = new Ball(fieldW / 2f, fieldH / 2f, 18);

            resetMatch();

            gameThread = new Thread(this);
            gameThread.start();

            hud.onStatus("HOST في الهاتف الأول، JOIN في باقي الهواتف");
            hud.onScore(redScore, blueScore);
            hud.onPlayers(0);
        }

        public void startHost() {
            stopNetwork();

            synchronized (lock) {
                isHost = true;
                isClient = false;
                localPlayerId = 0;
                clientMap.clear();
                resetMatch();

                players[0].active = true;
                players[0].isLocal = true;
                players[0].team = 0;
                spawnPlayer(players[0]);

                hud.onPlayers(getActivePlayerCount());
            }

            try {
                socket = new DatagramSocket(PORT);
                socket.setReuseAddress(true);

                hud.onStatus("HOST IP: " + getLocalIpAddress() + " | أنت اللاعب الأحمر 1");

                startHostListener();

            } catch (Exception e) {
                hud.onStatus("Host error: " + e.getMessage());
            }
        }

        public void startClient(String hostIp) {
            stopNetwork();

            if (hostIp == null || hostIp.length() < 7) {
                hud.onStatus("اكتب IP الهاتف المضيف");
                return;
            }

            synchronized (lock) {
                isHost = false;
                isClient = true;
                localPlayerId = -1;
                resetLocalClientState();
            }

            try {
                socket = new DatagramSocket();
                socket.setReuseAddress(true);

                remoteAddress = InetAddress.getByName(hostIp);
                remotePort = PORT;

                hud.onStatus("Connecting to " + hostIp + "...");

                sendRaw("HELLO");
                startClientListener();

            } catch (Exception e) {
                hud.onStatus("Join error: " + e.getMessage());
            }
        }

        public void stopNetwork() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception ignored) {}

            socket = null;
            remoteAddress = null;
            remotePort = PORT;

            synchronized (lock) {
                isHost = false;
                isClient = false;
                localPlayerId = -1;
                clientMap.clear();

                for (int i = 0; i < MAX_PLAYERS; i++) {
                    players[i].remoteKey = "";
                    players[i].isLocal = false;
                }
            }
        }

        private void startHostListener() {
            netThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[2048];

                    while (socket != null && !socket.isClosed()) {
                        try {
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            socket.receive(packet);

                            String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                            String key = endpointKey(packet.getAddress(), packet.getPort());

                            if (msg.startsWith("HELLO")) {
                                handleHello(packet.getAddress(), packet.getPort(), key);
                            } else if (msg.startsWith("INPUT|")) {
                                handleInput(msg, key);
                            }

                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            });

            netThread.start();
        }

        private void handleHello(InetAddress address, int port, String key) {
            synchronized (lock) {
                Integer existing = clientMap.get(key);

                if (existing != null) {
                    sendWelcome(existing, address, port);
                    return;
                }

                int newId = findFreePlayerSlot();

                if (newId == -1) {
                    sendRawTo("FULL", address, port);
                    hud.onStatus("غرفة ممتلئة: الحد الحالي 10 لاعبين");
                    return;
                }

                Player pl = players[newId];
                pl.active = true;
                pl.isLocal = false;
                pl.remoteAddress = address;
                pl.remotePort = port;
                pl.remoteKey = key;
                pl.team = newId % 2;

                spawnPlayer(pl);
                clientMap.put(key, newId);

                sendWelcome(newId, address, port);

                hud.onStatus("Player " + (newId + 1) + " connected: " + address.getHostAddress());
                hud.onPlayers(getActivePlayerCount());
                sendStateToAll();
            }
        }

        private void sendWelcome(int id, InetAddress address, int port) {
            String teamName = (id % 2 == 0) ? "RED" : "BLUE";
            String msg = "WELCOME|" + id + "|" + teamName + "|" + fieldW + "|" + fieldH;
            sendRawTo(msg, address, port);
        }

        private int findFreePlayerSlot() {
            for (int i = 1; i < MAX_PLAYERS; i++) {
                if (!players[i].active) {
                    return i;
                }
            }
            return -1;
        }

        private void handleInput(String msg, String key) {
            String[] s = msg.split("\\|");

            if (s.length < 4) return;

            synchronized (lock) {
                Integer idFromMap = clientMap.get(key);
                if (idFromMap == null) return;

                int id = safeInt(s[1], -1);
                if (id != idFromMap) return;
                if (id < 0 || id >= MAX_PLAYERS) return;

                players[id].inputX = safeFloat(s[2], 0);
                players[id].inputY = safeFloat(s[3], 0);
                players[id].lastPacketTime = System.currentTimeMillis();
            }
        }

        private void startClientListener() {
            netThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[8192];

                    while (socket != null && !socket.isClosed()) {
                        try {
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            socket.receive(packet);

                            String msg = new String(packet.getData(), 0, packet.getLength()).trim();

                            if (msg.startsWith("WELCOME|")) {
                                handleWelcome(msg);
                            } else if (msg.startsWith("STATE|")) {
                                applyState(msg);
                            } else if (msg.startsWith("FULL")) {
                                hud.onStatus("الغرفة ممتلئة");
                            }

                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            });

            netThread.start();
        }

        private void handleWelcome(String msg) {
            String[] s = msg.split("\\|");

            if (s.length < 5) return;

            synchronized (lock) {
                localPlayerId = safeInt(s[1], -1);
                String team = s[2];

                if (localPlayerId >= 0 && localPlayerId < MAX_PLAYERS) {
                    players[localPlayerId].isLocal = true;
                }

                hud.onStatus("Connected | أنت اللاعب " + (localPlayerId + 1) + " | Team " + team);
            }
        }

        private void applyState(String msg) {
            String[] main = msg.split("\\|");

            if (main.length < 8) return;

            synchronized (lock) {
                redScore = safeInt(main[1], 0);
                blueScore = safeInt(main[2], 0);

                ball.x = safeFloat(main[3], fieldW / 2f);
                ball.y = safeFloat(main[4], fieldH / 2f);
                ball.vx = safeFloat(main[5], 0);
                ball.vy = safeFloat(main[6], 0);

                for (int i = 0; i < MAX_PLAYERS; i++) {
                    players[i].active = false;
                }

                int count = safeInt(main[7], 0);

                for (int i = 0; i < count; i++) {
                    int index = 8 + i;
                    if (index >= main.length) break;

                    String[] d = main[index].split(",");

                    if (d.length < 7) continue;

                    int id = safeInt(d[0], -1);
                    if (id < 0 || id >= MAX_PLAYERS) continue;

                    Player pl = players[id];

                    pl.active = safeInt(d[1], 0) == 1;
                    pl.team = safeInt(d[2], id % 2);
                    pl.x = safeFloat(d[3], pl.x);
                    pl.y = safeFloat(d[4], pl.y);
                    pl.vx = safeFloat(d[5], 0);
                    pl.vy = safeFloat(d[6], 0);

                    pl.isLocal = id == localPlayerId;
                }

                hud.onScore(redScore, blueScore);
                hud.onPlayers(getActivePlayerCount());
            }
        }

        private void sendStateToAll() {
            if (!isHost || socket == null || socket.isClosed()) return;

            String state = buildStateMessage();

            for (int i = 1; i < MAX_PLAYERS; i++) {
                Player pl = players[i];

                if (pl.active && pl.remoteAddress != null) {
                    sendRawTo(state, pl.remoteAddress, pl.remotePort);
                }
            }
        }

        private String buildStateMessage() {
            StringBuilder sb = new StringBuilder();

            sb.append("STATE|");
            sb.append(redScore).append("|");
            sb.append(blueScore).append("|");
            sb.append(fmt(ball.x)).append("|");
            sb.append(fmt(ball.y)).append("|");
            sb.append(fmt(ball.vx)).append("|");
            sb.append(fmt(ball.vy)).append("|");

            int count = 0;
            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (players[i].active) {
                    count++;
                }
            }

            sb.append(count);

            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player pl = players[i];

                if (!pl.active) continue;

                sb.append("|");
                sb.append(pl.id).append(",");
                sb.append(pl.active ? 1 : 0).append(",");
                sb.append(pl.team).append(",");
                sb.append(fmt(pl.x)).append(",");
                sb.append(fmt(pl.y)).append(",");
                sb.append(fmt(pl.vx)).append(",");
                sb.append(fmt(pl.vy));
            }

            return sb.toString();
        }

        private void sendInput() {
            if (!isClient || socket == null || socket.isClosed()) return;
            if (remoteAddress == null) return;

            if (localPlayerId < 0) {
                long now = System.currentTimeMillis();
                if (now - lastHelloSend > 600) {
                    lastHelloSend = now;
                    sendRaw("HELLO");
                }
                return;
            }

            String msg;

            synchronized (lock) {
                msg = "INPUT|" + localPlayerId + "|" + fmt(joyX) + "|" + fmt(joyY);
            }

            sendRaw(msg);
        }

        private void sendRaw(String msg) {
            try {
                if (socket == null || socket.isClosed()) return;
                if (remoteAddress == null) return;

                byte[] data = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress, remotePort);
                socket.send(packet);

            } catch (Exception ignored) {}
        }

        private void sendRawTo(String msg, InetAddress address, int port) {
            try {
                if (socket == null || socket.isClosed()) return;
                if (address == null) return;

                byte[] data = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                socket.send(packet);

            } catch (Exception ignored) {}
        }

        @Override
        public void run() {
            long last = System.nanoTime();

            while (running) {
                long now = System.nanoTime();
                float dt = (now - last) / 1_000_000_000f;
                last = now;

                if (dt > 0.033f) dt = 0.033f;

                update(dt);
                postInvalidate();

                try {
                    Thread.sleep(16);
                } catch (Exception ignored) {}
            }
        }

        private void update(float dt) {
            synchronized (lock) {
                if (localPlayerId >= 0 && localPlayerId < MAX_PLAYERS) {
                    players[localPlayerId].inputX = joyX;
                    players[localPlayerId].inputY = joyY;
                }

                if (isHost) {
                    updateHostGame(dt);

                    long t = System.currentTimeMillis();
                    if (t - lastNetSend > 20) {
                        lastNetSend = t;
                        sendStateToAll();
                    }

                } else if (isClient) {
                    long t = System.currentTimeMillis();
                    if (t - lastNetSend > 20) {
                        lastNetSend = t;
                        sendInput();
                    }
                }
            }
        }

        private void updateHostGame(float dt) {
            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player pl = players[i];

                if (!pl.active) continue;

                applyPlayerInput(pl, dt);
                movePlayer(pl, dt);
                clampPlayer(pl);
            }

            moveBall(dt);
            handleBallWallsAndGoals();

            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (!players[i].active) continue;

                collidePlayerBall(players[i], ball);

                for (int j = i + 1; j < MAX_PLAYERS; j++) {
                    if (!players[j].active) continue;
                    collidePlayerPlayer(players[i], players[j]);
                }
            }
        }

        private void applyPlayerInput(Player pl, float dt) {
            float accel = 980f;

            pl.vx += pl.inputX * accel * dt;
            pl.vy += pl.inputY * accel * dt;

            float maxSpeed = 305f;

            float sp = (float) Math.sqrt(pl.vx * pl.vx + pl.vy * pl.vy);
            if (sp > maxSpeed) {
                pl.vx = pl.vx / sp * maxSpeed;
                pl.vy = pl.vy / sp * maxSpeed;
            }
        }

        private void movePlayer(Player pl, float dt) {
            pl.x += pl.vx * dt;
            pl.y += pl.vy * dt;

            pl.vx *= 0.91f;
            pl.vy *= 0.91f;
        }

        private void moveBall(float dt) {
            ball.x += ball.vx * dt;
            ball.y += ball.vy * dt;

            ball.vx *= 0.993f;
            ball.vy *= 0.993f;
        }

        private void clampPlayer(Player pl) {
            if (pl.x < pl.r) {
                pl.x = pl.r;
                pl.vx *= -0.35f;
            }

            if (pl.x > fieldW - pl.r) {
                pl.x = fieldW - pl.r;
                pl.vx *= -0.35f;
            }

            if (pl.y < pl.r) {
                pl.y = pl.r;
                pl.vy *= -0.35f;
            }

            if (pl.y > fieldH - pl.r) {
                pl.y = fieldH - pl.r;
                pl.vy *= -0.35f;
            }
        }

        private void handleBallWallsAndGoals() {
            float goalTop = fieldH / 2f - 135f;
            float goalBottom = fieldH / 2f + 135f;

            if (ball.y < ball.r) {
                ball.y = ball.r;
                ball.vy *= -0.78f;
            }

            if (ball.y > fieldH - ball.r) {
                ball.y = fieldH - ball.r;
                ball.vy *= -0.78f;
            }

            if (ball.x < ball.r) {
                if (ball.y > goalTop && ball.y < goalBottom) {
                    blueScore++;
                    hud.onScore(redScore, blueScore);
                    resetRound();
                    return;
                } else {
                    ball.x = ball.r;
                    ball.vx *= -0.78f;
                }
            }

            if (ball.x > fieldW - ball.r) {
                if (ball.y > goalTop && ball.y < goalBottom) {
                    redScore++;
                    hud.onScore(redScore, blueScore);
                    resetRound();
                } else {
                    ball.x = fieldW - ball.r;
                    ball.vx *= -0.78f;
                }
            }
        }

        private void collidePlayerPlayer(Player a, Player b) {
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float min = a.r + b.r;

            if (dist > 0 && dist < min) {
                float nx = dx / dist;
                float ny = dy / dist;
                float overlap = min - dist;

                a.x -= nx * overlap * 0.5f;
                a.y -= ny * overlap * 0.5f;
                b.x += nx * overlap * 0.5f;
                b.y += ny * overlap * 0.5f;

                float tx = -ny;
                float ty = nx;

                float dpTanA = a.vx * tx + a.vy * ty;
                float dp
