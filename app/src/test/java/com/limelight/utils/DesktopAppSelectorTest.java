package com.limelight.utils;

import com.limelight.nvstream.http.NvApp;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class DesktopAppSelectorTest {
    @Test public void prefersPrivacyEvenWhenDesktopListedFirst() {
        NvApp desktop = new NvApp("Desktop", 1, false);
        NvApp privacy = new NvApp("隐私桌面", 2, false);
        assertSame(privacy, DesktopAppSelector.select(Arrays.asList(desktop, privacy), 0));
    }
    @Test public void rememberedEntryWinsAfterRenameAndReordering() {
        NvApp remembered = new NvApp("Workstation", 7, false);
        assertSame(remembered, DesktopAppSelector.select(Arrays.asList(
                new NvApp("Privacy Desktop", 2, false), remembered), 7));
    }
    @Test public void removedRememberedEntryFallsBackToRecognizedDesktop() {
        NvApp desktop = new NvApp(" desktop ", 3, false);
        assertSame(desktop, DesktopAppSelector.select(Collections.singletonList(desktop), 99));
    }
    @Test public void duplicatePrivacyNamesRequireAChoice() {
        assertNull(DesktopAppSelector.select(Arrays.asList(new NvApp("隐私桌面", 1, false),
                new NvApp("Privacy Desktop", 2, false)), 0));
    }
    @Test public void neverLaunchesTheOnlyGameAsADesktop() {
        assertNull(DesktopAppSelector.select(Collections.singletonList(new NvApp("Steam", 1, false)), 0));
    }
    @Test public void ignoresUninitializedAndInvalidIds() {
        assertNull(DesktopAppSelector.select(Arrays.asList(new NvApp("Desktop"),
                new NvApp("Privacy Desktop", 0, false)), 0));
        assertNull(DesktopAppSelector.select(Collections.emptyList(), 0));
    }
}
