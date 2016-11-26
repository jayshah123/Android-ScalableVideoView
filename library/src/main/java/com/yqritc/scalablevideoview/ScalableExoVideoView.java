package com.yqritc.scalablevideoview;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.metadata.id3.GeobFrame;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.PrivFrame;
import com.google.android.exoplayer.metadata.id3.TxxxFrame;
import com.google.android.exoplayer.util.Util;
import com.yqritc.scalablevideoview.player.DashRendererBuilder;
import com.yqritc.scalablevideoview.player.DemoPlayer;
import com.yqritc.scalablevideoview.player.EventLogger;
import com.yqritc.scalablevideoview.player.ExtractorRendererBuilder;
import com.yqritc.scalablevideoview.player.HlsRendererBuilder;
import com.yqritc.scalablevideoview.player.WidevineTestMediaDrmCallback;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.Map;


public class ScalableExoVideoView extends TextureView implements TextureView.SurfaceTextureListener,
        MediaPlayer.OnVideoSizeChangedListener, DemoPlayer.Listener, DemoPlayer.Id3MetadataListener {

    private static final String TAG = "ScalableExoVideoView";
    protected MediaPlayer mMediaPlayer;
    protected DemoPlayer demoPlayer;
    protected EventLogger eventLogger;
    private boolean playerNeedsPrepare = false;
    private int playerPosition = 0;
    private boolean mSurfaceTextureAvailable;
    private Uri mUri;
    private Context mContext;
    private Surface mSurfaceTextureSurface;

    private static final boolean DEBUG_INIT_RELEASE = true;
    private static final boolean DEBUG_ERROR = true;
    private static final boolean DEBUG_SURFACE = true;
    private static final boolean DEBUG_STATE = true;

    int mVideoHeight = 0, mVideoWidth = 0;
    private int contentType;

    protected ScalableType mScalableType = ScalableType.NONE;

    public ScalableExoVideoView(Context context) {
        this(context, null);
        mContext = context;
    }

    public ScalableExoVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext = context;
    }

    public ScalableExoVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        if (attrs == null) {
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.scaleStyle, 0, 0);
        if (a == null) {
            return;
        }

        int scaleType = a.getInt(R.styleable.scaleStyle_scalableType, ScalableType.NONE.ordinal());
        a.recycle();
        mScalableType = ScalableType.values()[scaleType];
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if(DEBUG_SURFACE) {
            Log.d(TAG, " onSurfaceTextureAvailable : surfaceTexture = "+ surfaceTexture+" width = "+ width+" height = "+ height);
        }
        Surface surface = new Surface(surfaceTexture);
        mSurfaceTextureSurface = surface;
        mSurfaceTextureAvailable = true;
        openVideo();
