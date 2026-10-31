package com.cleanhome.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.TrafficStats;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/**
 * Glass card shown under the Wi-Fi button while it's focused: a QR code that joins the home
 * network (assets/wifi-qr.png, generated on the Mac from the saved password) and details of
 * the current connection — signal, band/channel, link speeds, latency, live throughput, router
 * and DNS. Reading the network name needs ACCESS_FINE_LOCATION (granted over adb) and location
 * services on.
 */
final class WifiCard extends LinearLayout {
    /** Latency is the time to open a TCP connection to Cloudflare's resolver. */
    private static final InetSocketAddress PING_TARGET = new InetSocketAddress("1.1.1.1", 443);

    private final WifiManager wifi;
    private final ConnectivityManager connectivity;
    private final TextView name;
    private final LinearLayout stats;
    private final TextView signal;
    private final TextView band;
    private final TextView link;
    private final TextView latency;
    private final TextView throughput;
    private final TextView address;
    private final TextView router;
    private final TextView dns;

    private long lastBytesRx = -1;
    private long lastBytesTx;
    private long lastBytesAt;
    private boolean pinging;

    WifiCard(Context ctx) {
        super(ctx);
        wifi = ctx.getApplicationContext().getSystemService(WifiManager.class);
        connectivity = ctx.getSystemService(ConnectivityManager.class);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        // More opaque than the other glass panels: it floats over app tiles and must stay legible.
        android.graphics.drawable.GradientDrawable bg = Ui.glass(ctx, 16);
        bg.setColor(0xF0101420);
        setBackground(bg);
        setElevation(Ui.dp(ctx, 24));
        int pad = Ui.dp(ctx, 16);
        setPadding(pad, Ui.dp(ctx, 14), Ui.dp(ctx, 20), Ui.dp(ctx, 14));

        View qr = qrCode(ctx);
        if (qr != null) addView(qr);

        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(VERTICAL);
        addView(info);

        name = text(ctx, 17, Ui.TEXT);
        info.addView(name);

        stats = new LinearLayout(ctx);
        stats.setOrientation(VERTICAL);
        stats.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        info.addView(stats);
        signal = row(ctx, "Signal");
        band = row(ctx, "Pas");
        link = row(ctx, "Hitrost");
        latency = row(ctx, "Zakasnitev");
        throughput = row(ctx, "Trenutno");
        address = row(ctx, "IP");
        router = row(ctx, "Usmerjevalnik");
        dns = row(ctx, "DNS");

        TextView scan = text(ctx, 13, Ui.TEXT);
        scan.setText("Skeniraj kodo s telefonom za povezavo");
        TextView hint = text(ctx, 12, Ui.TEXT);
        hint.setText("OK  ·  Wi-Fi nastavitve in druga omrežja");
        hint.setAlpha(0.7f);
        if (qr != null) {
            scan.setPadding(0, Ui.dp(ctx, 10), 0, 0);
            info.addView(scan);
        }
        hint.setPadding(0, Ui.dp(ctx, qr != null ? 4 : 10), 0, 0);
        info.addView(hint);
    }

    /** The join-network QR on a white rounded tile, or null if the image isn't bundled. */
    private static View qrCode(Context ctx) {
        android.graphics.Bitmap bmp;
        try (java.io.InputStream in = ctx.getAssets().open("wifi-qr.png")) {
            bmp = android.graphics.BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
        android.graphics.drawable.BitmapDrawable d =
                new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bmp);
        d.setFilterBitmap(false); // keep module edges sharp when scaled
        android.widget.ImageView img = new android.widget.ImageView(ctx);
        img.setImageDrawable(d);
        img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        android.graphics.drawable.GradientDrawable tile = new android.graphics.drawable.GradientDrawable();
        tile.setColor(0xFFFFFFFF);
        tile.setCornerRadius(Ui.dp(ctx, 10));
        img.setBackground(tile);
        int pad = Ui.dp(ctx, 4);
        img.setPadding(pad, pad, pad, pad);
        LayoutParams lp = new LayoutParams(Ui.dp(ctx, 160), Ui.dp(ctx, 160));
        lp.rightMargin = Ui.dp(ctx, 18);
        img.setLayoutParams(lp);
        return img;
    }

    /** Refreshes the card and returns signal quality 0..100, or -1 when not on Wi-Fi. */
    int refresh() {
        WifiInfo info = wifi != null ? wifi.getConnectionInfo() : null;
        boolean connected = info != null && info.getNetworkId() != -1
                && info.getSupplicantState() == SupplicantState.COMPLETED;
        if (!connected) {
            name.setText("Ni povezave Wi-Fi");
            stats.setVisibility(GONE);
            lastBytesRx = -1;
            return -1;
        }
        stats.setVisibility(VISIBLE);
        int quality = quality(info.getRssi());
        String ssid = info.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) {
            ssid = "Wi-Fi";
        } else if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        name.setText(ssid);
        signal.setText(String.format(Locale.getDefault(), "%d %%  ·  %d dBm  ·  %s",
                quality, info.getRssi(), signalWord(quality)));

        int mhz = info.getFrequency();
        StringBuilder b = new StringBuilder(mhz >= 5925 ? "6 GHz" : mhz >= 4900 ? "5 GHz" : "2,4 GHz");
        int channel = channel(mhz);
        if (channel > 0) b.append("  ·  kanal ").append(channel);
        String standard = standard(info);
        if (standard != null) b.append("  ·  ").append(standard);
        band.setText(b);

