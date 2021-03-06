package com.theeyes.theVUE2;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import com.theeyes.theVUE2.Preview.ApplicationInterface;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StorageUtils {
	private static final String TAG = "StorageUtils";
	Context context = null;
    private Uri last_media_scanned = null;

	// for testing:
	public boolean failed_to_scan = false;
	
	StorageUtils(Context context) {
		this.context = context;
	}
	
	Uri getLastMediaScanned() {
		return last_media_scanned;
	}
	void clearLastMediaScanned() {
		last_media_scanned = null;
	}

    void broadcastFile(final File file, final boolean is_new_picture, final boolean is_new_video) {
		if( MyDebug.LOG )
			Log.d(TAG, "broadcastFile");
    	// note that the new method means that the new folder shows up as a file when connected to a PC via MTP (at least tested on Windows 8)
    	if( file.isDirectory() ) {
    		//this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
        	// ACTION_MEDIA_MOUNTED no longer allowed on Android 4.4! Gives: SecurityException: Permission Denial: not allowed to send broadcast android.intent.action.MEDIA_MOUNTED
    		// note that we don't actually need to broadcast anything, the folder and contents appear straight away (both in Gallery on device, and on a PC when connecting via MTP)
    		// also note that we definitely don't want to broadcast ACTION_MEDIA_SCANNER_SCAN_FILE or use scanFile() for folders, as this means the folder shows up as a file on a PC via MTP (and isn't fixed by rebooting!)
    	}
    	else {
        	// both of these work fine, but using MediaScannerConnection.scanFile() seems to be preferred over sending an intent
    		//this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
 			failed_to_scan = true; // set to true until scanned okay
 			if( MyDebug.LOG )
 				Log.d(TAG, "failed_to_scan set to true");
        	MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null,
        			new MediaScannerConnection.OnScanCompletedListener() {
					public void onScanCompleted(String path, Uri uri) {
    		 			if( MyDebug.LOG ) {
    		 				Log.d("ExternalStorage", "Scanned " + path + ":");
    		 				Log.d("ExternalStorage", "-> uri=" + uri);
    		 			}
    		 			last_media_scanned = uri;
    		        	if( is_new_picture ) {
    		        		// note, we reference the string directly rather than via Camera.ACTION_NEW_PICTURE, as the latter class is now deprecated - but we still need to broadcase the string for other apps
    		        		context.sendBroadcast(new Intent( "android.hardware.action.NEW_PICTURE" , uri));
    		        		// for compatibility with some apps - apparently this is what used to be broadcast on Android?
    		        		context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));

	    		 			if( MyDebug.LOG ) // this code only used for debugging/logging
	    		 			{
    		        	        String[] CONTENT_PROJECTION = { Images.Media.DATA, Images.Media.DISPLAY_NAME, Images.Media.MIME_TYPE, Images.Media.SIZE, Images.Media.DATE_TAKEN, Images.Media.DATE_ADDED }; 
    		        	        Cursor c = context.getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null); 
    		        	        if( c == null ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [1]: " + uri); 
    		        	        }
    		        	        else if( !c.moveToFirst() ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [2]: " + uri); 
    		        	        }
    		        	        else {
    			        	        String file_path = c.getString(c.getColumnIndex(Images.Media.DATA)); 
    			        	        String file_name = c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME)); 
    			        	        String mime_type = c.getString(c.getColumnIndex(Images.Media.MIME_TYPE)); 
    			        	        long date_taken = c.getLong(c.getColumnIndex(Images.Media.DATE_TAKEN)); 
    			        	        long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED)); 
		    		 				Log.d(TAG, "file_path: " + file_path); 
		    		 				Log.d(TAG, "file_name: " + file_name); 
		    		 				Log.d(TAG, "mime_type: " + mime_type); 
		    		 				Log.d(TAG, "date_taken: " + date_taken); 
		    		 				Log.d(TAG, "date_added: " + date_added); 
    			        	        c.close(); 
    		        	        }
    		        		}
	    		 			/*{
	    		 				// hack: problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP (GPSDateStamp) set (tends to be around 2038 - possibly a driver bug of casting long to int?)
	    		 				// whilst we don't yet correct for that bug, the more immediate problem is that it also messes up the DATE_TAKEN field in the media store, which messes up Gallery apps
	    		 				// so for now, we correct it based on the DATE_ADDED value.
    		        	        String[] CONTENT_PROJECTION = { Images.Media.DATE_ADDED }; 
    		        	        Cursor c = getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null); 
    		        	        if( c == null ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [1]: " + uri); 
    		        	        }
    		        	        else if( !c.moveToFirst() ) { 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "Couldn't resolve given uri [2]: " + uri); 
    		        	        }
    		        	        else {
    			        	        long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED)); 
    		    		 			if( MyDebug.LOG )
    		    		 				Log.e(TAG, "replace date_taken with date_added: " + date_added); 
									ContentValues values = new ContentValues(); 
									values.put(Images.Media.DATE_TAKEN, date_added*1000); 
									getContentResolver().update(uri, values, null, null);
    			        	        c.close(); 
    		        	        }
	    		 			}*/
    		        	}
    		        	else if( is_new_video ) {
    		        		context.sendBroadcast(new Intent("android.hardware.action.NEW_VIDEO", uri));
    		        	}
    		 			failed_to_scan = false;
    		 		}
    			}
    		);
	        /*ContentValues values = new ContentValues(); 
	        values.put(ImageColumns.TITLE, file.getName().substring(0, file.getName().lastIndexOf(".")));
	        values.put(ImageColumns.DISPLAY_NAME, file.getName());
	        values.put(ImageColumns.DATE_TAKEN, System.currentTimeMillis()); 
	        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
	        // TODO: orientation
	        values.put(ImageColumns.DATA, file.getAbsolutePath());
	        Location location = preview.getLocation();
	        if( location != null ) {
    	        values.put(ImageColumns.LATITUDE, location.getLatitude()); 
    	        values.put(ImageColumns.LONGITUDE, location.getLongitude()); 
	        }
	        try {
	    		this.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values); 
	        }
	        catch (Throwable th) { 
    	        // This can happen when the external volume is already mounted, but 
    	        // MediaScanner has not notify MediaProvider to add that volume. 
    	        // The picture is still safe and MediaScanner will find it and 
    	        // insert it into MediaProvider. The only problem is that the user 
    	        // cannot click the thumbnail to review the picture. 
    	        Log.e(TAG, "Failed to write MediaStore" + th); 
    	    }*/
    	}
	}

    String getSaveLocation() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String folder_name = sharedPreferences.getString(PreferenceKeys.getSaveLocationPreferenceKey(), "theVUE2");
		return folder_name;
    }
    
    public static File getBaseFolder() {
    	return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    public static File getImageFolder(String folder_name) {
		File file = null;
		if( folder_name.length() > 0 && folder_name.lastIndexOf('/') == folder_name.length()-1 ) {
			// ignore final '/' character
			folder_name = folder_name.substring(0, folder_name.length()-1);
		}
		//if( folder_name.contains("/") ) {
		if( folder_name.startsWith("/") ) {
			file = new File(folder_name);
		}
		else {
	        file = new File(getBaseFolder(), folder_name);
		}
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "folder_name: " + folder_name);
			Log.d(TAG, "full path: " + file);
		}*/
        return file;
    }
    
    File getImageFolder() {
		String folder_name = getSaveLocation();
		return getImageFolder(folder_name);
    }

    @SuppressLint("SimpleDateFormat")
	File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

    	File mediaStorageDir = getImageFolder();
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if( !mediaStorageDir.exists() ) {
            if( !mediaStorageDir.mkdirs() ) {
        		if( MyDebug.LOG )
        			Log.e(TAG, "failed to create directory");
                return null;
            }
            broadcastFile(mediaStorageDir, false, false);
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String index = "";
        File mediaFile = null;
        for(int count=1;count<=100;count++) {
    		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if( type == ApplicationInterface.MEDIA_TYPE_IMAGE ) {
        		String prefix = sharedPreferences.getString(PreferenceKeys.getSavePhotoPrefixPreferenceKey(), "IMG_");
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                	prefix + timeStamp + index + ".jpg");
            }
            else if( type == ApplicationInterface.MEDIA_TYPE_VIDEO ) {
        		String prefix = sharedPreferences.getString(PreferenceKeys.getSaveVideoPrefixPreferenceKey(), "VID_");
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                		prefix + timeStamp + index + ".mp4");
            }
            else {
                return null;
            }
            if( !mediaFile.exists() ) {
            	break;
            }
            index = "_" + count; // try to find a unique filename
        }

		if( MyDebug.LOG ) {
			Log.d(TAG, "getOutputMediaFile returns: " + mediaFile);
		}
        return mediaFile;
    }

    class Media {
    	long id;
    	boolean video;
    	Uri uri;
    	long date;
    	int orientation;

    	Media(long id, boolean video, Uri uri, long date, int orientation) {
    		this.id = id;
    		this.video = video;
    		this.uri = uri;
    		this.date = date;
    		this.orientation = orientation;
    	}
    }
    
    private Media getLatestMedia(boolean video) {
		if( MyDebug.LOG )
			Log.d(TAG, "getLatestMedia: " + (video ? "video" : "images"));
    	Media media = null;
		Uri baseUri = video ? Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		//Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
		Uri query = baseUri;
		final int column_id_c = 0;
		final int column_date_taken_c = 1;
		final int column_data_c = 2;
		final int column_orientation_c = 3;
		String [] projection = video ? new String[] {VideoColumns._ID, VideoColumns.DATE_TAKEN, VideoColumns.DATA} : new String[] {ImageColumns._ID, ImageColumns.DATE_TAKEN, ImageColumns.DATA, ImageColumns.ORIENTATION};
		String selection = video ? "" : ImageColumns.MIME_TYPE + "='image/jpeg'";
		String order = video ? VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC" : ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";
		Cursor cursor = null;
		try {
			cursor = context.getContentResolver().query(query, projection, selection, null, order);
			if( cursor != null && cursor.moveToFirst() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "found: " + cursor.getCount());
				// now sorted in order of date - scan to most recent one in the Open Camera save folder
				boolean found = false;
				File save_folder = getImageFolder();
				String save_folder_string = save_folder.getAbsolutePath() + File.separator;
				if( MyDebug.LOG )
					Log.d(TAG, "save_folder_string: " + save_folder_string);
				do {
					String path = cursor.getString(column_data_c);
					if( MyDebug.LOG )
						Log.d(TAG, "path: " + path);
					// path may be null on Android 4.4!: http://stackoverflow.com/questions/3401579/get-filename-and-path-from-uri-from-mediastore
					if( path != null && path.contains(save_folder_string) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "found most recent in Open Camera folder");
						// we filter files with dates in future, in case there exists an image in the folder with incorrect datestamp set to the future
						// we allow up to 2 days in future, to avoid risk of issues to do with timezone etc
						long date = cursor.getLong(column_date_taken_c);
				    	long current_time = System.currentTimeMillis();
						if( date > current_time + 172800000 ) {
							if( MyDebug.LOG )
								Log.d(TAG, "skip date in the future!");
						}
						else {
							found = true;
							break;
						}
					}
				} while( cursor.moveToNext() );
				if( !found ) {
					if( MyDebug.LOG )
						Log.d(TAG, "can't find suitable in Open Camera folder, so just go with most recent");
					cursor.moveToFirst();
				}
				long id = cursor.getLong(column_id_c);
				long date = cursor.getLong(column_date_taken_c);
				int orientation = video ? 0 : cursor.getInt(column_orientation_c);
				Uri uri = ContentUris.withAppendedId(baseUri, id);
				if( MyDebug.LOG )
					Log.d(TAG, "found most recent uri for " + (video ? "video" : "images") + ": " + uri);
				media = new Media(id, video, uri, date, orientation);
			}
		}
		catch(SQLiteException e) {
			// had this reported on Google Play from getContentResolver().query() call
			if( MyDebug.LOG )
				Log.e(TAG, "SQLiteException trying to find latest media");
			e.printStackTrace();
		}
		finally {
			if( cursor != null ) {
				cursor.close();
			}
		}
		return media;
    }
    
    Media getLatestMedia() {
		Media image_media = getLatestMedia(false);
		Media video_media = getLatestMedia(true);
		Media media = null;
		if( image_media != null && video_media == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "only found images");
			media = image_media;
		}
		else if( image_media == null && video_media != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "only found videos");
			media = video_media;
		}
		else if( image_media != null && video_media != null ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "found images and videos");
				Log.d(TAG, "latest image date: " + image_media.date);
				Log.d(TAG, "latest video date: " + video_media.date);
			}
			if( image_media.date >= video_media.date ) {
				if( MyDebug.LOG )
					Log.d(TAG, "latest image is newer");
				media = image_media;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "latest video is newer");
				media = video_media;
			}
		}
		return media;
    }
}
