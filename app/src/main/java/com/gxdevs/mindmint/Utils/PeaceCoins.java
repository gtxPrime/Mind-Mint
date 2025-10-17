package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PeaceCoins {
    private static final String PREFS_NAME = "PeaceCoinsPrefs";
    private static final String COINS_KEY = "PeaceCoins";

    private SharedPreferences prefs;

    public PeaceCoins(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void addCoins(int amount) {
        int current = getCoins();
        setCoins(current + amount);
    }

    public synchronized void subtractCoins(int amount) {
        int current = getCoins();
        int newTotal = Math.max(0, current - amount);
        setCoins(newTotal);
    }

    public synchronized int getCoins() {
        return prefs.getInt(COINS_KEY, 0);
    }

    private void setCoins(int coins) {
        prefs.edit().putInt(COINS_KEY, coins).apply();
    }
}
