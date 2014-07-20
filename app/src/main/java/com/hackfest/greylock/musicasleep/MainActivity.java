// Music Asleep Application for Greylock Hackfest 2014
// Matt Piccolella, Zach Lawrence, Josh Drubin, Char Kwon
package com.hackfest.greylock.musicasleep;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.spotify.sdk.android.Spotify;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.authentication.SpotifyAuthentication;
import com.spotify.sdk.android.playback.ConnectionStateCallback;
import com.spotify.sdk.android.playback.Player;
import com.spotify.sdk.android.playback.PlayerNotificationCallback;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements
        PlayerNotificationCallback, ConnectionStateCallback {

    private static final String CLIENT_ID = "3acf3492794d499a87be2120198d616c";
    private static final String REDIRECT_URI = "music-asleep-login://callback";
    private static final String TRACK_QUERY_URL = "https://api.spotify.com/v1/tracks/";
    private static int currentTrackIndex = 0;

    private Button nextSongButton;
    private ImageView albumArtwork;
    private TextView songName;
    private TextView artistName;

    private AudioManager audioManager;

    private Player mPlayer;

    private static ArrayList<SpotifyTrack> tracks = new ArrayList<SpotifyTrack>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SpotifyAuthentication.openAuthWindow(CLIENT_ID, "token", REDIRECT_URI,
                  new String[]{"user-read-private", "streaming"}, null, this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        populateSongQueue();
        Uri uri = intent.getData();
        if (uri != null) {
            AuthenticationResponse response = SpotifyAuthentication.parseOauthResponse(uri);
            Spotify spotify = new Spotify(response.getAccessToken());
            mPlayer = spotify.getPlayer(this, "Greylock Hackfest 2014", this, new Player.InitializationObserver() {
                @Override
                public void onInitialized() {
                    mPlayer.addConnectionStateCallback(MainActivity.this);
                    mPlayer.addPlayerNotificationCallback(MainActivity.this);
                    playSongAndSetData();
                }

                @Override
                public void onError(Throwable throwable) {
                    Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                }
            });
            nextSongButton = (Button) findViewById(R.id.next_song_button);
            songName = (TextView) findViewById(R.id.song_name);
            artistName = (TextView) findViewById(R.id.artist_name);
            albumArtwork = (ImageView) findViewById(R.id.album_artwork);
            nextSongButton.setEnabled(true);
            nextSongButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    playNextSong();
                }
            });
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onNewCredentials(String s) {
        Log.d("MainActivity", "User credentials blob received");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void populateSongQueue() {
        this.tracks.clear();
        new RESTfulAPIService().execute("400");
    }

    public void playSongAndSetData() {
        SpotifyTrack currentTrack = tracks.get(currentTrackIndex);
        songName.setText(currentTrack.getTrackName());
        artistName.setText(currentTrack.getArtistName());
        new AlbumArtworkDownloader().execute(currentTrack.getAlbumURL());
        mPlayer.play(currentTrack.getTrackId());
    }

    public void playNextSong() {
        currentTrackIndex++;
        if (currentTrackIndex == this.tracks.size()) {
            populateSongQueue();
        }
        playSongAndSetData();
    }

    private class RESTfulAPIService extends AsyncTask<String, Void, String> {
        protected String getASCIIContentFromEntity(HttpEntity entity) throws IllegalStateException, IOException {
            InputStream in = entity.getContent();
            StringBuffer out = new StringBuffer();
            int byteCount = 1;
            while (byteCount > 0) {
                byte[] bytesRead = new byte[4096];
                byteCount =  in.read(bytesRead);
                if (byteCount > 0) {
                    out.append(new String(bytesRead, 0, byteCount));
                }
            }
            return out.toString();
        }
        @Override
        protected String doInBackground(String... params) {
            String urlParam = params[0];
            HttpClient httpClient = new DefaultHttpClient();
            HttpContext localContext = new BasicHttpContext();
            HttpGet httpGet = new HttpGet(TRACK_QUERY_URL + urlParam);
            String textResponse = null;
            try {
                HttpResponse response = httpClient.execute(httpGet, localContext);
                HttpEntity entity = response.getEntity();
                textResponse = getASCIIContentFromEntity(entity);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return textResponse;
        }
        @Override
        protected void onPostExecute(String results) {
            if (results != null) {
                try {
                    JSONObject responseJson = new JSONObject(results);
                    if (responseJson.getString("status").equals("success")) {
                        JSONArray trackResults = responseJson.getJSONArray("results");
                        for (int i = 0; i < trackResults.length(); i++) {
                            JSONObject trackResult = (JSONObject) trackResults.get(i);
                            String artistName = trackResult.getString("artist_name");
                            String trackName = trackResult.getString("track_name");
                            String albumURL = trackResult.getString("album_url");
                            String trackID = trackResult.getString("track_id");
                            SpotifyTrack newTrack = new SpotifyTrack(trackName, artistName, albumURL, trackID);
                            tracks.add(newTrack);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private class AlbumArtworkDownloader extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... urls) {
            String imageUrl = urls[0];
            Bitmap albumCover = null;
            try {
                InputStream in = new java.net.URL(imageUrl).openStream();
                albumCover = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return albumCover;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            albumArtwork.setImageBitmap(result);
        }
    }
}