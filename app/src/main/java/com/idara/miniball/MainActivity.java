package com.idara.miniball;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.content.pm.ActivityInfo;
import android.content.DialogInterface;

import java.net.*;
import java.util.*;

public class MainActivity extends Activity {

    private GameView gameView;

    private TextView statusText;
    private TextView scoreText;
    private TextView playersText;
    private TextView timerText;

    private EditText ipInput;

    private FrameLayout splashOverlay;
    private Handler splashHandler = new Handler();

    private Button hostBtn;
    private Button joinBtn;
    private Button redTeamBtn;
    private Button blueTeamBtn;
    private Button settingsBtn;

    private int requestedTeam = 1;

    private int hostRedColor = Color.rgb(235, 70, 70);
    private int hostBlueColor = Color.rgb(70, 145, 255);
    private int matchMinutes = 5;

    private final int[] redPalette = new int[] {
            Color.rgb(235, 70, 70),
            Color.rgb(255, 118, 52),
            Color.rgb(222, 60, 110),
            Color.rgb(190, 55, 235),
            Color.rgb(245, 205, 45)
    };

    private final int[] bluePalette = new int[] {
            Color.rgb(70, 145, 255),
            Color.rgb(55, 210, 255),
            Color.rgb(80, 210, 130),
            Color.rgb(110, 100, 255),
            Color.rgb(245, 245, 245)
    };

