package com.osbornnick.jukebot;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
//Spotify SDK Imports
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.Track;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SessionAdminActivity extends AppCompatActivity {
    private static final String TAG = "SessionAdminActivity";
    private String SESSION_ID;
    private String SESSION_NAME;
    private boolean admin = true;

    RecyclerView songQueue;
    TextView songTitle, songArtist, queueLabel, disconnectedText, sessionTitle;
    ImageButton playButton, pauseButton, back, leaveSession, sessionChat, sessionSettings, addFriend;
    ImageView coverArt;
    FloatingActionButton addSongFAB;
    ProgressBar loader;

    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
    boolean isConnected = false;
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    Handler handler;
    SongQueueAdapter sqAdapter;
    Song currentSong;

    private SpotifyAppRemote mSpotifyAppRemote;
    private static final String CLIENT_ID = "fe144966828b41b5ab78f844e0630286";
    private static final String REDIRECT_URI = "com.osbornnick.jukebot://callback";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_session_admin);
        Intent i = getIntent();
        SESSION_ID = i.getStringExtra("session_id");
        SESSION_NAME = i.getStringExtra("session_name");
        handler = new Handler(Looper.getMainLooper());

        songQueue = findViewById(R.id.songQueue);
        songTitle = findViewById(R.id.songTitle);
        songArtist = findViewById(R.id.songArtist);
        queueLabel = findViewById(R.id.queueLabel);
        disconnectedText = findViewById(R.id.disconnectedText);
        playButton = findViewById(R.id.playButton);
        pauseButton = findViewById(R.id.pauseButton);
        back = findViewById(R.id.back);
        leaveSession = findViewById(R.id.leaveSession);
        sessionChat = findViewById(R.id.sessionChat);
        sessionSettings = findViewById(R.id.sessionSettings);
        addFriend = findViewById(R.id.addFriend);
        coverArt = findViewById(R.id.coverArt);
        addSongFAB = findViewById(R.id.addSongFAB);
        loader = findViewById(R.id.loader);
        sessionTitle = findViewById(R.id.sessionTitle);

        //Set Tool Bar On clicks
        initToolbar();

        //Set Spotify Controls
        initSongControls();

        //initialize the recycle view with empty list
        songQueue.setLayoutManager(new LinearLayoutManager(this));
        sqAdapter = new SongQueueAdapter();
        songQueue.setAdapter(sqAdapter);

        songQueue.addItemDecoration(new DividerItemDecoration(songQueue.getContext(), ((LinearLayoutManager)songQueue.getLayoutManager()).getOrientation()));

        listenToSongQueue();

        //onClickListener for FAB
        addSongFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SessionAdminActivity.this, AddSongActivity.class);
                intent.putExtra("session_id", SESSION_ID);
                intent.putExtra("session_name", SESSION_NAME);
                intent.putExtra("admin", true);
                startActivity(intent);
            }
        });
    }

    /*
        onStart(), onStop(), connected() code sourced from Spotify's Quick start guide: https://developer.spotify.com/documentation/android/quick-start/
        Modifications may have been made compared to source code
     */
    @Override
    protected void onStart() {
        // TODO: should probably show a loading screen before this happens
        super.onStart();
        ConnectionParams connectionParams = new ConnectionParams.Builder(CLIENT_ID).setRedirectUri(REDIRECT_URI).showAuthView(true).build();
        SpotifyAppRemote.connect(this, connectionParams, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                mSpotifyAppRemote = spotifyAppRemote;
                Log.d(TAG, "Connected to Spotify");
                connected();
            }
            @Override
            public void onFailure(Throwable throwable) {
                Log.e(TAG, throwable.getMessage(), throwable);
            }
        });
    }

    private void initToolbar() {
        back.setOnClickListener(v -> onBackPressed());

        sessionTitle.setText(SESSION_NAME);

        leaveSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(SessionAdminActivity.this, HomeActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });

        sessionChat.setOnClickListener(v -> {
            Intent intent = new Intent(SessionAdminActivity.this, SessionChatActivity.class);
            intent.putExtra("session_id", SESSION_ID);
            startActivity(intent);
        });

        //maybe change from Activity to AlertDialog
        sessionSettings.setOnClickListener(v -> {
            Intent intent = new Intent(SessionAdminActivity.this, SessionSettingsActivity.class);
            intent.putExtra("session_id", SESSION_ID);
            startActivity(intent);
        });

        addFriend.setOnClickListener(v -> {
            Intent intent = new Intent(SessionAdminActivity.this, InviteFriendsActivity.class);
            intent.putExtra("session_id", SESSION_ID);
            startActivity(intent);
        });
    }

    @SuppressLint("NewApi")
    private void listenToSongQueue() {
        db.collection("Session").document(SESSION_ID).collection("queue").addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.w(TAG, "listen:error", error);
                return;
            }
            if (value == null) return;
            value.getDocumentChanges().forEach(dc -> {
                Map<String, Object> data = dc.getDocument().getData();
                Song s = new Song(data);
                s.key = dc.getDocument().getId();
                List<String> upVotes = (List<String>) data.get("upVotes");
                List<String> downVotes = (List<String>) data.get("downVotes");
                if (upVotes != null && upVotes.contains(currentUser.getUid())) s.voted = "UP";
                if (downVotes != null && downVotes.contains(currentUser.getUid())) s.voted = "DOWN";
                // update song info from spotify?
                Log.d("Song update heard", s.toString());
                if (s.played) sqAdapter.remove(s);
                else if (s.deleted) sqAdapter.remove(s);
                else if (s.playing) sqAdapter.remove(s);
                else {
                    s.session_id = SESSION_ID;
                    sqAdapter.add(s);
                }
                Log.d(TAG, s.toString());
            });
        });
    }

    private void initSongControls() {
        //show spotify controls only for admin user
            //set appropriate onClickListeners utilizing Spotify SDK
            playButton.setOnClickListener(v -> {
                //resume currently playing song in the Spotify player
                Log.d(TAG, "play button clicked");
                if (currentSong == null) playNextFromQueue();
                else mSpotifyAppRemote.getPlayerApi().resume();
                playButton.setVisibility(View.INVISIBLE);
                pauseButton.setVisibility(View.VISIBLE);
            });

            pauseButton.setOnClickListener(v -> {
                //pause currently playing song in the Spotify player
                Log.d(TAG, "pause button clicked");
                mSpotifyAppRemote.getPlayerApi().pause();
                playButton.setVisibility(View.VISIBLE);
                pauseButton.setVisibility(View.INVISIBLE);
            });
    }


    private void connected() {
        // Subscribe to PlayerState to check for a song ending
        this.isConnected = true;
        mSpotifyAppRemote.getPlayerApi().subscribeToPlayerState().setEventCallback(playerState -> {
            Track currentlyPlaying = playerState.track;
            if (!Objects.equals(currentlyPlaying.uri, currentSong.getUri())) {
                mSpotifyAppRemote.getPlayerApi().pause();
                playNextFromQueue();
            }
        });
    }

    public void test(View v) {
        playNextFromQueue();
    }

    private void playNextFromQueue() {
        // update currentSong from playing to played
        if (currentSong != null) {
            Map<String, Object> endSong = new HashMap<String, Object>() {{
                put("playing", false);
                put("played", true);
            }};
            db.collection("Session").document(SESSION_ID).collection("queue").document(currentSong.getKey()).update(endSong);
        }

        //update nextSong to playing
        Song nextSong = sqAdapter.getFirst();
        // update nextSong to be played
        if (nextSong == null) {
            mSpotifyAppRemote.getPlayerApi().pause();
            currentSong = null;
            return;
        }
        db.collection("Session").document(SESSION_ID).collection("queue").document(nextSong.getKey()).update("playing", true);
        updateCurrentSong(nextSong);
        // play it
        mSpotifyAppRemote.getPlayerApi().play(nextSong.getUri());
    }

    private void updateCurrentSong(Song s) {
        currentSong = s;
        songArtist.setText(s.getArtist());
        songTitle.setText(s.getName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
    }

    @Override
    protected void onRestart() {
        if (isConnected) {
            // do something
        }
        super.onRestart();
    }
}