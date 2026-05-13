package com.idara.miniball;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.content.pm.ActivityInfo;

import java.net.*;
import java.io.*;
import java.util.Locale;

public class MainActivity extends Activity {

    private GameView gameView;
    private TextView statusText;
    private TextView scoreText;
    private EditText ipInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 22, 26));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(12, 8, 12, 8);
        topBar.setBackgroundColor(Color.rgb(28, 31, 38));

        Button hostBtn = makeButton("HOST");
        Button joinBtn = makeButton("JOIN");

        ipInput = new EditText(this);
        ipInput.setHint("Host IP");
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.LTGRAY);
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ipInput.setText("192.168.1.");
        ipInput.setBackgroundColor(Color.rgb(45, 49, 58));
        ipInput.setPadding(12, 0, 12, 0);

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setPadding(14, 0, 14, 0);

        scoreText = new TextView(this);
        scoreText.setText("0 - 0");
        scoreText.setTextColor(Color.WHITE);
        scoreText.setTextSize(22);
        scoreText.setGravity(Gravity.CENTER);
        scoreText.setTypeface(Typeface.DEFAULT_BOLD);

        topBar.addView(hostBtn, new LinearLayout.LayoutParams(dp(90), dp(44)));
        topBar.addView(joinBtn, new LinearLayout.LayoutParams(dp(90), dp(44)));
        topBar.addView(ipInput, new LinearLayout.LayoutParams(dp(190), dp(44)));
        topBar.addView(statusText, new LinearLayout.LayoutParams(0, dp(44), 1));
        topBar.addView(scoreText, new LinearLayout.LayoutParams(dp(120), dp(44)));

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
            public void onScore(final int a, final int b) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scoreText.setText(a + " - " + b);
                    }
                });
            }
        });

        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
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
        b.setBackgroundColor(Color.rgb(42, 126, 255));
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
            void onScore(int a, int b);
        }

        private final HudListener hud;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Thread gameThread;
        private boolean running = true;

        private static final int PORT = 50005;

        private DatagramSocket socket;
        private InetAddress remoteAddress;
        private int remotePort = PORT;
        private Thread netThread;

        private boolean isHost = false;
        private boolean isClient = false;
        private int localPlayer = 0;

        private final Object lock = new Object();

        private float fieldW = 900f;
        private float fieldH = 500f;

        private Disc p1 = new Disc(190, 250, 24);
        private Disc p2 = new Disc(710, 250, 24);
        private Ball ball = new Ball(450, 250, 16);

        private float p1InputX = 0;
        private float p1InputY = 0;
        private float p2InputX = 0;
        private float p2InputY = 0;

        private int score1 = 0;
        private int score2 = 0;

        private float touchStartX = -1;
        private float touchStartY = -1;
        private float joyX = 0;
        private float joyY = 0;
        private boolean touching = false;

        private long lastNetSend = 0;

        public GameView(Activity context, HudListener hud) {
            super(context);
            this.hud = hud;
            setFocusable(true);

            resetGame();

            gameThread = new Thread(this);
            gameThread.start();

            hud.onStatus("اضغط HOST في الهاتف الأول، وJOIN في الهاتف الثاني");
            hud.onScore(score1, score2);
        }

        public void startHost() {
            stopNetwork();

            isHost = true;
            isClient = false;
            localPlayer = 1;

            resetGame();

            try {
                socket = new DatagramSocket(PORT);
                socket.setReuseAddress(true);
                hud.onStatus("HOST: IP = " + getLocalIpAddress() + " | انت اللاعب الأحمر");
                startHostListener();
            } catch (Exception e) {
                hud.onStatus("Host error: " + e.getMessage());
            }
        }

        public void startClient(String hostIp) {
            stopNetwork();

            if (hostIp == null || hostIp.length() < 7) {
                hud.onStatus("اكتب IP الهاتف الأول");
                return;
            }

            isHost = false;
            isClient = true;
            localPlayer = 2;

            try {
                socket = new DatagramSocket();
                socket.setReuseAddress(true);
                remoteAddress = InetAddress.getByName(hostIp);
                remotePort = PORT;

                hud.onStatus("JOIN: الاتصال بـ " + hostIp + " | انت اللاعب الأزرق");

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
            isHost = false;
            isClient = false;
            localPlayer = 0;
        }

        private void startHostListener() {
            netThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[1024];

                    while (socket != null && !socket.isClosed()) {
                        try {
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            socket.receive(packet);

                            String msg = new String(packet.getData(), 0, packet.getLength()).trim();

                            remoteAddress = packet.getAddress();
                            remotePort = packet.getPort();

                            if (msg.startsWith("HELLO")) {
                                hud.onStatus("Player 2 connected: " + remoteAddress.getHostAddress());
                                sendState();
                            } else if (msg.startsWith("INPUT|")) {
                                String[] parts = msg.split("\\|");
                                if (parts.length >= 3) {
                                    synchronized (lock) {
                                        p2InputX = parse(parts[1]);
                                        p2InputY = parse(parts[2]);
                                    }
                                }
                            }

                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            });

            netThread.start();
        }

        private void startClientListener() {
            netThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[2048];

                    while (socket != null && !socket.isClosed()) {
                        try {
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            socket.receive(packet);

                            String msg = new String(packet.getData(), 0, packet.getLength()).trim();

                            if (msg.startsWith("STATE|")) {
                                applyState(msg);
                            }

                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            });

            netThread.start();
        }

        private void applyState(String msg) {
            String[] s = msg.split("\\|");

            if (s.length < 12) return;

            synchronized (lock) {
                p1.x = parse(s[1]);
                p1.y = parse(s[2]);
                p1.vx = parse(s[3]);
                p1.vy = parse(s[4]);

                p2.x = parse(s[5]);
                p2.y = parse(s[6]);
                p2.vx = parse(s[7]);
                p2.vy = parse(s[8]);

                ball.x = parse(s[9]);
                ball.y = parse(s[10]);
                ball.vx = parse(s[11]);

                if (s.length >= 15) {
                    ball.vy = parse(s[12]);
                    score1 = (int) parse(s[13]);
                    score2 = (int) parse(s[14]);
                    hud.onScore(score1, score2);
                }
            }
        }

        private void sendState() {
            if (!isHost || remoteAddress == null || socket == null || socket.isClosed()) return;

            String msg;

            synchronized (lock) {
                msg = String.format(Locale.US,
                        "STATE|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%d|%d",
                        p1.x, p1.y, p1.vx, p1.vy,
                        p2.x, p2.y, p2.vx, p2.vy,
                        ball.x, ball.y, ball.vx, ball.vy,
                        score1, score2
                );
            }

            sendRaw(msg);
        }

        private void sendInput() {
            if (!isClient || remoteAddress == null || socket == null || socket.isClosed()) return;

            float ix;
            float iy;

            synchronized (lock) {
                ix = p2InputX;
                iy = p2InputY;
            }

            String msg = String.format(Locale.US, "INPUT|%.3f|%.3f", ix, iy);
            sendRaw(msg);
        }

        private void sendRaw(String msg) {
            try {
                if (socket == null || socket.isClosed() || remoteAddress == null) return;

                byte[] data = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress, remotePort);
                socket.send(packet);

            } catch (Exception ignored) {}
        }

        private float parse(String v) {
            try {
                return Float.parseFloat(v);
            } catch (Exception e) {
                return 0;
            }
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

                if (localPlayer == 1) {
                    p1InputX = joyX;
                    p1InputY = joyY;
                } else if (localPlayer == 2) {
                    p2InputX = joyX;
                    p2InputY = joyY;
                }

                if (isHost) {
                    applyPlayerInput(p1, p1InputX, p1InputY, dt);
                    applyPlayerInput(p2, p2InputX, p2InputY, dt);

                    moveDisc(p1, dt);
                    moveDisc(p2, dt);
                    moveBall(dt);

                    collideDiscDisc(p1, p2);
                    collideDiscBall(p1, ball);
                    collideDiscBall(p2, ball);

                    clampPlayer(p1);
                    clampPlayer(p2);

                    handleBallWallsAndGoals();

                    long t = System.currentTimeMillis();
                    if (t - lastNetSend > 20) {
                        lastNetSend = t;
                        sendState();
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

        private void applyPlayerInput(Disc d, float ix, float iy, float dt) {
            float accel = 960f;
            d.vx += ix * accel * dt;
            d.vy += iy * accel * dt;

            float maxSpeed = 260f;
            float sp = (float) Math.sqrt(d.vx * d.vx + d.vy * d.vy);
            if (sp > maxSpeed) {
                d.vx = d.vx / sp * maxSpeed;
                d.vy = d.vy / sp * maxSpeed;
            }
        }

        private void moveDisc(Disc d, float dt) {
            d.x += d.vx * dt;
            d.y += d.vy * dt;

            d.vx *= 0.90f;
            d.vy *= 0.90f;
        }

        private void moveBall(float dt) {
            ball.x += ball.vx * dt;
            ball.y += ball.vy * dt;

            ball.vx *= 0.992f;
            ball.vy *= 0.992f;
        }

        private void clampPlayer(Disc d) {
            if (d.x < d.r) {
                d.x = d.r;
                d.vx *= -0.35f;
            }

            if (d.x > fieldW - d.r) {
                d.x = fieldW - d.r;
                d.vx *= -0.35f;
            }

            if (d.y < d.r) {
                d.y = d.r;
                d.vy *= -0.35f;
            }

            if (d.y > fieldH - d.r) {
                d.y = fieldH - d.r;
                d.vy *= -0.35f;
            }
        }

        private void handleBallWallsAndGoals() {
            float goalTop = fieldH / 2f - 80f;
            float goalBottom = fieldH / 2f + 80f;

            if (ball.y < ball.r) {
                ball.y = ball.r;
                ball.vy *= -0.75f;
            }

            if (ball.y > fieldH - ball.r) {
                ball.y = fieldH - ball.r;
                ball.vy *= -0.75f;
            }

            if (ball.x < ball.r) {
                if (ball.y > goalTop && ball.y < goalBottom) {
                    score2++;
                    hud.onScore(score1, score2);
                    resetRound();
                    return;
                } else {
                    ball.x = ball.r;
                    ball.vx *= -0.75f;
                }
            }

            if (ball.x > fieldW - ball.r) {
                if (ball.y > goalTop && ball.y < goalBottom) {
                    score1++;
                    hud.onScore(score1, score2);
                    resetRound();
                } else {
                    ball.x = fieldW - ball.r;
                    ball.vx *= -0.75f;
                }
            }
        }

        private void collideDiscDisc(Disc a, Disc b) {
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

                float dpTan1 = a.vx * tx + a.vy * ty;
                float dpTan2 = b.vx * tx + b.vy * ty;

                float dpNorm1 = a.vx * nx + a.vy * ny;
                float dpNorm2 = b.vx * nx + b.vy * ny;

                a.vx = tx * dpTan1 + nx * dpNorm2;
                a.vy = ty * dpTan1 + ny * dpNorm2;
                b.vx = tx * dpTan2 + nx * dpNorm1;
                b.vy = ty * dpTan2 + ny * dpNorm1;
            }
        }

        private void collideDiscBall(Disc d, Ball b) {
            float dx = b.x - d.x;
            float dy = b.y - d.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float min = d.r + b.r;

            if (dist > 0 && dist < min) {
                float nx = dx / dist;
                float ny = dy / dist;
                float overlap = min - dist;

                b.x += nx * overlap;
                b.y += ny * overlap;

                float power = 1.35f;

                float relVx = b.vx - d.vx;
                float relVy = b.vy - d.vy;
                float sep = relVx * nx + relVy * ny;

                if (sep < 0) {
                    b.vx -= (1 + power) * sep * nx;
                    b.vy -= (1 + power) * sep * ny;
                }

                b.vx += d.vx * 0.45f;
                b.vy += d.vy * 0.45f;

                float maxBall = 520f;
                float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                if (sp > maxBall) {
                    b.vx = b.vx / sp * maxBall;
                    b.vy = b.vy / sp * maxBall;
                }
            }
        }

        private void resetGame() {
            score1 = 0;
            score2 = 0;
            resetRound();
            hud.onScore(score1, score2);
        }

        private void resetRound() {
            p1.x = 190;
            p1.y = 250;
            p1.vx = 0;
            p1.vy = 0;

            p2.x = 710;
            p2.y = 250;
            p2.vx = 0;
            p2.vy = 0;

            ball.x = 450;
            ball.y = 250;
            ball.vx = 0;
            ball.vy = 0;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();

            float scale = Math.min(w / fieldW, h / fieldH);
            float ox = (w - fieldW * scale) / 2f;
            float oy = (h - fieldH * scale) / 2f;

            c.drawColor(Color.rgb(14, 18, 22));

            c.save();
            c.translate(ox, oy);
            c.scale(scale, scale);

            drawField(c);
            drawObjects(c);

            c.restore();

            drawJoystick(c);
        }

        private void drawField(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(41, 135, 76));
            c.drawRoundRect(0, 0, fieldW, fieldH, 22, 22, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(Color.argb(190, 255, 255, 255));
            c.drawRoundRect(12, 12, fieldW - 12, fieldH - 12, 16, 16, p);

            p.setStrokeWidth(3);
            c.drawLine(fieldW / 2, 12, fieldW / 2, fieldH - 12, p);
            c.drawCircle(fieldW / 2, fieldH / 2, 70, p);

            float goalTop = fieldH / 2f - 80f;
            float goalBottom = fieldH / 2f + 80f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(35, 37, 44));
            c.drawRect(-8, goalTop, 18, goalBottom, p);
            c.drawRect(fieldW - 18, goalTop, fieldW + 8, goalBottom, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(Color.WHITE);
            c.drawLine(0, goalTop, 0, goalBottom, p);
            c.drawLine(fieldW, goalTop, fieldW, goalBottom, p);
        }

        private void drawObjects(Canvas c) {
            synchronized (lock) {
                drawDisc(c, p1, Color.rgb(235, 70, 70), "1");
                drawDisc(c, p2, Color.rgb(70, 145, 255), "2");

                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                c.drawCircle(ball.x, ball.y, ball.r, p);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(Color.rgb(30, 30, 30));
                c.drawCircle(ball.x, ball.y, ball.r, p);
            }
        }

        private void drawDisc(Canvas c, Disc d, int color, String label) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(80, 0, 0, 0));
            c.drawCircle(d.x + 4, d.y + 5, d.r + 2, p);

            p.setColor(color);
            c.drawCircle(d.x, d.y, d.r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(Color.WHITE);
            c.drawCircle(d.x, d.y, d.r, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTextSize(24);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(label, d.x, d.y + 8, p);
        }

        private void drawJoystick(Canvas c) {
            if (!touching) return;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(70, 255, 255, 255));
            c.drawCircle(touchStartX, touchStartY, 54, p);

            p.setColor(Color.argb(150, 255, 255, 255));
            c.drawCircle(touchStartX + joyX * 44, touchStartY + joyY * 44, 24, p);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            int action = e.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                touching = true;
                touchStartX = e.getX();
                touchStartY = e.getY();
                joyX = 0;
                joyY = 0;
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                float dx = e.getX() - touchStartX;
                float dy = e.getY() - touchStartY;

                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float max = 70f;

                if (len > max) {
                    dx = dx / len * max;
                    dy = dy / len * max;
                    len = max;
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

        private String getLocalIpAddress() {
            try {
                for (NetworkInterface intf : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    for (InetAddress addr : java.util.Collections.list(intf.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            } catch (Exception ignored) {}

            return "Unknown";
        }

        private static class Disc {
            float x, y, vx, vy, r;

            Disc(float x, float y, float r) {
                this.x = x;
                this.y = y;
                this.r = r;
            }
        }

        private static class Ball {
            float x, y, vx, vy, r;

            Ball(float x, float y, float r) {
                this.x = x;
                this.y = y;
                this.r = r;
            }
        }
    }
}