    private int redColorIndex = 0;
    private int blueColorIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 10, 14));

        buildCompactHud(root);

        gameView = new GameView(MainActivity.this, new GameView.HudListener() {
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
                            playersText.setText("👥 " + count);
                        }
                    }
                });
            }

            @Override
            public void onTime(final int secondsLeft) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (timerText == null) return;
                        int m = Math.max(0, secondsLeft) / 60;
                        int s = Math.max(0, secondsLeft) % 60;
                        timerText.setText(String.format(Locale.US, "%02d:%02d", m, s));
                    }
                });
            }
        });

        gameView.setHostConfig(hostRedColor, hostBlueColor, matchMinutes);

        root.addView(gameView, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        addSplashScreen(root);

        setContentView(root);

        hostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gameView.setHostConfig(hostRedColor, hostBlueColor, matchMinutes);
                gameView.startHost();
            }
        });

        joinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String ip = ipInput.getText().toString().trim();
                gameView.startClient(ip, requestedTeam);
            }
        });

        redTeamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestedTeam = 0;
                updateTeamButtons();
            }
        });

        blueTeamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestedTeam = 1;
                updateTeamButtons();
            }
        });

        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSettingsDialog();
            }
        });

        updateTeamButtons();
    }


    private void addSplashScreen(final FrameLayout root) {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.rgb(9, 12, 18));
        splashOverlay.setClickable(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("fireball");
        title.setTextColor(Color.WHITE);
        title.setTextSize(52);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setShadowLayer(18f, 0f, 0f, Color.rgb(255, 115, 30));

        TextView subtitle = new TextView(this);
        subtitle.setText("2D Wi-Fi Football");
        subtitle.setTextColor(Color.argb(225, 255, 210, 120));
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        subtitle.setPadding(0, dp(10), 0, 0);

        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        content.addView(subtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        splashOverlay.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView credit = new TextView(this);
        credit.setText("Mohammed BELKEBIR ABDELKARIM");
        credit.setTextColor(Color.argb(230, 255, 255, 255));
        credit.setTextSize(15);
        credit.setGravity(Gravity.CENTER);
        credit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        credit.setPadding(dp(12), 0, dp(12), dp(24));

        FrameLayout.LayoutParams creditParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        creditParams.gravity = Gravity.BOTTOM;
        splashOverlay.addView(credit, creditParams);

        root.addView(splashOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        splashHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (splashOverlay != null) {
                    splashOverlay.animate()
                            .alpha(0f)
                            .setDuration(450)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    if (splashOverlay != null) {
                                        root.removeView(splashOverlay);
                                        splashOverlay = null;
                                    }
                                }
                            })
                            .start();
                }
            }
        }, 5000);
    }

    private void buildCompactHud(FrameLayout root) {
        LinearLayout topHud = new LinearLayout(this);
        topHud.setOrientation(LinearLayout.HORIZONTAL);
        topHud.setGravity(Gravity.CENTER_VERTICAL);
        topHud.setPadding(dp(7), dp(4), dp(7), dp(4));
        topHud.setBackgroundColor(Color.argb(105, 10, 13, 18));

        hostBtn = makeMiniButton("H");
        joinBtn = makeMiniButton("J");

        ipInput = new EditText(this);
        ipInput.setText("192.168.1.");
        ipInput.setHint("IP");
        ipInput.setSingleLine(true);
        ipInput.setTextSize(12);
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.LTGRAY);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ipInput.setPadding(dp(8), 0, dp(8), 0);
        ipInput.setBackgroundColor(Color.argb(170, 35, 43, 55));

        redTeamBtn = makeMiniButton("R");
        blueTeamBtn = makeMiniButton("B");
        settingsBtn = makeMiniButton("⚙");

        statusText = makeChipText("Ready", 12, Gravity.CENTER_VERTICAL);
        playersText = makeChipText("👥 0", 13, Gravity.CENTER);
        timerText = makeChipText("05:00", 15, Gravity.CENTER);
        scoreText = makeChipText("0 - 0", 22, Gravity.CENTER);
        scoreText.setTypeface(Typeface.DEFAULT_BOLD);

        topHud.addView(hostBtn, new LinearLayout.LayoutParams(dp(40), dp(36)));
        topHud.addView(joinBtn, new LinearLayout.LayoutParams(dp(40), dp(36)));
        topHud.addView(ipInput, new LinearLayout.LayoutParams(dp(125), dp(36)));
        topHud.addView(redTeamBtn, new LinearLayout.LayoutParams(dp(34), dp(36)));
        topHud.addView(blueTeamBtn, new LinearLayout.LayoutParams(dp(34), dp(36)));
        topHud.addView(settingsBtn, new LinearLayout.LayoutParams(dp(40), dp(36)));
        topHud.addView(statusText, new LinearLayout.LayoutParams(0, dp(36), 1));
        topHud.addView(playersText, new LinearLayout.LayoutParams(dp(70), dp(36)));
        topHud.addView(timerText, new LinearLayout.LayoutParams(dp(80), dp(36)));
        topHud.addView(scoreText, new LinearLayout.LayoutParams(dp(95), dp(36)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        params.gravity = Gravity.TOP;

        root.addView(topHud, params);
    }

    private TextView makeChipText(String text, int size, int gravity) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setSingleLine(true);
        t.setGravity(gravity);
        t.setPadding(dp(6), 0, dp(6), 0);
        return t;
    }

    private Button makeMiniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.argb(210, 45, 115, 245));
        return b;
    }

    private void updateTeamButtons() {
        redTeamBtn.setBackgroundColor(requestedTeam == 0 ? Color.rgb(235, 70, 70) : Color.argb(145, 70, 70, 70));
        blueTeamBtn.setBackgroundColor(requestedTeam == 1 ? Color.rgb(70, 145, 255) : Color.argb(145, 70, 70, 70));
    }

    private void showSettingsDialog() {
        final String[] items = new String[] {
                "إعدادات المباراة والألوان",
                "إعدادات أزرار التحكم"
        };

        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showHostSettingsDialog();
                        } else {
                            showControlSettingsDialog();
                        }
                    }
                })
                .show();
    }

    private interface ControlSliderListener {
        void onChanged(int value);
    }

    private void showControlSettingsDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(14), dp(22), dp(10));

        TextView hint = new TextView(this);
        hint.setText("عدّل حجم ومكان أزرار التحكم على هذا الهاتف فقط.");
        hint.setTextColor(Color.DKGRAY);
        hint.setTextSize(14);
        hint.setPadding(0, 0, 0, dp(10));
        box.addView(hint);

        addControlSlider(box, "حجم أزرار PASS/THROUGH/SHOOT", 70, 170, gameView.getActionButtonSizePercent(), new ControlSliderListener() {
            @Override
            public void onChanged(int value) {
                gameView.setActionButtonSizePercent(value);
            }
        });

        addControlSlider(box, "إدخال الأزرار من اليمين", 0, 220, gameView.getActionButtonInsetX(), new ControlSliderListener() {
            @Override
            public void onChanged(int value) {
                gameView.setActionButtonInsetX(value);
            }
        });

        addControlSlider(box, "رفع الأزرار من الأسفل", 0, 220, gameView.getActionButtonInsetY(), new ControlSliderListener() {
            @Override
            public void onChanged(int value) {
                gameView.setActionButtonInsetY(value);
            }
        });

        addControlSlider(box, "حجم joystick", 70, 150, gameView.getJoystickSizePercent(), new ControlSliderListener() {
            @Override
            public void onChanged(int value) {
                gameView.setJoystickSizePercent(value);
            }
        });

        addControlSlider(box, "حساسية joystick", 60, 160, gameView.getJoystickRangePercent(), new ControlSliderListener() {
            @Override
            public void onChanged(int value) {
                gameView.setJoystickRangePercent(value);
            }
        });

        Button resetBtn = new Button(this);
        resetBtn.setText("إرجاع التحكم للوضع الافتراضي");
        resetBtn.setTextColor(Color.WHITE);
        resetBtn.setBackgroundColor(Color.rgb(60, 105, 220));
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gameView.resetControlLayout();
            }
        });

        box.addView(resetBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        ));

        new AlertDialog.Builder(this)
                .setTitle("أزرار التحكم")
                .setView(box)
                .setPositiveButton("تم", null)
                .show();
    }

    private void addControlSlider(LinearLayout box, final String title, int min, int max, int current, final ControlSliderListener listener) {
        final TextView label = new TextView(this);
        label.setTextColor(Color.BLACK);
        label.setTextSize(15);
        label.setText(title + ": " + current);
        label.setPadding(0, dp(8), 0, 0);
        box.addView(label);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(current - min);
        box.addView(seek);

        final int minValue = min;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = minValue + progress;
                label.setText(title + ": " + value);
                listener.onChanged(value);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void showHostSettingsDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(16), dp(22), dp(10));

        final TextView durationLabel = new TextView(this);
        durationLabel.setTextColor(Color.BLACK);
        durationLabel.setTextSize(18);
        durationLabel.setText("وقت المباراة: " + matchMinutes + " دقائق");

        SeekBar durationSeek = new SeekBar(this);
        durationSeek.setMax(10);
        durationSeek.setProgress(matchMinutes - 5);

        final Button redColorBtn = new Button(this);
        redColorBtn.setText("تغيير لون الفريق الأحمر");
        redColorBtn.setTextColor(Color.WHITE);
        redColorBtn.setBackgroundColor(hostRedColor);

        final Button blueColorBtn = new Button(this);
        blueColorBtn.setText("تغيير لون الفريق الأزرق");
        blueColorBtn.setTextColor(Color.WHITE);
        blueColorBtn.setBackgroundColor(hostBlueColor);

        TextView hint = new TextView(this);
        hint.setText("هذه الإعدادات يختارها المضيف قبل الضغط على HOST، ويمكنه تغييرها أثناء اللعب أيضاً.");
        hint.setTextSize(13);
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, dp(10), 0, 0);

        box.addView(durationLabel);
        box.addView(durationSeek);
        box.addView(redColorBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ));
        box.addView(blueColorBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ));
        box.addView(hint);

        durationSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                matchMinutes = 5 + progress;
                durationLabel.setText("وقت المباراة: " + matchMinutes + " دقائق");
                if (gameView != null) {
                    gameView.setHostConfig(hostRedColor, hostBlueColor, matchMinutes);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        redColorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                redColorIndex = (redColorIndex + 1) % redPalette.length;
                hostRedColor = redPalette[redColorIndex];
                redColorBtn.setBackgroundColor(hostRedColor);
                if (gameView != null) {
                    gameView.setHostConfig(hostRedColor, hostBlueColor, matchMinutes);
                }
            }
        });

        blueColorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                blueColorIndex = (blueColorIndex + 1) % bluePalette.length;
                hostBlueColor = bluePalette[blueColorIndex];
                blueColorBtn.setBackgroundColor(hostBlueColor);
                if (gameView != null) {
                    gameView.setHostConfig(hostRedColor, hostBlueColor, matchMinutes);
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("إعدادات المضيف")
                .setView(box)
                .setPositiveButton("تم", null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (splashHandler != null) {
            splashHandler.removeCallbacksAndMessages(null);
        }
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
            void onTime(int secondsLeft);
        }

        private static final int PORT = 50005;
        private static final int MAX_PLAYERS = 10;
        private static final int MAX_TEAM_PLAYERS = 5;

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

        private float fieldW = 1950f;
        private float fieldH = 900f;

        private float touchStartX = -1;
        private float touchStartY = -1;
        private float joyX = 0;
        private float joyY = 0;
        private boolean touching = false;

        private RectF passButton = new RectF();
        private RectF throughButton = new RectF();
        private RectF shootButton = new RectF();

        private int actionButtonSizePercent = 115;
        private int actionButtonInsetX = 36;
        private int actionButtonInsetY = 42;
        private int joystickSizePercent = 100;
        private int joystickRangePercent = 100;

        private int pendingActionCode = 0;
        private int joystickPointerId = -1;

        private long lastNetSend = 0;
        private long lastHelloSend = 0;

        private int carrierId = -1;
        private long lastTackleTime = 0;

        private int redTeamColor = Color.rgb(235, 70, 70);
        private int blueTeamColor = Color.rgb(70, 145, 255);

        private int matchDurationMinutes = 5;
        private long matchStartTime = 0;
        private int lastSecondsLeft = 300;

        private int requestedTeamForClient = 1;

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

            hud.onStatus("H=HOST  J=JOIN  R/B=اختيار الفريق  ⚙=إعدادات");
            hud.onScore(redScore, blueScore);
            hud.onPlayers(0);
            hud.onTime(lastSecondsLeft);
        }

        public void setHostConfig(int redColor, int blueColor, int minutes) {
            synchronized (lock) {
                redTeamColor = redColor;
                blueTeamColor = blueColor;
                matchDurationMinutes = Math.max(5, Math.min(15, minutes));
                if (!isHost && !isClient) {
                    lastSecondsLeft = matchDurationMinutes * 60;
                    hud.onTime(lastSecondsLeft);
                }
            }
        }

        public int getActionButtonSizePercent() {
            return actionButtonSizePercent;
        }

        public void setActionButtonSizePercent(int value) {
            actionButtonSizePercent = Math.max(70, Math.min(170, value));
        }

        public int getActionButtonInsetX() {
            return actionButtonInsetX;
        }

        public void setActionButtonInsetX(int value) {
            actionButtonInsetX = Math.max(0, Math.min(220, value));
        }

        public int getActionButtonInsetY() {
            return actionButtonInsetY;
        }

        public void setActionButtonInsetY(int value) {
            actionButtonInsetY = Math.max(0, Math.min(220, value));
        }

        public int getJoystickSizePercent() {
            return joystickSizePercent;
        }

        public void setJoystickSizePercent(int value) {
            joystickSizePercent = Math.max(70, Math.min(150, value));
        }

        public int getJoystickRangePercent() {
            return joystickRangePercent;
        }

        public void setJoystickRangePercent(int value) {
            joystickRangePercent = Math.max(60, Math.min(160, value));
        }

        public void resetControlLayout() {
            actionButtonSizePercent = 115;
            actionButtonInsetX = 36;
            actionButtonInsetY = 42;
            joystickSizePercent = 100;
            joystickRangePercent = 100;
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

                matchStartTime = System.currentTimeMillis();
                lastSecondsLeft = matchDurationMinutes * 60;

                hud.onPlayers(getActivePlayerCount());
                hud.onTime(lastSecondsLeft);
            }

            try {
                socket = new DatagramSocket(PORT);
                socket.setReuseAddress(true);

                hud.onStatus("HOST IP: " + getLocalIpAddress());

                startHostListener();

            } catch (Exception e) {
                hud.onStatus("Host error: " + e.getMessage());
            }
        }

        public void startClient(String hostIp, int requestedTeam) {
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
                requestedTeamForClient = requestedTeam;
            }

            try {
                socket = new DatagramSocket();
                socket.setReuseAddress(true);

                remoteAddress = InetAddress.getByName(hostIp);
                remotePort = PORT;

                hud.onStatus("Connecting to " + hostIp + "...");

                sendRaw("HELLO|" + requestedTeam);
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
                                handleHello(msg, packet.getAddress(), packet.getPort(), key);
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

        private void handleHello(String msg, InetAddress address, int port, String key) {
            synchronized (lock) {
                Integer existing = clientMap.get(key);

                if (existing != null) {
                    sendWelcome(existing, address, port);
                    return;
                }

                int requestedTeam = 1;
                String[] parts = msg.split("\\|");
                if (parts.length >= 2) {
                    requestedTeam = safeInt(parts[1], 1);
                }
                requestedTeam = requestedTeam == 0 ? 0 : 1;

                int assignedTeam = chooseAvailableTeam(requestedTeam);

                if (assignedTeam == -1) {
                    sendRawTo("FULL", address, port);
                    hud.onStatus("الغرفة ممتلئة");
                    return;
                }

                int newId = findFreePlayerSlot();

                if (newId == -1) {
                    sendRawTo("FULL", address, port);
                    hud.onStatus("الغرفة ممتلئة");
                    return;
                }

                Player pl = players[newId];
                pl.active = true;
                pl.isLocal = false;
                pl.remoteAddress = address;
                pl.remotePort = port;
                pl.remoteKey = key;
                pl.team = assignedTeam;

                spawnPlayer(pl);
                clientMap.put(key, newId);

                sendWelcome(newId, address, port);

                hud.onStatus("Player " + (newId + 1) + " connected");
                hud.onPlayers(getActivePlayerCount());
                sendStateToAll();
            }
        }

        private int chooseAvailableTeam(int requestedTeam) {
            int other = requestedTeam == 0 ? 1 : 0;

            if (getTeamCount(requestedTeam) < MAX_TEAM_PLAYERS) return requestedTeam;
            if (getTeamCount(other) < MAX_TEAM_PLAYERS) return other;

            return -1;
        }

        private int getTeamCount(int team) {
            int count = 0;
            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (players[i].active && players[i].team == team) count++;
            }
            return count;
        }

        private void sendWelcome(int id, InetAddress address, int port) {
            int team = players[id].team;
            String msg = "WELCOME|" + id + "|" + team + "|" + fieldW + "|" + fieldH + "|" +
                    redTeamColor + "|" + blueTeamColor + "|" + matchDurationMinutes;
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

                if (s.length >= 5) {
                    int code = safeInt(s[4], 0);
                    if (code >= 1 && code <= 3) {
                        players[id].actionCode = code;
                    }
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
                int team = safeInt(s[2], 1);

                if (s.length >= 8) {
                    redTeamColor = safeInt(s[5], redTeamColor);
                    blueTeamColor = safeInt(s[6], blueTeamColor);
                    matchDurationMinutes = safeInt(s[7], matchDurationMinutes);
                }

                if (localPlayerId >= 0 && localPlayerId < MAX_PLAYERS) {
                    players[localPlayerId].isLocal = true;
                    players[localPlayerId].team = team;
                }

                String teamName = team == 0 ? "RED" : "BLUE";
                hud.onStatus("Connected | Player " + (localPlayerId + 1) + " | " + teamName);
            }
        }

        private void applyState(String msg) {
            String[] main = msg.split("\\|");

            if (main.length < 13) return;

            synchronized (lock) {
                redScore = safeInt(main[1], 0);
                blueScore = safeInt(main[2], 0);

                ball.x = safeFloat(main[3], fieldW / 2f);
                ball.y = safeFloat(main[4], fieldH / 2f);
                ball.vx = safeFloat(main[5], 0);
                ball.vy = safeFloat(main[6], 0);

                carrierId = safeInt(main[7], -1);
                redTeamColor = safeInt(main[8], redTeamColor);
                blueTeamColor = safeInt(main[9], blueTeamColor);
                lastSecondsLeft = safeInt(main[10], lastSecondsLeft);
                matchDurationMinutes = safeInt(main[11], matchDurationMinutes);

                for (int i = 0; i < MAX_PLAYERS; i++) {
                    players[i].active = false;
                }

                int count = safeInt(main[12], 0);

                for (int i = 0; i < count; i++) {
                    int index = 13 + i;
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
                hud.onTime(lastSecondsLeft);
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
            sb.append(redTeamColor).append("|");
            sb.append(blueTeamColor).append("|");
            sb.append(lastSecondsLeft).append("|");
            sb.append(matchDurationMinutes).append("|");

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
                    sendRaw("HELLO|" + requestedTeamForClient);
                }
                return;
            }

            String msg;

            synchronized (lock) {
                int code = pendingActionCode;
                pendingActionCode = 0;

                msg = "INPUT|" + localPlayerId + "|" + fmt(joyX) + "|" + fmt(joyY) + "|" + code;
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

                    if (pendingActionCode > 0 && isHost) {
                        players[localPlayerId].actionCode = pendingActionCode;
                        pendingActionCode = 0;
                    }
                }

                if (isHost) {
                    updateTimer();
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

        private void updateTimer() {
            if (matchStartTime <= 0) {
                matchStartTime = System.currentTimeMillis();
            }

            long elapsed = (System.currentTimeMillis() - matchStartTime) / 1000;
            int total = matchDurationMinutes * 60;
            int left = Math.max(0, total - (int) elapsed);

            if (left != lastSecondsLeft) {
                lastSecondsLeft = left;
                hud.onTime(lastSecondsLeft);
            }

            if (left <= 0) {
                hud.onStatus("انتهت المباراة");
                matchStartTime = System.currentTimeMillis();
                redScore = 0;
                blueScore = 0;
                hud.onScore(redScore, blueScore);
                resetRound();
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
                players[i].actionCode = 0;
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

                float maxBallSpeed = 680f;

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

                        hud.onStatus("TACKLE! Player " + (defender.id + 1));
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

            int action = controller.actionCode;

            if (action == 1) {
                passToTeammate(controller);
                carrierId = -1;
                return;
            }

            if (action == 2) {
                decisiveThroughPass(controller);
                carrierId = -1;
                return;
            }

            if (action >= 3) {
                shootToGoal(controller, 900f);
                carrierId = -1;
                return;
            }

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
            Player target = findNearestTeammate(from);

            if (target == null) {
                shootForward(from, 500f);
                return;
            }

            float dx = target.x - ball.x;
            float dy = target.y - ball.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len < 1f) return;

            ball.vx = dx / len * 540f;
            ball.vy = dy / len * 540f;
        }

        private void decisiveThroughPass(Player from) {
            Player target = findBestForwardTeammate(from);

            if (target == null) {
                shootForward(from, 650f);
                return;
            }

            float forward = from.team == 0 ? 170f : -170f;
            float targetX = target.x + forward;
            float targetY = target.y + target.vy * 0.25f;

            if (targetX < 80f) targetX = 80f;
            if (targetX > fieldW - 80f) targetX = fieldW - 80f;
            if (targetY < 80f) targetY = 80f;
            if (targetY > fieldH - 80f) targetY = fieldH - 80f;

            float dx = targetX - ball.x;
            float dy = targetY - ball.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len < 1f) return;

            ball.vx = dx / len * 700f;
            ball.vy = dy / len * 700f;
        }

        private Player findNearestTeammate(Player from) {
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

            return target;
        }

        private Player findBestForwardTeammate(Player from) {
            Player target = null;
            float bestScore = -999999f;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                Player pl = players[i];

                if (!pl.active) continue;
                if (pl.id == from.id) continue;
                if (pl.team != from.team) continue;

                float progress = from.team == 0 ? pl.x : fieldW - pl.x;
                float distancePenalty = Math.abs(pl.y - from.y) * 0.35f;
                float score = progress - distancePenalty;

                if (score > bestScore) {
                    bestScore = score;
                    target = pl;
                }
            }

            return target;
        }

        private void shootToGoal(Player from, float power) {
            float goalX = from.team == 0 ? fieldW + 80f : -80f;
            float goalY = fieldH / 2f;

            float dx = goalX - ball.x;
            float dy = goalY - ball.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len < 1f) return;

            ball.vx = dx / len * power;
            ball.vy = dy / len * power;
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
                players[i].actionCode = 0;
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
            matchStartTime = System.currentTimeMillis();
            lastSecondsLeft = matchDurationMinutes * 60;

            for (int i = 0; i < MAX_PLAYERS; i++) {
                players[i].active = false;
                players[i].isLocal = false;
                players[i].inputX = 0;
                players[i].inputY = 0;
                players[i].actionCode = 0;
                players[i].team = i % 2;
            }

            resetRound();
            hud.onScore(redScore, blueScore);
            hud.onTime(lastSecondsLeft);
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
            int teamIndex = getTeamIndexForSpawn(pl);
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
            pl.actionCode = 0;
        }

        private int getTeamIndexForSpawn(Player target) {
            int index = 0;
            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (players[i].active && players[i].team == target.team) {
                    if (players[i].id == target.id) return index;
                    index++;
                }
            }
            return index;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float screenW = getWidth();
            float screenH = getHeight();

            float scale = Math.min(screenW / fieldW, screenH / fieldH);
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
            p.setStrokeWidth(8);
            p.setColor(Color.rgb(245, 245, 245));
            c.drawRect(4, 4, fieldW - 4, fieldH - 4, p);

            p.setStrokeWidth(5);
            p.setColor(Color.argb(220, 255, 255, 255));
            c.drawLine(fieldW / 2f, 4, fieldW / 2f, fieldH - 4, p);
            c.drawCircle(fieldW / 2f, fieldH / 2f, 115, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(fieldW / 2f, fieldH / 2f, 7, p);

            float goalTop = fieldH / 2f - 150f;
            float goalBottom = fieldH / 2f + 150f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(25, 31, 39));
            c.drawRect(0, goalTop, 22, goalBottom, p);
            c.drawRect(fieldW - 22, goalTop, fieldW, goalBottom, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            p.setColor(Color.WHITE);
            c.drawLine(4, goalTop, 4, goalBottom, p);
            c.drawLine(fieldW - 4, goalTop, fieldW - 4, goalBottom, p);

            p.setStrokeWidth(4);
            p.setColor(Color.argb(200, 255, 255, 255));
            c.drawRect(4, fieldH / 2f - 210f, 210, fieldH / 2f + 210f, p);
            c.drawRect(fieldW - 210, fieldH / 2f - 210f, fieldW - 4, fieldH / 2f + 210f, p);
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
            int color = pl.team == 0 ? redTeamColor : blueTeamColor;

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

            float baseRadius = 62f * (joystickSizePercent / 100f);
            float knobRadius = 25f * (joystickSizePercent / 100f);
            float knobRange = 48f * (joystickSizePercent / 100f);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(65, 255, 255, 255));
            c.drawCircle(touchStartX, touchStartY, baseRadius, p);

            p.setColor(Color.argb(165, 255, 255, 255));
            c.drawCircle(touchStartX + joyX * knobRange, touchStartY + joyY * knobRange, knobRadius, p);
        }

        private void drawActionButtons(Canvas c) {
            float w = getWidth();
            float h = getHeight();

            float size = 104f * (actionButtonSizePercent / 100f);
            float gap = 18f * (actionButtonSizePercent / 100f);
            float right = 58f + actionButtonInsetX;
            float bottom = 48f + actionButtonInsetY;

            shootButton.set(w - right - size, h - bottom - size, w - right, h - bottom);
            passButton.set(w - right - size * 2f - gap, h - bottom - size, w - right - size - gap, h - bottom);
            throughButton.set(w - right - size * 1.5f - gap / 2f, h - bottom - size * 2f - gap, w - right - size * 0.5f - gap / 2f, h - bottom - size - gap);

            drawGameButton(c, passButton, Color.argb(205, 42, 145, 255), "PASS", "1");
            drawGameButton(c, throughButton, Color.argb(205, 125, 80, 255), "THROUGH", "2");
            drawGameButton(c, shootButton, Color.argb(210, 255, 158, 28), "SHOOT", "3");
        }

        private void drawGameButton(Canvas c, RectF r, int color, String title, String small) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(70, 0, 0, 0));
            c.drawOval(r.left + 4, r.top + 5, r.right + 4, r.bottom + 5, p);

            p.setColor(color);
            c.drawOval(r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(Color.WHITE);
            c.drawOval(r, p);

            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.WHITE);

            p.setTextSize(18);
            c.drawText(title, r.centerX(), r.centerY() + 3, p);

            p.setTextSize(15);
            c.drawText(small, r.centerX(), r.centerY() + 27, p);
        }

        private void drawLocalPlayerInfo(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(24);
            p.setColor(Color.argb(230, 255, 255, 255));

            String text;

            if (isHost) {
                text = "Player 1 / RED / HOST";
            } else if (isClient && localPlayerId >= 0) {
                String team = players[localPlayerId].team == 0 ? "RED" : "BLUE";
                text = "Player " + (localPlayerId + 1) + " / " + team;
            } else if (isClient) {
                text = "Connecting...";
            } else {
                text = "H HOST | J JOIN | R/B choose team";
            }

            c.drawText(text, 16, getHeight() - 18, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            int index = e.getActionIndex();
            int pointerId = e.getPointerId(index);
            float x = e.getX(index);
            float y = e.getY(index);

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (shootButton.contains(x, y)) {
                    pendingActionCode = 3;
                    hud.onStatus("POWER SHOOT");
                    return true;
                }

                if (throughButton.contains(x, y)) {
                    pendingActionCode = 2;
                    hud.onStatus("THROUGH PASS");
                    return true;
                }

                if (passButton.contains(x, y)) {
                    pendingActionCode = 1;
                    hud.onStatus("PASS");
                    return true;
                }

                if (joystickPointerId == -1) {
                    joystickPointerId = pointerId;
                    touching = true;
                    touchStartX = x;
                    touchStartY = y;
                    joyX = 0;
                    joyY = 0;
                }
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                if (!touching || joystickPointerId == -1) return true;

                int moveIndex = e.findPointerIndex(joystickPointerId);
                if (moveIndex < 0) return true;

                float mx = e.getX(moveIndex);
                float my = e.getY(moveIndex);

                float dx = mx - touchStartX;
                float dy = my - touchStartY;

                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float max = 78f * (joystickRangePercent / 100f);

                if (len > max) {
                    dx = dx / len * max;
                    dy = dy / len * max;
                }

                joyX = dx / max;
                joyY = dy / max;

                return true;
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (pointerId == joystickPointerId || action == MotionEvent.ACTION_CANCEL) {
                    joystickPointerId = -1;
                    touching = false;
                    joyX = 0;
                    joyY = 0;
                }
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

            int actionCode = 0;

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
