package com.limelight.utils;

import com.limelight.nvstream.http.NvApp;
import java.util.List;
import java.util.Locale;

/** Resolves desktop entries without guessing that an arbitrary game is a desktop. */
public final class DesktopAppSelector {
    private DesktopAppSelector() { }

    /** Returns the remembered app or a unique best desktop match; null requires a choice. */
    public static NvApp select(List<NvApp> apps, int rememberedId) {
        NvApp selected = null;
        int best = 0;
        boolean ambiguous = false;
        for (NvApp app : apps) {
            if (!app.isInitialized() || app.getAppId() <= 0) {
                continue;
            }
            if (rememberedId > 0 && app.getAppId() == rememberedId) {
                return app;
            }
            int score = priority(app.getAppName());
            if (score > best) {
                best = score;
                selected = app;
                ambiguous = false;
            } else if (score > 0 && score == best && selected.getAppId() != app.getAppId()) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : selected;
    }

    private static int priority(String name) {
        if (name == null) {
            return 0;
        }
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "隐私桌面":
            case "privacy desktop":
                return 2;
            case "desktop":
            case "桌面":
            case "remote desktop":
                return 1;
            default:
                return 0;
        }
    }
}
