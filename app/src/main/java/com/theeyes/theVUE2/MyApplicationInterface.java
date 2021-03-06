package com.theeyes.theVUE2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.theeyes.theVUE2.CameraController.CameraController;
import com.theeyes.theVUE2.Preview.ApplicationInterface;
import com.theeyes.theVUE2.Preview.Preview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;

public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";
	
	private static final String TAG_GPS_IMG_DIRECTION = "GPSImgDirection";
	private static final String TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef";

	private MainActivity main_activity = null;
	private LocationSupplier locationSupplier = null;
	private StorageUtils storageUtils = null;

    private boolean immersive_mode = false;
    private boolean show_gui = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video
    
	private Paint p = new Paint();
	private Rect text_bounds = new Rect();
	private RectF face_rect = new RectF();
	private RectF draw_rect = new RectF();
	private int [] gui_location = new int[2];
	private DecimalFormat decimalFormat = new DecimalFormat("#0.0");

	private float free_memory_gb = -1.0f;
	private long last_free_memory_time = 0;

	private IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private boolean has_battery_frac = false;
	private float battery_frac = 0.0f;
	private long last_battery_time = 0;

	private Bitmap location_bitmap = null;
	private Bitmap location_off_bitmap = null;
	private Rect location_dest = new Rect();
	
	private String last_image_name = null;
	
	private Bitmap last_thumbnail = null; // thumbnail of last picture taken
	private boolean thumbnail_anim = false; // whether we are displaying the thumbnail animation
	private long thumbnail_anim_start_ms = -1; // time that the thumbnail animation started
	private RectF thumbnail_anim_src_rect = new RectF();
	private RectF thumbnail_anim_dst_rect = new RectF();
	private Matrix thumbnail_anim_matrix = new Matrix();

	private ToastBoxer stopstart_video_toast = new ToastBoxer();
	
	// camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
	private int cameraId = 0;
	private int zoom_factor = 0;
	private float focus_distance = 0.0f;

	MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface");
		this.main_activity = main_activity;
		this.locationSupplier = new LocationSupplier(main_activity);
		this.storageUtils = new StorageUtils(main_activity);

        if( savedInstanceState != null ) {
    		cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
    		zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
			focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
			if( MyDebug.LOG )
				Log.d(TAG, "found focus_distance: " + focus_distance);
        }

        location_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.earth);
    	location_off_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.earth_off);
	}
	
	void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
		if( MyDebug.LOG )
			Log.d(TAG, "save cameraId: " + cameraId);
    	state.putInt("cameraId", cameraId);
		if( MyDebug.LOG )
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
    	state.putInt("zoom_factor", zoom_factor);
		if( MyDebug.LOG )
			Log.d(TAG, "save focus_distance: " + focus_distance);
    	state.putFloat("focus_distance", focus_distance);
	}

	LocationSupplier getLocationSupplier() {
		return locationSupplier;
	}
	
	StorageUtils getStorageUtils() {
		return storageUtils;
	}

    @Override
	public Context getContext() {
    	return main_activity;
    }
    
    @Override
	public boolean useCamera2() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if( main_activity.supportsCamera2() ) {
    		return sharedPreferences.getBoolean(PreferenceKeys.getUseCamera2PreferenceKey(), false);
        }
        return false;
    }
    
	@Override
	public Location getLocation() {
		return locationSupplier.getLocation();
	}

	@Override
	public File getOutputMediaFile(int type) {
		return storageUtils.getOutputMediaFile(type);
	}

	@Override
	public int getCameraIdPref() {
		return cameraId;
	}
	
    @Override
	public String getFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "");
    }

    @Override
	public String getFocusPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFocusPreferenceKey(cameraId), "");
    }

    @Override
	public boolean isVideoPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getIsVideoPreferenceKey(), false);
    }

    @Override
	public String getSceneModePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getSceneModePreferenceKey(), "auto");
		return value;
    }
    
    @Override
    public String getColorEffectPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getColorEffectPreferenceKey(), "none");
    }

    @Override
    public String getWhiteBalancePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getWhiteBalancePreferenceKey(), "auto");
    }

    @Override
	public String getISOPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getISOPreferenceKey(), "auto");
    }
    
    @Override
	public int getExposureCompensationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getExposurePreferenceKey(), "0");
		if( MyDebug.LOG )
			Log.d(TAG, "saved exposure value: " + value);
		int exposure = 0;
		try {
			exposure = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "exposure: " + exposure);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "exposure invalid format, can't parse to int");
		}
		return exposure;
    }

    @Override
	public Pair<Integer, Integer> getCameraResolutionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String resolution_value = sharedPreferences.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId), "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		if( resolution_value.length() > 0 ) {
			// parse the saved size, and make sure it is still valid
			int index = resolution_value.indexOf(' ');
			if( index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resolution_value invalid format, can't find space");
			}
			else {
				String resolution_w_s = resolution_value.substring(0, index);
				String resolution_h_s = resolution_value.substring(index+1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "resolution_w_s: " + resolution_w_s);
					Log.d(TAG, "resolution_h_s: " + resolution_h_s);
				}
				try {
					int resolution_w = Integer.parseInt(resolution_w_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_w: " + resolution_w);
					int resolution_h = Integer.parseInt(resolution_h_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_h: " + resolution_h);
					return new Pair<Integer, Integer>(resolution_w, resolution_h);
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
				}
			}
		}
		return null;
    }
    
    @Override
    public int getImageQualityPref(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQualityPref");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String image_quality_s = sharedPreferences.getString(PreferenceKeys.getQualityPreferenceKey(), "90");
		int image_quality = 0;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
    }
    
	@Override
	public boolean getFaceDetectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getFaceDetectionPreferenceKey(), false);
    }

    @Override
    public boolean getMotionDetectionPred() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sharedPreferences.getBoolean(PreferenceKeys.getMotionDetectionPreferenceKey(), false);
    }

	@Override
	public String getVideoQualityPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), "");
	}
	
    @Override
	public boolean getVideoStabilizationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getVideoStabilizationPreferenceKey(), false);
    }
    
    @Override
	public boolean getForce4KPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( cameraId == 0 && sharedPreferences.getBoolean(PreferenceKeys.getForceVideo4KPreferenceKey(), false) && main_activity.supportsForceVideo4K() ) {
			return true;
		}
		return false;
    }
    
    @Override
    public String getVideoBitratePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getVideoBitratePreferenceKey(), "default");
    }

    @Override
    public String getVideoFPSPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getVideoFPSPreferenceKey(), "default");
    }
    
    @Override
    public long getVideoMaxDurationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String video_max_duration_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "0");
		long video_max_duration = 0;
		try {
			video_max_duration = Integer.parseInt(video_max_duration_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_duration value: " + video_max_duration_value);
    		e.printStackTrace();
    		video_max_duration = 0;
        }
		return video_max_duration;
    }
    
    @Override
    public int getVideoRestartTimesPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String restart_value = sharedPreferences.getString(PreferenceKeys.getVideoRestartPreferenceKey(), "0");
		int remaining_restart_video = 0;
		try {
			remaining_restart_video = Integer.parseInt(restart_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_restart value: " + restart_value);
    		e.printStackTrace();
    		remaining_restart_video = 0;
        }
		return remaining_restart_video;
    }
    
    @Override
    public boolean getVideoFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoFlashPreferenceKey(), false);
    }
    
    @Override
	public String getPreviewSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg");
    }
    
    @Override
    public String getPreviewRotationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
    }
    
    @Override
    public String getLockOrientationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
    }

    @Override
    public boolean getPausePreviewPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getPausePreviewPreferenceKey(), false);
    }
    
    @Override
    public boolean getThumbnailAnimationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getThumbnailAnimationPreferenceKey(), true);
    }
    
    @Override
    public boolean getShutterSoundPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), true);
    }

    @Override
    public long getTimerPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
		long timer_delay = 0;
		try {
			timer_delay = Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_timer value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }
		return timer_delay;
    }
    
    @Override
    public String getRepeatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getBurstModePreferenceKey(), "1");
    }
    
    @Override
    public long getRepeatIntervalPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getBurstIntervalPreferenceKey(), "0");
		long timer_delay = 0;
		try {
			timer_delay = Integer.parseInt(timer_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_burst_interval value: " + timer_value);
    		e.printStackTrace();
    		timer_delay = 0;
        }
		return timer_delay;
    }
    
    @Override
    public boolean getGeotaggingPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getLocationPreferenceKey(), false);
    }
    
    @Override
    public boolean getRequireLocationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getRequireLocationPreferenceKey(), false);
    }
    
    @Override
    public boolean getGeodirectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getGPSDirectionPreferenceKey(), false);
    }
    
    @Override
	public boolean getRecordAudioPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getBoolean(PreferenceKeys.getRecordAudioPreferenceKey(), true);
    }
    
    @Override
    public String getRecordAudioSourcePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioSourcePreferenceKey(), "audio_src_camcorder");
    }

    @Override
    public boolean getAutoStabilisePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false);
		if( auto_stabilise && main_activity.supportsAutoStabilise() )
			return true;
		return false;
    }
    
    @Override
    public String getStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getStampPreferenceKey(), "preference_stamp_no");
    }
    
    @Override
    public String getTextStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getString(PreferenceKeys.getTextStampPreferenceKey(), "");
    }
    
    @Override
    public int getTextStampFontSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	int font_size = 12;
		String value = sharedPreferences.getString(PreferenceKeys.getStampFontSizePreferenceKey(), "12");
		if( MyDebug.LOG )
			Log.d(TAG, "saved font size: " + value);
		try {
			font_size = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "font_size: " + font_size);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "font size invalid format, can't parse to int");
		}
		return font_size;
    }
    
    @Override
    public int getZoomPref() {
		Log.d(TAG, "getZoomPref: " + zoom_factor);
    	return zoom_factor;
    }

    @Override
    public long getExposureTimePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    	return sharedPreferences.getLong(PreferenceKeys.getExposureTimePreferenceKey(), 1000000000l/30);
    }
    
    @Override
	public float getFocusDistancePref() {
    	return focus_distance;
    }

    @Override
    public boolean isTestAlwaysFocus() {
    	return main_activity.is_test;
    }


	@Override
	public void broadcastFile(File file, boolean is_new_picture, boolean is_new_video) {
		storageUtils.broadcastFile(file, is_new_picture, is_new_video);
		if( is_new_video ) {
			// create thumbnail
        	long time_s = System.currentTimeMillis();
    		Bitmap thumbnail = null;
    	    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				retriever.setDataSource(file.getPath());
				thumbnail = retriever.getFrameAtTime(-1);
			}
    	    catch(IllegalArgumentException ex) {
    	    	// corrupt video file?
    	    }
    	    catch(RuntimeException ex) {
    	    	// corrupt video file?
    	    }
    	    finally {
    	    	try {
    	    		retriever.release();
    	    	}
    	    	catch(RuntimeException ex) {
    	    		// ignore
    	    	}
    	    }
    	    if( thumbnail != null ) {
    	    	ImageButton galleryButton = (ImageButton) main_activity.findViewById(R.id.gallery);
    	    	int width = thumbnail.getWidth();
    	    	int height = thumbnail.getHeight();
				if( MyDebug.LOG )
					Log.d(TAG, "    video thumbnail size " + width + " x " + height);
    	    	if( width > galleryButton.getWidth() ) {
    	    		float scale = (float) galleryButton.getWidth() / width;
    	    		int new_width = Math.round(scale * width);
    	    		int new_height = Math.round(scale * height);
					if( MyDebug.LOG )
						Log.d(TAG, "    scale video thumbnail to " + new_width + " x " + new_height);
    	    		Bitmap scaled_thumbnail = Bitmap.createScaledBitmap(thumbnail, new_width, new_height, true);
        		    // careful, as scaled_thumbnail is sometimes not a copy!
        		    if( scaled_thumbnail != thumbnail ) {
        		    	thumbnail.recycle();
        		    	thumbnail = scaled_thumbnail;
        		    }
    	    	}
    	    	final Bitmap thumbnail_f = thumbnail;
    	    	main_activity.runOnUiThread(new Runnable() {
					public void run() {
    	    	    	updateThumbnail(thumbnail_f);
					}
				});
    	    }
			if( MyDebug.LOG )
				Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - time_s));
		}
	}

	@Override
	public void cameraSetup() {
		main_activity.cameraSetup();
	}

	@Override
	public void touchEvent(MotionEvent event) {
		main_activity.clearSeekBar();
		main_activity.closePopup();
		if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}
	}
	
	@Override
	public void startingVideo() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(PreferenceKeys.getLockVideoPreferenceKey(), false) ) {
			main_activity.lockScreen();
		}
	}

	@Override
	public void stoppingVideo() {
		main_activity.unlockScreen();
		if( main_activity.getPreview().isVideoRecording() ) {
			String toast = getContext().getResources().getString(R.string.stopped_recording_video);
			if( main_activity.getPreview().getRemainingRestartVideo() > 0 ) {
				toast += " (" + main_activity.getPreview().getRemainingRestartVideo() + " " + getContext().getResources().getString(R.string.repeats_to_go) + ")";
			}
			main_activity.getPreview().showToast(stopstart_video_toast, toast);
		}
	}

	@Override
	public void onVideoInfo(int what, int extra) {
		if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
			int message_id = 0;
			if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "max duration reached");
				message_id = R.string.video_max_duration;
			}
			else if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "max filesize reached");
				message_id = R.string.video_max_filesize;
			}
			if( message_id != 0 )
				main_activity.getPreview().showToast(null, message_id);
			String debug_value = "error_" + what + "_" + extra;
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("last_video_error", debug_value);
			editor.apply();
			main_activity.getPreview().stopVideo(false);
		}
	}

	@Override
	public void onFailedStartPreview() {
		main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
	}

	@Override
	public void onPhotoError() {
	    main_activity.getPreview().showToast(null, R.string.failed_to_take_picture);
	}

	@Override
	public void onVideoError(int what, int extra) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
		}
		int message_id = R.string.video_error_unknown;
		if( what == MediaRecorder.MEDIA_ERROR_SERVER_DIED  ) {
			if( MyDebug.LOG )
				Log.d(TAG, "error: server died");
			message_id = R.string.video_error_server_died;
		}
		main_activity.getPreview().showToast(null, message_id);
		String debug_value = "info_" + what + "_" + extra;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("last_video_error", debug_value);
		editor.apply();
		main_activity.getPreview().stopVideo(false);
	}
	
	@Override
	public void onVideoRecordStartError(CamcorderProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStartError");
		String error_message = "";
		String features = main_activity.getPreview().getErrorFeatures(profile);
		if( features.length() > 0 ) {
			error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		else {
			error_message = getContext().getResources().getString(R.string.failed_to_record_video);
		}
		main_activity.getPreview().showToast(null, error_message);
	}

	@Override
	public void onVideoRecordStopError(CamcorderProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStopError");
		//main_activity.getPreview().showToast(null, R.string.failed_to_record_video);
		String features = main_activity.getPreview().getErrorFeatures(profile);
		String error_message = getContext().getResources().getString(R.string.video_may_be_corrupted);
		if( features.length() > 0 ) {
			error_message += ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		main_activity.getPreview().showToast(null, error_message);
	}
	
	@Override
	public void onFailedReconnectError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_reconnect_camera);
	}
	
	@Override
	public void onFailedCreateVideoFileError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_save_video);
	}

    void setImmersiveMode(final boolean immersive_mode) {
		if( MyDebug.LOG )
			Log.d(TAG, "setImmersiveMode: " + immersive_mode);
    	this.immersive_mode = immersive_mode;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
				// if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
		    	//final int visibility_gone = immersive_mode ? View.GONE : View.VISIBLE;
		    	final int visibility = immersive_mode ? View.GONE : View.VISIBLE;
				if( MyDebug.LOG )
					Log.d(TAG, "setImmersiveMode: set visibility: " + visibility);
		    	// n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
//			    View switchCameraButton = (View) main_activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = (View) main_activity.findViewById(R.id.switch_video);
			    View exposureButton = (View) main_activity.findViewById(R.id.exposure);
			    View exposureLockButton = (View) main_activity.findViewById(R.id.exposure_lock);
			    View popupButton = (View) main_activity.findViewById(R.id.popup);
			    View galleryButton = (View) main_activity.findViewById(R.id.gallery);
			    View settingsButton = (View) main_activity.findViewById(R.id.settings);
			    View zoomControls = (View) main_activity.findViewById(R.id.zoom);
			    View zoomSeekBar = (View) main_activity.findViewById(R.id.zoom_seekbar);
//			    if( main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 )
//			    	switchCameraButton.setVisibility(visibility);
		    	switchVideoButton.setVisibility(visibility);
			    if( main_activity.supportsExposureButton() )
			    	exposureButton.setVisibility(visibility);
			    if( main_activity.getPreview().supportsExposureLock() )
			    	exposureLockButton.setVisibility(visibility);
		    	popupButton.setVisibility(visibility);
			    galleryButton.setVisibility(visibility);
			    settingsButton.setVisibility(visibility);
				if( MyDebug.LOG ) {
					Log.d(TAG, "has_zoom: " + main_activity.getPreview().supportsZoom());
				}
				if( main_activity.getPreview().supportsZoom() && sharedPreferences.getBoolean(PreferenceKeys.getShowZoomControlsPreferenceKey(), false) ) {
					zoomControls.setVisibility(visibility);
				}
				if( main_activity.getPreview().supportsZoom() && sharedPreferences.getBoolean(PreferenceKeys.getShowZoomSliderControlsPreferenceKey(), true) ) {
					zoomSeekBar.setVisibility(visibility);
				}
        		String pref_immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
        		if( pref_immersive_mode.equals("immersive_mode_everything") ) {
    			    View takePhotoButton = (View) main_activity.findViewById(R.id.take_photo);
    			    takePhotoButton.setVisibility(visibility);
        		}
				if( !immersive_mode ) {
					// make sure the GUI is set up as expected
					showGUI(show_gui);
				}
			}
		});
    }
    
    boolean inImmersiveMode() {
    	return immersive_mode;
    }

    private void showGUI(final boolean show) {
		if( MyDebug.LOG )
			Log.d(TAG, "showGUI: " + show);
		this.show_gui = show;
		if( inImmersiveMode() )
			return;
		if( show && main_activity.usingKitKatImmersiveMode() ) {
			// call to reset the timer
			main_activity.initImmersiveMode();
		}
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
		    	final int visibility = show ? View.VISIBLE : View.GONE;
//			    View switchCameraButton = (View) main_activity.findViewById(R.id.switch_camera);
			    View switchVideoButton = (View) main_activity.findViewById(R.id.switch_video);
			    View exposureButton = (View) main_activity.findViewById(R.id.exposure);
			    View exposureLockButton = (View) main_activity.findViewById(R.id.exposure_lock);
			    View popupButton = (View) main_activity.findViewById(R.id.popup);
//			    if( main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 )
//			    	switchCameraButton.setVisibility(visibility);
			    if( !main_activity.getPreview().isVideo() )
			    	switchVideoButton.setVisibility(visibility); // still allow switch video when recording video
			    if( main_activity.supportsExposureButton() && !main_activity.getPreview().isVideo() ) // still allow exposure when recording video
			    	exposureButton.setVisibility(visibility);
			    if( main_activity.getPreview().supportsExposureLock() && !main_activity.getPreview().isVideo() ) // still allow exposure lock when recording video
			    	exposureLockButton.setVisibility(visibility);
			    if( !show ) {
			    	main_activity.closePopup(); // we still allow the popup when recording video, but need to update the UI (so it only shows flash options), so easiest to just close
			    }
			    if( !main_activity.getPreview().isVideo() || !main_activity.getPreview().supportsFlash() )
			    	popupButton.setVisibility(visibility); // still allow popup in order to change flash mode when recording video
			}
		});
    }

    @Override
	public void hasPausedPreview(boolean paused) {
	    View shareButton = (View) main_activity.findViewById(R.id.share);
	    View trashButton = (View) main_activity.findViewById(R.id.trash);
	    if( paused ) {
		    shareButton.setVisibility(View.VISIBLE);
		    trashButton.setVisibility(View.VISIBLE);
	    }
	    else {
			shareButton.setVisibility(View.GONE);
		    trashButton.setVisibility(View.GONE);
	    }
		
	}
	
    @Override
    public void cameraInOperation(boolean in_operation) {
    	showGUI(!in_operation);
    }

	@Override
	public void cameraClosed() {
		main_activity.clearSeekBar();
	}

	@Override
	public void updateThumbnail(Bitmap thumbnail) {
		thumbnail_anim = true;
		thumbnail_anim_start_ms = System.currentTimeMillis();
		main_activity.updateThumbnail(thumbnail);

    	Bitmap old_thumbnail = this.last_thumbnail;
    	this.last_thumbnail = thumbnail;
    	if( old_thumbnail != null ) {
    		// only recycle after we've set the new thumbnail
    		old_thumbnail.recycle();
    	}
	}
	
	@Override
	public void timerBeep() {
		if( MyDebug.LOG )
			Log.d(TAG, "timerBeep()");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( sharedPreferences.getBoolean(PreferenceKeys.getTimerBeepPreferenceKey(), true) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "play beep!");
		    try {
		        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				Activity activity = (Activity)getContext();
		        Ringtone r = RingtoneManager.getRingtone(activity.getApplicationContext(), notification);
		        r.play();
		    }
		    catch(Exception e) {
		    }		
		}
	}

	@Override
	public void layoutUI() {
		main_activity.layoutUI();
	}
	
	@Override
	public void multitouchZoom(int new_zoom) {
		main_activity.setSeekbarZoom();
	}

	@Override
	public void setCameraIdPref(int cameraId) {
		this.cameraId = cameraId;
	}

    @Override
    public void setFlashPref(String flash_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value);
		editor.apply();
    }

    @Override
    public void setFocusPref(String focus_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFocusPreferenceKey(cameraId), focus_value);
		editor.apply();
		// focus may be updated by preview (e.g., when switching to/from video mode)
    	final int visibility = main_activity.getPreview().getCurrentFocusValue() != null && main_activity.getPreview().getCurrentFocusValue().equals("focus_mode_manual2") ? View.VISIBLE : View.INVISIBLE;
	    View focusSeekBar = (SeekBar) main_activity.findViewById(R.id.focus_seekbar);
	    focusSeekBar.setVisibility(visibility);
    }

    @Override
	public void setVideoPref(boolean is_video) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PreferenceKeys.getIsVideoPreferenceKey(), is_video);
		editor.apply();
    }

    @Override
    public void setSceneModePref(String scene_mode) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getSceneModePreferenceKey(), scene_mode);
		editor.apply();
    }
    
    @Override
	public void clearSceneModePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getSceneModePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setColorEffectPref(String color_effect) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getColorEffectPreferenceKey(), color_effect);
		editor.apply();
    }
	
    @Override
	public void clearColorEffectPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getColorEffectPreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setWhiteBalancePref(String white_balance) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getWhiteBalancePreferenceKey(), white_balance);
		editor.apply();
    }

    @Override
	public void clearWhiteBalancePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getWhiteBalancePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setISOPref(String iso) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getISOPreferenceKey(), iso);
		editor.apply();
    }

    @Override
	public void clearISOPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getISOPreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setExposureCompensationPref(int exposure) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getExposurePreferenceKey(), "" + exposure);
		editor.apply();
    }

    @Override
	public void clearExposureCompensationPref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getExposurePreferenceKey());
		editor.apply();
    }
	
    @Override
	public void setCameraResolutionPref(int width, int height) {
		String resolution_value = width + " " + height;
		if( MyDebug.LOG ) {
			Log.d(TAG, "save new resolution_value: " + resolution_value);
		}
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getResolutionPreferenceKey(cameraId), resolution_value);
		editor.apply();
    }
    
    @Override
    public void setVideoQualityPref(String video_quality) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), video_quality);
		editor.apply();
    }
    
    @Override
	public void setZoomPref(int zoom) {
		Log.d(TAG, "setZoomPref: " + zoom);
    	this.zoom_factor = zoom;
    }
    
    @Override
	public void setExposureTimePref(long exposure_time) {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(PreferenceKeys.getExposureTimePreferenceKey(), exposure_time);
		editor.apply();
	}

    @Override
	public void clearExposureTimePref() {
    	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getExposureTimePreferenceKey());
		editor.apply();
    }

    @Override
	public void setFocusDistancePref(float focus_distance) {
		this.focus_distance = focus_distance;
	}

    @Override
    public void onDrawPreview(Canvas canvas) {
		MainActivity main_activity = (MainActivity)this.getContext();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		boolean has_level_angle = preview.hasLevelAngle();
		double level_angle = preview.getLevelAngle();
		boolean has_geo_direction = preview.hasGeoDirection();
		double geo_direction = preview.getGeoDirection();
		boolean ui_placement_right = main_activity.getUIPlacementRight();
		if( inImmersiveMode() ) {
			String immersive_mode = sharedPreferences.getString(PreferenceKeys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
			if( immersive_mode.equals("immersive_mode_everything") ) {
				// exit, to ensure we don't display anything!
				return;
			}
		}
		final float scale = getContext().getResources().getDisplayMetrics().density;
		String preference_grid = sharedPreferences.getString(PreferenceKeys.getShowGridPreferenceKey(), "preference_grid_none");
		if( camera_controller != null && preference_grid.equals("preference_grid_3x3") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/3.0f, 0.0f, canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(2.0f*canvas.getWidth()/3.0f, 0.0f, 2.0f*canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, canvas.getHeight()/3.0f, p);
			canvas.drawLine(0.0f, 2.0f*canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, 2.0f*canvas.getHeight()/3.0f, p);
		}
		if( camera_controller != null && preference_grid.equals("preference_grid_4x2") ) {
			p.setColor(Color.GRAY);
			canvas.drawLine(canvas.getWidth()/4.0f, 0.0f, canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(canvas.getWidth()/2.0f, 0.0f, canvas.getWidth()/2.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(3.0f*canvas.getWidth()/4.0f, 0.0f, 3.0f*canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.0f, canvas.getWidth()-1.0f, canvas.getHeight()/2.0f, p);
			p.setColor(Color.WHITE);
			int crosshairs_radius = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawLine(canvas.getWidth()/2.0f, canvas.getHeight()/2.0f - crosshairs_radius, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f + crosshairs_radius, p);
			canvas.drawLine(canvas.getWidth()/2.0f - crosshairs_radius, canvas.getHeight()/2.0f, canvas.getWidth()/2.0f + crosshairs_radius, canvas.getHeight()/2.0f, p);
		}
		if( preview.isVideo() || sharedPreferences.getString(PreferenceKeys.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg").equals("preference_preview_size_wysiwyg") ) {
			String preference_crop_guide = sharedPreferences.getString(PreferenceKeys.getShowCropGuidePreferenceKey(), "crop_guide_none");
			if( camera_controller != null && preview.getTargetRatio() > 0.0 && !preference_crop_guide.equals("crop_guide_none") ) {
				p.setStyle(Paint.Style.STROKE);
				p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
				double crop_ratio = -1.0;
				if( preference_crop_guide.equals("crop_guide_1.33") ) {
					crop_ratio = 1.33333333;
				}
				else if( preference_crop_guide.equals("crop_guide_1.5") ) {
					crop_ratio = 1.5;
				}
				else if( preference_crop_guide.equals("crop_guide_1.78") ) {
					crop_ratio = 1.77777778;
				}
				else if( preference_crop_guide.equals("crop_guide_1.85") ) {
					crop_ratio = 1.85;
				}
				else if( preference_crop_guide.equals("crop_guide_2.33") ) {
					crop_ratio = 2.33333333;
				}
				else if( preference_crop_guide.equals("crop_guide_2.35") ) {
					crop_ratio = 2.35006120; // actually 1920:817
				}
				else if( preference_crop_guide.equals("crop_guide_2.4") ) {
					crop_ratio = 2.4;
				}
				if( crop_ratio > 0.0 && Math.abs(preview.getTargetRatio() - crop_ratio) > 1.0e-5 ) {
		    		/*if( MyDebug.LOG ) {
		    			Log.d(TAG, "crop_ratio: " + crop_ratio);
		    			Log.d(TAG, "preview_targetRatio: " + preview_targetRatio);
		    			Log.d(TAG, "canvas width: " + canvas.getWidth());
		    			Log.d(TAG, "canvas height: " + canvas.getHeight());
		    		}*/
					int left = 1, top = 1, right = canvas.getWidth()-1, bottom = canvas.getHeight()-1;
					if( crop_ratio > preview.getTargetRatio() ) {
						// crop ratio is wider, so we have to crop top/bottom
						double new_hheight = ((double)canvas.getWidth()) / (2.0f*crop_ratio);
						top = (int)(canvas.getHeight()/2 - new_hheight);
						bottom = (int)(canvas.getHeight()/2 + new_hheight);
					}
					else {
						// crop ratio is taller, so we have to crop left/right
						double new_hwidth = (((double)canvas.getHeight()) * crop_ratio) / 2.0f;
						left = (int)(canvas.getWidth()/2 - new_hwidth);
						right = (int)(canvas.getWidth()/2 + new_hwidth);
					}
					canvas.drawRect(left, top, right, bottom, p);
				}
			}
		}

		// note, no need to check preferences here, as we do that when setting thumbnail_anim
		if( camera_controller != null && this.thumbnail_anim && last_thumbnail != null ) {
			long time = System.currentTimeMillis() - this.thumbnail_anim_start_ms;
			final long duration = 500;
			if( time > duration ) {
				this.thumbnail_anim = false;
			}
			else {
				thumbnail_anim_src_rect.left = 0;
				thumbnail_anim_src_rect.top = 0;
				thumbnail_anim_src_rect.right = last_thumbnail.getWidth();
				thumbnail_anim_src_rect.bottom = last_thumbnail.getHeight();
			    View galleryButton = (View) main_activity.findViewById(R.id.gallery);
				float alpha = ((float)time)/(float)duration;

				int st_x = canvas.getWidth()/2;
				int st_y = canvas.getHeight()/2;
				int nd_x = galleryButton.getLeft() + galleryButton.getWidth()/2;
				int nd_y = galleryButton.getTop() + galleryButton.getHeight()/2;
				int thumbnail_x = (int)( (1.0f-alpha)*st_x + alpha*nd_x );
				int thumbnail_y = (int)( (1.0f-alpha)*st_y + alpha*nd_y );

				float st_w = canvas.getWidth();
				float st_h = canvas.getHeight();
				float nd_w = galleryButton.getWidth();
				float nd_h = galleryButton.getHeight();
				//int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
				//int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
				float correction_w = st_w/nd_w - 1.0f;
				float correction_h = st_h/nd_h - 1.0f;
				int thumbnail_w = (int)(st_w/(1.0f+alpha*correction_w));
				int thumbnail_h = (int)(st_h/(1.0f+alpha*correction_h));
				thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w/2;
				thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h/2;
				thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w/2;
				thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h/2;
				//canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
				thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect, thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
				//thumbnail_anim_matrix.reset();
				if( ui_rotation == 90 || ui_rotation == 270 ) {
					float ratio = ((float)last_thumbnail.getWidth())/(float)last_thumbnail.getHeight();
					thumbnail_anim_matrix.preScale(ratio, 1.0f/ratio, last_thumbnail.getWidth()/2, last_thumbnail.getHeight()/2);
				}
				thumbnail_anim_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2, last_thumbnail.getHeight()/2);
				canvas.drawBitmap(last_thumbnail, thumbnail_anim_matrix, p);
			}
		}
		
		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth()/2, canvas.getHeight()/2);

		int text_y = (int) (20 * scale + 0.5f); // convert dps to pixels
		// fine tuning to adjust placement of text with respect to the GUI, depending on orientation
		int text_base_y = 0;
		if( ui_rotation == ( ui_placement_right ? 0 : 180 ) ) {
			text_base_y = canvas.getHeight() - (int)(0.5*text_y);
		}
		else if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
			text_base_y = canvas.getHeight() - (int)(2.5*text_y);
		}
		else if( ui_rotation == 90 || ui_rotation == 270 ) {
			//text_base_y = canvas.getHeight() + (int)(0.5*text_y);
			ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
			// align with "top" of the take_photo button, but remember to take the rotation into account!
			view.getLocationOnScreen(gui_location);
			int view_left = gui_location[0];
			preview.getView().getLocationOnScreen(gui_location);
			int this_left = gui_location[0];
			int diff_x = view_left - ( this_left + canvas.getWidth()/2 );
    		/*if( MyDebug.LOG ) {
    			Log.d(TAG, "view left: " + view_left);
    			Log.d(TAG, "this left: " + this_left);
    			Log.d(TAG, "canvas is " + canvas.getWidth() + " x " + canvas.getHeight());
    		}*/
			int max_x = canvas.getWidth();
			if( ui_rotation == 90 ) {
				// so we don't interfere with the top bar info (time, etc)
				max_x -= (int)(1.5*text_y);
			}
			if( canvas.getWidth()/2 + diff_x > max_x ) {
				// in case goes off the size of the canvas, for "black bar" cases (when preview aspect ratio != screen aspect ratio)
				diff_x = max_x - canvas.getWidth()/2;
			}
			text_base_y = canvas.getHeight()/2 + diff_x - (int)(0.5*text_y);
		}
		final int top_y = (int) (5 * scale + 0.5f); // convert dps to pixels

		final String ybounds_text = getContext().getResources().getString(R.string.zoom) + getContext().getResources().getString(R.string.angle) + getContext().getResources().getString(R.string.direction);
		final double close_angle = 1.0f;
		if( camera_controller != null && !preview.isPreviewPaused() ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/
			boolean draw_angle = has_level_angle && sharedPreferences.getBoolean(PreferenceKeys.getShowAnglePreferenceKey(), true);
			boolean draw_geo_direction = has_geo_direction && sharedPreferences.getBoolean(PreferenceKeys.getShowGeoDirectionPreferenceKey(), true);
			if( draw_angle ) {
				int color = Color.WHITE;
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				int pixels_offset_x = 0;
				if( draw_geo_direction ) {
					pixels_offset_x = - (int) (82 * scale + 0.5f); // convert dps to pixels
					p.setTextAlign(Paint.Align.LEFT);
				}
				else {
					p.setTextAlign(Paint.Align.CENTER);
				}
				if( Math.abs(level_angle) <= close_angle ) {
					color = Color.rgb(20, 231, 21); // Green A400
					p.setUnderlineText(true);
				}
				String number_string = decimalFormat.format(level_angle);
				number_string = number_string.replaceAll( "^-(?=0(.0*)?$)", ""); // avoids displaying "-0.0", see http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
				String string = getContext().getResources().getString(R.string.angle) + ": " + number_string + (char)0x00B0;
				drawTextWithBackground(canvas, p, string, color, Color.BLACK, canvas.getWidth() / 2 + pixels_offset_x, text_base_y, false, ybounds_text);
				p.setUnderlineText(false);
			}
			if( draw_geo_direction ) {
				int color = Color.WHITE;
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				if( draw_angle ) {
					p.setTextAlign(Paint.Align.LEFT);
				}
				else {
					p.setTextAlign(Paint.Align.CENTER);
				}
				float geo_angle = (float)Math.toDegrees(geo_direction);
				if( geo_angle < 0.0f ) {
					geo_angle += 360.0f;
				}
				String string = " " + getContext().getResources().getString(R.string.direction) + ": " + Math.round(geo_angle) + (char)0x00B0;
				drawTextWithBackground(canvas, p, string, color, Color.BLACK, canvas.getWidth() / 2, text_base_y, false, ybounds_text);
			}
			if( preview.isOnTimer() ) {
				long remaining_time = (preview.getTimerEndTime() - System.currentTimeMillis() + 999)/1000;
				if( MyDebug.LOG )
					Log.d(TAG, "remaining_time: " + remaining_time);
				if( remaining_time >= 0 ) {
					p.setTextSize(42 * scale + 0.5f); // convert dps to pixels
					p.setTextAlign(Paint.Align.CENTER);
					drawTextWithBackground(canvas, p, "" + remaining_time, Color.rgb(229, 28, 35), Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() / 2); // Red 500
				}
			}
			else if( preview.isVideoRecording() ) {
            	long video_time = preview.getVideoTime();
            	//int ms = (int)(video_time % 1000);
            	video_time /= 1000;
            	int secs = (int)(video_time % 60);
            	video_time /= 60;
            	int mins = (int)(video_time % 60);
            	video_time /= 60;
            	long hours = video_time;
            	//String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs) + ":" + String.format("%03d", ms);
            	String time_s = hours + ":" + String.format("%02d", mins) + ":" + String.format("%02d", secs);
            	/*if( MyDebug.LOG )
					Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
    			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
    			p.setTextAlign(Paint.Align.CENTER);
				int pixels_offset_y = 3*text_y; // avoid overwriting the zoom or ISO label
				int color = Color.rgb(229, 28, 35); // Red 500
            	if( main_activity.isScreenLocked() ) {
            		// writing in reverse order, bottom to top
            		drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_2), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
            		pixels_offset_y += text_y;
            		drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_1), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
            		pixels_offset_y += text_y;
            	}
            	drawTextWithBackground(canvas, p, time_s, color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
			}
		}
		else if( camera_controller == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_1), canvas.getWidth() / 2, canvas.getHeight() / 2, p);
			canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_2), canvas.getWidth() / 2, canvas.getHeight() / 2 + pixels_offset, p);
			canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_3), canvas.getWidth() / 2, canvas.getHeight() / 2 + 2*pixels_offset, p);
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		if( camera_controller != null && sharedPreferences.getBoolean(PreferenceKeys.getShowISOPreferenceKey(), true) ) {
			int pixels_offset_y = 2*text_y;
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			String string = "";
			if( camera_controller.captureResultHasIso() ) {
				int iso = camera_controller.captureResultIso();
				if( string.length() > 0 )
					string += " ";
				string += preview.getISOString(iso);
			}
			if( camera_controller.captureResultHasExposureTime() ) {
				long exposure_time = camera_controller.captureResultExposureTime();
				if( string.length() > 0 )
					string += " ";
				string += preview.getExposureTimeString(exposure_time);
			}
			if( camera_controller.captureResultHasFrameDuration() ) {
				long frame_duration = camera_controller.captureResultFrameDuration();
				if( string.length() > 0 )
					string += " ";
				string += preview.getFrameDurationString(frame_duration);
			}
			if( string.length() > 0 ) {
				drawTextWithBackground(canvas, p, string, Color.rgb(255, 235, 59), Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y, false, ybounds_text); // Yellow 500
			}
		}
		if( preview.supportsZoom() && camera_controller != null && sharedPreferences.getBoolean(PreferenceKeys.getShowZoomPreferenceKey(), true) ) {
			float zoom_ratio = preview.getZoomRatio();
			// only show when actually zoomed in
			if( zoom_ratio > 1.0f + 1.0e-5f ) {
				// Convert the dps to pixels, based on density scale
				int pixels_offset_y = text_y;
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				p.setTextAlign(Paint.Align.CENTER);
				drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.zoom) + ": " + zoom_ratio +"x", Color.WHITE, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y, false, ybounds_text);
			}
		}

		if( sharedPreferences.getBoolean(PreferenceKeys.getShowBatteryPreferenceKey(), true) ) {
			if( !this.has_battery_frac || System.currentTimeMillis() > this.last_battery_time + 60000 ) {
				// only check periodically - unclear if checking is costly in any way
				Intent batteryStatus = main_activity.registerReceiver(null, battery_ifilter);
				int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				has_battery_frac = true;
				battery_frac = battery_level/(float)battery_scale;
				last_battery_time = System.currentTimeMillis();
				if( MyDebug.LOG )
					Log.d(TAG, "Battery status is " + battery_level + " / " + battery_scale + " : " + battery_frac);
			}
			//battery_frac = 0.2999f; // test
			int battery_x = (int) (5 * scale + 0.5f); // convert dps to pixels
			int battery_y = top_y;
			int battery_height = (int) (20 * scale + 0.5f); // convert dps to pixels
            int battery_width = (int) (400 * scale + 0.5f);
//			int battery_width = 25*battery_height;
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				battery_x += diff/2;
				battery_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				battery_y = canvas.getHeight() - battery_y - battery_height;
			}
			if( ui_rotation == 180 ) {
				battery_x = canvas.getWidth() - battery_x - battery_width;
			}
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			canvas.drawRect(battery_x, battery_y, battery_x+battery_width, battery_y+battery_height, p);
			p.setColor(battery_frac >= 0.3f ? Color.rgb(37, 155, 36) : Color.rgb(229, 28, 35)); // Green 500 or Red 500
			p.setStyle(Paint.Style.FILL);
//			canvas.drawRect(battery_x+1, battery_y+1+(1.0f-battery_frac)*(battery_height-2), battery_x+battery_width-1, battery_y+battery_height-1, p);
            canvas.drawRect(battery_x+1, battery_y+1, +(0.0f + battery_frac)*(battery_width-2), battery_y+battery_height-1, p);
		}
		
		boolean store_location = sharedPreferences.getBoolean(PreferenceKeys.getLocationPreferenceKey(), false);
		final int location_size = (int) (20 * scale + 0.5f); // convert dps to pixels
		if( store_location ) {
			int location_x = (int) (20 * scale + 0.5f); // convert dps to pixels
			int location_y = top_y;
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				location_x += diff/2;
				location_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				location_y = canvas.getHeight() - location_y - location_size;
			}
			if( ui_rotation == 180 ) {
				location_x = canvas.getWidth() - location_x - location_size;
			}
			location_dest.set(location_x, location_y, location_x + location_size, location_y + location_size);
			if( this.getLocation() != null ) {
				canvas.drawBitmap(location_bitmap, null, location_dest, p);
				int location_radius = location_size/10;
				int indicator_x = location_x + location_size;
				int indicator_y = location_y + location_radius/2 + 1;
				p.setStyle(Paint.Style.FILL);
				p.setColor(this.getLocation().getAccuracy() < 25.01f ? Color.rgb(37, 155, 36) : Color.rgb(255, 235, 59)); // Green 500 or Yellow 500
				canvas.drawCircle(indicator_x, indicator_y, location_radius, p);
			}
			else {
				canvas.drawBitmap(location_off_bitmap, null, location_dest, p);
			}
		}
		
		if( sharedPreferences.getBoolean(PreferenceKeys.getShowTimePreferenceKey(), true) ) {
//            int battery_x = (int) (5 * scale + 0.5f); // convert dps to pixels
//            int battery_y = top_y;
//            int battery_height = (int) (20 * scale + 0.5f); // convert dps to pixels
//            int battery_width = 20*battery_height;

			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.LEFT);
//			int location_x = (int) (50 * scale + 0.5f); // convert dps to pixels
//			int location_y = top_y;
            int location_x = (int) (250 * scale + 0.5f); // convert dps to pixels
            int location_y = top_y + (int) (3 * scale + 0.5f); // convert dps to pixels
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				location_x += diff/2;
				location_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				location_y = canvas.getHeight() - location_y - location_size;
			}
			if( ui_rotation == 180 ) {
				location_x = canvas.getWidth() - location_x;
				p.setTextAlign(Paint.Align.RIGHT);
			}
	        Calendar c = Calendar.getInstance();
	        // n.b., DateFormat.getTimeInstance() ignores user preferences such as 12/24 hour or date format, but this is an Android bug.
	        // Whilst DateUtils.formatDateTime doesn't have that problem, it doesn't print out seconds! See:
	        // http://stackoverflow.com/questions/15981516/simpledateformat-gettimeinstance-ignores-24-hour-format
	        // http://daniel-codes.blogspot.co.uk/2013/06/how-to-correctly-format-datetime.html
	        // http://code.google.com/p/android/issues/detail?id=42104
	        String current_time = DateFormat.getTimeInstance().format(c.getTime());
	        //String current_time = DateUtils.formatDateTime(getContext(), c.getTimeInMillis(), DateUtils.FORMAT_SHOW_TIME);
	        drawTextWithBackground(canvas, p, current_time, Color.WHITE, Color.TRANSPARENT, location_x, location_y, true);
	    }

		if( camera_controller != null && sharedPreferences.getBoolean(PreferenceKeys.getShowFreeMemoryPreferenceKey(), true) ) {
//            int battery_x = (int) (5 * scale + 0.5f); // convert dps to pixels
//            int battery_y = top_y;
//            int battery_height = (int) (20 * scale + 0.5f); // convert dps to pixels
//            int battery_width = 20*battery_height;
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.LEFT);
			int location_x = (int) (100 * scale + 0.5f); // convert dps to pixels
			int location_y = top_y + (int) (3 * scale + 0.5f); // convert dps to pixels
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				int diff = canvas.getWidth() - canvas.getHeight();
				location_x += diff/2;
				location_y -= diff/2;
			}
			if( ui_rotation == 90 ) {
				location_y = canvas.getHeight() - location_y - location_size;
			}
			if( ui_rotation == 180 ) {
				location_x = canvas.getWidth() - location_x;
				p.setTextAlign(Paint.Align.RIGHT);
			}
			long time_now = System.currentTimeMillis();
			if( free_memory_gb < 0.0f || time_now > last_free_memory_time + 1000 ) {
				long free_mb = main_activity.freeMemory();
				if( free_mb >= 0 ) {
					free_memory_gb = free_mb/1024.0f;
					last_free_memory_time = time_now;
				}
			}
			if( free_memory_gb >= 0.0f ) {
				drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.free_memory) + ": " + decimalFormat.format(free_memory_gb) + "GB", Color.WHITE, Color.BLACK, location_x, location_y, true);
			}
		}

		canvas.restore();
		
		if( camera_controller != null && !preview.isPreviewPaused() && has_level_angle && sharedPreferences.getBoolean(PreferenceKeys.getShowAngleLinePreferenceKey(), false) ) {
			// n.b., must draw this without the standard canvas rotation
			int radius_dps = (ui_rotation == 90 || ui_rotation == 270) ? 60 : 80;
			int radius = (int) (radius_dps * scale + 0.5f); // convert dps to pixels
			double angle = - preview.getOrigLevelAngle();
			// see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
		    int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
		    switch (rotation) {
	    	case Surface.ROTATION_90:
	    	case Surface.ROTATION_270:
	    		angle += 90.0;
	    		break;
		    }
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "orig_level_angle: " + orig_level_angle);
				Log.d(TAG, "angle: " + angle);
			}*/
			int cx = canvas.getWidth()/2;
			int cy = canvas.getHeight()/2;
			
			boolean is_level = false;
			if( Math.abs(level_angle) <= close_angle ) { // n.b., use level_angle, not angle or orig_level_angle
				is_level = true;
			}
			
			if( is_level ) {
				radius = (int)(radius * 1.2);
			}

			canvas.save();
			canvas.rotate((float)angle, cx, cy);

			final int line_alpha = 96;
			p.setStyle(Paint.Style.FILL);
			float hthickness = (0.5f * scale + 0.5f); // convert dps to pixels
			p.setColor(Color.BLACK);
			p.setAlpha(64);
			// can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
			draw_rect.set(cx - radius - hthickness, cy - 2*hthickness, cx + radius + hthickness, cy + 2*hthickness);
			canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
			if( is_level ) {
				p.setColor(Color.rgb(20, 231, 21)); // Green A400
			}
			else {
				p.setColor(Color.WHITE);
			}
			p.setAlpha(line_alpha);
			draw_rect.set(cx - radius, cy - hthickness, cx + radius, cy + hthickness);
			canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

			if( is_level ) {
				// draw a second line

				p.setColor(Color.BLACK);
				p.setAlpha(64);
				draw_rect.set(cx - radius - hthickness, cy - 7*hthickness, cx + radius + hthickness, cy - 3*hthickness);
				canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);

				p.setColor(Color.rgb(20, 231, 21)); // Green A400
				p.setAlpha(line_alpha);
				draw_rect.set(cx - radius, cy - 6*hthickness, cx + radius, cy - 4*hthickness);
				canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
			}
			p.setAlpha(255);

			canvas.restore();
		}

		if( preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure() ) {
			int size = (int) (50 * scale + 0.5f); // convert dps to pixels
			if( preview.isFocusRecentSuccess() )
				p.setColor(Color.rgb(20, 231, 21)); // Green A400
			else if( preview.isFocusRecentFailure() )
				p.setColor(Color.rgb(229, 28, 35)); // Red 500
			else
				p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int pos_x = 0;
			int pos_y = 0;
			if( preview.hasFocusArea() ) {
				Pair<Integer, Integer> focus_pos = preview.getFocusPos();
				pos_x = focus_pos.first;
				pos_y = focus_pos.second;
			}
			else {
				pos_x = canvas.getWidth() / 2;
				pos_y = canvas.getHeight() / 2;
			}
			canvas.drawRect(pos_x - size, pos_y - size, pos_x + size, pos_y + size, p);
			p.setStyle(Paint.Style.FILL); // reset
		}
		if( preview.getFacesDetected() != null ) {
			p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
			p.setStyle(Paint.Style.STROKE);
			for(CameraController.Face face : preview.getFacesDetected()) {
				// Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
				if( face.score >= 50 ) {
					face_rect.set(face.rect);
					preview.getCameraToPreviewMatrix().mapRect(face_rect);
					/*int eye_radius = (int) (5 * scale + 0.5f); // convert dps to pixels
					int mouth_radius = (int) (10 * scale + 0.5f); // convert dps to pixels
					float [] top_left = {face.rect.left, face.rect.top};
					float [] bottom_right = {face.rect.right, face.rect.bottom};
					canvas.drawRect(top_left[0], top_left[1], bottom_right[0], bottom_right[1], p);*/
					canvas.drawRect(face_rect, p);
					/*if( face.leftEye != null ) {
						float [] left_point = {face.leftEye.x, face.leftEye.y};
						cameraToPreview(left_point);
						canvas.drawCircle(left_point[0], left_point[1], eye_radius, p);
					}
					if( face.rightEye != null ) {
						float [] right_point = {face.rightEye.x, face.rightEye.y};
						cameraToPreview(right_point);
						canvas.drawCircle(right_point[0], right_point[1], eye_radius, p);
					}
					if( face.mouth != null ) {
						float [] mouth_point = {face.mouth.x, face.mouth.y};
						cameraToPreview(mouth_point);
						canvas.drawCircle(mouth_point[0], mouth_point[1], mouth_radius, p);
					}*/
				}
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
    }

	private void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, false);
	}

	private void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, boolean align_top) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, align_top, null);
	}

	private void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, boolean align_top, String ybounds_text) {
		final float scale = getContext().getResources().getDisplayMetrics().density;
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(background);
		paint.setAlpha(0);
		int alt_height = 0;
		if( ybounds_text != null ) {
			paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
			alt_height = text_bounds.bottom - text_bounds.top;
		}
		paint.getTextBounds(text, 0, text.length(), text_bounds);
		if( ybounds_text != null ) {
			text_bounds.bottom = text_bounds.top + alt_height;
		}
		final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
		if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
			float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
			if( paint.getTextAlign() == Paint.Align.CENTER )
				width /= 2.0f;
			text_bounds.left -= width;
			text_bounds.right -= width;
		}
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
		text_bounds.left += location_x - padding;
		text_bounds.right += location_x + padding;
		if( align_top ) {
			int height = text_bounds.bottom - text_bounds.top + 2*padding;
			// unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
			int y_diff = - text_bounds.top + padding - 1;
			text_bounds.top = location_y - 1;
			text_bounds.bottom = text_bounds.top + height;
			location_y += y_diff;
		}
		else {
			text_bounds.top += location_y - padding;
			text_bounds.bottom += location_y + padding;
		}
		canvas.drawRect(text_bounds, paint);
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}
	
    @Override
    @SuppressWarnings("deprecation")
	public boolean onPictureTaken(byte [] data) {
        System.gc();
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken");

		boolean image_capture_intent = false;
	        Uri image_capture_intent_uri = null;
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			image_capture_intent = true;
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	image_capture_intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
    			if( MyDebug.LOG )
    				Log.d(TAG, "save to: " + image_capture_intent_uri);
	        }
        }

        boolean success = false;
        Bitmap bitmap = null;
		if( getAutoStabilisePref() && main_activity.getPreview().hasLevelAngle() )
		{
			double level_angle = main_activity.getPreview().getLevelAngle();
			//level_angle = -129;
			if( main_activity.test_have_angle )
				level_angle = main_activity.test_angle;
			while( level_angle < -90 )
				level_angle += 180;
			while( level_angle > 90 )
				level_angle -= 180;
			if( MyDebug.LOG )
				Log.d(TAG, "auto stabilising... angle: " + level_angle);
			BitmapFactory.Options options = new BitmapFactory.Options();
			//options.inMutable = true;
			if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
				// setting is ignored in Android 5 onwards
				options.inPurgeable = true;
			}
			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
			if( bitmap == null ) {
				main_activity.getPreview().showToast(null, R.string.failed_to_auto_stabilise);
	            System.gc();
			}
			else {
    			int width = bitmap.getWidth();
    			int height = bitmap.getHeight();
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "level_angle: " + level_angle);
    				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
    				Log.d(TAG, "bitmap size: " + width*height*4);
    			}
    			/*for(int y=0;y<height;y++) {
    				for(int x=0;x<width;x++) {
    					int col = bitmap.getPixel(x, y);
    					col = col & 0xffff0000; // mask out red component
    					bitmap.setPixel(x, y, col);
    				}
    			}*/
    			if( main_activity.test_low_memory ) {
    		    	level_angle = 45.0;
    			}
    		    Matrix matrix = new Matrix();
    		    double level_angle_rad_abs = Math.abs( Math.toRadians(level_angle) );
    		    int w1 = width, h1 = height;
    		    double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
    		    double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));
    		    // apply a scale so that the overall image size isn't increased
    		    float orig_size = w1*h1;
    		    float rotated_size = (float)(w0*h0);
    		    float scale = (float)Math.sqrt(orig_size/rotated_size);
    			if( main_activity.test_low_memory ) {
        			if( MyDebug.LOG )
        				Log.d(TAG, "TESTING LOW MEMORY");
    		    	scale *= 2.0f; // test 20MP on Galaxy Nexus or Nexus 7; 52MP on Nexus 6
    			}
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "w0 = " + w0 + " , h0 = " + h0);
    				Log.d(TAG, "w1 = " + w1 + " , h1 = " + h1);
    				Log.d(TAG, "scale = sqrt " + orig_size + " / " + rotated_size + " = " + scale);
    			}
    		    matrix.postScale(scale, scale);
    		    w0 *= scale;
    		    h0 *= scale;
    		    w1 *= scale;
    		    h1 *= scale;
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "after scaling: w0 = " + w0 + " , h0 = " + h0);
    				Log.d(TAG, "after scaling: w1 = " + w1 + " , h1 = " + h1);
    			}
				// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
    		    if( main_activity.getPreview().getCameraController() != null && main_activity.getPreview().getCameraController().isFrontFacing() ) {
        		    matrix.postRotate((float)-level_angle);
    		    }
    		    else {
        		    matrix.postRotate((float)level_angle);
    		    }
    		    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    		    // careful, as new_bitmap is sometimes not a copy!
    		    if( new_bitmap != bitmap ) {
    		    	bitmap.recycle();
    		    	bitmap = new_bitmap;
    		    }
	            System.gc();
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "rotated and scaled bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
    				Log.d(TAG, "rotated and scaled bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
    			}
    			double tan_theta = Math.tan(level_angle_rad_abs);
    			double sin_theta = Math.sin(level_angle_rad_abs);
    			double denom = (double)( h0/w0 + tan_theta );
    			double alt_denom = (double)( w0/h0 + tan_theta );
    			if( denom == 0.0 || denom < 1.0e-14 ) {
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "zero denominator?!");
    			}
    			else if( alt_denom == 0.0 || alt_denom < 1.0e-14 ) {
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "zero alt denominator?!");
    			}
    			else {
        			int w2 = (int)(( h0 + 2.0*h1*sin_theta*tan_theta - w0*tan_theta ) / denom);
        			int h2 = (int)(w2*h0/(double)w0);
        			int alt_h2 = (int)(( w0 + 2.0*w1*sin_theta*tan_theta - h0*tan_theta ) / alt_denom);
        			int alt_w2 = (int)(alt_h2*w0/(double)h0);
        			if( MyDebug.LOG ) {
        				//Log.d(TAG, "h0 " + h0 + " 2.0*h1*sin_theta*tan_theta " + 2.0*h1*sin_theta*tan_theta + " w0*tan_theta " + w0*tan_theta + " / h0/w0 " + h0/w0 + " tan_theta " + tan_theta);
        				Log.d(TAG, "w2 = " + w2 + " , h2 = " + h2);
        				Log.d(TAG, "alt_w2 = " + alt_w2 + " , alt_h2 = " + alt_h2);
        			}
        			if( alt_w2 < w2 ) {
            			if( MyDebug.LOG ) {
            				Log.d(TAG, "chose alt!");
            			}
        				w2 = alt_w2;
        				h2 = alt_h2;
        			}
        			if( w2 <= 0 )
        				w2 = 1;
        			else if( w2 >= bitmap.getWidth() )
        				w2 = bitmap.getWidth()-1;
        			if( h2 <= 0 )
        				h2 = 1;
        			else if( h2 >= bitmap.getHeight() )
        				h2 = bitmap.getHeight()-1;
        			int x0 = (bitmap.getWidth()-w2)/2;
        			int y0 = (bitmap.getHeight()-h2)/2;
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "x0 = " + x0 + " , y0 = " + y0);
        			}
        			new_bitmap = Bitmap.createBitmap(bitmap, x0, y0, w2, h2);
        		    if( new_bitmap != bitmap ) {
        		    	bitmap.recycle();
        		    	bitmap = new_bitmap;
        		    }
    	            System.gc();
    			}
			}
		}
		String preference_stamp = this.getStampPref();
		String preference_textstamp = this.getTextStampPref();
		boolean dategeo_stamp = preference_stamp.equals("preference_stamp_yes");
		boolean text_stamp = preference_textstamp.length() > 0;
		if( dategeo_stamp || text_stamp ) {
			if( bitmap == null ) {
    			if( MyDebug.LOG )
    				Log.d(TAG, "decode bitmap in order to stamp info");
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inMutable = true;
				if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
					// setting is ignored in Android 5 onwards
					options.inPurgeable = true;
				}
    			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
    			if( bitmap == null ) {
    				main_activity.getPreview().showToast(null, R.string.failed_to_stamp);
    	            System.gc();
    			}
			}
			if( bitmap != null ) {
    			if( MyDebug.LOG )
    				Log.d(TAG, "stamp info to bitmap");
    			int width = bitmap.getWidth();
    			int height = bitmap.getHeight();
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
    				Log.d(TAG, "bitmap size: " + width*height*4);
    			}
    			Canvas canvas = new Canvas(bitmap);
    			p.setColor(Color.WHITE);
    			int font_size = getTextStampFontSizePref();
    			// we don't use the density of the screen, because we're stamping to the image, not drawing on the screen (we don't want the font height to depend on the device's resolution
    			// instead we go by 1 pt == 1/72 inch height, and scale for an image height (or width if in portrait) of 4" (this means the font height is also independent of the photo resolution)
    			int smallest_size = (width<height) ? width : height;
    			float scale = ((float)smallest_size) / (72.0f*4.0f);
    			int font_size_pixel = (int)(font_size * scale + 0.5f); // convert pt to pixels
    			if( MyDebug.LOG ) {
    				Log.d(TAG, "scale: " + scale);
    				Log.d(TAG, "font_size: " + font_size);
    				Log.d(TAG, "font_size_pixel: " + font_size_pixel);
    			}
    			p.setTextSize(font_size_pixel);
    	        int offset_x = (int)(8 * scale + 0.5f); // convert pt to pixels
    	        int offset_y = (int)(8 * scale + 0.5f); // convert pt to pixels
    	        int diff_y = (int)((font_size+4) * scale + 0.5f); // convert pt to pixels
    	        int ypos = height - offset_y;
    	        p.setTextAlign(Align.RIGHT);
    	        if( dategeo_stamp ) {
        			if( MyDebug.LOG )
        				Log.d(TAG, "stamp date");
        			// doesn't respect user preferences such as 12/24 hour - see note about in draw() about DateFormat.getTimeInstance()
        	        String time_stamp = DateFormat.getDateTimeInstance().format(new Date());
    				drawTextWithBackground(canvas, p, time_stamp, Color.WHITE, Color.BLACK, width - offset_x, ypos);
    				ypos -= diff_y;
    				String location_string = "";
    				boolean store_location = getGeotaggingPref();
    				if( store_location && getLocation() != null ) {
    					Location location = getLocation();
    					location_string += Location.convert(location.getLatitude(), Location.FORMAT_DEGREES) + ", " + Location.convert(location.getLongitude(), Location.FORMAT_DEGREES);
    					if( location.hasAltitude() ) {
	    					location_string += ", " + decimalFormat.format(location.getAltitude()) + getContext().getResources().getString(R.string.metres_abbreviation);
    					}
    				}
			    	if( main_activity.getPreview().hasGeoDirection() && getGeodirectionPref() ) {
						float geo_angle = (float)Math.toDegrees(main_activity.getPreview().getGeoDirection());
						if( geo_angle < 0.0f ) {
							geo_angle += 360.0f;
						}
	        			if( MyDebug.LOG )
	        				Log.d(TAG, "geo_angle: " + geo_angle);
    			    	if( location_string.length() > 0 )
    			    		location_string += ", ";
						location_string += "" + Math.round(geo_angle) + (char)0x00B0;
			    	}
			    	if( location_string.length() > 0 ) {
	        			if( MyDebug.LOG )
	        				Log.d(TAG, "stamp with location_string: " + location_string);
	        			drawTextWithBackground(canvas, p, location_string, Color.WHITE, Color.BLACK, width - offset_x, ypos);
	    				ypos -= diff_y;
			    	}
    	        }
    	        if( text_stamp ) {
        			if( MyDebug.LOG )
        				Log.d(TAG, "stamp text");
        			drawTextWithBackground(canvas, p, preference_textstamp, Color.WHITE, Color.BLACK, width - offset_x, ypos);
    				ypos -= diff_y;
    	        }
			}
		}

		String exif_orientation_s = null;
		String picFileName = null;
		File picFile = null;
        try {
			OutputStream outputStream = null;
			if( image_capture_intent ) {
    			if( MyDebug.LOG )
    				Log.d(TAG, "image_capture_intent");
    			if( image_capture_intent_uri != null )
    			{
    			    // Save the bitmap to the specified URI (use a try/catch block)
        			if( MyDebug.LOG )
        				Log.d(TAG, "save to: " + image_capture_intent_uri);
    			    outputStream = main_activity.getContentResolver().openOutputStream(image_capture_intent_uri);
    			}
    			else
    			{
    			    // If the intent doesn't contain an URI, send the bitmap as a parcel
    			    // (it is a good idea to reduce its size to ~50k pixels before)
        			if( MyDebug.LOG )
        				Log.d(TAG, "sent to intent via parcel");
    				if( bitmap == null ) {
	        			if( MyDebug.LOG )
	        				Log.d(TAG, "create bitmap");
	    				BitmapFactory.Options options = new BitmapFactory.Options();
	    				//options.inMutable = true;
	    				if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
	    					// setting is ignored in Android 5 onwards
	    					options.inPurgeable = true;
	    				}
	        			bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
    				}
    				if( bitmap != null ) {
	        			int width = bitmap.getWidth();
	        			int height = bitmap.getHeight();
	        			if( MyDebug.LOG ) {
	        				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
	        				Log.d(TAG, "bitmap size: " + width*height*4);
	        			}
	        			final int small_size_c = 128;
	        			if( width > small_size_c ) {
	        				float scale = ((float)small_size_c)/(float)width;
		        			if( MyDebug.LOG )
		        				Log.d(TAG, "scale to " + scale);
		        		    Matrix matrix = new Matrix();
		        		    matrix.postScale(scale, scale);
		        		    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
		        		    // careful, as new_bitmap is sometimes not a copy!
		        		    if( new_bitmap != bitmap ) {
		        		    	bitmap.recycle();
		        		    	bitmap = new_bitmap;
		        		    }
		        		}
    				}
        			if( MyDebug.LOG ) {
        				Log.d(TAG, "returned bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
        				Log.d(TAG, "returned bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
        			}
        			main_activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
        			main_activity.finish();
    			}
			}
			else {
    			picFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
    	        if( picFile == null ) {
    	            Log.e(TAG, "Couldn't create media image file; check storage permissions?");
    	            main_activity.getPreview().showToast(null, R.string.failed_to_save_image);
    	        }
    	        else {
    	            picFileName = picFile.getAbsolutePath();
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "save to: " + picFileName);
    	            outputStream = new FileOutputStream(picFile);
    	        }
			}
			
			if( outputStream != null ) {
	            if( bitmap != null ) {
	    			int image_quality = getImageQualityPref();
    	            bitmap.compress(Bitmap.CompressFormat.JPEG, image_quality, outputStream);
	            }
	            else {
	            	outputStream.write(data);
	            }
	            outputStream.close();
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "onPictureTaken saved photo");

				success = true;
	            if( picFile != null ) {
	            	if( bitmap != null ) {
	            		// need to update EXIF data!
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "write temp file to record EXIF data");
	            		File tempFile = File.createTempFile("theVUE2_exif", "");
	    	            OutputStream tempOutputStream = new FileOutputStream(tempFile);
    	            	tempOutputStream.write(data);
    	            	tempOutputStream.close();
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "read back EXIF data");
    	            	ExifInterface exif = new ExifInterface(tempFile.getAbsolutePath());
    	            	String exif_aperture = exif.getAttribute(ExifInterface.TAG_APERTURE);
    	            	String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
    	            	String exif_exposure_time = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
    	            	String exif_flash = exif.getAttribute(ExifInterface.TAG_FLASH);
    	            	String exif_focal_length = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
    	            	String exif_gps_altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
    	            	String exif_gps_altitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
    	            	String exif_gps_datestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
    	            	String exif_gps_latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
    	            	String exif_gps_latitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
    	            	String exif_gps_longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
    	            	String exif_gps_longitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
    	            	String exif_gps_processing_method = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD);
    	            	String exif_gps_timestamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
    	            	// leave width/height, as this will have changed!
    	            	String exif_iso = exif.getAttribute(ExifInterface.TAG_ISO);
    	            	String exif_make = exif.getAttribute(ExifInterface.TAG_MAKE);
    	            	String exif_model = exif.getAttribute(ExifInterface.TAG_MODEL);
    	            	String exif_orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
    	            	exif_orientation_s = exif_orientation; // store for later use (for the thumbnail, to save rereading it)
    	            	String exif_white_balance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);

    					if( !tempFile.delete() ) {
    						if( MyDebug.LOG )
    							Log.e(TAG, "failed to delete temp " + tempFile.getAbsolutePath());
    					}
    	            	if( MyDebug.LOG )
        	    			Log.d(TAG, "now write new EXIF data");
    	            	ExifInterface exif_new = new ExifInterface(picFile.getAbsolutePath());
    	            	if( exif_aperture != null )
    	            		exif_new.setAttribute(ExifInterface.TAG_APERTURE, exif_aperture);
    	            	if( exif_datetime != null )
    	            		exif_new.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
    	            	if( exif_exposure_time != null )
    	            		exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exif_exposure_time);
    	            	if( exif_flash != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_FLASH, exif_flash);
        	            if( exif_focal_length != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exif_focal_length);
        	            if( exif_gps_altitude != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, exif_gps_altitude);
        	            if( exif_gps_altitude_ref != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, exif_gps_altitude_ref);
        	            if( exif_gps_datestamp != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, exif_gps_datestamp);
        	            if( exif_gps_latitude != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, exif_gps_latitude);
        	            if( exif_gps_latitude_ref != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exif_gps_latitude_ref);
        	            if( exif_gps_longitude != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, exif_gps_longitude);
        	            if( exif_gps_longitude_ref != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exif_gps_longitude_ref);
        	            if( exif_gps_processing_method != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, exif_gps_processing_method);
        	            if( exif_gps_timestamp != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, exif_gps_timestamp);
    	            	// leave width/height, as this will have changed!
        	            if( exif_iso != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_ISO, exif_iso);
        	            if( exif_make != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_MAKE, exif_make);
        	            if( exif_model != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_MODEL, exif_model);
        	            if( exif_orientation != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_ORIENTATION, exif_orientation);
        	            if( exif_white_balance != null )
        	            	exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exif_white_balance);
        	            setGPSDirectionExif(exif_new);
        	            setDateTimeExif(exif_new);
        	            if( needGPSTimestampHack() ) {
        	            	fixGPSTimestamp(exif_new);
        	            }
    	            	exif_new.saveAttributes();
        	    		if( MyDebug.LOG )
        	    			Log.d(TAG, "now saved EXIF data");
	            	}
	            	else if( main_activity.getPreview().hasGeoDirection() && getGeodirectionPref() ) {
    	            	if( MyDebug.LOG )
        	    			Log.d(TAG, "add GPS direction exif info");
    	            	long time_s = System.currentTimeMillis();
    	            	ExifInterface exif = new ExifInterface(picFile.getAbsolutePath());
        	            setGPSDirectionExif(exif);
        	            setDateTimeExif(exif);
        	            if( needGPSTimestampHack() ) {
        	            	fixGPSTimestamp(exif);
        	            }
    	            	exif.saveAttributes();
        	    		if( MyDebug.LOG ) {
        	    			Log.d(TAG, "done adding GPS direction exif info, time taken: " + (System.currentTimeMillis() - time_s));
        	    		}
	            	}
	            	else if( needGPSTimestampHack() ) {
    	            	if( MyDebug.LOG )
        	    			Log.d(TAG, "remove GPS timestamp hack");
    	            	long time_s = System.currentTimeMillis();
    	            	ExifInterface exif = new ExifInterface(picFile.getAbsolutePath());
    	            	fixGPSTimestamp(exif);
    	            	exif.saveAttributes();
        	    		if( MyDebug.LOG ) {
        	    			Log.d(TAG, "done removing GPS timestamp exif info, time taken: " + (System.currentTimeMillis() - time_s));
        	    		}
	            	}

	            	// shouldn't currently have a picFile if image_capture_intent, but put this here in case we ever do want to try reading intent's file (if it exists)
    	            if( !image_capture_intent ) {
    	            	broadcastFile(picFile, true, false);
    	            	main_activity.test_last_saved_image = picFileName;
    	            }
	            }
	            if( image_capture_intent ) {
    	    		if( MyDebug.LOG )
    	    			Log.d(TAG, "finish activity due to being called from intent");
	            	main_activity.setResult(Activity.RESULT_OK);
	            	main_activity.finish();
	            }
	        }
		}
        catch(FileNotFoundException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "File not found: " + e.getMessage());
            e.getStackTrace();
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        catch(IOException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "I/O error writing file: " + e.getMessage());
            e.getStackTrace();
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }

		last_image_name = picFileName;

		// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
        if( success && picFile != null && main_activity.getPreview().getCameraController() != null ) {
        	// update thumbnail - this should be done after restarting preview, so that the preview is started asap
        	long time_s = System.currentTimeMillis();
        	CameraController.Size size = main_activity.getPreview().getCameraController().getPictureSize();
    		int ratio = (int) Math.ceil((double) size.width / main_activity.getPreview().getView().getWidth());
    		int sample_size = Integer.highestOneBit(ratio) * 4; // * 4 to increase performance, without noticeable loss in visual quality
			if( !getThumbnailAnimationPref() ) {
				// can use lower resolution if we don't have the thumbnail animation
				sample_size *= 4;
			}
    		if( MyDebug.LOG ) {
    			Log.d(TAG, "    picture width: " + size.width);
    			Log.d(TAG, "    preview width: " + main_activity.getPreview().getView().getWidth());
    			Log.d(TAG, "    ratio        : " + ratio);
    			Log.d(TAG, "    sample_size  : " + sample_size);
    		}
    		Bitmap thumbnail = null;
			if( bitmap == null ) {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inMutable = false;
				if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
					// setting is ignored in Android 5 onwards
					options.inPurgeable = true;
				}
				options.inSampleSize = sample_size;
    			thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length, options);
			}
			else {
    			int width = bitmap.getWidth();
    			int height = bitmap.getHeight();
    		    Matrix matrix = new Matrix();
    		    float scale = 1.0f / (float)sample_size;
    		    matrix.postScale(scale, scale);
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "    scale: " + scale);
    		    thumbnail = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
			}
			if( thumbnail == null ) {
				// received crashes on Google Play suggesting that thumbnail could not be created
	    		if( MyDebug.LOG )
	    			Log.e(TAG, "failed to create thumbnail bitmap");
			}
			else {
				int thumbnail_rotation = 0;
				// now get the rotation from the Exif data
				try {
					if( exif_orientation_s == null ) {
						// haven't already read the exif orientation
	    	    		if( MyDebug.LOG )
	    	    			Log.d(TAG, "    read exif orientation");
	                	ExifInterface exif = new ExifInterface(picFile.getAbsolutePath());
		            	exif_orientation_s = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
					}
		    		if( MyDebug.LOG )
		    			Log.d(TAG, "    exif orientation string: " + exif_orientation_s);
					int exif_orientation = 0;
					// from http://jpegclub.org/exif_orientation.html
					if( exif_orientation_s.equals("0") || exif_orientation_s.equals("1") ) {
						// leave at 0
					}
					else if( exif_orientation_s.equals("3") ) {
						exif_orientation = 180;
					}
					else if( exif_orientation_s.equals("6") ) {
						exif_orientation = 90;
					}
					else if( exif_orientation_s.equals("8") ) {
						exif_orientation = 270;
					}
					else {
						// just leave at 0
	    	    		if( MyDebug.LOG )
	    	    			Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
					}
		    		if( MyDebug.LOG )
		    			Log.d(TAG, "    exif orientation: " + exif_orientation);
					thumbnail_rotation = (thumbnail_rotation + exif_orientation) % 360;
				}
				catch(IOException exception) {
					if( MyDebug.LOG )
						Log.e(TAG, "exif orientation ioexception");
					exception.printStackTrace();
				}
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "    thumbnail orientation: " + thumbnail_rotation);

				if( thumbnail_rotation != 0 ) {
					Matrix m = new Matrix();
					m.setRotate(thumbnail_rotation, thumbnail.getWidth() * 0.5f, thumbnail.getHeight() * 0.5f);
					Bitmap rotated_thumbnail = Bitmap.createBitmap(thumbnail, 0, 0,thumbnail.getWidth(), thumbnail.getHeight(), m, true);
					if( rotated_thumbnail != thumbnail ) {
						thumbnail.recycle();
						thumbnail = rotated_thumbnail;
					}
				}

		    	updateThumbnail(thumbnail);
	    		if( MyDebug.LOG )
	    			Log.d(TAG, "    time to create thumbnail: " + (System.currentTimeMillis() - time_s));
			}
        }

        if( bitmap != null ) {
		    bitmap.recycle();
		    bitmap = null;
        }

        System.gc();
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken complete");
		
		return success;
	}

	private void setGPSDirectionExif(ExifInterface exif) {
    	if( main_activity.getPreview().hasGeoDirection() && getGeodirectionPref() ) {
			float geo_angle = (float)Math.toDegrees(main_activity.getPreview().getGeoDirection());
			if( geo_angle < 0.0f ) {
				geo_angle += 360.0f;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "save geo_angle: " + geo_angle);
			// see http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/GPS.html
			String GPSImgDirection_string = Math.round(geo_angle*100) + "/100";
			if( MyDebug.LOG )
				Log.d(TAG, "GPSImgDirection_string: " + GPSImgDirection_string);
		   	exif.setAttribute(TAG_GPS_IMG_DIRECTION, GPSImgDirection_string);
		   	exif.setAttribute(TAG_GPS_IMG_DIRECTION_REF, "M");
    	}
	}

	private void setDateTimeExif(ExifInterface exif) {
    	String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
    	if( exif_datetime != null ) {
        	if( MyDebug.LOG )
    			Log.d(TAG, "write datetime tags: " + exif_datetime);
        	exif.setAttribute("DateTimeOriginal", exif_datetime);
        	exif.setAttribute("DateTimeDigitized", exif_datetime);
    	}
	}
	
	private void fixGPSTimestamp(ExifInterface exif) {
		if( MyDebug.LOG )
			Log.d(TAG, "fixGPSTimestamp");
		// hack: problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP (GPSDateStamp) set (tends to be around 2038 - possibly a driver bug of casting long to int?)
		// whilst we don't yet correct for that bug, the more immediate problem is that it also messes up the DATE_TAKEN field in the media store, which messes up Gallery apps
		// so for now, we correct it based on the DATE_ADDED value.
    	// see http://stackoverflow.com/questions/4879435/android-put-gpstimestamp-into-jpg-exif-tags
    	exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, Long.toString(System.currentTimeMillis()));
	}
	
	private boolean needGPSTimestampHack() {
		if( main_activity.getPreview().usingCamera2API() ) {
    		boolean store_location = getGeotaggingPref();
    		return store_location;
		}
		return false;
	}
	
	String getLastImageName() {
		return last_image_name;
	}
	
	void clearLastImageName() {
		last_image_name = null;
	}
}
