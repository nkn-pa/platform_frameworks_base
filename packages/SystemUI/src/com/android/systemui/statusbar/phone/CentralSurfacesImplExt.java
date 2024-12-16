/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_TICKER;

import android.annotation.NonNull;
import android.app.Notification;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;

import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.notification.collection.NotifCollection.CancellationReason;
import com.android.systemui.statusbar.notification.collection.NotifPipeline;
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider;
import com.android.systemui.statusbar.policy.ClockCenter;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.window.StatusBarWindowController;

import org.sun.systemui.statusbar.ticker.AdvertSwitcherView;
import org.sun.systemui.statusbar.ticker.MarqueeTickerEx;
import org.sun.systemui.statusbar.ticker.MarqueeTickerView;
import org.sun.systemui.statusbar.ticker.TickerController;
import org.sun.systemui.statusbar.ticker.TickerEx;

class CentralSurfacesImplExt {

    private static final String TAG = "CentralSurfacesImplExt";
    private static final boolean DEBUG_TICKER = false;

    private static class InstanceHolder {
        private static CentralSurfacesImplExt INSTANCE = new CentralSurfacesImplExt();
    }

    static CentralSurfacesImplExt getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private CentralSurfacesImpl mCentralSurfacesImpl;
    private Context mContext;
    private DemoModeController mDemoModeController;
    private DeviceProvisionedController mDeviceProvisionedController;
    private HeadsUpManagerPhone mHeadsUpManager;
    private KeyguardStateController mKeyguardStateController;
    private NotifCollectionListener mNotifCollectionListener;
    private NotifPipeline mNotifPipeline;
    private NotificationInterruptStateProvider mNotificationInterruptStateProvider;
    private NotificationLockscreenUserManager mLockscreenUserManager;
    private StatusBarWindowController mStatusBarWindowController;
    private TickerController mTickerController;

    private AdvertSwitcherView mSwitcherView;
    private MarqueeTickerEx mTicker;

    void init(CentralSurfacesImpl centralSurfacesImpl,
            Context context,
            DemoModeController demoModeController,
            DeviceProvisionedController deviceProvisionedController,
            HeadsUpManagerPhone headsUpManager,
            KeyguardStateController keyguardStateController,
            NotifPipeline notifPipeline,
            NotificationInterruptStateProvider notificationInterruptStateProvider,
            NotificationLockscreenUserManager lockscreenUserManager,
            StatusBarWindowController statusBarWindowController,
            TickerController tickerController) {
        mCentralSurfacesImpl = centralSurfacesImpl;
        mContext = context;
        mDemoModeController = demoModeController;
        mDeviceProvisionedController = deviceProvisionedController;
        mHeadsUpManager = headsUpManager;
        mLockscreenUserManager = lockscreenUserManager;
        mKeyguardStateController = keyguardStateController;
        mNotifPipeline = notifPipeline;
        mNotificationInterruptStateProvider = notificationInterruptStateProvider;
        mStatusBarWindowController = statusBarWindowController;
        mTickerController = tickerController;
    }

    private void initEntryListener() {
        mNotifCollectionListener = new NotifCollectionListener() {
            @Override
            public void onEntryAdded(@NonNull NotificationEntry entry) {
                if (!mTickerController.showNotificationTicker()) {
                    return;
                }
                if (shouldFilterHeadsUpNotification(entry)) {
                    return;
                }
                TickerEx.tickFilter(entry, false, () -> tick(entry, true));
            }

            @Override
            public void onEntryUpdated(@NonNull NotificationEntry entry) {
                if (!mTickerController.showNotificationTicker()) {
                    return;
                }
                if (mDemoModeController.isInDemoMode()) {
                    return;
                }
                if (shouldUpdateNotificationTicker(entry.getSbn())) {
                    updateSwitcherViewVisibility(true);
                } else {
                    if (shouldFilterHeadsUpNotification(entry)) {
                        return;
                    }
                    TickerEx.tickFilter(entry, false, () -> tick(entry, false));
                }
            }

            @Override
            public void onEntryRemoved(@NonNull NotificationEntry entry, @CancellationReason int reason) {
                TickerEx.removeTickFilter(entry);
                mTicker.removeEntry(entry.getSbn());
                mSwitcherView.removeNotification(entry.getSbn().getKey());
            }
        };
        mNotifPipeline.addCollectionListener(mNotifCollectionListener);
    }

