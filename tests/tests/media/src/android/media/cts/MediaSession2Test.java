/*
 * Copyright 2018 The Android Open Source Project
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

package android.media.cts;

import static android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
import static android.media.MediaItem2.FLAG_PLAYABLE;
import static android.media.cts.TestUtils.ensurePlaylistParamsModeEquals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.DataSourceDesc;
import android.media.MediaController2;
import android.media.MediaController2.ControllerCallback;
import android.media.MediaController2.PlaybackInfo;
import android.media.MediaItem2;
import android.media.MediaMetadata2;
import android.media.MediaPlayerBase;
import android.media.MediaPlaylistAgent;
import android.media.MediaSession2;
import android.media.MediaSession2.Builder;
import android.media.MediaSession2.Command;
import android.media.MediaSession2.CommandButton;
import android.media.MediaSession2.CommandGroup;
import android.media.MediaSession2.ControllerInfo;
import android.media.MediaSession2.PlaylistParams;
import android.media.MediaSession2.SessionCallback;
import android.media.VolumeProvider2;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MediaSession2}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaSession2Test extends MediaSession2TestBase {
    private static final String TAG = "MediaSession2Test";

    private MediaSession2 mSession;
    private MockPlayer mPlayer;

    // Note: If your tests needs notifying an event from the playlist agent, don't use this.
    //       Here's the reason. When this notifies an event, it passes its mInstance to the session,
    //       but the mInstance differs from the mMockAgent so session discards event from this.
    private MockPlaylistAgent mMockAgent;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPlayer = new MockPlayer(0);
        mMockAgent = spy(new MockPlaylistAgent(mContext));
        mSession = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setPlaylistAgent(mMockAgent)
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                    @Override
                    public CommandGroup onConnect(MediaSession2 session,
                            ControllerInfo controller) {
                        if (Process.myUid() == controller.getUid()) {
                            return super.onConnect(session, controller);
                        }
                        return null;
                    }
                }).build();
    }

    @After
    @Override
    public void cleanUp() throws Exception {
        super.cleanUp();
        mSession.close();
    }

    @Ignore
    @Test
    public void testBuilder() throws Exception {
        try {
            MediaSession2.Builder builder = new Builder(mContext);
            fail("null player shouldn't be allowed");
        } catch (IllegalArgumentException e) {
            // expected. pass-through
        }
        MediaSession2.Builder builder = new Builder(mContext).setPlayer(mPlayer);
        try {
            builder.setId(null);
            fail("null id shouldn't be allowed");
        } catch (IllegalArgumentException e) {
            // expected. pass-through
        }
    }

    @Test
    public void testPlayerStateChange() throws Exception {
        final int targetState = MediaPlayerBase.PLAYER_STATE_PLAYING;
        final CountDownLatch latchForSessionCallback = new CountDownLatch(1);
        sHandler.postAndSync(() -> {
            mSession.close();
            mSession = new MediaSession2.Builder(mContext).setPlayer(mPlayer)
                    .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                        @Override
                        public void onPlayerStateChanged(MediaSession2 session,
                                MediaPlayerBase player, int state) {
                            assertEquals(targetState, state);
                            latchForSessionCallback.countDown();
                        }
                    }).build();
        });

        final CountDownLatch latchForControllerCallback = new CountDownLatch(1);
        final MediaController2 controller =
                createController(mSession.getToken(), true, new ControllerCallback() {
                    @Override
                    public void onPlayerStateChanged(MediaController2 controllerOut, int state) {
                        assertEquals(targetState, state);
                        latchForControllerCallback.countDown();
                    }
                });

        mPlayer.notifyPlaybackState(MediaPlayerBase.PLAYER_STATE_PLAYING);
        assertTrue(latchForSessionCallback.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(latchForControllerCallback.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertEquals(targetState, controller.getPlayerState());
    }

    @Test
    public void testCurrentDataSourceChanged() throws Exception {
        final int listSize = 5;
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, listSize);
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public List<MediaItem2> getPlaylist() {
                return list;
            }
        };

        MediaItem2 currentItem = list.get(3);

        final CountDownLatch latchForSessionCallback = new CountDownLatch(1);
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setPlaylistAgent(agent)
                .setId("testCurrentDataSourceChanged")
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                    @Override
                    public void onCurrentMediaItemChanged(MediaSession2 session,
                            MediaPlayerBase player, MediaItem2 itemOut) {
                        assertSame(currentItem, itemOut);
                        latchForSessionCallback.countDown();
                    }
                }).build()) {

            mPlayer.notifyCurrentDataSourceChanged(currentItem.getDataSourceDesc());
            assertTrue(latchForSessionCallback.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
            // TODO (jaewan): Test that controllers are also notified. (b/74505936)
        }
    }

    @Test
    public void testMediaPrepared() throws Exception {
        final int listSize = 5;
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, listSize);
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public List<MediaItem2> getPlaylist() {
                return list;
            }
        };

        MediaItem2 currentItem = list.get(3);

        final CountDownLatch latchForSessionCallback = new CountDownLatch(1);
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setPlaylistAgent(agent)
                .setId("testMediaPrepared")
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                    @Override
                    public void onMediaPrepared(MediaSession2 session, MediaPlayerBase player,
                            MediaItem2 itemOut) {
                        assertSame(currentItem, itemOut);
                        latchForSessionCallback.countDown();
                    }
                }).build()) {

            mPlayer.notifyMediaPrepared(currentItem.getDataSourceDesc());
            assertTrue(latchForSessionCallback.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
            // TODO (jaewan): Test that controllers are also notified. (b/74505936)
        }
    }

    @Test
    public void testBufferingStateChanged() throws Exception {
        final int listSize = 5;
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, listSize);
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public List<MediaItem2> getPlaylist() {
                return list;
            }
        };

        MediaItem2 currentItem = list.get(3);
        final int buffState = MediaPlayerBase.BUFFERING_STATE_BUFFERING_COMPLETE;

        final CountDownLatch latchForSessionCallback = new CountDownLatch(1);
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setPlaylistAgent(agent)
                .setId("testBufferingStateChanged")
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                    @Override
                    public void onBufferingStateChanged(MediaSession2 session,
                            MediaPlayerBase player, MediaItem2 itemOut, int stateOut) {
                        assertSame(currentItem, itemOut);
                        assertEquals(buffState, stateOut);
                        latchForSessionCallback.countDown();
                    }
                }).build()) {

            mPlayer.notifyBufferingStateChanged(currentItem.getDataSourceDesc(), buffState);
            assertTrue(latchForSessionCallback.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
            // TODO (jaewan): Test that controllers are also notified. (b/74505936)
        }
    }

    @Test
    public void testUpdatePlayer() throws Exception {
        final int targetState = MediaPlayerBase.PLAYER_STATE_PLAYING;
        final CountDownLatch latch = new CountDownLatch(1);
        sHandler.postAndSync(() -> {
            mSession.close();
            mSession = new MediaSession2.Builder(mContext).setPlayer(mPlayer)
                    .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                        @Override
                        public void onPlayerStateChanged(MediaSession2 session,
                                MediaPlayerBase player, int state) {
                            assertEquals(targetState, state);
                            latch.countDown();
                        }
                    }).build();
        });

        MockPlayer player = new MockPlayer(0);

        // Test if setPlayer doesn't crash with various situations.
        mSession.updatePlayer(mPlayer, null, null);
        assertEquals(mPlayer, mSession.getPlayer());
        MediaPlaylistAgent agent = mSession.getPlaylistAgent();
        assertNotNull(agent);

        mSession.updatePlayer(player, null, null);
        assertEquals(player, mSession.getPlayer());
        assertNotNull(mSession.getPlaylistAgent());
        assertNotEquals(agent, mSession.getPlaylistAgent());

        player.notifyPlaybackState(MediaPlayerBase.PLAYER_STATE_PLAYING);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
   }

    @Test
    public void testSetPlayer_playbackInfo() throws Exception {
        MockPlayer player = new MockPlayer(0);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setContentType(CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(attrs);

        final int maxVolume = 100;
        final int currentVolume = 23;
        final int volumeControlType = VolumeProvider2.VOLUME_CONTROL_ABSOLUTE;
        VolumeProvider2 volumeProvider =
                new VolumeProvider2(mContext, volumeControlType, maxVolume, currentVolume) { };

        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onPlaybackInfoChanged(MediaController2 controller,
                    PlaybackInfo info) {
                Assert.assertEquals(PlaybackInfo.PLAYBACK_TYPE_REMOTE, info.getPlaybackType());
                assertEquals(attrs, info.getAudioAttributes());
                assertEquals(volumeControlType, info.getPlaybackType());
                assertEquals(maxVolume, info.getMaxVolume());
                assertEquals(currentVolume, info.getCurrentVolume());
                latch.countDown();
            }
        };

        mSession.updatePlayer(player, null, null);

        final MediaController2 controller = createController(mSession.getToken(), true, callback);
        PlaybackInfo info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(PlaybackInfo.PLAYBACK_TYPE_LOCAL, info.getPlaybackType());
        assertEquals(attrs, info.getAudioAttributes());
        AudioManager manager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int localVolumeControlType = manager.isVolumeFixed()
                ? VolumeProvider2.VOLUME_CONTROL_FIXED : VolumeProvider2.VOLUME_CONTROL_ABSOLUTE;
        assertEquals(localVolumeControlType, info.getControlType());
        assertEquals(manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), info.getMaxVolume());
        assertEquals(manager.getStreamVolume(AudioManager.STREAM_MUSIC), info.getCurrentVolume());

        mSession.updatePlayer(player, null, volumeProvider);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(PlaybackInfo.PLAYBACK_TYPE_REMOTE, info.getPlaybackType());
        assertEquals(attrs, info.getAudioAttributes());
        assertEquals(volumeControlType, info.getControlType());
        assertEquals(maxVolume, info.getMaxVolume());
        assertEquals(currentVolume, info.getCurrentVolume());
    }

    @Test
    public void testPlay() throws Exception {
        sHandler.postAndSync(() -> {
            mSession.play();
            assertTrue(mPlayer.mPlayCalled);
        });
    }

    @Test
    public void testPause() throws Exception {
        sHandler.postAndSync(() -> {
            mSession.pause();
            assertTrue(mPlayer.mPauseCalled);
        });
    }

    @Ignore
    @Test
    public void testStop() throws Exception {
        sHandler.postAndSync(() -> {
            mSession.stop();
            assertTrue(mPlayer.mStopCalled);
        });
    }

    @Test
    public void testSkipToPreviousItem() {
        mSession.skipToPreviousItem();
        verify(mMockAgent).skipToPreviousItem();
    }

    @Test
    public void testSkipToNextItem() throws Exception {
        mSession.skipToNextItem();
        verify(mMockAgent).skipToNextItem();
    }

    @Test
    public void testSkipToPlaylistItem() throws Exception {
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata(mContext);
        mSession.skipToPlaylistItem(testMediaItem);
        verify(mMockAgent).skipToPlaylistItem(eq(testMediaItem));
    }

    @Test
    public void testSetPlaylist() {
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, 2);
        mSession.setPlaylist(list, null);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).setPlaylist(eq(list), isNull());
    }

    @Test
    public void testGetPlaylist() {
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, 2);
        doReturn(list).when(mMockAgent).getPlaylist();
        assertEquals(list, mSession.getPlaylist());
    }

    @Test
    public void testUpdatePlaylistMetadata() {
        final MediaMetadata2 testMetadata = TestUtils.createMetadata(mContext);
        mSession.updatePlaylistMetadata(testMetadata);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).updatePlaylistMetadata(
                eq(testMetadata));
    }

    @Test
    public void testGetPlaylistMetadata() {
        final MediaMetadata2 testMetadata = TestUtils.createMetadata(mContext);
        doReturn(testMetadata).when(mMockAgent).getPlaylistMetadata();
        assertEquals(testMetadata, mSession.getPlaylistMetadata());
    }

    @Test
    public void testSessionCallback_onPlaylistChanged() throws InterruptedException {
        final List<MediaItem2> list = TestUtils.createPlaylist(mContext, 2);
        final CountDownLatch latch = new CountDownLatch(1);
        final MediaPlaylistAgent agent = new MediaPlaylistAgent(mContext) {
            @Override
            public List<MediaItem2> getPlaylist() {
                return list;
            }
        };
        final SessionCallback sessionCallback = new SessionCallback(mContext) {
            @Override
            public void onPlaylistChanged(MediaSession2 session, MediaPlaylistAgent playlistAgent,
                    List<MediaItem2> playlist, MediaMetadata2 metadata) {
                assertEquals(agent, playlistAgent);
                assertEquals(list, playlist);
                assertNull(metadata);
                latch.countDown();
            }
        };
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setPlaylistAgent(agent)
                .setId("testSessionCallback")
                .setSessionCallback(sHandlerExecutor, sessionCallback)
                .build()) {
            agent.notifyPlaylistChanged();
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testAddPlaylistItem() {
        final int testIndex = 12;
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata(mContext);
        mSession.addPlaylistItem(testIndex, testMediaItem);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .addPlaylistItem(testIndex, testMediaItem);
    }

    @Test
    public void testRemovePlaylistItem() {
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata(mContext);
        mSession.removePlaylistItem(testMediaItem);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce()).removePlaylistItem(testMediaItem);
    }

    @Test
    public void testReplacePlaylistItem() throws InterruptedException {
        final int testIndex = 12;
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata(mContext);
        mSession.replacePlaylistItem(testIndex, testMediaItem);
        verify(mMockAgent, timeout(TIMEOUT_MS).atLeastOnce())
                .replacePlaylistItem(testIndex, testMediaItem);
    }

    @Ignore
    @Test
    public void testSetPlaylistParams() throws Exception {
        final PlaylistParams params = new PlaylistParams(mContext,
                PlaylistParams.REPEAT_MODE_ALL,
                PlaylistParams.SHUFFLE_MODE_ALL,
                null /* PlaylistMetadata */);

        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onPlaylistParamsChanged(MediaController2 controller,
                    PlaylistParams givenParams) {
                ensurePlaylistParamsModeEquals(params, givenParams);
                latch.countDown();
            }
        };

        final MediaController2 controller = createController(mSession.getToken(), true, callback);
        mSession.setPlaylistParams(params);
        assertTrue(mPlayer.mSetPlaylistParamsCalled);
        ensurePlaylistParamsModeEquals(params, mPlayer.mPlaylistParams);
        ensurePlaylistParamsModeEquals(params, mSession.getPlaylistParams());
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    // TODO (jaewan): Revisit
    @Test
    public void testBadPlayer() throws InterruptedException {
        // TODO(jaewan): Add equivalent tests again
        final CountDownLatch latch = new CountDownLatch(4); // expected call + 1
        final BadPlayer player = new BadPlayer(0);

        mSession.updatePlayer(player, null, null);
        mSession.updatePlayer(mPlayer, null, null);
        player.notifyPlaybackState(MediaPlayerBase.PLAYER_STATE_PAUSED);
        assertFalse(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    // This bad player will keep push events to the listener that is previously
    // registered by session.setPlayer().
    private static class BadPlayer extends MockPlayer {
        public BadPlayer(int count) {
            super(count);
        }

        @Override
        public void unregisterPlayerEventCallback(
                @NonNull MediaPlayerBase.PlayerEventCallback listener) {
            // No-op.
        }
    }

    @Test
    public void testOnCommandCallback() throws InterruptedException {
        final MockOnCommandCallback callback = new MockOnCommandCallback();
        sHandler.postAndSync(() -> {
            mSession.close();
            mPlayer = new MockPlayer(1);
            mSession = new MediaSession2.Builder(mContext).setPlayer(mPlayer)
                    .setSessionCallback(sHandlerExecutor, callback).build();
        });
        MediaController2 controller = createController(mSession.getToken());
        controller.pause();
        assertFalse(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mPlayer.mPauseCalled);
        assertEquals(1, callback.commands.size());
        assertEquals(MediaSession2.COMMAND_CODE_PLAYBACK_PAUSE,
                (long) callback.commands.get(0).getCommandCode());
        // TODO(jaewan): uncomment followings once skipToNextItem is implemented (b/74090741)
//        controller.skipToNextItem();
//        assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
//        assertTrue(mPlayer.mSkipToNextCalled);
//        assertFalse(mPlayer.mPauseCalled);
//        assertEquals(2, callback.commands.size());
//        assertEquals(MediaSession2.COMMAND_CODE_PLAYBACK_SKIP_NEXT_ITEM,
//                (long) callback.commands.get(1).getCommandCode());
    }

    @Test
    public void testOnConnectCallback() throws InterruptedException {
        final MockOnConnectCallback sessionCallback = new MockOnConnectCallback();
        sHandler.postAndSync(() -> {
            mSession.close();
            mSession = new MediaSession2.Builder(mContext).setPlayer(mPlayer)
                    .setSessionCallback(sHandlerExecutor, sessionCallback).build();
        });
        MediaController2 controller = createController(mSession.getToken(), false, null);
        assertNotNull(controller);
        waitForConnect(controller, false);
        waitForDisconnect(controller, true);
    }

    @Test
    public void testOnDisconnectCallback() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setId("testOnDisconnectCallback")
                .setSessionCallback(sHandlerExecutor, new SessionCallback(mContext) {
                    @Override
                    public void onDisconnected(MediaSession2 session,
                            ControllerInfo controller) {
                        assertEquals(Process.myUid(), controller.getUid());
                        latch.countDown();
                    }
                }).build()) {
            MediaController2 controller = createController(session.getToken());
            controller.close();
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testSetCustomLayout() throws InterruptedException {
        final List<CommandButton> buttons = new ArrayList<>();
        buttons.add(new CommandButton.Builder(mContext)
                .setCommand(new Command(mContext, MediaSession2.COMMAND_CODE_PLAYBACK_PLAY))
                .setDisplayName("button").build());
        final CountDownLatch latch = new CountDownLatch(1);
        final SessionCallback sessionCallback = new SessionCallback(mContext) {
            @Override
            public CommandGroup onConnect(MediaSession2 session,
                    ControllerInfo controller) {
                if (mContext.getPackageName().equals(controller.getPackageName())) {
                    mSession.setCustomLayout(controller, buttons);
                }
                return super.onConnect(session, controller);
            }
        };

        try (final MediaSession2 session = new MediaSession2.Builder(mContext)
                .setPlayer(mPlayer)
                .setId("testSetCustomLayout")
                .setSessionCallback(sHandlerExecutor, sessionCallback)
                .build()) {
            if (mSession != null) {
                mSession.close();
                mSession = session;
            }
            final ControllerCallback callback = new ControllerCallback() {
                @Override
                public void onCustomLayoutChanged(MediaController2 controller2,
                        List<CommandButton> layout) {
                    assertEquals(layout.size(), buttons.size());
                    for (int i = 0; i < layout.size(); i++) {
                        assertEquals(layout.get(i).getCommand(), buttons.get(i).getCommand());
                        assertEquals(layout.get(i).getDisplayName(),
                                buttons.get(i).getDisplayName());
                    }
                    latch.countDown();
                }
            };
            final MediaController2 controller =
                    createController(session.getToken(), true, callback);
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testSetAllowedCommands() throws InterruptedException {
        final CommandGroup commands = new CommandGroup(mContext);
        commands.addCommand(new Command(mContext, MediaSession2.COMMAND_CODE_PLAYBACK_PLAY));
        commands.addCommand(new Command(mContext, MediaSession2.COMMAND_CODE_PLAYBACK_PAUSE));
        commands.addCommand(new Command(mContext, MediaSession2.COMMAND_CODE_PLAYBACK_STOP));

        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onAllowedCommandsChanged(MediaController2 controller,
                    CommandGroup commandsOut) {
                assertNotNull(commandsOut);
                List<Command> expected = commands.getCommands();
                List<Command> actual = commandsOut.getCommands();

                assertNotNull(actual);
                assertEquals(expected.size(), actual.size());
                for (int i = 0; i < expected.size(); i++) {
                    assertEquals(expected.get(i), actual.get(i));
                }
                latch.countDown();
            }
        };

        final MediaController2 controller = createController(mSession.getToken(), true, callback);
        ControllerInfo controllerInfo = getTestControllerInfo();
        assertNotNull(controllerInfo);

        mSession.setAllowedCommands(controllerInfo, commands);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSendCustomAction() throws InterruptedException {
        final Command testCommand =
                new Command(mContext, MediaSession2.COMMAND_CODE_PLAYBACK_PREPARE);
        final Bundle testArgs = new Bundle();
        testArgs.putString("args", "testSendCustomAction");

        final CountDownLatch latch = new CountDownLatch(2);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onCustomCommand(MediaController2 controller, Command command,
                    Bundle args, ResultReceiver receiver) {
                assertEquals(testCommand, command);
                assertTrue(TestUtils.equals(testArgs, args));
                assertNull(receiver);
                latch.countDown();
            }
        };
        final MediaController2 controller =
                createController(mSession.getToken(), true, callback);
        // TODO(jaewan): Test with multiple controllers
        mSession.sendCustomCommand(testCommand, testArgs);

        ControllerInfo controllerInfo = getTestControllerInfo();
        assertNotNull(controllerInfo);
        // TODO(jaewan): Test receivers as well.
        mSession.sendCustomCommand(controllerInfo, testCommand, testArgs, null);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    private ControllerInfo getTestControllerInfo() {
        List<ControllerInfo> controllers = mSession.getConnectedControllers();
        assertNotNull(controllers);
        for (int i = 0; i < controllers.size(); i++) {
            if (Process.myUid() == controllers.get(i).getUid()) {
                return controllers.get(i);
            }
        }
        fail("Failed to get test controller info");
        return null;
    }

    public class MockOnConnectCallback extends SessionCallback {
        public MockOnConnectCallback() {
            super(mContext);
        }

        @Override
        public MediaSession2.CommandGroup onConnect(MediaSession2 session,
                ControllerInfo controllerInfo) {
            if (Process.myUid() != controllerInfo.getUid()) {
                return null;
            }
            assertEquals(mContext.getPackageName(), controllerInfo.getPackageName());
            assertEquals(Process.myUid(), controllerInfo.getUid());
            assertFalse(controllerInfo.isTrusted());
            // Reject all
            return null;
        }
    }

    public class MockOnCommandCallback extends SessionCallback {
        public final ArrayList<MediaSession2.Command> commands = new ArrayList<>();

        public MockOnCommandCallback() {
            super(mContext);
        }

        @Override
        public boolean onCommandRequest(MediaSession2 session, ControllerInfo controllerInfo,
                MediaSession2.Command command) {
            assertEquals(mContext.getPackageName(), controllerInfo.getPackageName());
            assertEquals(Process.myUid(), controllerInfo.getUid());
            assertFalse(controllerInfo.isTrusted());
            commands.add(command);
            if (command.getCommandCode() == MediaSession2.COMMAND_CODE_PLAYBACK_PAUSE) {
                return false;
            }
            return true;
        }
    }

    private static void assertMediaItemListEquals(List<MediaItem2> a, List<MediaItem2> b) {
        if (a == null || b == null) {
            assertEquals(a, b);
        }
        assertEquals(a.size(), b.size());

        for (int i = 0; i < a.size(); i++) {
            MediaItem2 aItem = a.get(i);
            MediaItem2 bItem = b.get(i);

            if (aItem == null || bItem == null) {
                assertEquals(aItem, bItem);
                continue;
            }

            assertEquals(aItem.getMediaId(), bItem.getMediaId());
            assertEquals(aItem.getFlags(), bItem.getFlags());
            TestUtils.equals(aItem.getMetadata().toBundle(), bItem.getMetadata().toBundle());

            // Note: Here it does not check whether DataSourceDesc are equal,
            // since there DataSourceDec is not comparable.
        }
    }
}