//        if (mMediaPlayer != null) {
//            mMediaPlayer.setSurface(surface);
//        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if(DEBUG_SURFACE) {
            Log.d(TAG, " onSurfaceTextureSizeChanged : surface = "+ surface+" width = "+ width+" height = "+ height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if(DEBUG_SURFACE) {
            Log.d(TAG, " onSurfaceTextureDestroyed returning false ");
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
//        if (mMediaPlayer == null) {
//            return;
//        }
        if(demoPlayer == null){
            return;
        }
        if (isPlaying()) {
            stop();
        }
        release();
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        scaleVideoSize(width, height);
    }

    private void scaleVideoSize(int videoWidth, int videoHeight) {
        if (videoWidth == 0 || videoHeight == 0) {
            return;
        }

        Size viewSize = new Size(getWidth(), getHeight());
        Size videoSize = new Size(videoWidth, videoHeight);
        ScaleManager scaleManager = new ScaleManager(viewSize, videoSize);
        Matrix matrix = scaleManager.getScaleMatrix(mScalableType);
        if (matrix != null) {
            setTransform(matrix);
        }
    }

    private void initializeMediaPlayer() {
//        if (mMediaPlayer == null) {
//            mMediaPlayer = new MediaPlayer();
//            mMediaPlayer.setOnVideoSizeChangedListener(this);
//            setSurfaceTextureListener(this);
//        } else {
//            reset();
//        }

        //

        if(DEBUG_INIT_RELEASE){
            Log.d(TAG," initializeMediaPlayer, demoPlayer = "+ demoPlayer + " playerNeedsPrepare = "+ playerNeedsPrepare+" mSurfaceTextureSurface = "+mSurfaceTextureSurface);
        }

        if (demoPlayer == null) {
            demoPlayer = new DemoPlayer(getRendererBuilder());
            demoPlayer.addListener(this);
            demoPlayer.setMetadataListener(this);
            demoPlayer.seekTo(playerPosition);
            playerNeedsPrepare = true;
            eventLogger = new EventLogger();
            eventLogger.startSession();
            demoPlayer.addListener(eventLogger);
            demoPlayer.setInfoListener(eventLogger);
            demoPlayer.setInternalErrorListener(eventLogger);
            setSurfaceTextureListener(this); //TODO ask yourself does this really belong here?
//            debugViewHelper = new DebugTextViewHelper(demoPlayer, debugTextView);
//            debugViewHelper.start();
        }

        if (playerNeedsPrepare) {
            demoPlayer.prepare();
            playerNeedsPrepare = false;
//            updateButtonVisibilities();
        }
        demoPlayer.setSurface(mSurfaceTextureSurface);
        demoPlayer.setPlayWhenReady(true);

    }

    private void openVideo() {
        if (mUri == null || !mSurfaceTextureAvailable ) {
            // not ready for playback just yet, will try again later
            return;
        }

        if(demoPlayer == null){
            Log.d(TAG,"openVideo calling initializeMediaPlayer------>");
            initializeMediaPlayer(); // all the listener setup stuff
        } else {
            demoPlayer.setSurface(mSurfaceTextureSurface);
        }

        Log.d(TAG, "openVideo -->");
    }

    private DemoPlayer.RendererBuilder getRendererBuilder() {
        String userAgent = Util.getUserAgent(mContext, "ExoPlayerScalableVideoView");
        switch (contentType) {
            case Util.TYPE_DASH:
                Log.d(TAG, " RendererBuilder : contentType = dash");
                return null; //TODO DASH
            case Util.TYPE_HLS:
                Log.d(TAG," RendererBuilder : contentType = HLS");
                return new HlsRendererBuilder(mContext, userAgent, mUri.toString());
            case Util.TYPE_OTHER:
                Log.d(TAG," RendererBuilder : contentType = other");
                return new ExtractorRendererBuilder(mContext, userAgent, mUri);
            default:
                throw new IllegalStateException("Unsupported type: " + contentType);
        }
    }

    /**
     * Makes a best guess to infer the type from a media {@link Uri} and an optional overriding file
     * extension.
     *
     * @param uri The {@link Uri} of the media.
     * @param fileExtension An overriding file extension.
     * @return The inferred type.
     */
    private static int inferContentType(Uri uri, String fileExtension) {
        String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension
                : uri.getLastPathSegment();
        return Util.inferContentType(lastPathSegment);
    }

    public void setRawData(@RawRes int id) throws IOException {
        AssetFileDescriptor afd = getResources().openRawResourceFd(id);
        setDataSource(afd);
    }

    public void setAssetData(@NonNull String assetName) throws IOException {
        AssetManager manager = getContext().getAssets();
        AssetFileDescriptor afd = manager.openFd(assetName);
        setDataSource(afd);
    }

    private void setDataSource(@NonNull AssetFileDescriptor afd) throws IOException {
        setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
    }

    public void setDataSource(@NonNull String path) throws IOException {
        initializeMediaPlayer();
//        mMediaPlayer.setDataSource(path);
    }

    public void setDataSource(@NonNull Context context, @NonNull Uri uri,
                              @Nullable Map<String, String> headers) throws IOException {
        if(DEBUG_INIT_RELEASE) {
            Log.d(TAG, " setDataSource with Headers uri = "+uri);
        }
        mUri = uri;
        contentType = inferContentType(mUri,"");
        initializeMediaPlayer();
//        mMediaPlayer.setDataSource(context, uri, headers);
    }

    public void setDataSource(@NonNull Context context, @NonNull Uri uri) throws IOException {
        if(DEBUG_INIT_RELEASE){
            Log.d(TAG," setDataSource uri = " + uri);
        }
        mUri = uri;
        contentType = inferContentType(mUri,"");
        initializeMediaPlayer();
//        mMediaPlayer.setDataSource(context, uri);
    }

    public void setDataSource(@NonNull FileDescriptor fd, long offset, long length)
            throws IOException {
        initializeMediaPlayer();
//        mMediaPlayer.setDataSource(fd, offset, length);
    }

    public void setDataSource(@NonNull FileDescriptor fd) throws IOException {
        initializeMediaPlayer();
//        mMediaPlayer.setDataSource(fd);
    }

    public void setScalableType(ScalableType scalableType) {
        mScalableType = scalableType;
        scaleVideoSize(getVideoWidth(), getVideoHeight());
    }

    public void prepare(@Nullable MediaPlayer.OnPreparedListener listener)
            throws IOException, IllegalStateException {
//        mMediaPlayer.setOnPreparedListener(listener);
//        mMediaPlayer.prepare();
        demoPlayer.prepare();
    }

    public void prepareAsync(@Nullable MediaPlayer.OnPreparedListener listener)
            throws IllegalStateException {
//        mMediaPlayer.setOnPreparedListener(listener);
//        mMediaPlayer.prepareAsync();
        //TODO preparedListeners
        demoPlayer.prepare();
    }

    public void prepare() throws IOException, IllegalStateException {
        prepare(null);
    }

    public void prepareAsync() throws IllegalStateException {
        prepareAsync(null);
    }

    public void setOnErrorListener(DemoPlayer.InternalErrorListener listener) {
        demoPlayer.setInternalErrorListener(listener);
//        mMediaPlayer.setOnErrorListener(listener);
    }

    public void setOnCompletionListener(@Nullable MediaPlayer.OnCompletionListener listener) {
        //TODO
//        mMediaPlayer.setOnCompletionListener(listener);
    }

    public void setOnInfoListener(DemoPlayer.InfoListener listener) {
//        mMediaPlayer.setOnInfoListener(listener);
        demoPlayer.setInfoListener(listener);
    }

    public int getCurrentPosition() {
        return (int) demoPlayer.getCurrentPosition();
//        return mMediaPlayer.getCurrentPosition();
    }

    public int getDuration() {
        return (int) demoPlayer.getDuration();
//        return mMediaPlayer.getDuration();
    }

    public int getVideoHeight() {
        return mVideoHeight;
//        return mMediaPlayer.getVideoHeight();
    }

    public int getVideoWidth() {
        return mVideoWidth;
//        return mMediaPlayer.getVideoWidth();
    }

    public boolean isLooping() {
//        return mMediaPlayer.isLooping();
        //TODO
        return false;
    }

    public boolean isPlaying() {
        return demoPlayer.getPlayerControl().isPlaying();
//        return mMediaPlayer.isPlaying();
    }

    public void pause() {
        demoPlayer.setPlayWhenReady(false);
//        mMediaPlayer.pause();
    }

    public void seekTo(int msec) {
        demoPlayer.seekTo(msec);
//        mMediaPlayer.seekTo(msec);
    }

    public void setLooping(boolean looping) {
//        mMediaPlayer.setLooping(looping);
    }

    public void setVolume(float leftVolume, float rightVolume) {
//        mMediaPlayer.setVolume(leftVolume, rightVolume);
    }


    public void start() {
        demoPlayer.setPlayWhenReady(true);
    }

    public void stop() {
        demoPlayer.setPlayWhenReady(false);
    }

    public void reset() {
//        mMediaPlayer.reset();
    }

    public void release() {
        if(DEBUG_INIT_RELEASE){
            Log.d(TAG," release() => demoPlayer.release()");
        }
        demoPlayer.release();
        demoPlayer = null;
//        reset();
//        mMediaPlayer.release();
//        mMediaPlayer = null;
    }

    // demoplayer callbacks

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch(playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
                break;
            case ExoPlayer.STATE_PREPARING:
                text += "preparing";
                break;
            case ExoPlayer.STATE_READY:
                text += "ready";
                break;
            default:
                text += "unknown";
                break;
        }
//        playerStateTextView.setText(text);
        if(DEBUG_STATE){
            Log.d(TAG, " onStateChanged: "+ text);
        }
    }

    @Override
    public void onError(Exception e) {
        if(DEBUG_ERROR) {
            Log.d(TAG, " onError() e = "+e.getMessage());
        }
        playerNeedsPrepare = true;
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        mVideoHeight = height;
        mVideoWidth = width;
    }

    @Override
    public void onId3Metadata(List<Id3Frame> id3Frames) {
        for (Id3Frame id3Frame : id3Frames) {
            if (id3Frame instanceof TxxxFrame) {
                TxxxFrame txxxFrame = (TxxxFrame) id3Frame;
                Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s", txxxFrame.id,
                        txxxFrame.description, txxxFrame.value));
            } else if (id3Frame instanceof PrivFrame) {
                PrivFrame privFrame = (PrivFrame) id3Frame;
                Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s", privFrame.id, privFrame.owner));
            } else if (id3Frame instanceof GeobFrame) {
                GeobFrame geobFrame = (GeobFrame) id3Frame;
                Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, description=%s",
                        geobFrame.id, geobFrame.mimeType, geobFrame.filename, geobFrame.description));
            } else {
                Log.i(TAG, String.format("ID3 TimedMetadata %s", id3Frame.id));
            }
        }
    }
}
