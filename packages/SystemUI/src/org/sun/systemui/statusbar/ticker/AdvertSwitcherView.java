/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.sun.systemui.statusbar.ticker;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ViewSwitcher;

import com.android.systemui.res.R;
import com.android.systemui.statusbar.policy.ClockCenter;

public class AdvertSwitcherView extends ViewSwitcher {

    private static final String TAG = "Ticker::AdvertSwitcherView";

    private OnModeChange mCallBack = null;
    private StatusBarNotification mCurrentNotification = null;

    private AdvertTickerView mTickerView;
    private ClockCenter mCenterClockView;
    private View mCurrentView;
    private View mLeftIconView;

    private boolean mShouldShowTicker;
    private int mUserId;

    public interface OnModeChange {
        void onChange(boolean show);
    }

    public AdvertSwitcherView(Context context) {
        super(context);
    }

    public AdvertSwitcherView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean addNotification(StatusBarNotification sbn) {
        if (sbn == null || sbn.getUserId() != mUserId) {
            return false;
        }
        if (mTickerView == null) {
            return false;
        }
        if (mTickerView.addNotification(sbn)) {
            mCurrentNotification = sbn;
            if (mCurrentView != mTickerView && mShouldShowTicker) {
                mCurrentView = mTickerView;
                setDisplayedChild(indexOfChild(mCurrentView));
                applyCallBack(true);
            }
            return true;
        }
        hideTickerViewIfNeed();
        return false;
    }

    private void hideTickerViewIfNeed() {
        if (mTickerView.isShow()) {
            return;
        }
        mCurrentNotification = null;
        if (mCurrentView != mLeftIconView) {
            mCurrentView = mLeftIconView;
            setDisplayedChild(indexOfChild(mCurrentView));
            applyCallBack(false);
        }
    }

    public void hideLrcTickerView(boolean isOnGoing, String pkgName) {
        if (TextUtils.equals(pkgName, mTickerView.getCurrentPkg())) {
            updateTickerViewVisibility(isOnGoing);
            if (!isOnGoing && mCurrentNotification != null) {
                removeNotification(mCurrentNotification.getKey());
            }
        }
    }

    public void removeNotification(String key) {
        if (mTickerView != null) {
            mTickerView.removeNotification(key);
            hideTickerViewIfNeed();
        }
    }

    public void userSwitched(int newUserId) {
        if (mUserId == newUserId) {
            return;
        }
        mUserId = newUserId;
        if (mCurrentNotification == null) {
            return;
        }
        if (mCurrentNotification.getUserId() == mUserId) {
            addNotification(mCurrentNotification);
        } else {
            removeNotification(mCurrentNotification.getKey());
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTickerView = (AdvertTickerView) findViewById(R.id.ticker_ext);
        mLeftIconView = findViewById(R.id.status_bar_start_side_content);
    }

    private void applyCallBack(boolean show) {
        if (mCallBack != null) {
            mCallBack.onChange(show);
        }
        if (mCenterClockView != null && mCenterClockView.isCenterClock()) {
            if (show) {
                mCenterClockView.setVisibility(View.GONE);
                mCenterClockView.setVisibilityLocked(true);
            } else {
                mCenterClockView.setVisibilityLocked(false);
                mCenterClockView.setVisibility(View.VISIBLE);
            }
        }
    }

    public void setCallBack(OnModeChange callBack) {
        mCallBack = callBack;
    }

    public void setCenterClockView(ClockCenter centerClockView) {
        mCenterClockView = centerClockView;
    }

    public void updateTickerViewVisibility(boolean visible) {
        if (mShouldShowTicker == visible) {
            return;
        }
        mShouldShowTicker = visible;
        if (!mTickerView.isShow()) {
            return;
        }
        if (visible) {
            mCurrentView = mTickerView;
            setDisplayedChild(indexOfChild(mCurrentView));
            applyCallBack(true);
        } else {
            mCurrentView = mLeftIconView;
            setDisplayedChild(indexOfChild(mCurrentView));
            applyCallBack(false);
        }
    }

    public void setAdvertTickerViewPadding(int paddingEnd) {
        mTickerView.setPadding(0, 0, paddingEnd, 0);
    }

    public boolean isShow() {
        return mTickerView.isShow() && mShouldShowTicker;
    }
}
