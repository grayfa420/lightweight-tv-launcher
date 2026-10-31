package com.cleanhome.tv;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Today's weather from Open-Meteo (free, no API key). */
final class Weather {
    // Zreče, Slovenia
    static final double LATITUDE = 46.3822;
    static final double LONGITUDE = 15.3792;

    final double temperature;
    final double high;
    final double low;
    final int rainChance;
    final int code;
    final boolean isDay;

    private Weather(double temperature, double high, double low, int rainChance, int code, boolean isDay) {
        this.temperature = temperature;
        this.high = high;
        this.low = low;
        this.rainChance = rainChance;
        this.code = code;
        this.isDay = isDay;
    }

    /** Blocking; call off the main thread. */
    static Weather fetch() throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=precipitation_probability_max,temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=1",
                LATITUDE, LONGITUDE);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject json = new JSONObject(out.toString("UTF-8"));
            JSONObject now = json.getJSONObject("current");
            JSONObject day = json.getJSONObject("daily");
            return new Weather(
                    now.getDouble("temperature_2m"),
                    day.getJSONArray("temperature_2m_max").getDouble(0),
                    day.getJSONArray("temperature_2m_min").getDouble(0),
                    day.getJSONArray("precipitation_probability_max").optInt(0, 0),
                    now.getInt("weather_code"),
                    now.optInt("is_day", 1) == 1);
        } finally {
            c.disconnect();
        }
    }

    /** Emoji for a WMO weather code. */
    String icon() {
        if (code == 0) return isDay ? "☀️" : "🌙";          // ☀️ / 🌙
        if (code <= 2) return isDay ? "⛅" : "☁️";               // ⛅ / ☁️
        if (code == 3) return "☁️";                                   // ☁️
        if (code == 45 || code == 48) return "🌫️";              // 🌫️
        if (code >= 51 && code <= 57) return "🌦️";              // 🌦️
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄️"; // ❄️
        if (code >= 95) return "⛈️";                                  // ⛈️
        return "🌧️";                                            // 🌧️ rain & showers
    }
}
