package com.android.cts.verifier.notifications;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.EditText;
import android.widget.TextView;

import com.android.cts.verifier.R;

/**
 * Used in BubblesVerifierActivity as the contents of the bubble.
 */
public class BubbleActivity extends Activity {
    public static final String EXTRA_TEST_NAME = "test_id";
    public static final String TEST_MIN_HEIGHT = "minHeight";
    public static final String TEST_MAX_HEIGHT = "maxHeight";

    private View mRoot;
    private TextView mTitle;
    private TextView mTestMessage;
    private EditText mEditText;

    private String mTestName = null;
    private Rect mBounds = new Rect();
    private boolean mIsLargeScreen;

    private ViewTreeObserver.OnGlobalLayoutListener mListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mRoot.getBoundsOnScreen(mBounds);
                    checkHeight();
                    mRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            };

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.bubble_activity);
        mRoot = findViewById(R.id.layout_root);
        mTitle = findViewById(R.id.title_text);
        mTestMessage = findViewById(R.id.test_message);
        mEditText = findViewById(R.id.edit_text);

        mIsLargeScreen = getResources().getConfiguration().smallestScreenWidthDp >= 600;

        getActionBar().hide();
        setUpTestForExtras();
    }

    private void setUpTestForExtras() {
        mTestName = getIntent().getStringExtra(EXTRA_TEST_NAME);
        if (mTestName == null) {
            mTestMessage.setVisibility(GONE);
            return;
        }
        if (TEST_MIN_HEIGHT.equals(mTestName)
                || TEST_MAX_HEIGHT.equals(mTestName)) {
            mTestMessage.setVisibility(VISIBLE);
            mTitle.setVisibility(GONE);
            mEditText.setVisibility(GONE);
            ViewTreeObserver observer = mRoot.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(mListener);
        }
    }

    private void checkHeight() {
        if (TEST_MIN_HEIGHT.equals(mTestName)) {
            // Height should be equal or larger than 180dp
            int minHeight = getResources().getDimensionPixelSize(
                    R.dimen.bubble_expanded_view_min_height);
            if (mRoot.getHeight() < minHeight) {
                mTestMessage.setText("Test failed -- height too small! bubble expanded view is: "
                        + mRoot.getHeight() + " vs desired minimum height:" + minHeight);
            } else {
                mTestMessage.setText("Test Passed!");
            }
        } else if (TEST_MAX_HEIGHT.equals(mTestName)) {
            WindowMetrics windowMetrics =
                    getSystemService(WindowManager.class).getMaximumWindowMetrics();
            WindowInsets metricInsets = windowMetrics.getWindowInsets();
            Insets insets = metricInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars()
                            | WindowInsets.Type.statusBars()
                            | WindowInsets.Type.displayCutout());
            if (mIsLargeScreen) {
                final float percentOfScreen = windowMetrics.getBounds().height() * 0.70f;
                if (mBounds.height() < percentOfScreen) {
                    mTestMessage.setText("Test failed --"
                            + " the bubble expanded view is too small, it is: " + mBounds.height()
                            + " and minimum is: " + percentOfScreen);
                } else {
                    mTestMessage.setText("Test Passed!");
                }
            } else {
                // Bottom of bubble view should be close to the bottom inset. There needs to be some
                // padding for the manage button so we allow some flux (200dp).
                int maxHeightBuffer = getResources().getDimensionPixelSize(
                        R.dimen.bubble_expanded_view_max_height_buffer);
                int bottomInset = windowMetrics.getBounds().bottom - insets.bottom;
                int diff = bottomInset - mBounds.bottom;
                if (diff > maxHeightBuffer) {
                    mTestMessage.setText("Test failed -- bottom of the bubble expanded view "
                            + "isn't close enough to the bottom inset: " + diff);
                } else {
                    mTestMessage.setText("Test Passed!");
                }
            }
        }
    }
}