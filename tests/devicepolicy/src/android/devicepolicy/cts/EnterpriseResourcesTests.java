/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.devicepolicy.cts;

import static android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES;
import static android.app.admin.DevicePolicyResources.Drawable.INVALID_ID;
import static android.app.admin.DevicePolicyResources.Drawable.Style.SOLID_COLORED;
import static android.app.admin.DevicePolicyResources.Drawable.WORK_PROFILE_ICON;
import static android.app.admin.DevicePolicyResources.Drawable.WORK_PROFILE_ICON_BADGE;
import static android.app.admin.DevicePolicyResources.Drawable.WORK_PROFILE_OFF_ICON;
import static android.app.admin.DevicePolicyResources.Drawable.WORK_PROFILE_USER_ICON;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyDrawableResource;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

// TODO(b/208084779): Add more cts tests to cover setting different styles and sources, also
//  add more tests to cover calling from other packages after adding support for the new APIs in
//  the test sdk.
@RunWith(BedsteadJUnit4.class)
public class EnterpriseResourcesTests {
    private static final String TAG = "EnterpriseResourcesTests";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDpm =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final int UPDATABLE_DRAWABLE_1 = WORK_PROFILE_ICON_BADGE;
    private static final int UPDATABLE_DRAWABLE_2 = WORK_PROFILE_ICON;
    private static final int DRAWABLE_STYLE_1 = SOLID_COLORED;
    private static final int INVALID_DRAWABLE_RESOURCE_ID = -1;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @After
    public void tearDown() {
        resetAllDrawables();
    }

    @Before
    public void setup() {
        resetAllDrawables();
    }

    private void resetAllDrawables() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sDpm.resetDrawables(
                    new int[]{
                            WORK_PROFILE_ICON_BADGE,
                            WORK_PROFILE_ICON,
                            WORK_PROFILE_OFF_ICON,
                            WORK_PROFILE_USER_ICON});
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDpm.setDrawables(createDrawable(
                        UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withInvalidUpdatableDrawableId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                sDpm.setDrawables(createDrawable(
                        INVALID_ID, DRAWABLE_STYLE_1, R.drawable.test_drawable_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updatesCorrectUpdatableDrawable() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_1,
                DRAWABLE_STYLE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updatesCurrentlyUpdatedDrawable() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_doesNotUpdateOtherUpdatableDrawables() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_2});

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        assertThat(
                sDpm.getDrawable(UPDATABLE_DRAWABLE_2, DRAWABLE_STYLE_1, /* default= */ () -> null))
                .isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_drawableChangedFromNull_sendsBroadcast() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_drawableChangedFromOtherDrawable_sendsBroadcast() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void setDrawables_drawableNotChanged_doesNotSendBroadcast() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDpm.resetDrawables(
                new int[]{UPDATABLE_DRAWABLE_1}));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_removesPreviouslysetDrawables() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});

        assertThat(
                sDpm.getDrawable(UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, /* default= */ () -> null))
                .isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_doesNotResetOtherSetDrawables() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_2, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_2, DRAWABLE_STYLE_1, /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_drawableChanged_sendsBroadcast() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void resetDrawables_drawableNotChanged_doesNotSendBroadcast() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_1});

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    public void getDrawable_drawableIsSet_returnsUpdatedDrawable() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sDpm.setDrawables(createDrawable(
                    UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        }

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, /* default= */ () -> null);

        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    public void getDrawable_drawableIsNotSet_returnsDefaultDrawable() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_1);

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);

        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    public void getDrawable_defaultIsNull_throwsException() {
        assertThrows(NullPointerException.class, () -> sDpm.getDrawable(
                UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyDrawableResource_withNonExistentDrawable_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, INVALID_DRAWABLE_RESOURCE_ID));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyDrawableResourc_withNonDrawableResource_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_1, DRAWABLE_STYLE_1, R.string.test_string));
    }

    // TODO(b/16348282): extract to a common place to make it reusable.
    private static boolean areSameDrawables(Drawable drawable1, Drawable drawable2) {
        return drawable1 == drawable2 || getBitmap(drawable1).sameAs(getBitmap(drawable2));
    }

    private static Bitmap getBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        // Some drawables have no intrinsic width - e.g. solid colours.
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return result;
    }

    private Set<DevicePolicyDrawableResource> createDrawable(
            int updatableDrawableId, int style, int resourceId) {
        return Set.of(new DevicePolicyDrawableResource(
                sContext, updatableDrawableId, style, resourceId));
    }
}
