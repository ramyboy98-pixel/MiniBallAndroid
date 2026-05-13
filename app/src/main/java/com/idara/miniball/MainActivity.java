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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 10, 14));

        /*
         * مهم:
         * في النسخة السابقة كان GameView يُنشأ قبل TextViews،
         * وهذا قد يسبب خروج التطبيق مباشرة عند التشغيل.
         * لذلك ننشئ عناصر الواجهة أولاً ثم ننشئ GameView.
         */

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        topBar.setBackgroundColor(Color.argb(150, 16, 20, 28));

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
        ipInput.setPadding(dp(12), 0, dp(12), 0);
        ipInput.setBackgroundColor(Color.argb(190, 38, 44, 58));

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(13);
        statusText.setPadding(dp(12), 0, dp(12), 0);
        statusText.setSingleLine(true);

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
                        if (statusText != null) {
                            statusText.setText(s);
                        }
                    }
                });
            }

            @Override
            public void onScore(final int red, final int blue) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (scoreText != null) {
                            scoreText.setText(red + " - " + blue);
                        }
                    }
                });
            }

            @Override
            public void onPlayers(final int count) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (playersText != null) {
                            playersText.setText("Players: " + count);
                        }
                    }
                });
            }
        });

        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topBar, topParams);

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
            gameView.stopGameLoop();
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

        private float fieldW = 1600f;
        private float fieldH = 900f;

        private float touchStartX = -1;
        private float touchStartY = -1;
        private float joyX = 0;
        private float joyY = 0;
        private boolean touching = false;

        private boolean passPressed = false;
        private boolean shootPressed = false;

        private RectF passButton = new RectF();
        private RectF shootButton = new RectF();

        private long lastNetSend = 0;
        private long lastHelloSend = 0;

        /*
         * الحيازة والدفاع:
         * carrierId = اللاعب الذي يملك الكرة حالياً.
         * إذا اصطدم به لاعب من الفريق الآخر بقوة كافية، تُنزع الكرة منه.
         */
        private int carrierId = -1;
        private long lastTackleTime = 0;

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

        public void stopGameLoop() {
            running = false;
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

                if (s.length >= 6) {
                    players[id].passPressed = safeInt(s[4], 0) == 1;
                    players[id].shootPressed = safeInt(s[5], 0) == 1;
                }

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

            if (main.length < 9) return;

            synchronized (lock) {
                redScore = safeInt(main[1], 0);
                blueScore = safeInt(main[2], 0);

                ball.x = safeFloat(main[3], fieldW / 2f);
                ball.y = safeFloat(main[4], fieldH / 2f);
                ball.vx = safeFloat(main[5], 0);
                ball.vy = safeFloat(main[6], 0);

                carrierId = safeInt(main[7], -1);

                for (int i = 0; i < MAX_PLAYERS; i++) {
                    players[i].active = false;
                }

                int count = safeInt(main[8], 0);

                for (int i = 0; i < count; i++) {
                    int index = 9 + i;
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
            sb.append(carrierId).append("|");

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
                int pass = passPressed ? 1 : 0;
                int shoot = shootPressed ? 1 : 0;

                msg = "INPUT|" + localPlayerId + "|" + fmt(joyX) + "|" + fmt(joyY) + "|" + pass + "|" + shoot;

                passPressed = false;
                shootPressed = false;
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

                    if (passPressed) {
                        players[localPlayerId].passPressed = true;
                        passPressed = false;
                    }

                    if (shootPressed) {
                        players[localPlayerId].shootPressed = true;
                        shootPressed = false;
                    }
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

            /*
             * الدفاع أولاً:
             * إذا لاعب من الفريق الخصم اصطدم بحامل الكرة بقوة،
             * نزع الكرة يحدث قبل تنفيذ التحكم بالكرة.
             */
            applyDefenseTackles();

            applyBallControlAndActions();

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

            for (int i = 0; i < MAX_PLAYERS; i++) {
                players[i].passPressed = false;
                players[i].shootPressed = false;
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
            float goalTop = fieldH / 2f - 150f;
            float goalBottom = fieldH / 2f + 150f;

            if (ball.y < ball.r) {
                ball.y = ball.r;
                ball.vy *= -0.78f;
                carrierId = -1;
            }

            if (ball.y > fieldH - ball.r) {
                ball.y = fieldH - ball.r;
                ball.vy *= -0.78f;
                carrierId = -1;
            }

            if (ball.x < ball.r) {
                if (ball.y > goalTop && ball.y < goalBottom) {
                    blueScore++;
                    carrierId = -1;
                    hud.onScore(redScore, blueScore);
                    resetRound();
                    return;
                } else {
                    ball.x = ball.r;
                    ball.vx *= -0.78f;
                    carrierId = -1;
                }
            }

            if (ball.x > fieldW - ball.r) {
                if (ball.y > goalTop && ball.y < goalBottom) {
                    redScore++;
                    carrierId = -1;
                    hud.onScore(redScore, blueScore);
                    resetRound();
                } else {
                    ball.x = fieldW - ball.r;
                    ball.vx *= -0.78f;
                    carrierId = -1;
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
                float dpTanB = b.vx * tx + b.vy * ty;

                float dpNormA = a.vx * nx + a.vy * ny;
                float dpNormB = b.vx * nx + b.vy * ny;

                a.vx = tx * dpTanA + nx * dpNormB * 0.85f;
                a.vy = ty * dpTanA + ny * dpNormB * 0.85f;

                b.vx = tx * dpTanB + nx * dpNormA * 0.85f;
                b.vy = ty * dpTanB + ny * dpNormA * 0.85f;
            }
        }

        private void collidePlayerBall(Player pl, Ball b) {
            float dx = b.x - pl.x;
            float dy = b.y - pl.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float min = pl.r + b.r;

            if (dist > 0 && dist < min) {
                float nx = dx / dist;
                float ny = dy / dist;
                float overlap = min - dist;

                b.x += nx * overlap;
                b.y += ny * overlap;

                float relVx = b.vx - pl.vx;
                float relVy = b.vy - pl.vy;
                float sep = relVx * nx + relVy * ny;

                if (sep < 0) {
                    b.vx -= 1.75f * sep * nx;
                    b.vy -= 1.75f * sep * ny;
                }

                b.vx += pl.vx * 0.42f;
                b.vy += pl.vy * 0.42f;

                float maxBallSpeed = 650f;

                float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                if (sp > maxBallSpeed) {
                    b.vx = b.vx / sp * maxBallSpeed;
                    b.vy = b.vy / sp * maxBallSpeed;
                }
            }
        }

        private void applyDefenseTackles() {
            if (carrierId < 0 || carrierId >= MAX_PLAYERS) return;

            Player carrier = players[carrierId];
            if (!carrier.active) {
                carrierId = -1;
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastTackleTime < 220) return;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player defender = players[i];

                if (!defender.active) continue;
                if (defender.id == carrier.id) continue;
                if (defender.team == carrier.team) continue;

                float dx = defender.x - carrier.x;
                float dy = defender.y - carrier.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float tackleDistance = defender.r + carrier.r + 7f;

                if (dist < tackleDistance) {
                    float defenderSpeed = (float) Math.sqrt(defender.vx * defender.vx + defender.vy * defender.vy);
                    float carrierSpeed = (float) Math.sqrt(carrier.vx * carrier.vx + carrier.vy * carrier.vy);

                    /*
                     * نزع الكرة إذا المدافع دخل بقوة أو إذا الاصطدام واضح.
                     */
                    if (defenderSpeed > 80f || defenderSpeed > carrierSpeed * 0.65f) {
                        float nx;
                        float ny;

                        if (dist > 1f) {
                            nx = dx / dist;
                            ny = dy / dist;
                        } else {
                            nx = defender.team == 0 ? 1f : -1f;
                            ny = 0f;
                        }

                        carrierId = defender.id;
                        lastTackleTime = now;

                        ball.x = defender.x - nx * (defender.r + ball.r + 4f);
                        ball.y = defender.y - ny * (defender.r + ball.r + 4f);

                        ball.vx = defender.vx * 0.85f - nx * 180f;
                        ball.vy = defender.vy * 0.85f - ny * 180f;

                        hud.onStatus("TACKLE! Player " + (defender.id + 1) + " نزع الكرة");
                        return;
                    }
                }
            }
        }

        private void applyBallControlAndActions() {
            Player controller;

            if (carrierId >= 0 && carrierId < MAX_PLAYERS && players[carrierId].active) {
                controller = players[carrierId];

                float dx = ball.x - controller.x;
                float dy = ball.y - controller.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist > 95f) {
                    carrierId = -1;
                    controller = getClosestPlayerToBall(60f);
                }
            } else {
                controller = getClosestPlayerToBall(60f);
                if (controller != null) {
                    carrierId = controller.id;
                }
            }

            if (controller == null) return;

            float inputLen = (float) Math.sqrt(controller.inputX * controller.inputX + controller.inputY * controller.inputY);

            float dirX;
            float dirY;

            if (inputLen > 0.15f) {
                dirX = controller.inputX / inputLen;
                dirY = controller.inputY / inputLen;
            } else {
                float vx = controller.vx;
                float vy = controller.vy;
                float vLen = (float) Math.sqrt(vx * vx + vy * vy);

                if (vLen > 5f) {
                    dirX = vx / vLen;
                    dirY = vy / vLen;
                } else {
                    dirX = controller.team == 0 ? 1f : -1f;
                    dirY = 0f;
                }
            }

            if (controller.shootPressed) {
                shootToGoal(controller);
                carrierId = -1;
                return;
            }

            if (controller.passPressed) {
                passToTeammate(controller);
                carrierId = -1;
                return;
            }

            if (inputLen > 0.08f) {
                float targetX = controller.x + dirX * (controller.r + ball.r + 5f);
                float targetY = controller.y + dirY * (controller.r + ball.r + 5f);

                ball.x += (targetX - ball.x) * 0.24f;
                ball.y += (targetY - ball.y) * 0.24f;

                ball.vx = controller.vx + dirX * 85f;
                ball.vy = controller.vy + dirY * 85f;
            }
        }

        private Player getClosestPlayerToBall(float maxDistance) {
            Player best = null;
            float bestDist = maxDistance;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player pl = players[i];

                if (!pl.active) continue;

                float dx = ball.x - pl.x;
                float dy = ball.y - pl.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < bestDist) {
                    bestDist = dist;
                    best = pl;
                }
            }

            return best;
        }

        private void passToTeammate(Player from) {
            Player target = null;
            float bestDist = 999999f;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player pl = players[i];

                if (!pl.active) continue;
                if (pl.id == from.id) continue;
                if (pl.team != from.team) continue;

                float dx = pl.x - from.x;
                float dy = pl.y - from.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < bestDist) {
                    bestDist = dist;
                    target = pl;
                }
            }

            if (target == null) {
                shootForward(from, 470f);
                return;
            }

            float dx = target.x - ball.x;
            float dy = target.y - ball.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len < 1f) return;

            ball.vx = dx / len * 520f;
            ball.vy = dy / len * 520f;
        }

        private void shootToGoal(Player from) {
            float goalX = from.team == 0 ? fieldW + 80f : -80f;
            float goalY = fieldH / 2f;

            float dx = goalX - ball.x;
            float dy = goalY - ball.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len < 1f) return;

            ball.vx = dx / len * 760f;
            ball.vy = dy / len * 760f;
        }

        private void shootForward(Player from, float power) {
            float inputLen = (float) Math.sqrt(from.inputX * from.inputX + from.inputY * from.inputY);

            float dirX;
            float dirY;

            if (inputLen > 0.15f) {
                dirX = from.inputX / inputLen;
                dirY = from.inputY / inputLen;
            } else {
                dirX = from.team == 0 ? 1f : -1f;
                dirY = 0f;
            }

            ball.vx = dirX * power;
            ball.vy = dirY * power;
        }

        private void resetLocalClientState() {
            carrierId = -1;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                players[i].active = false;
                players[i].isLocal = false;
                players[i].inputX = 0;
                players[i].inputY = 0;
                players[i].passPressed = false;
                players[i].shootPressed = false;
            }

            ball.x = fieldW / 2f;
            ball.y = fieldH / 2f;
            ball.vx = 0;
            ball.vy = 0;
        }

        private void resetMatch() {
            redScore = 0;
            blueScore = 0;
            carrierId = -1;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                players[i].active = false;
                players[i].isLocal = false;
                players[i].inputX = 0;
                players[i].inputY = 0;
                players[i].passPressed = false;
                players[i].shootPressed = false;
                players[i].team = i % 2;
            }

            resetRound();
            hud.onScore(redScore, blueScore);
        }

        private void resetRound() {
            carrierId = -1;

            ball.x = fieldW / 2f;
            ball.y = fieldH / 2f;
            ball.vx = 0;
            ball.vy = 0;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (players[i].active) {
                    spawnPlayer(players[i]);
                }
            }
        }

        private void spawnPlayer(Player pl) {
            int teamIndex = pl.id / 2;
            float gap = 92f;

            if (pl.team == 0) {
                pl.x = 240f + teamIndex * 58f;
                pl.y = fieldH / 2f - 185f + teamIndex * gap;
            } else {
                pl.x = fieldW - 240f - teamIndex * 58f;
                pl.y = fieldH / 2f - 185f + teamIndex * gap;
            }

            if (pl.y < 90f) pl.y = 90f;
            if (pl.y > fieldH - 90f) pl.y = fieldH - 90f;

            pl.vx = 0;
            pl.vy = 0;
            pl.inputX = 0;
            pl.inputY = 0;
            pl.passPressed = false;
            pl.shootPressed = false;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float screenW = getWidth();
            float screenH = getHeight();

            float scale = Math.max(screenW / fieldW, screenH / fieldH);
            float ox = (screenW - fieldW * scale) / 2f;
            float oy = (screenH - fieldH * scale) / 2f;

            c.drawColor(Color.rgb(10, 12, 15));

            c.save();
            c.translate(ox, oy);
            c.scale(scale, scale);

            drawField(c);
            drawPlayersAndBall(c);

            c.restore();

            drawJoystick(c);
            drawActionButtons(c);
            drawLocalPlayerInfo(c);
        }

        private void drawField(Canvas c) {
            p.setStyle(Paint.Style.FILL);

            float stripeW = fieldW / 10f;
            for (int i = 0; i < 10; i++) {
                if (i % 2 == 0) {
                    p.setColor(Color.rgb(64, 165, 76));
                } else {
                    p.setColor(Color.rgb(82, 190, 90));
                }
                c.drawRect(i * stripeW, 0, (i + 1) * stripeW, fieldH, p);
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setColor(Color.rgb(245, 245, 245));
            c.drawRect(18, 18, fieldW - 18, fieldH - 18, p);

            p.setStrokeWidth(5);
            p.setColor(Color.argb(220, 255, 255, 255));
            c.drawLine(fieldW / 2f, 18, fieldW / 2f, fieldH - 18, p);
            c.drawCircle(fieldW / 2f, fieldH / 2f, 115, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(fieldW / 2f, fieldH / 2f, 7, p);

            float goalTop = fieldH / 2f - 150f;
            float goalBottom = fieldH / 2f + 150f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(36, 44, 54));
            c.drawRect(-30, goalTop, 28, goalBottom, p);
            c.drawRect(fieldW - 28, goalTop, fieldW + 30, goalBottom, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            p.setColor(Color.WHITE);
            c.drawLine(0, goalTop, 0, goalBottom, p);
            c.drawLine(fieldW, goalTop, fieldW, goalBottom, p);

            p.setStrokeWidth(4);
            p.setColor(Color.argb(200, 255, 255, 255));
            c.drawRect(18, fieldH / 2f - 210f, 210, fieldH / 2f + 210f, p);
            c.drawRect(fieldW - 210, fieldH / 2f - 210f, fieldW - 18, fieldH / 2f + 210f, p);
        }

        private void drawPlayersAndBall(Canvas c) {
            synchronized (lock) {
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    if (players[i].active) {
                        drawPlayer(c, players[i]);
                    }
                }

                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                c.drawCircle(ball.x, ball.y, ball.r, p);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                p.setColor(Color.rgb(35, 35, 35));
                c.drawCircle(ball.x, ball.y, ball.r, p);
            }
        }

        private void drawPlayer(Canvas c, Player pl) {
            int color = playerColors[pl.id % playerColors.length];

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(75, 0, 0, 0));
            c.drawCircle(pl.x + 5, pl.y + 6, pl.r + 3, p);

            p.setColor(color);
            c.drawCircle(pl.x, pl.y, pl.r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(pl.isLocal ? 6 : 3);

            if (pl.id == carrierId) {
                p.setColor(Color.rgb(255, 230, 35));
            } else {
                p.setColor(pl.isLocal ? Color.YELLOW : Color.WHITE);
            }

            c.drawCircle(pl.x, pl.y, pl.r, p);

            if (pl.id == carrierId) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                p.setColor(Color.argb(220, 255, 230, 35));
                c.drawCircle(pl.x, pl.y, pl.r + 9, p);
            }

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(24);
            p.setColor(Color.WHITE);
            c.drawText(String.valueOf(pl.id + 1), pl.x, pl.y + 8, p);
        }

        private void drawJoystick(Canvas c) {
            if (!touching) return;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(65, 255, 255, 255));
            c.drawCircle(touchStartX, touchStartY, 62, p);

            p.setColor(Color.argb(165, 255, 255, 255));
            c.drawCircle(touchStartX + joyX * 48, touchStartY + joyY * 48, 25, p);
        }

        private void drawActionButtons(Canvas c) {
            float w = getWidth();
            float h = getHeight();

            float size = 92f;
            float gap = 18f;

            shootButton.set(w - size - 28f, h - size - 34f, w - 28f, h - 34f);
            passButton.set(w - size * 2f - gap - 28f, h - size - 34f, w - size - gap - 28f, h - 34f);

            p.setStyle(Paint.Style.FILL);

            p.setColor(Color.argb(185, 255, 172, 32));
            c.drawOval(shootButton, p);

            p.setColor(Color.argb(185, 45, 145, 255));
            c.drawOval(passButton, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(Color.WHITE);
            c.drawOval(shootButton, p);
            c.drawOval(passButton, p);

            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(21);
            p.setColor(Color.WHITE);

            c.drawText("SHOOT", shootButton.centerX(), shootButton.centerY() + 8, p);
            c.drawText("PASS", passButton.centerX(), passButton.centerY() + 8, p);
        }

        private void drawLocalPlayerInfo(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(26);
            p.setColor(Color.argb(230, 255, 255, 255));

            String text;

            if (isHost) {
                text = "You: Player 1 / RED / HOST";
            } else if (isClient && localPlayerId >= 0) {
                String team = (localPlayerId % 2 == 0) ? "RED" : "BLUE";
                text = "You: Player " + (localPlayerId + 1) + " / " + team;
            } else if (isClient) {
                text = "Connecting...";
            } else {
                text = "Start HOST or JOIN";
            }

            c.drawText(text, 22, getHeight() - 24, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();

            float x = e.getX();
            float y = e.getY();

            if (action == MotionEvent.ACTION_DOWN) {
                if (shootButton.contains(x, y)) {
                    shootPressed = true;
                    return true;
                }

                if (passButton.contains(x, y)) {
                    passPressed = true;
                    return true;
                }

                touching = true;
                touchStartX = x;
                touchStartY = y;
                joyX = 0;
                joyY = 0;
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                if (!touching) return true;

                float dx = x - touchStartX;
                float dy = y - touchStartY;

                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float max = 78f;

                if (len > max) {
                    dx = dx / len * max;
                    dy = dy / len * max;
                }

                joyX = dx / max;
                joyY = dy / max;

                return true;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touching = false;
                joyX = 0;
                joyY = 0;
                return true;
            }

            return true;
        }

        private int getActivePlayerCount() {
            int count = 0;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (players[i].active) {
                    count++;
                }
            }

            return count;
        }

        private String endpointKey(InetAddress address, int port) {
            return address.getHostAddress() + ":" + port;
        }

        private String fmt(float v) {
            return String.format(Locale.US, "%.2f", v);
        }

        private int safeInt(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                return fallback;
            }
        }

        private float safeFloat(String value, float fallback) {
            try {
                return Float.parseFloat(value);
            } catch (Exception e) {
                return fallback;
            }
        }

        private String getLocalIpAddress() {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements()) {
                    NetworkInterface intf = interfaces.nextElement();
                    Enumeration<InetAddress> addresses = intf.getInetAddresses();

                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();

                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            } catch (Exception ignored) {}

            return "Unknown";
        }

        private static class Player {
            int id;
            int team;

            boolean active = false;
            boolean isLocal = false;

            float x = 0;
            float y = 0;
            float vx = 0;
            float vy = 0;
            float r = 27;

            float inputX = 0;
            float inputY = 0;

            boolean passPressed = false;
            boolean shootPressed = false;

            InetAddress remoteAddress;
            int remotePort;
            String remoteKey = "";

            long lastPacketTime = 0;

            Player(int id) {
                this.id = id;
                this.team = id % 2;
            }
        }

        private static class Ball {
            float x;
            float y;
            float vx;
            float vy;
            float r;

            Ball(float x, float y, float r) {
                this.x = x;
                this.y = y;
                this.r = r;
            }
        }
    }
}
