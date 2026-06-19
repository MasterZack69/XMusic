package com.xapps.media.xmusic.service;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.media.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.service.controls.actions.CommandAction;
import com.xapps.media.xmusic.service.resume.ResumeSong;
import com.xapps.media.xmusic.service.resume.ResumeState;
import com.xapps.media.xmusic.utils.Log;
import android.view.Choreographer;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.*;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.*;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSession.*;
import androidx.media3.session.MediaStyleNotificationHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.xapps.media.xmusic.activity.*;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.application.XApplication;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.helper.ServiceCallback;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.stats.StatsAudioAnalyzer;
import com.xapps.media.xmusic.utils.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Futures;

public class PlayerService extends MediaLibraryService {
	
	private static final String CHANNEL_ID = "music_channel";  
	private static final int NOTIFICATION_ID = 1;  
	public static ExoPlayer player;   
	private Handler handler;  
	private Runnable updateProgressRunnable;  
    private final Context c = this;
    public static long currentProgress;
	
	private Bitmap icon;  
	public static boolean isPlaying, isRunning;  
	private int currentState;
	public static int lastProgress = 0;
	public static int lastMax = 0;
    public static int currentPosition = 0;
    private boolean isBuilt = false;
    private boolean isNotifDead = true;
    private boolean isColorChanged = false;
    private byte tb[];
    private MediaMetadata mt;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor2 = Executors.newSingleThreadScheduledExecutor();
    private boolean isExecutorStarted = false;
    private Bitmap current;
    private ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2);
	private ScheduledFuture<?> currentTask;
	private MediaLibrarySession mediaSession;  
    public static List<MediaItem> mediaItems;
    public static boolean isReceiving = false;
    HandlerThread handlerThread = new HandlerThread("ExoPlayerThread");
	private static Handler ExoPlayerHandler;
	private android.media.AudioManager audioManager;
    private android.media.AudioFocusRequest audioFocusRequest;
    private boolean wasPausedDueToFocus = false;
    private boolean isInForeground = false;
	private ResumeState lastSavedState;
	private long lastResumeSave;
    
    private final IBinder binder = new LocalBinder();
    private Callback callback;
    
    private volatile long currentPlayerPositionMs = 0;
	
	private StatsAudioAnalyzer statsAnalyzer;
    
    private Choreographer choreographer;
    
    private final Runnable queryPlayerPositionRunnable = () -> {
        currentProgress = player.getCurrentPosition();
        RuntimeData.currentProgress = player.getCurrentPosition();
        ServiceCallback.Hub.send(ServiceCallback.CALLBACK_PROGRESS_UPDATE);
    };
    
    private int resId = R.drawable.placeholder;
    public static Uri fallbackUri;
    
    public static Map<String, Integer> lightColors;
    public static Map<String, Integer> darkColors;
    
    public static ArrayList<Song> songs = new ArrayList<>();
	
    
    Player.Commands playerCommands = new Player.Commands.Builder().addAllCommands().build();
    
    @Nullable
    @Override
    public MediaLibrarySession onGetSession(@NonNull ControllerInfo controllerInfo) {
        return mediaSession; 
    }
    
    @Override
    public void onTaskRemoved(Intent i) {
        ExoPlayerHandler.post(() -> {
            if (player.getMediaItemCount() == 0) {
				Log.d("RESUME_TEST", "media items count is 0, saving state to file");
				mainHandler.post(() -> {
					
				});
			}
        });
		Log.e("yes", "yes");
		//stopForeground(STOP_FOREGROUND_REMOVE);
		//NotificationManagerCompat.from(this).cancel(1001);
		saveResumeState();
		super.onTaskRemoved(i);
		//pauseAllPlayersAndStopSelf();
    }
	
	@Override
	public void onUpdateNotification(MediaSession session, boolean b) {
		super.onUpdateNotification(session, b);
		Log.e("hmm", "hmm");
	}

	@Override 
	public void onCreate() {  
		super.onCreate();  
		registerReceiver(
            noisyReceiver,
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        );
		statsAnalyzer = new StatsAudioAnalyzer(getApplicationContext());
        sendInitialUpdate(false); 
        DefaultMediaNotificationProvider cnp = new DefaultMediaNotificationProvider(this);
        cnp.setSmallIcon(R.drawable.service_icon);
        setMediaNotificationProvider(cnp);
        handlerThread.start();
        Looper backgroundLooper = handlerThread.getLooper();
		ExoPlayerHandler = new Handler(Looper.getMainLooper()/*backgroundLooper*/);
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
		player = new ExoPlayer.Builder(this, renderersFactory).setLooper(Looper.getMainLooper()).build();
        if (!isBuilt) {
		    setupMediaSession();
            isBuilt = true;
        }
        fallbackUri = Uri.parse("android.resource://" + this.getPackageName() + "/" + resId);
        androidx.media3.common.AudioAttributes attrs = new androidx.media3.common.AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build();
        ExoPlayerHandler.post(() -> {
            choreographer = Choreographer.getInstance();
            player.setAudioAttributes(attrs, true);
        });
		handler = new Handler(Looper.getMainLooper());  
        isNotifDead = false;
		createNotificationChannel();  
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setupAudioFocusRequest();
	}  
	
	@Nullable  
	@Override  
	public IBinder onBind(Intent intent) {  
		if (intent.getAction() == null) return null;
        if (intent.getAction().equals(MediaSessionService.SERVICE_INTERFACE)) {
            return super.onBind(intent);
        }
		return binder;
	}  
	
    private String data = "";
    
	@Override  
	public int onStartCommand(Intent intent, int flags, int startId) {  
		if (intent != null && intent.getAction() != null ) {  
			if (intent.getAction().equals("ACTION_UPDATE")) {
                mediaItems = new ArrayList<>();
                data = "";
                ArrayList<Song> song = RuntimeData.songs;
                executor.execute(() -> {
                    for (int i = 0; i < song.size(); i++) {
                        if (song.get(i).getArtworkUri() != null) {
                            mt = new MediaMetadata.Builder().setTitle(song.get(i).title).setArtist(song.get(i).artist).setArtworkUri(song.get(i).getArtworkUri()).build();
                        } else {
                            mt = new MediaMetadata.Builder().setTitle(song.get(i).title).setArtist(song.get(i).artist).setArtworkUri(fallbackUri).build();
                        }
                        String path = song.get(i).path;
                        Uri uri2 = Uri.fromFile(new File(path));
                        data = data + song.get(i).title + "|s|" + song.get(i).artist + "|s|" + path + "#i#";
                        MediaItem mediaItem = new MediaItem.Builder().setMediaMetadata(mt).setUri(uri2).build();
                        mediaItems.add(mediaItem);
                    }
                    DataManager.saveItemsList(data);
                    ExoPlayerHandler.post(() -> {
                        if (player.getMediaItemCount() == 0) {
                            player.addListener(new Player.Listener() {
                            @Override
                            public void onPlaybackStateChanged(int state) {
                                ServiceCallback.Hub.send(ServiceCallback.CALLBACK_VUMETER_UPDATE);
                                if (state == Player.STATE_READY) {
                                    anySongActive = true;
                                    currentPosition = player.getCurrentMediaItemIndex();
                                    sendUpdate(true);
                                    long duration = player.getDuration();
                                    if (!isRunning) {
                                        startUpdates();
                                        isRunning = true;
                                    }
                                    lastMax = (int) duration;
                                } else if (state == Player.STATE_BUFFERING || state == Player.STATE_ENDED) {
                                    anySongActive = true;
                                } else {
                                    anySongActive = false;
                                }
                            }
                                
                            @Override
                            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                                currentPosition = player.getCurrentMediaItemIndex();
								if (mediaItem == null || mediaItem.localConfiguration == null) {
									statsAnalyzer.stopAnalysis();
								} else {
									statsAnalyzer.startAnalysis(RuntimeData.songs.get(currentPosition));
								}
                                sendUpdate(true);
                            }
                                
                            @Override
                            public void onIsPlayingChanged(boolean playing) {
                                isPlaying = playing;
                                ServiceCallback.Hub.send(ServiceCallback.CALLBACK_VUMETER_UPDATE);
								if (isPlaying) {
									statsAnalyzer.resumeAnalysis();
								} else {
									statsAnalyzer.pauseAnalysis();
								}
                            }
								
							@Override
                            public void onEvents(Player player, Player.Events events) {
                                if (
                                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                                events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
                                ) {
                                    saveResumeState();
                                }
                            }
                    
                            });
                        }
                    });
                });
            } else if (intent.getAction().equals("ACTION_STOP")) {
				ExoPlayerHandler.post(() -> {
					player.stop();
					player.clearMediaItems();
				});
			} else if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                super.onStartCommand(intent, flags, startId);
            }
		}     
		return START_STICKY;  
	} 
    
    private ExecutorService executor_ = Executors.newFixedThreadPool(2);
    private long lastUpdate = 0;
    
    public void sendUpdate(boolean isFromNotif) {
        if (System.currentTimeMillis() - lastUpdate < 50 || currentPosition < 0) return;
        lastUpdate = System.currentTimeMillis();
        executor_.execute(() -> {
            Bitmap transparentBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.transparent); 
            Bitmap bmp;
            if (RuntimeData.songs == null) {
                bmp = transparentBitmap;
            } else {
                Uri thumb = RuntimeData.songs.get(currentPosition).getArtworkUri();
				boolean exists = false;
                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(thumb, "r")) {
                    exists = pfd != null;
                } catch (Exception ignored) {
                }
                bmp = exists? loadBitmapFromPath(thumb) : transparentBitmap;
            }
            if (DataManager.areStableColors()) {
                ColorPaletteUtils.generateFromColor(MaterialColorUtils.colorPrimary, (light, dark) -> {
                    lightColors = light;
                    darkColors = dark;
                    ServiceCallback.Hub.send(ServiceCallback.CALLBACK_COLORS_UPDATE);
                });
            } else {
                ColorPaletteUtils.generateFromBitmap(bmp, (light, dark) -> {
                    lightColors = light;
                    darkColors = dark;
                    ServiceCallback.Hub.send(ServiceCallback.CALLBACK_COLORS_UPDATE);
                });
            }if (DataManager.areStableColors()) {
                ColorPaletteUtils.generateFromColor(MaterialColorUtils.colorPrimary, (light, dark) -> {
                    lightColors = light;
                    darkColors = dark;
                    ServiceCallback.Hub.send(ServiceCallback.CALLBACK_COLORS_UPDATE);
                });
            } else {
                ColorPaletteUtils.generateFromBitmap(bmp, (light, dark) -> {
                    lightColors = light;
                    darkColors = dark;
                    ServiceCallback.Hub.send(ServiceCallback.CALLBACK_COLORS_UPDATE);
                });
            }
        });
    }
    
    private void sendInitialUpdate(boolean isFromNotif) {
        executor_.execute(() -> {
            Bitmap transparentBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.transparent); 
            ColorPaletteUtils.generateFromBitmap(transparentBitmap, (light, dark) -> {
                lightColors = light;
                darkColors = dark;
                ServiceCallback.Hub.send(ServiceCallback.CALLBACK_COLORS_UPDATE);
            });
        });
    }
    
    private Bitmap loadBitmapFromPath(Uri uri) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }
	
	private ResumeState buildResumeState() {
		
        ResumeState state = new ResumeState();
		
		state.shuffle = player.getShuffleModeEnabled();
        state.repeatMode = player.getRepeatMode();
        state.currentIndex = player.getCurrentMediaItemIndex();
        state.positionMs = player.getCurrentPosition();
        state.speed = player.getPlaybackParameters().speed;

        for (Song song : RuntimeData.songs) {
        state.songs.add(
            new ResumeSong(
                song.title,
                song.artist,
                song.path,
                song.getArtworkUri() != null
                    ? song.getArtworkUri().toString()
                    : null
                )
            );
        }

        return state;
    }
	
	private void saveResumeState() {
        long now = System.currentTimeMillis();

        if (now - lastResumeSave < 3000) {
            return;
        }

        lastResumeSave = now;

        ExoPlayerHandler.post(() -> {
            ResumeState state = buildResumeState();

            executor.execute(() -> {
                try (
                FileOutputStream fos = openFileOutput("resume_state.dat", MODE_PRIVATE);
                ObjectOutputStream oos = new ObjectOutputStream(fos)
                ) {
                    oos.writeObject(state);
                } catch (Exception e) {
                    Log.e("RESUME_TEST", "SAVE FAILED", e);
                }
            });
        });
    }
	
	private ResumeState loadResumeState() {
        try (
        FileInputStream fis = openFileInput("resume_state.dat");
        ObjectInputStream ois = new ObjectInputStream(fis)
        ) {
            return (ResumeState) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
	
	private MediaSession.MediaItemsWithStartPosition restorePlaylist() {
        ResumeState state = loadResumeState();

        if (state == null || state.songs.isEmpty()) {
            return new MediaSession.MediaItemsWithStartPosition(
                new ArrayList<>(),
                0,
                0
            );
        }

        ArrayList<MediaItem> items = new ArrayList<>();

        for (ResumeSong song : state.songs) {
            MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(
                    song.artworkUri != null
                    ? Uri.parse(song.artworkUri)
                    : fallbackUri
                )
                .build();

            items.add(
            new MediaItem.Builder()
                .setMediaMetadata(metadata)
                .setUri(Uri.fromFile(new File(song.path)))
                .build()
            );
        }

        return new MediaSession.MediaItemsWithStartPosition(
            items,
            9,
            9999
        );
    }
    
    private byte[] toByteArray(Bitmap bitmap) {
        java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }
    
    public void cancelProgressUpdates() {
        if (isExecutorStarted) {
            if (currentTask != null && !currentTask.isCancelled()) {
                currentTask.cancel(true);
            }
        }
    }
    
    private final Choreographer.FrameCallback playerProgressCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (player != null && isExecutorStarted) {
                currentProgress = player.getCurrentPosition();
                RuntimeData.currentProgress = currentProgress;
                ServiceCallback.Hub.send(ServiceCallback.CALLBACK_PROGRESS_UPDATE);
            }
            choreographer.postFrameCallback(this);
        }
    };
    
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

