package com.applozic.audiovideo.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.applozic.audiovideo.authentication.Dialog;
import com.applozic.audiovideo.authentication.MakeAsyncRequest;
import com.applozic.audiovideo.authentication.Token;
import com.applozic.audiovideo.authentication.TokenGeneratorCallback;
import com.applozic.mobicomkit.api.MobiComKitConstants;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.api.conversation.MessageIntentService;
import com.applozic.mobicomkit.api.conversation.MobiComMessageService;
import com.applozic.mobicomkit.api.notification.VideoCallNotificationHelper;
import com.applozic.mobicomkit.broadcast.BroadcastService;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.commons.image.ImageLoader;
import com.applozic.mobicommons.json.GsonUtils;
import com.applozic.mobicommons.people.contact.Contact;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import java.util.Collections;

import applozic.com.audiovideo.R;

import static com.twilio.video.Room.State.CONNECTED;
import static com.twilio.video.Room.State.DISCONNECTED;

/**
 * Created by Adarsh on 12/15/16.
 */

public class AudioCallActivityV2 extends AppCompatActivity implements TokenGeneratorCallback {
    public static final long IN_COMING_CALL_TIMEOUT = 30 * 1000L;
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "AudioCallActivityV2";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";

    /*
     * The Video Client allows a client to connect to a room
     */
    /*
     * A Room represents communication between the client and one or more participants.
     */
    protected Room room;
    protected LocalParticipant localParticipant;


    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    protected VideoView primaryVideoView;
    protected VideoView thumbnailVideoView;

    protected String accessToken;
    /*
     * Android application UI elements
     */
    protected TextView videoStatusTextView;
    protected TextView audioStatusTextView;
    protected CameraCapturer cameraCapturer;

    protected LocalAudioTrack localAudioTrack;
    protected LocalVideoTrack localVideoTrack;
    protected VideoView localVideoView;
    protected FloatingActionButton connectActionFab;
    protected FloatingActionButton switchCameraActionFab;
    protected FloatingActionButton localVideoActionFab;
    protected FloatingActionButton muteActionFab;
    protected AudioManager audioManager;
    protected VideoCallNotificationHelper videoCallNotificationHelper;
    protected Contact contactToCall;
    protected Token token;
    static final String CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";
    // ************** APPLOZIC NOTIFICATION AND PROFILE ***************************//
    protected boolean answered;
    protected ProgressDialog progress;
    protected MobiComMessageService messageService;
    protected BroadcastReceiver applozicBroadCastReceiver;
    protected boolean incomingCall;
    protected boolean inviteSent;
    protected String callId;
    protected long callStartTime;
    protected boolean autoCall = false;
    protected MediaPlayer mediaPlayer;
    protected AppContactService contactService;
    protected TextView contactName;
    protected ImageView profileImage;
    protected boolean pauseVideo;
    protected boolean disconnectedFromOnDestroy;
    protected boolean videoCall = false;
    protected FloatingActionButton speakerActionFab;
    ImageLoader mImageLoader;
    CountDownTimer timer;
    TextView txtCount;
    int rejectClickCount;
    private AlertDialog alertDialog;
    private String remoteParticipantIdentity;
    private int previousAudioMode;
    private int cnt;
    private boolean previousMicrophoneMute;


    public AudioCallActivityV2() {
        this.videoCall = false;
    }


    public AudioCallActivityV2(boolean videoCall) {
        this.videoCall = videoCall;
    }

    public static void setOpenStatus(boolean isInOpenStatus) {
        BroadcastService.videoCallAcitivityOpend = isInOpenStatus;
    }

    static IntentFilter BrodCastIntentFilters() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(MobiComKitConstants.APPLOZIC_VIDEO_CALL_REJECTED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_CANCELED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_END);
        intentFilter.addAction(MobiComKitConstants.APPLOZIC_VIDEO_DIALED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_MISSED);
        intentFilter.addAction(CONNECTIVITY_CHANGE);