    void initTicker(PhoneStatusBarView statusBarView) {
        if (mNotifCollectionListener != null) {
            mNotifPipeline.removeCollectionListener(mNotifCollectionListener);
        }
        mSwitcherView = (AdvertSwitcherView) statusBarView.findViewById(R.id.status_bar_switcher);
        mTicker = inflateTickerView(statusBarView);
        mTicker.setStatusBarContents(statusBarView.findViewById(R.id.status_bar_contents));
        mTicker.setSwitcherView(mSwitcherView);
        mTicker.setCenterClockView((ClockCenter) statusBarView.findViewById(R.id.center_clock));
        initEntryListener();
    }

    private MarqueeTickerEx inflateTickerView(PhoneStatusBarView statusBarView) {
        final ViewStub tickerStub = (ViewStub) statusBarView.findViewById(R.id.ticker_stub);
        if (tickerStub == null) {
            return null;
        }
        final View tickerView = tickerStub.inflate();
        final MarqueeTickerEx marqueeTicker = new MarqueeTickerEx(mContext, statusBarView);
        marqueeTicker.setTickerView(tickerView);
        final MarqueeTickerView tickerText = (MarqueeTickerView) statusBarView.findViewById(R.id.tickerText);
        tickerText.setTicker(marqueeTicker);
        statusBarView.setTickerView(tickerView);
        return marqueeTicker;
    }

    private void tick(NotificationEntry notificationEntry, boolean firstTime) {
        if (mDemoModeController.isInDemoMode()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: in demo mode");
            }
            return;
        }
        if (!mDeviceProvisionedController.isDeviceProvisioned()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: device is not provisioned");
            }
            return;
        }
        final StatusBarNotification n = notificationEntry.getSbn();
        final int notificationUserId = n.getUserId();
        if (!mLockscreenUserManager.isCurrentProfile(notificationUserId)) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: not for current user");
            }
            return;
        }
        if (mHeadsUpManager.hasPinnedHeadsUp()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: already has pinned heads up");
            }
            return;
        }
        if (mKeyguardStateController.isShowing() && !mKeyguardStateController.isOccluded()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: keyguard showing and not occluded");
            }
            return;
        }
        if (mCentralSurfacesImpl.getNotificationPanelViewController().isFullyExpanded()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: notification panel is fully expanded");
            }
            return;
        }
        if (mLockscreenUserManager.isAnyProfilePublicMode()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: any of the profiles are in public mode");
            }
            return;
        }
        if (n.getNotification().tickerText == null ||
                n.getNotification().tickerText.toString().isEmpty()) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: tickerText is empty");
            }
            return;
        }
        if (mCentralSurfacesImpl.getNotificationShadeWindowView().getWindowToken() == null) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: window token is null");
            }
            return;
        }
        if ((mCentralSurfacesImpl.getDisabled1() & (DISABLE_NOTIFICATION_ICONS | DISABLE_NOTIFICATION_TICKER)) != 0) {
            if (DEBUG_TICKER) {
                Log.d(TAG, "tick, return: notification icon/ticker disabled");
            }
            return;
        }

        mTicker.halt();
        if (!mSwitcherView.addNotification(n)) {
            mTicker.addEntry(n);
        }
    }

    void tickerHalt() {
        if (DEBUG_TICKER) {
            Log.d(TAG, "tickerHalt");
        }
        if (mTicker != null) {
            mTicker.halt();
        }
        updateSwitcherViewVisibility(false);
    }

    private boolean shouldFilterHeadsUpNotification(NotificationEntry entry) {
        if (mHeadsUpManager.shouldHeadsUpBecomePinned(entry) &&
                mNotificationInterruptStateProvider.shouldHeadsUp(entry) &&
                !mHeadsUpManager.isSnoozed(entry.getSbn().getPackageName())) {
            return true;
        }
        return false;
    }

    private boolean shouldUpdateNotificationTicker(StatusBarNotification sbn) {
        if (sbn == null || mSwitcherView == null) {
            return false;
        }
        final Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }
        if (!mSwitcherView.addNotification(sbn)) {
            return false;
        }
        return (notification.flags & Notification.FLAG_ONLY_UPDATE_TICKER) != 0;
    }

    private void updateSwitcherViewVisibility(boolean visible) {
        if (mSwitcherView != null) {
            visible &= !(mKeyguardStateController.isShowing() && mKeyguardStateController.isOccluded());
            if (DEBUG_TICKER) {
                Log.d(TAG, "updateSwitcherViewVisibility, visible=" + visible);
            }
            mSwitcherView.updateTickerViewVisibility(visible);
        }
    }
}