private final Runnable progressRunnable = new Runnable() {
    @Override
    public void run() {
        if (player != null && isExecutorStarted) {
            currentProgress = player.getCurrentPosition();
            RuntimeData.currentProgress = currentProgress;
            ServiceCallback.Hub.send(ServiceCallback.CALLBACK_PROGRESS_UPDATE);
            
            ExoPlayerHandler.postDelayed(this, 10);
        }
    }
};
    

    /*private void startUpdates() {
        if (isExecutorStarted) return;
        isExecutorStarted = true;
        choreographer.postFrameCallback(playerProgressCallback);
    }

    private void stopUpdates() {
        isExecutorStarted = false;
        choreographer.removeFrameCallback(playerProgressCallback);
    }*/
    
    private void startUpdates() {
    if (isExecutorStarted) return;
    isExecutorStarted = true;
   
    ExoPlayerHandler.removeCallbacks(progressRunnable);
    
    ExoPlayerHandler.post(progressRunnable);
}

private void stopUpdates() {
    isExecutorStarted = false;
    ExoPlayerHandler.removeCallbacks(progressRunnable);
}
    

	
    
	private Notification buildNotification(String title, String artist, String cover) {
        if (mediaSession == null) {
            setupMediaSession();
        }
        MediaStyleNotificationHelper.MediaStyle mediaStyle = new MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(3 , 2 , 1, 0);
        Intent resumeIntent = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent contentIntent = PendingIntent.getActivity(c, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
		.setSmallIcon(R.drawable.service_icon)
		.setContentTitle(title)
		.setContentText(artist)
		.setLargeIcon(current)
    	.setStyle(mediaStyle)
		.setOngoing(true)
        .setContentIntent(contentIntent)
		.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
		.build();
    }
    
    public interface BitmapCallback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    public void loadFromUri(String path, BitmapCallback callback) {
        executor.execute(() -> {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onBitmapLoaded(bmp);
            });
        });
    }
	
	private void createNotificationChannel() {  
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
			NotificationChannel channel = new NotificationChannel(  
			CHANNEL_ID,  
			"Music Playback",  
			NotificationManager.IMPORTANCE_LOW  
			);  
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);  
			if (manager != null) {  
				manager.createNotificationChannel(channel);  
			}  
		}  
	}  
	
    @Override
    public void onDestroy() {
        stopUpdates();

        ExoPlayerHandler.post(() -> {
            if (player != null) {
                player.clearMediaItems();
                player.release();
                player = null;
            }

            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
            }

            handlerThread.quitSafely();
        });

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }

        abandonAudioFocus();

        try {
            unregisterReceiver(noisyReceiver);
        } catch (Exception ignored) {}

        super.onDestroy();
    }
	
	private PendingIntent getServiceIntent(Context context, String action) {  
		Intent intent = new Intent(context, PlayerService.class);  
		intent.setAction(action);  
		return PendingIntent.getService(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);  
	}

    public void loadBitmapFromUri(Uri uri, Consumer<Bitmap> callback) {
        executor.execute(() -> {
            Bitmap result = null;
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(uri);
                result = BitmapFactory.decodeStream(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {}
                }
            }
            Bitmap finalResult = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.accept(finalResult);
            });
        });

    }
	
	public static Bitmap cropToWide(Bitmap source) {
		if (source == null) throw new IllegalArgumentException("Bitmap is null, genius.");
		final float TARGET_RATIO = 2f;
		int srcWidth = source.getWidth();
		int srcHeight = source.getHeight();
		float srcRatio = (float) srcWidth / srcHeight;
		if (Math.abs(srcRatio - TARGET_RATIO) < 0.01f) {
			return source;
		}
		int cropWidth, cropHeight;
		if (srcRatio > TARGET_RATIO) {
			cropHeight = srcHeight;
			cropWidth = Math.round(TARGET_RATIO * cropHeight);
		} else {
			cropWidth = srcWidth;
			cropHeight = Math.round(cropWidth / TARGET_RATIO);
		}
		int x = Math.max((srcWidth - cropWidth) / 2, 0);
		int y = Math.max((srcHeight - cropHeight) / 2, 0);
		return Bitmap.createBitmap(source, x, y, cropWidth, cropHeight);
	}
	
	private void setupMediaSession() {
		if (mediaSession != null) {
			return;
		}
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession = new androidx.media3.session.MediaLibraryService.MediaLibrarySession.Builder(this, player, new CustomCallback()).setId("XMusicMediaSession").setSessionActivity(sessionActivity).build();
    }
    
    public class CustomCallback implements MediaLibrarySession.Callback {
        private String loopMode = DataManager.getLatestRepeatMode();
        private String shuffleMode = DataManager.getLatestShuffleMode();
        
        private int loopIcon = loopMode.equals("LOOP_OFF")? R.drawable.ic_repeat_off : loopMode.equals("LOOP_SINGLE")? R.drawable.ic_repeat_one : R.drawable.ic_repeat;
        private int shuffleIcon = shuffleMode.equals("SHUFFLE_ON")? R.drawable.ic_shuffle : R.drawable.ic_shuffle_off;
     
        SessionCommand loopCommand = new SessionCommand(loopMode, Bundle.EMPTY);
        CommandButton loopButton = new CommandButton.Builder(getLoopMedia3Icon(loopMode))
                                    .setDisplayName("repeat")
                                    .setSessionCommand(loopCommand)
                                    .setSlots(CommandButton.SLOT_FORWARD)
                                    .build();
                                    
        SessionCommand shuffleCommand = new SessionCommand(shuffleMode, Bundle.EMPTY);
        CommandButton shuffleButton = new CommandButton.Builder(getShuffleMedia3Icon(shuffleMode))
                                        .setDisplayName("shuffle")
                                        .setSessionCommand(shuffleCommand)
                                        .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                                        .build();
        List<CommandButton> commandsList = ImmutableList.of(loopButton, shuffleButton);
        SessionCommands sessionCommands = SessionCommands.EMPTY.buildUpon()
                                            .add(new SessionCommand("LOOP_ALL", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_SINGLE", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_OFF", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_ON", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_OFF", Bundle.EMPTY))
                                            .build();
            @Override
            public ConnectionResult onConnect(MediaSession mediaSession, ControllerInfo controllerInfo) {
                ExoPlayerHandler.post(() -> {
                    if (loopMode.equals("LOOP_OFF")) {
                        player.setRepeatMode(Player.REPEAT_MODE_OFF);
                        player.setPauseAtEndOfMediaItems(true);
                    } else if (loopMode.equals("LOOP_SINGLE")) {
                        player.setRepeatMode(Player.REPEAT_MODE_ONE);
                    } else if (loopMode.equals("LOOP_ALL")) {
                        player.setPauseAtEndOfMediaItems(false);
                        player.setRepeatMode(Player.REPEAT_MODE_ALL);
                    }
                    if (shuffleMode.equals("SHUFFLE_ON")) {
                        player.setShuffleModeEnabled(true);
                    } else {
                        player.setShuffleModeEnabled(false);
                    }
                });
                return ConnectionResult.accept(sessionCommands, playerCommands);
            }
            
            @Override
            public void onPostConnect(MediaSession mediaSession, ControllerInfo controllerInfo) {
                mediaSession.setAvailableCommands(controllerInfo, sessionCommands, playerCommands);
                mediaSession.setCustomLayout(controllerInfo, commandsList);
            }
            
            @Override
            public ListenableFuture<SessionResult> onCustomCommand(MediaSession mediaSession, ControllerInfo controllerInfo, SessionCommand sessionCommand, Bundle bundle) {
                ExoPlayerHandler.post(() -> {
                    loopMode = switch (sessionCommand.customAction) {
                        case "LOOP_ALL" -> {
                            loopIcon = R.drawable.ic_repeat_one;
                            player.setRepeatMode(Player.REPEAT_MODE_ONE);
                            yield "LOOP_SINGLE"; 
                        }
                        case "LOOP_SINGLE" -> {
                            player.setPauseAtEndOfMediaItems(true);
                            loopIcon = R.drawable.ic_repeat_off;
                            player.setRepeatMode(Player.REPEAT_MODE_OFF);
                            yield "LOOP_OFF";
                        }
                        case "LOOP_OFF" -> {
                            player.setPauseAtEndOfMediaItems(false);
                            player.setRepeatMode(Player.REPEAT_MODE_ALL);
                            loopIcon = R.drawable.ic_repeat;
                            yield "LOOP_ALL"; 
                        }
                        default -> loopMode;
                    };
                
                    shuffleMode = switch (sessionCommand.customAction) {
                        case "SHUFFLE_ON" -> {
                            player.setShuffleModeEnabled(false);
                            shuffleIcon = R.drawable.ic_shuffle_off;
                            yield "SHUFFLE_OFF";
                        }
                        case "SHUFFLE_OFF" -> {
                            player.setShuffleModeEnabled(true);
                            shuffleIcon = R.drawable.ic_shuffle;
                            yield "SHUFFLE_ON"; 
                        }
                        default -> shuffleMode;
                    };

                    loopCommand = new SessionCommand(loopMode, Bundle.EMPTY);
                    loopButton = new CommandButton.Builder(getLoopMedia3Icon(loopMode))
                        .setDisplayName("repeat")
                        .setSessionCommand(loopCommand)
                        .setSlots(CommandButton.SLOT_FORWARD)
                        .build();
                    shuffleCommand = new SessionCommand(shuffleMode, Bundle.EMPTY);
                    shuffleButton = new CommandButton.Builder(getShuffleMedia3Icon(shuffleMode))
                                        .setDisplayName("shuffle")
                                        .setSessionCommand(shuffleCommand)
                                        .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                                        .build();
                    commandsList = ImmutableList.of(loopButton, shuffleButton);
                    DataManager.saveLatestRepeatMode(loopMode);
                    DataManager.saveLatestShuffleMode(shuffleMode);
                    mediaSession.setCustomLayout(controllerInfo, commandsList);
                });

                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            
            
            @Override
public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
        MediaSession mediaSession,
        ControllerInfo controller,
        boolean isForPlayback
) {
    SettableFuture<MediaSession.MediaItemsWithStartPosition> future = SettableFuture.create();

    executor.execute(() -> {
        try {
            MediaSession.MediaItemsWithStartPosition items = restorePlaylist();
            future.set(items);
        } catch (Exception e) {
            future.setException(e);
        }
    });

    return future;
}

		
		@Override
public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
        MediaLibrarySession session, 
        ControllerInfo browser, 
        @Nullable LibraryParams params
) {
    MediaItem rootItem = new MediaItem.Builder()
            .setMediaId("XMUSIC_ROOT_ID")
            .setMediaMetadata(new MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build())
            .build();

    if (params != null && params.isRecent) {
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params));
    }

    return Futures.immediateFuture(LibraryResult.ofItem(rootItem, null));
}
		

    }
	
	private int getLoopMedia3Icon(String mode) {
        return switch (mode) {
            case "LOOP_OFF" -> CommandButton.ICON_REPEAT_OFF;
            case "LOOP_SINGLE" -> CommandButton.ICON_REPEAT_ONE;
            default -> CommandButton.ICON_REPEAT_ALL;
        };
    }

    private int getShuffleMedia3Icon(String mode) {
        return switch (mode) {
            case "SHUFFLE_ON" -> CommandButton.ICON_SHUFFLE_ON;
            default -> CommandButton.ICON_SHUFFLE_OFF;
        };
    }

    private void setupAudioFocusRequest() {
        android.media.AudioFocusRequest.Builder builder = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setOnAudioFocusChangeListener(this::handleAudioFocusChange)
        .setAudioAttributes(new android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build());
        audioFocusRequest = builder.build();
    }

    private void handleAudioFocusChange(int focusChange) {
        
    }

    public void abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest);
    }

    public interface Callback {
        void onData(String data);
    }
    
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
				ExoPlayerHandler.post(() -> {
                    if (player.isPlaying()) {
                        player.pause();
                    }
				});
            }
        }
    };
	

    public class LocalBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void sendDataToActivity(String data) {
        mainHandler.post(() -> {
            if (callback != null) callback.onData(data);
        });
    }

    public static int getCurrentPos() {
        return currentPosition;
    }
    
    public static boolean areMediaItemsEmpty = true;
    public static boolean anySongActive;

    public static boolean isAnythingPlaying() {
        return !areMediaItemsEmpty && anySongActive;
    }
}
