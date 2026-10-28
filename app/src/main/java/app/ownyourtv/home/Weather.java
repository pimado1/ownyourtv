package app.ownyourtv.home;

import app.ownyourtv.R;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Current weather for one place under the top buttons (the overlay's weather_latitude, weather_longitude,
 * weather_place). Read from Open-Meteo, no key or account: only the coordinates are sent. Off unless both
 * coordinates are set.
 */
final class Weather {
    private static final String TAG = "Weather";
    static final long REFRESH_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;

    final double latitude;
    final double longitude;
    final String place;

    private Weather(double latitude, double longitude, String place) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place;
    }

    private static final String PREFS = "horizon_weather";

    /** The city chosen on the TV (Réglages rapides ▸ Météo) wins over the overlay's coordinates. */
    static void save(Context ctx, double lat, double lon, String place) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("lat", String.valueOf(lat)).putString("lon", String.valueOf(lon))
                .putString("place", place).apply();
    }

    static final class Place {
        double latitude, longitude;
        String name, detail;
    }

    /** Open-Meteo's place search: up to six matches for a city name, in French. Blocking; empty on failure. */
    static java.util.List<Place> search(String query) {
        java.util.List<Place> out = new java.util.ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?count=6&language=fr&format=json&name="
                    + java.net.URLEncoder.encode(query.trim(), "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return out;
            org.json.JSONArray results = new JSONObject(body(conn.getInputStream())).optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                Place p = new Place();
                p.latitude = r.getDouble("latitude");
                p.longitude = r.getDouble("longitude");
                p.name = r.optString("name");
                String admin = r.optString("admin1", ""), country = r.optString("country", "");
                p.detail = admin.isEmpty() ? country : admin + ", " + country;
                out.add(p);
            }
        } catch (Exception e) {
            Log.w(TAG, "place search: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    /** The configured place, or null when neither the TV nor the overlay sets one. */
    static Weather from(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.contains("lat")) {
            try {
                return new Weather(Double.parseDouble(prefs.getString("lat", "")),
                        Double.parseDouble(prefs.getString("lon", "")), prefs.getString("place", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        JSONObject json = HomePrefs.overlay(ctx);
        if (json == null || !json.has("weather_latitude") || !json.has("weather_longitude")) return null;
        double lat = json.optDouble("weather_latitude", Double.NaN);
        double lon = json.optDouble("weather_longitude", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
        return new Weather(lat, lon, json.optString("weather_place", "").trim());
    }

    /** "☁  16°  Nuageux · Lyon", or null on any failure. Blocking: call off the main thread. */
    String readLine(Context ctx) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code,is_day&timezone=auto", latitude, longitude));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;
            JSONObject current = new JSONObject(body(conn.getInputStream())).getJSONObject("current");
            long temp = Math.round(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            boolean day = current.optInt("is_day", 1) == 1;
            StringBuilder line = new StringBuilder();
            line.append(icon(code, day)).append("  ").append(temp).append("°  ").append(ctx.getString(labelRes(code)));
            if (!place.isEmpty()) line.append(" · ").append(place);
            return line.toString();
        } catch (Exception e) {
            Log.w(TAG, "weather: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** One reading for the Horizon home: now, today's sun times, and the next two days. */
    static final class Reading {
        long temp;
        long feels;
        int code;
        boolean day;
        String sunrise = "";
        String sunset = "";
        float sunriseHour = 8f;
        float sunsetHour = 17.5f;
        static final int DAYS = 6;
        final String[] nextDays = new String[DAYS];
        final long[] nextMax = new long[DAYS];
        final long[] nextMin = new long[DAYS];
        final int[] nextCode = new int[DAYS];

        int label() {
            return Weather.labelRes(code);
        }

        boolean fog() {
            return isFog(code);
        }

        boolean rain() {
            return isRain(code);
        }

        boolean snow() {
            return isSnow(code);
        }
    }

    static boolean isFog(int code) {
        return code == 45 || code == 48;
    }

    static boolean isRain(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    static boolean isSnow(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    /** Blocking, off the main thread; null on any failure. */
    Reading read() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                            + "&forecast_days=7&timezone=auto", latitude, longitude));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;
            JSONObject json = new JSONObject(body(conn.getInputStream()));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            Reading r = new Reading();
            r.temp = Math.round(current.getDouble("temperature_2m"));
            r.feels = Math.round(current.optDouble("apparent_temperature", current.getDouble("temperature_2m")));
            r.code = current.getInt("weather_code");
            r.day = current.optInt("is_day", 1) == 1;
            String rise = daily.getJSONArray("sunrise").getString(0);
            String set = daily.getJSONArray("sunset").getString(0);
            r.sunrise = rise.substring(rise.indexOf('T') + 1);
            r.sunset = set.substring(set.indexOf('T') + 1);
            r.sunriseHour = hours(r.sunrise);
            r.sunsetHour = hours(r.sunset);
            for (int i = 0; i < Reading.DAYS; i++) {
                String date = daily.getJSONArray("time").getString(i + 1);
                r.nextDays[i] = dayName(date);
                r.nextMax[i] = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i + 1));
                r.nextMin[i] = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(i + 1));
                r.nextCode[i] = daily.getJSONArray("weather_code").getInt(i + 1);
            }
            return r;
        } catch (Exception e) {
            Log.w(TAG, "weather: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static float hours(String hhmm) {
        try {
            return Integer.parseInt(hhmm.substring(0, 2)) + Integer.parseInt(hhmm.substring(3, 5)) / 60f;
        } catch (RuntimeException e) {
            return 12f;
        }
    }

    private static String dayName(String isoDate) {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate));
            String d = new java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(c.getTime());
            return Character.toUpperCase(d.charAt(0)) + d.substring(1);
        } catch (Exception e) {
            return "";
        }
    }

    private static String body(InputStream in) throws java.io.IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    private static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code == 85 || code == 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them; 0 for an unknown code. */
    static int labelRes(int code) {
        if (code == 0) return R.string.w_clear;
        if (code == 1) return R.string.w_few;
        if (code == 2) return R.string.w_partly;
        if (code == 3) return R.string.w_cloudy;
        if (code == 45 || code == 48) return R.string.w_fog;
        if (code >= 51 && code <= 57) return R.string.w_drizzle;
        if (code >= 61 && code <= 67) return R.string.w_rain;
        if (code >= 71 && code <= 77) return R.string.w_snow;
        if (code >= 80 && code <= 82) return R.string.w_showers;
        if (code == 85 || code == 86) return R.string.w_snow_showers;
        if (code >= 95) return R.string.w_storm;
        return R.string.w_cloudy;
    }
}
