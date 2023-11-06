/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

  protected PlayerView mExoplayerView;
  protected ExoPlayer mPlayer;
  protected static List<String> sVideoUrls = new ArrayList<>();
  protected Player.Listener mPlayerListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    buildPlayer();
  }

  /**
   * Build the player
   */
  protected void buildPlayer() {
    mPlayer = new ExoPlayer.Builder(this).build();
    mExoplayerView = findViewById(R.id.exoplayer);
    mExoplayerView.setPlayer(mPlayer);
  }

  /**
   * Prepare input list and add it to player's playlist.
   */
  public void prepareMediaItems(List<String> urls) {
    sVideoUrls = urls != null ? Collections.unmodifiableList(urls) : null;
    if (sVideoUrls == null) {
      return;
    }
    for (String videoUrl : sVideoUrls) {
      MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
      mPlayer.addMediaItem(mediaItem);
    }
  }

  /**
   * Prepare the player and play the list
   */
  public void run() {
    mPlayer.prepare();
    mPlayer.play();
  }

  /**
   * Resume the player.
   */
  @Override
  protected void onResume() {
    super.onResume();
    mPlayer.play();
  }

  /**
   * Stop the player.
   */
  @Override
  protected void onStop() {
    super.onStop();
    mPlayer.pause();
  }

  /**
   * Release the player and destroy the activity
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    mPlayer.release();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  /**
   * Register a listener to receive events from the player.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to register.
   */
  public void addPlayerListener(Player.Listener listener) {
    mPlayer.addListener(listener);
    this.mPlayerListener = listener;
  }

  /**
   * Unregister a listener registered through addPlayerListener(Listener). The listener will no
   * longer receive events.
   */
  public void removePlayerListener() {
    mPlayer.removeListener(this.mPlayerListener);
  }
}