        link.setText(linkSpeed(info));

        int ip = info.getIpAddress();
        address.setText(String.format(Locale.US, "%d.%d.%d.%d",
                ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff));
        showRouterAndDns();
        showThroughput();
        if (isShown()) measureLatency();
        return quality;
    }

    /** "144 Mb/s", or "↑ 144 · ↓ 130 Mb/s" when the driver reports both directions. */
    private static String linkSpeed(WifiInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int tx = info.getTxLinkSpeedMbps();
            int rx = info.getRxLinkSpeedMbps();
            if (tx > 0 && rx > 0 && tx != rx) {
                return String.format(Locale.getDefault(), "↑ %d  ·  ↓ %d Mb/s", tx, rx);
            }
        }
        return info.getLinkSpeed() + " Mb/s";
    }

    private void showRouterAndDns() {
        LinkProperties lp = null;
        try {
            lp = connectivity != null ? connectivity.getLinkProperties(connectivity.getActiveNetwork()) : null;
        } catch (Exception ignored) {
        }
        String gateway = null;
        StringBuilder servers = new StringBuilder();
        if (lp != null) {
            for (RouteInfo r : lp.getRoutes()) {
                InetAddress g = r.getGateway();
                if (r.isDefaultRoute() && g instanceof Inet4Address) gateway = g.getHostAddress();
            }
            for (InetAddress d : lp.getDnsServers()) {
                if (!(d instanceof Inet4Address)) continue;
                if (servers.length() > 0) servers.append(",  ");
                servers.append(d.getHostAddress());
            }
        }
        router.setText(gateway != null ? gateway : "—");
        dns.setText(servers.length() > 0 ? servers : "—");
    }

    /** Current download/upload rate of the whole TV, from the byte counters since the last refresh. */
    private void showThroughput() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long now = SystemClock.elapsedRealtime();
        if (rx == TrafficStats.UNSUPPORTED) {
            throughput.setText("—");
            return;
        }
        if (lastBytesRx >= 0 && now - lastBytesAt >= 1000) {
            double secs = (now - lastBytesAt) / 1000.0;
            throughput.setText("↓ " + rate((rx - lastBytesRx) / secs)
                    + "   ↑ " + rate((tx - lastBytesTx) / secs));
        } else if (lastBytesRx >= 0) {
            return; // too soon for a meaningful rate; keep the last reading
        } else {
            throughput.setText("merim …");
        }
        lastBytesRx = rx;
        lastBytesTx = tx;
        lastBytesAt = now;
    }

    private static String rate(double bytesPerSec) {
        double mbit = bytesPerSec * 8 / 1e6;
        if (mbit >= 10) return Math.round(mbit) + " Mb/s";
        if (mbit >= 0.1) return String.format(Locale.getDefault(), "%.1f Mb/s", mbit);
        return Math.round(bytesPerSec * 8 / 1e3) + " kb/s";
    }

    /** Times a TCP handshake to the internet off the main thread; shows "—" when unreachable. */
    private void measureLatency() {
        if (pinging) return;
        pinging = true;
        if (latency.getText().length() == 0) latency.setText("merim …");
        new Thread(() -> {
            long ms = -1;
            try (Socket s = new Socket()) {
                long start = SystemClock.elapsedRealtime();
                s.connect(PING_TARGET, 1500);
                ms = SystemClock.elapsedRealtime() - start;
            } catch (Exception ignored) {
            }
            long result = ms;
            post(() -> {
                pinging = false;
                latency.setText(result < 0 ? "ni internetne povezave"
                        : result + " ms do interneta");
            });
        }).start();
    }

    /** Wi-Fi 4/5/6 from the negotiated standard (Android 11+), or null if unknown. */
    private static String standard(WifiInfo info) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null;
        switch (info.getWifiStandard()) {
            case 4: return "Wi-Fi 4 (802.11n)";
            case 5: return "Wi-Fi 5 (802.11ac)";
            case 6: return "Wi-Fi 6 (802.11ax)";
            case 1: return "802.11a/b/g";
            default: return null;
        }
    }

    private static int channel(int mhz) {
        if (mhz == 2484) return 14;
        if (mhz >= 2412 && mhz < 2484) return (mhz - 2407) / 5;
        if (mhz >= 5955) return (mhz - 5950) / 5;
        if (mhz >= 4900) return (mhz - 5000) / 5;
        return -1;
    }

    private static String signalWord(int quality) {
        return quality >= 80 ? "odličen" : quality >= 60 ? "dober" : quality >= 40 ? "zmeren" : "šibek";
    }

    /** Common RSSI mapping: -50 dBm or better = 100 %, -100 dBm = 0 %. */
    private static int quality(int rssi) {
        return Math.max(0, Math.min(100, 2 * (rssi + 100)));
    }

    /** A "label   value" line in the stats list; returns the value view. */
    private TextView row(Context ctx, String label) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(HORIZONTAL);
        r.setPadding(0, Ui.dp(ctx, 2), 0, 0);
        TextView l = text(ctx, 13, Ui.TEXT_MUTED);
        l.setText(label);
        r.addView(l, new LayoutParams(Ui.dp(ctx, 104), -2));
        TextView v = text(ctx, 13, Ui.TEXT);
        r.addView(v);
        stats.addView(r);
        return v;
    }

    private static TextView text(Context ctx, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }
}