        return intentFilter;
    }

    boolean isVideoCallStatusTextVideoPaused() {
        return getString(R.string.status_text_paused).equals(videoStatusTextView.getText());
    }

    void hideVideoCallStatusText() {
        if(videoStatusTextView != null) {
            videoStatusTextView.setVisibility(View.GONE);
        }
    }

    void setVideoCallStatusText(String callStatusText) {
        videoStatusTextView.setVisibility(View.VISIBLE);
        videoStatusTextView.setText(callStatusText);
    }

    void hideAudioCallStatusText() {
        if(audioStatusTextView != null) {
            audioStatusTextView.setVisibility(View.GONE);
        }
    }

    void setAudioCallStatusText(String callStatusText) {
        audioStatusTextView.setVisibility(View.VISIBLE);
        audioStatusTextView.setText(callStatusText);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setOpenStatus(true);

        /*
         * Set the initial state of the UI
         */
        if (videoCall) {
            System.out.println(" video call returning ...");
            return;
        }
        setContentView(R.layout.applozic_audio_call);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        init();

        contactName = (TextView) findViewById(R.id.contact_name);
        profileImage = (ImageView) findViewById(R.id.applozic_audio_profile_image);
        txtCount = (TextView) findViewById(R.id.applozic_audio_timer);

        contactName.setText(contactToCall.getDisplayName());
        pauseVideo = true;

        mImageLoader = new ImageLoader(this, profileImage.getHeight()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                return contactService.downloadContactImage(AudioCallActivityV2.this, (Contact) data);
            }
        };
        mImageLoader.setLoadingImage(R.drawable.applozic_ic_contact_picture_holo_light);
        // Add a cache to the image loader
        mImageLoader.setImageFadeIn(false);
        mImageLoader.loadImage(contactToCall, profileImage);

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);
        audioStatusTextView = (TextView) findViewById(R.id.applozic_audio_status);
        setAudioCallStatusText(getString(R.string.status_text_calling));

        connectActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speaker_action_fab);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            createAudioAndVideoTracks();
            intializeUI();
            initializeApplozic();
        }

    }

    protected void createAudioAndVideoTracks() {
        try {
            // Share your microphone
            localAudioTrack = LocalAudioTrack.create(this, true);
            // Share your camera
            try {
                cameraCapturer = new CameraCapturer(this, CameraCapturer.CameraSource.FRONT_CAMERA);
            }catch(IllegalStateException e){
                Utils.printLog(this,TAG,"Front camera not found on device, using back camera..");
                cameraCapturer = new CameraCapturer(this, CameraCapturer.CameraSource.BACK_CAMERA);
            }
            localVideoTrack = LocalVideoTrack.create(this, true, cameraCapturer, LOCAL_VIDEO_TRACK_NAME);
            primaryVideoView.setMirror(true);
            if (videoCall) {
                localVideoTrack.addRenderer(primaryVideoView);
                localVideoView = primaryVideoView;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            boolean cameraAndMicPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraAndMicPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraAndMicPermissionGranted) {

                createAudioAndVideoTracks();
                intializeUI();
                initializeApplozic();

            } else {

                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * If the local video track was released when the app was put in the background, recreate.
         */
        try {
            if (videoCall) {
                if (localVideoTrack == null && checkPermissionForCameraAndMicrophone()) {
                    localVideoTrack = LocalVideoTrack.create(this, true, cameraCapturer, LOCAL_VIDEO_TRACK_NAME);
                    localVideoTrack.addRenderer(localVideoView);

            /*
             * If connected to a Room then share the local video track.
             */
                    if (localParticipant != null && localVideoTrack != null) {
                        localParticipant.publishTrack(localVideoTrack);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, remove from local
             * participant before releasing the video track. Participants will be notified that
             * the track has been removed.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }

            localVideoTrack.release();
            localVideoTrack = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(applozicBroadCastReceiver);
        super.onDestroy();
        setOpenStatus(false);

    }

    protected boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    protected void requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    protected void connectToRoom(String roomName) {
        try {
            configureAudio(true);
            ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                    .roomName(roomName);

        /*
         * Add local audio track to connect options to share with participants.
         */
            if (localAudioTrack != null) {
                connectOptionsBuilder
                        .audioTracks(Collections.singletonList(localAudioTrack));
            }

        /*
         * Add local video track to connect options to share with participants.
         */
            if (localVideoTrack != null) {
                connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
            }
            room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
            setDisconnectAction();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /*
     * The initial state when there is no active conversation.
     */
    protected void intializeUI() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
        if (videoCall) {
            switchCameraActionFab.show();
            switchCameraActionFab.setOnClickListener(switchCameraClickListener());
            localVideoActionFab.show();
            localVideoActionFab.setOnClickListener(localVideoClickListener());
        }
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
        speakerActionFab.setOnClickListener(speakerClickListener());
    }

    /*
     * The actions performed during disconnect.
     */
    protected void setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }

    /*
     * Called when remote participant joins the room
     */
    @SuppressLint("SetTextI18n")
    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Snackbar.make(connectActionFab,
                    R.string.multiple_participants_not_available,
                    Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }
        videoCallNotificationHelper.sendVideoCallAnswer(contactToCall, callId);
        remoteParticipantIdentity = remoteParticipant.getIdentity();

        /*
         * Add remote participant renderer
         */
        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        /*
         * Start listening for participant events
         */
        remoteParticipant.setListener(remoteParticipantListener());
    }

    /*  Set primary view as renderer for participant video track
    */
    protected void addRemoteParticipantVideo(VideoTrack videoTrack) {
        if (videoCall) {
            moveLocalVideoToThumbnailView();
            primaryVideoView.setMirror(false);
            videoTrack.addRenderer(primaryVideoView);
        }
    }

    protected void moveLocalVideoToThumbnailView() {
        try {
            if (thumbnailVideoView.getVisibility() == View.GONE) {
                thumbnailVideoView.setVisibility(View.VISIBLE);
                if (localVideoTrack != null) {
                    localVideoTrack.removeRenderer(primaryVideoView);
                    localVideoTrack.addRenderer(thumbnailVideoView);
                    localVideoView = thumbnailVideoView;
                    thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                            CameraCapturer.CameraSource.FRONT_CAMERA);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Called when remote participant leaves the room
     */
    @SuppressLint("SetTextI18n")
    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        if (!remoteParticipant.getIdentity().equals(remoteParticipantIdentity)) {
            return;
        }

        /*
         * Remove remote participant renderer
         */
        if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            /*
             * Remove video only if subscribed to participant track
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }
        if (videoCall) {
            moveLocalVideoToPrimaryView();
        }
    }

    protected void removeParticipantVideo(VideoTrack videoTrack) {
        if (videoTrack != null) {
            videoTrack.removeRenderer(primaryVideoView);
        }
    }

    protected void moveLocalVideoToPrimaryView() {
        try {
            if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                if (localVideoTrack != null) {
                    localVideoTrack.removeRenderer(thumbnailVideoView);
                    thumbnailVideoView.setVisibility(View.GONE);
                    localVideoTrack.addRenderer(primaryVideoView);
                    localVideoView = primaryVideoView;
                    primaryVideoView.setMirror(cameraCapturer.getCameraSource() ==
                            CameraCapturer.CameraSource.FRONT_CAMERA);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
 * Room events listener
 */
    protected Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(@androidx.annotation.NonNull Room room) {
                localParticipant = room.getLocalParticipant();
                Log.d(TAG, "Connected to room");
                setTitle(room.getName());
                setSpeakerphoneOn(videoCall);
                for (RemoteParticipant participant : room.getRemoteParticipants()) {
                    addRemoteParticipant(participant);
                    hideProgress();
                    if (!videoCall) {
                        timer.start();
                    }
                    break;
                }
            }

            @Override
            public void onConnectFailure(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull TwilioException e) {
                Log.d(TAG, "Failed to connect to room");
                inviteSent = false;
                hideProgress();
                hideVideoCallStatusText();
                hideAudioCallStatusText();
                configureAudio(false);
                finish();
            }

            @Override
            public void onReconnecting(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull TwilioException twilioException) {
                if(videoCall) {
                    setVideoCallStatusText(getString(R.string.status_text_reconnecting));
                } else {
                    setAudioCallStatusText(getString(R.string.status_text_reconnecting));
                }
            }

            @Override
            public void onReconnected(@androidx.annotation.NonNull Room room) {
                RemoteParticipant remoteParticipant = room.getRemoteParticipants().get(0);
                hideVideoCallStatusText();
                hideAudioCallStatusText();
                //if remote video is paused (logic is for 1-to-1 video call only)
                if(videoCall && remoteParticipant != null  && !remoteParticipant.getRemoteVideoTracks().get(0).isTrackEnabled()) {
                    setVideoCallStatusText(getString(R.string.status_text_paused));
                }
            }

            @Override
            public void onDisconnected(@NonNull Room room, TwilioException e) {
                try {
                    localParticipant = null;
                    Log.d(TAG, "Disconnected from room" + room.getName());
                    AudioCallActivityV2.this.room = null;
                    configureAudio(false);
                    if (!videoCall) {
                        timer.cancel();
                    }

                    if (!incomingCall && callStartTime > 0) {
                        long diff = (System.currentTimeMillis() - callStartTime);
                        videoCallNotificationHelper.sendVideoCallEnd(contactToCall, callId, String.valueOf(diff));
                    }

                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                    finish();
                } catch (Exception exp) {
                }
            }

            @Override
            public void onParticipantConnected(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG, "onParticipantConnected for room");
                addRemoteParticipant(remoteParticipant);
                hideProgress();
                hideVideoCallStatusText();
                hideAudioCallStatusText();
                if (!videoCall) {
                    timer.start();
                }
                if (!incomingCall) {
                    callStartTime = System.currentTimeMillis();
                }
            }

            @Override
            public void onParticipantDisconnected(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull RemoteParticipant remoteParticipant) {
                removeRemoteParticipant(remoteParticipant);
                Log.d(TAG, "Participant has disconnected");
                if (room != null) {
                    room.disconnect();
                } else {
                    finish();
                }
            }

            @Override
            public void onRecordingStarted(@androidx.annotation.NonNull Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(@androidx.annotation.NonNull Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped");
            }
        };
    }

    protected RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) { }

            @Override
            public void onAudioTrackUnpublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) { }

            @Override
            public void onAudioTrackSubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @androidx.annotation.NonNull RemoteAudioTrack remoteAudioTrack) {

            }

            @Override
            public void onAudioTrackSubscriptionFailed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @androidx.annotation.NonNull TwilioException twilioException) {

            }

            @Override
            public void onAudioTrackUnsubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @androidx.annotation.NonNull RemoteAudioTrack remoteAudioTrack) {

            }

            @Override
            public void onVideoTrackPublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackUnpublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackSubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @androidx.annotation.NonNull RemoteVideoTrack remoteVideoTrack) {
                addRemoteParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @androidx.annotation.NonNull TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackUnsubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @androidx.annotation.NonNull RemoteVideoTrack remoteVideoTrack) {
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onDataTrackPublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication) {

            }

            @Override
            public void onDataTrackUnpublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication) {

            }

            @Override
            public void onDataTrackSubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication, @androidx.annotation.NonNull RemoteDataTrack remoteDataTrack) {

            }

            @Override
            public void onDataTrackSubscriptionFailed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication, @androidx.annotation.NonNull TwilioException twilioException) {

            }

            @Override
            public void onDataTrackUnsubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication, @androidx.annotation.NonNull RemoteDataTrack remoteDataTrack) {

            }

            @Override
            public void onAudioTrackEnabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackDisabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onVideoTrackEnabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {
                if(isVideoCallStatusTextVideoPaused()) {
                    hideVideoCallStatusText();
                }
            }

            @Override
            public void onVideoTrackDisabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {
                if (videoCall && remoteParticipant.getIdentity().equals(contactToCall.getUserId()) && room.getState() == CONNECTED) {
                    setVideoCallStatusText(getString(R.string.status_text_paused));
                }
            }
        };
    }

    private View.OnClickListener disconnectClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //invite sent but NOT Yet connected,
                if (inviteSent && remoteParticipantIdentity == null) {

                    inviteSent = false;
                    videoCallNotificationHelper.sendCallMissed(contactToCall, callId);
                    videoCallNotificationHelper.sendVideoCallMissedMessage(contactToCall, callId);

                }
                disconnectAndExit();
            }
        };
    }

    private void disconnectAndExit() {
        if (room != null) {
            room.disconnect();
        } else {
            finish();
        }
    }

    private View.OnClickListener connectActionClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Send request to same userId...
            }
        };
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                intializeUI();
                alertDialog.dismiss();
            }
        };
    }

    private View.OnClickListener switchCameraClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (cameraCapturer != null) {
                        CameraCapturer.CameraSource cameraSource = cameraCapturer.getCameraSource();
                        cameraCapturer.switchCamera();
                        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                            thumbnailVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                        } else {
                            primaryVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local video track
                 */
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_green_24px;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_red_24px;
                        switchCameraActionFab.hide();
                    }
                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(AudioCallActivityV2.this, icon));
                }
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local audio track
                 */
                if (localAudioTrack != null) {

                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);
                    int icon = enable ?
                            R.drawable.ic_mic_green_24px : R.drawable.ic_mic_off_red_24px;
                    muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                            AudioCallActivityV2.this, icon));
                }
            }
        };
    }

    private void retrieveAccessTokenfromServer(Token token) {

        accessToken = token.getToken();
        initiateCall();
    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.

            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }


    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    new AudioManager.OnAudioFocusChangeListener() {
                                        @Override
                                        public void onAudioFocusChange(int i) {
                                        }
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }


    @Override
    public void onNetworkComplete(String response) {
        Log.i(TAG, "Token response: " + response);
        if (TextUtils.isEmpty(response)) {
            Log.i(TAG, "Not able to get token");
            return;
        }

        Token token = (Token) GsonUtils.getObjectFromJson(response, Token.class);
        MobiComUserPreference.getInstance(this).setVideoCallToken(token.getToken());
        retrieveAccessTokenfromServer(token);
    }

    public void initializeApplozic() {
         /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */

        mediaPlayer = MediaPlayer.create(this, R.raw.hangouts_video_call);
        mediaPlayer.setLooping(true);
        if (!Utils.isInternetAvailable(this)) {
            Toast toast = Toast.makeText(this, getString(R.string.internet_connection_not_available), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            finish();
            return;
        }

         /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            if (incomingCall) {
                progress = new ProgressDialog(this);
                progress.setMessage(getString(R.string.connecting));
                progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progress.setIndeterminate(true);
                progress.setCancelable(false);
                progress.show();
                scheduleStopRinging(callId);
            } else {
                mediaPlayer.start();
            }

            LocalBroadcastManager.getInstance(this).registerReceiver(applozicBroadCastReceiver,
                    BrodCastIntentFilters());
        }

        timer = initializeTimer();
        startCallWithAcessToken();
    }

    @NonNull
    public CountDownTimer initializeTimer() {
        return new CountDownTimer(Long.MAX_VALUE, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {

                cnt++;
                // long millis = cnt;
                int seconds = (cnt);
                int hrs = seconds / (60 * 60);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                txtCount.setText(String.format("%d:%02d:%02d", hrs, minutes, seconds));
            }

            @Override
            public void onFinish() {

            }
        };
    }

    protected void init() {

        Intent intent = getIntent();
        String contactId = intent.getStringExtra("CONTACT_ID");
        Log.i(TAG, "ContactId: " + contactId);
        contactService = new AppContactService(this);
        messageService = new MobiComMessageService(this, MessageIntentService.class);
        Log.i(TAG, "init():::" + this.videoCall);
        videoCallNotificationHelper = new VideoCallNotificationHelper(this, !this.videoCall);

        contactToCall = contactService.getContactById(contactId);
        incomingCall = intent.getBooleanExtra("INCOMING_CALL", Boolean.FALSE);
        callId = intent.getStringExtra("CALL_ID");
        registerForNotificationBroadcast();

    }

    private void startCallWithAcessToken() {

        //Token generations ..
        String accessTokenValue = null;
        //MobiComUserPreference.getInstance(this).getVideoCallToken();

        if (TextUtils.isEmpty(accessTokenValue)) {

            MakeAsyncRequest asyncTask = new MakeAsyncRequest(this, this);
            asyncTask.execute((Void) null);

        } else {

            Token tokenObj = new Token(MobiComUserPreference.getInstance(this).getUserId(), accessTokenValue);
            retrieveAccessTokenfromServer(tokenObj);

        }

    }

    public void initiateCall() {
        if (incomingCall) {
            connectToRoom(callId);
        } else {
            sendInvite();
            inviteSent = true;
        }

    }

    public void registerForNotificationBroadcast() {

        applozicBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String incomingCallId = intent.getStringExtra(VideoCallNotificationHelper.CALL_ID);
                boolean isNotificationForSameId = false;

                Log.i(TAG, "incomingCallId: " + incomingCallId + ", intent.getAction(): " + intent.getAction());

                if (CONNECTIVITY_CHANGE.equals(intent.getAction())) {
                    if (!Utils.isInternetAvailable(context)) {
                        Toast.makeText(context, R.string.no_network_connectivity, Toast.LENGTH_LONG);
                        if (room != null && room.getState().equals(CONNECTED)) {
                            room.disconnect();
                        }
                    }
                    return;
                }

                if (!TextUtils.isEmpty(callId)) {
                    isNotificationForSameId = (callId.equals(incomingCallId));
                }
//                //
//                if (MobiComKitConstants.APPLOZIC_VIDEO_CALL_ANSWER.equals(intent.getAction()) && isNotificationForSameId) {
//                    answered = true;
//                    sendInvite();
//                } else
                if ((MobiComKitConstants.APPLOZIC_VIDEO_CALL_REJECTED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_CANCELED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_MISSED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_END.equals(intent.getAction()))
                        && isNotificationForSameId) {

                    Toast.makeText(context, R.string.participant_busy, Toast.LENGTH_LONG).show();
                    hideProgress();
                    if (room != null) {
                        inviteSent = false;
                        room.disconnect();
                    } else {
                        finish();
                    }
                } else if (MobiComKitConstants.APPLOZIC_VIDEO_DIALED.equals(intent.getAction())) {

                    String contactId = intent.getStringExtra("CONTACT_ID");

                    if (!contactId.equals(contactToCall.getUserId()) || (room != null && room.getState().equals(CONNECTED))) {
                        Contact contact = contactService.getContactById(contactId);
                        videoCallNotificationHelper.sendVideoCallReject(contact, incomingCallId);
                        return;
                    }
                    callId = incomingCallId;
                    connectToRoom(callId);
                }
            }
        };
    }

    protected void sendInvite() {

        if (videoCall) {
            callId = videoCallNotificationHelper.sendVideoCallRequest(contactToCall);
        } else {
            callId = videoCallNotificationHelper.sendAudioCallRequest(contactToCall);
        }
        scheduleStopRinging(callId);
        connectToRoom(callId);
        setDisconnectAction();

    }

    public void scheduleStopRinging(final String callIdScheduled) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long timeDuration = incomingCall ? IN_COMING_CALL_TIMEOUT : VideoCallNotificationHelper.MAX_NOTIFICATION_RING_DURATION + 10 * 1000;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Check for incoming call if
                        if (incomingCall && remoteParticipantIdentity == null) {
                            Toast.makeText(context, R.string.connection_error, Toast.LENGTH_LONG).show();
                            hideProgress();
                            disconnectAndExit();
                            return;
                        }

                        if (isScheduleStopRequire()) {

                            videoCallNotificationHelper.sendCallMissed(contactToCall, callId);
                            videoCallNotificationHelper.sendVideoCallMissedMessage(contactToCall, callId);
                            Toast.makeText(context, R.string.no_answer, Toast.LENGTH_LONG).show();
                            hideProgress();
                            disconnectAndExit();
                        }
                    }
                }, timeDuration);
            }
        });
    }

    private boolean isScheduleStopRequire() {

        return (inviteSent &&
                (remoteParticipantIdentity == null || !remoteParticipantIdentity.equals(contactToCall.getUserId())));
    }

    protected void hideProgress() {
        try {
            Log.i(TAG, "Hiding progress dialog.");
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            if (progress != null && progress.isShowing()) {
                progress.dismiss();
            }
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onBackPressed() {

        //Connected....ROOM
        if (room != null && room.getState().equals(CONNECTED)) {
            alertDialog = Dialog.createCloseSessionDialog(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "onBackPressed cancel do nothing.. ");
                }
            }, closeSessionListener(), this);
            alertDialog.setTitle(getString(R.string.call_close_session_dialog_title));
            alertDialog.setMessage(getString(R.string.call_close_session_dialog_message));
            alertDialog.show();

        } else if (room != null && !room.getState().equals(CONNECTED)) {
            //DO nothing....
        } else {
            super.onBackPressed();
        }

    }


    private DialogInterface.OnClickListener closeSessionListener() {

        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (room != null) {
                    room.disconnect();
                }
            }
        };
    }


    private View.OnClickListener speakerClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /*
                Audio routing to speakerphone or headset*/

                if (room == null) {
                    Log.e(TAG, "Unable to set audio output, conversation client is null");
                    return;
                }
                setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
            }
        };
    }

    protected void setSpeakerphoneOn(boolean onOrOff) {
        if (room == null) {
            Log.e(TAG, "Unable to set audio output, conversation client is null");
            return;
        }

        try {
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(onOrOff);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (onOrOff) {
            Drawable drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_green_24px);
            speakerActionFab.setImageDrawable(drawable);
        } else {
            // route back to headset
            Drawable drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_white_24px);
            speakerActionFab.setImageDrawable(drawable);
        }
    }

}

