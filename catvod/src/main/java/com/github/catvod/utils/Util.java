package com.github.catvod.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Base64;

import com.github.catvod.Runtime;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import okhttp3.OkHttp;

public class Util {

    public static final String OKHTTP = "okhttp/" + OkHttp.VERSION;
    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36";
    public static final int URL_SAFE = Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP;

    private static final Pattern INVALID_PATTERN = Pattern.compile("(?!)");
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    public static String base64(String s) {
        return base64(s.getBytes());
    }

    public static String base64(byte[] bytes) {
        return base64(bytes, Base64.DEFAULT | Base64.NO_WRAP);
    }

    public static String base64(String s, int flags) {
        return base64(s.getBytes(), flags);
    }

    public static String base64(byte[] bytes, int flags) {
        return Base64.encodeToString(bytes, flags);
    }

    public static byte[] decode(String s) {
        return decode(s, Base64.DEFAULT | Base64.NO_WRAP);
    }

    public static byte[] decode(String s, int flags) {
        return Base64.decode(s, flags);
    }

    public static byte[] hex2byte(String s) {
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new NumberFormatException("Invalid hex: " + s.substring(i * 2, i * 2 + 2));
            bytes[i] = (byte) ((hi << 4) + lo);
        }
        return bytes;
    }

    public static boolean containOrMatch(String text, String regex) {
        try {
            if (text.contains(regex)) return true;
            Pattern pattern = PATTERN_CACHE.get(regex);
            if (pattern == null) {
                try {
                    pattern = Pattern.compile(regex);
                } catch (Exception e) {
                    pattern = INVALID_PATTERN;
                }
                if (PATTERN_CACHE.size() < 128) PATTERN_CACHE.put(regex, pattern);
            }
            return pattern != INVALID_PATTERN && pattern.matcher(text).matches();
        } catch (Exception e) {
            return false;
        }
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static String getIp() {
        try {
            String ip = getHostAddress("wlan");
            if (!ip.isEmpty()) return ip;
            ip = getHostAddress("eth");
            if (!ip.isEmpty()) return ip;
            ip = getWifiAddress();
            if (!ip.isEmpty()) return ip;
            return getHostAddress("");
        } catch (Exception e) {
            return "";
        }
    }

    private static String getWifiAddress() {
        WifiManager manager = (WifiManager) Runtime.context().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = manager.getConnectionInfo().getIpAddress();
        return ip == 0 ? "" : String.format(Locale.getDefault(), "%d.%d.%d.%d", ip & 0xFF, (ip >> 8) & 0xFF, (ip >> 16) & 0xFF, (ip >> 24) & 0xFF);
    }

    private static String getHostAddress(String keyword) throws SocketException {
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
            NetworkInterface nif = en.nextElement();
            if (!keyword.isEmpty() && !nif.getName().startsWith(keyword)) continue;
            for (Enumeration<InetAddress> addresses = nif.getInetAddresses(); addresses.hasMoreElements(); ) {
                InetAddress addr = addresses.nextElement();
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
            }
        }
        return "";
    }
}
