package com.theeyes.theVUE2;

import com.theeyes.theVUE2.Preview.Preview;
import com.theeyes.theVUE2.UI.FolderChooserDialog;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;

public class MyPreferenceFragment extends PreferenceFragment {
	private static final String TAG = "MyPreferenceFragment";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		final Bundle bundle = getArguments();
		final int cameraId = bundle.getInt("cameraId");
		if( MyDebug.LOG )
			Log.d(TAG, "cameraId: " + cameraId);
		
		final String camera_api = bundle.getString("camera_api");
		
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

		final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

		/*if( !supports_auto_stabilise ) {
			Preference pref = findPreference("preference_auto_stabilise");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_camera_effects");
        	pg.removePreference(pref);
		}*/

		//readFromBundle(bundle, "color_effects", Preview.getColorEffectPreferenceKey(), Camera.Parameters.EFFECT_NONE, "preference_category_camera_effects");
		//readFromBundle(bundle, "scene_modes", Preview.getSceneModePreferenceKey(), Camera.Parameters.SCENE_MODE_AUTO, "preference_category_camera_effects");
		//readFromBundle(bundle, "white_balances", Preview.getWhiteBalancePreferenceKey(), Camera.Parameters.WHITE_BALANCE_AUTO, "preference_category_camera_effects");
		//readFromBundle(bundle, "isos", Preview.getISOPreferenceKey(), "auto", "preference_category_camera_effects");
		//readFromBundle(bundle, "exposures", "preference_exposure", "0", "preference_category_camera_effects");

		final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_face_detection: " + supports_face_detection);

		if( !supports_face_detection ) {
			Preference pref = findPreference("preference_face_detection");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_camera_effects");
        	pg.removePreference(pref);
		}

		final int preview_width = bundle.getInt("preview_width");
		final int preview_height = bundle.getInt("preview_height");
		final int [] preview_widths = bundle.getIntArray("preview_widths");
		final int [] preview_heights = bundle.getIntArray("preview_heights");
		final int [] video_widths = bundle.getIntArray("video_widths");
		final int [] video_heights = bundle.getIntArray("video_heights");

		final int resolution_width = bundle.getInt("resolution_width");
		final int resolution_height = bundle.getInt("resolution_height");
		final int [] widths = bundle.getIntArray("resolution_widths");
		final int [] heights = bundle.getIntArray("resolution_heights");
		if( widths != null && heights != null ) {
			CharSequence [] entries = new CharSequence[widths.length];
			CharSequence [] values = new CharSequence[widths.length];
			for(int i=0;i<widths.length;i++) {
				entries[i] = widths[i] + " x " + heights[i] + " " + Preview.getAspectRatioMPString(widths[i], heights[i]);
				values[i] = widths[i] + " " + heights[i];
			}
			ListPreference lp = (ListPreference)findPreference("preference_resolution");
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String resolution_preference_key = PreferenceKeys.getResolutionPreferenceKey(cameraId);
			String resolution_value = sharedPreferences.getString(resolution_preference_key, "");
			if( MyDebug.LOG )
				Log.d(TAG, "resolution_value: " + resolution_value);
			lp.setValue(resolution_value);
			// now set the key, so we save for the correct cameraId
			lp.setKey(resolution_preference_key);
		}
		else {
			Preference pref = findPreference("preference_resolution");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
        	pg.removePreference(pref);
		}

		{
			final int n_quality = 100;
			CharSequence [] entries = new CharSequence[n_quality];
			CharSequence [] values = new CharSequence[n_quality];
			for(int i=0;i<n_quality;i++) {
				entries[i] = "" + (i+1) + "%";
				values[i] = "" + (i+1);
			}
			ListPreference lp = (ListPreference)findPreference("preference_quality");
			lp.setEntries(entries);
			lp.setEntryValues(values);
		}

		final String [] video_quality = bundle.getStringArray("video_quality");
		final String [] video_quality_string = bundle.getStringArray("video_quality_string");
		if( video_quality != null && video_quality_string != null ) {
			CharSequence [] entries = new CharSequence[video_quality.length];
			CharSequence [] values = new CharSequence[video_quality.length];
			for(int i=0;i<video_quality.length;i++) {
				entries[i] = video_quality_string[i];
				values[i] = video_quality[i];
			}
			ListPreference lp = (ListPreference)findPreference("preference_video_quality");
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String video_quality_preference_key = PreferenceKeys.getVideoQualityPreferenceKey(cameraId);
			String video_quality_value = sharedPreferences.getString(video_quality_preference_key, "");
			if( MyDebug.LOG )
				Log.d(TAG, "video_quality_value: " + video_quality_value);
			lp.setValue(video_quality_value);
			// now set the key, so we save for the correct cameraId
			lp.setKey(video_quality_preference_key);
		}
		else {
			Preference pref = findPreference("preference_video_quality");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
        	pg.removePreference(pref);
		}
		final String current_video_quality = bundle.getString("current_video_quality");
		final int video_frame_width = bundle.getInt("video_frame_width");
		final int video_frame_height = bundle.getInt("video_frame_height");
		final int video_bit_rate = bundle.getInt("video_bit_rate");
		final int video_frame_rate = bundle.getInt("video_frame_rate");

		final boolean supports_force_video_4k = bundle.getBoolean("supports_force_video_4k");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_force_video_4k: " + supports_force_video_4k);
		if( !supports_force_video_4k || video_quality == null || video_quality_string == null ) {
			Preference pref = findPreference("preference_force_video_4k");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
        	pg.removePreference(pref);
		}
		
		final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_video_stabilization: " + supports_video_stabilization);
		if( !supports_video_stabilization ) {
			Preference pref = findPreference("preference_video_stabilization");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
        	pg.removePreference(pref);
		}

		final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");
		if( MyDebug.LOG )
			Log.d(TAG, "can_disable_shutter_sound: " + can_disable_shutter_sound);
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !can_disable_shutter_sound ) {
        	// Camera.enableShutterSound requires JELLY_BEAN_MR1 or greater
        	Preference pref = findPreference("preference_shutter_sound");
        	PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_camera_controls_more");
        	pg.removePreference(pref);
        }

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ) {
        	// Some immersive modes require KITKAT - simpler to require Kitkat for any of the menu options
        	Preference pref = findPreference("preference_immersive_mode");
        	PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
        	pg.removePreference(pref);
        }
        
		final boolean using_android_l = bundle.getBoolean("using_android_l");
        if( !using_android_l ) {
        	Preference pref = findPreference("preference_show_iso");
        	PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
        	pg.removePreference(pref);
        }

		final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_camera2: " + supports_camera2);
        if( supports_camera2 ) {
        	final Preference pref = findPreference("preference_use_camera2");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_use_camera2") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked camera2 API - need to restart");
                		// see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
                		Intent i = getActivity().getBaseContext().getPackageManager().getLaunchIntentForPackage( getActivity().getBaseContext().getPackageName() );
	                	i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	                	startActivity(i);
	                	return false;
                	}
                	return false;
                }
            });
        }
        else {
        	Preference pref = findPreference("preference_use_camera2");
        	PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_online");
        	pg.removePreference(pref);
        }
        
        {
            final Preference pref = findPreference("preference_online_help");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_online_help") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked online help");
            	        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://opencamera.sourceforge.net/"));
            	        startActivity(browserIntent);
                		return false;
                	}
                	return false;
                }
            });
        }

        /*{
        	EditTextPreference edit = (EditTextPreference)findPreference("preference_save_location");
        	InputFilter filter = new InputFilter() { 
        		// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
        		String disallowed = "|\\?*<\":>";
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) { 
                    for(int i=start;i<end;i++) { 
                    	if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
                            return ""; 
                    	}
                    } 
                    return null; 
                }
        	}; 
        	edit.getEditText().setFilters(new InputFilter[]{filter});         	
        }*/
        {
        	Preference pref = findPreference("preference_save_location");
        	pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        		@Override
                public boolean onPreferenceClick(Preference arg0) {
            		if( MyDebug.LOG )
            			Log.d(TAG, "clicked save location");
            		FolderChooserDialog fragment = new FolderChooserDialog();
            		fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
                	return true;
                }
            });        	
        }

        {
            final Preference pref = findPreference("preference_donate");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_donate") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked to donate");
            	        /*Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.getDonateMarketLink()));
            	        try {
            	        	startActivity(browserIntent);
            	        }
            			catch(ActivityNotFoundException e) {
            				// needed in case market:// not supported
            				if( MyDebug.LOG )
            					Log.d(TAG, "can't launch market:// intent");
                	        browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.getDonateLink()));
            	        	startActivity(browserIntent);
            			}*/
            	        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.getDonateLink()));
        	        	startActivity(browserIntent);
                		return false;
                	}
                	return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_about");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_about") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked about");
            	        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setTitle("About");
                        final StringBuilder about_string = new StringBuilder();
                        String version = "UNKNOWN_VERSION";
                        int version_code = -1;
						try {
	                        PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
	                        version = pInfo.versionName;
	                        version_code = pInfo.versionCode;
	                    }
						catch(NameNotFoundException e) {
	                		if( MyDebug.LOG )
	                			Log.d(TAG, "NameNotFoundException exception trying to get version number");
							e.printStackTrace();
						}
                        about_string.append("Open Camera v");
                        about_string.append(version);
                        about_string.append("\nVersion Code: ");
                        about_string.append(version_code);
                        about_string.append("\n(c) 2013-2015 Mark Harman");
                        about_string.append("\nReleased under the GPL v3 or later");
                        about_string.append("\nPackage: ");
                        about_string.append(MyPreferenceFragment.this.getActivity().getPackageName());
                        about_string.append("\nAndroid API version: ");
                        about_string.append(Build.VERSION.SDK_INT);
                        about_string.append("\nDevice manufacturer: ");
                        about_string.append(Build.MANUFACTURER);
                        about_string.append("\nDevice model: ");
                        about_string.append(Build.MODEL);
                        about_string.append("\nDevice code-name: ");
                        about_string.append(Build.HARDWARE);
                        about_string.append("\nDevice variant: ");
                        about_string.append(Build.DEVICE);
                        {
                    		ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
                            about_string.append("\nStandard max heap? (MB): ");
                            about_string.append(activityManager.getMemoryClass());
                            about_string.append("\nLarge max heap? (MB): ");
                            about_string.append(activityManager.getLargeMemoryClass());
                        }
                        {
                            Point display_size = new Point();
                            Display display = MyPreferenceFragment.this.getActivity().getWindowManager().getDefaultDisplay();
                            display.getSize(display_size);
                            about_string.append("\nDisplay size: ");
                            about_string.append(display_size.x);
                            about_string.append("x");
                            about_string.append(display_size.y);
                        }
                        about_string.append("\nCurrent camera ID: ");
                        about_string.append(cameraId);
                        about_string.append("\nCamera API: ");
                        about_string.append(camera_api);
                        {
                        	SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyPreferenceFragment.this.getActivity());
                        	String last_video_error = sharedPreferences.getString("last_video_error", "");
                        	if( last_video_error != null && last_video_error.length() > 0 ) {
                                about_string.append("\nLast video error: ");
                                about_string.append(last_video_error);
                        	}
                        }
                        if( preview_widths != null && preview_heights != null ) {
                            about_string.append("\nPreview resolutions: ");
                			for(int i=0;i<preview_widths.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(preview_widths[i]);
                				about_string.append("x");
                				about_string.append(preview_heights[i]);
                			}
                        }
                        about_string.append("\nPreview resolution: " + preview_width + "x" + preview_height);
                        if( widths != null && heights != null ) {
                            about_string.append("\nPhoto resolutions: ");
                			for(int i=0;i<widths.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(widths[i]);
                				about_string.append("x");
                				about_string.append(heights[i]);
                			}
                        }
                        about_string.append("\nPhoto resolution: " + resolution_width + "x" + resolution_height);
                        if( video_quality != null ) {
                            about_string.append("\nVideo qualities: ");
                			for(int i=0;i<video_quality.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(video_quality[i]);
                			}
                        }
                        if( video_widths != null && video_heights != null ) {
                            about_string.append("\nVideo resolutions: ");
                			for(int i=0;i<video_widths.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(video_widths[i]);
                				about_string.append("x");
                				about_string.append(video_heights[i]);
                			}
                        }
        				about_string.append("\nVideo quality: " + current_video_quality);
        				about_string.append("\nVideo frame width: " + video_frame_width);
        				about_string.append("\nVideo frame height: " + video_frame_height);
        				about_string.append("\nVideo bit rate: " + video_bit_rate);
        				about_string.append("\nVideo frame rate: " + video_frame_rate);
                        about_string.append("\nAuto-stabilise?: ");
                        about_string.append(getString(supports_auto_stabilise ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nFace detection?: ");
                        about_string.append(getString(supports_face_detection ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nVideo stabilization?: ");
                        about_string.append(getString(supports_video_stabilization ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nFlash modes: ");
                		String [] flash_values = bundle.getStringArray("flash_values");
                		if( flash_values != null && flash_values.length > 0 ) {
                			for(int i=0;i<flash_values.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(flash_values[i]);
                			}
                		}
                		else {
                            about_string.append("None");
                		}
                        about_string.append("\nFocus modes: ");
                		String [] focus_values = bundle.getStringArray("focus_values");
                		if( focus_values != null && focus_values.length > 0 ) {
                			for(int i=0;i<focus_values.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(focus_values[i]);
                			}
                		}
                		else {
                            about_string.append("None");
                		}
                        about_string.append("\nColor effects: ");
                		String [] color_effects_values = bundle.getStringArray("color_effects");
                		if( color_effects_values != null && color_effects_values.length > 0 ) {
                			for(int i=0;i<color_effects_values.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(color_effects_values[i]);
                			}
                		}
                		else {
                            about_string.append("None");
                		}
                        about_string.append("\nScene modes: ");
                		String [] scene_modes_values = bundle.getStringArray("scene_modes");
                		if( scene_modes_values != null && scene_modes_values.length > 0 ) {
                			for(int i=0;i<scene_modes_values.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(scene_modes_values[i]);
                			}
                		}
                		else {
                            about_string.append("None");
                		}
                        about_string.append("\nWhite balances: ");
                		String [] white_balances_values = bundle.getStringArray("white_balances");
                		if( white_balances_values != null && white_balances_values.length > 0 ) {
                			for(int i=0;i<white_balances_values.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(white_balances_values[i]);
                			}
                		}
                		else {
                            about_string.append("None");
                		}
                        about_string.append("\nISOs: ");
                		String [] isos = bundle.getStringArray("isos");
                		if( isos != null && isos.length > 0 ) {
                			for(int i=0;i<isos.length;i++) {
                				if( i > 0 ) {
                    				about_string.append(", ");
                				}
                				about_string.append(isos[i]);
                			}
                		}
                		else {
                            about_string.append("None");
                		}
                		String iso_key = bundle.getString("iso_key");
                		if( iso_key != null ) {
                			about_string.append("\nISO key: " + iso_key);
                		}

                		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyPreferenceFragment.this.getActivity());
                		String save_location = sharedPreferences.getString(PreferenceKeys.getSaveLocationPreferenceKey(), "OpenCamera");
                		about_string.append("\nSave Location: " + save_location);

                		about_string.append("\nParameters: ");
                		String parameters_string = bundle.getString("parameters_string");
                		if( parameters_string != null ) {
                			about_string.append(parameters_string);
                		}
                		else {
                            about_string.append("None");
                		}
                        
                        alertDialog.setMessage(about_string);
                        alertDialog.setPositiveButton(R.string.about_ok, null);
                        alertDialog.setNegativeButton(R.string.about_copy_to_clipboard, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                        		if( MyDebug.LOG )
                        			Log.d(TAG, "user clicked copy to clipboard");
							 	ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE); 
							 	ClipData clip = ClipData.newPlainText("OpenCamera About", about_string);
							 	clipboard.setPrimaryClip(clip);
                            }
                        });
                        alertDialog.show();
                		return false;
                	}
                	return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_reset");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                	if( pref.getKey().equals("preference_reset") ) {
                		if( MyDebug.LOG )
                			Log.d(TAG, "user clicked reset");
    				    new AlertDialog.Builder(MyPreferenceFragment.this.getActivity())
			        	.setIcon(android.R.drawable.ic_dialog_alert)
			        	.setTitle(R.string.preference_reset)
			        	.setMessage(R.string.preference_reset_question)
			        	.setPositiveButton(R.string.answer_yes, new DialogInterface.OnClickListener() {
			        		@Override
					        public void onClick(DialogInterface dialog, int which) {
		                		if( MyDebug.LOG )
		                			Log.d(TAG, "user confirmed reset");
		                		SharedPreferences.Editor editor = sharedPreferences.edit();
		                		editor.clear();
		                		editor.putBoolean(PreferenceKeys.getFirstTimePreferenceKey(), true);
		                		editor.apply();
		                		if( MyDebug.LOG )
		                			Log.d(TAG, "user clicked reset - need to restart");
		                		// see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
		                		Intent i = getActivity().getBaseContext().getPackageManager().getLaunchIntentForPackage( getActivity().getBaseContext().getPackageName() );
			                	i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                	startActivity(i);
					        }
			        	})
			        	.setNegativeButton(R.string.answer_no, null)
			        	.show();
                	}
                	return false;
                }
            });
        }
	}
	
	/*private void readFromBundle(Bundle bundle, String intent_key, String preference_key, String default_value, String preference_category_key) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "readFromBundle: " + intent_key);
		}
		String [] values = bundle.getStringArray(intent_key);
		if( values != null && values.length > 0 ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, intent_key + " values:");
				for(int i=0;i<values.length;i++) {
					Log.d(TAG, values[i]);
				}
			}
			ListPreference lp = (ListPreference)findPreference(preference_key);
			lp.setEntries(values);
			lp.setEntryValues(values);
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
			String value = sharedPreferences.getString(preference_key, default_value);
			if( MyDebug.LOG )
				Log.d(TAG, "    value: " + values);
			lp.setValue(value);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "remove preference " + preference_key + " from category " + preference_category_key);
			Preference pref = findPreference(preference_key);
        	PreferenceGroup pg = (PreferenceGroup)this.findPreference(preference_category_key);
        	pg.removePreference(pref);
		}
	}*/
	
	public void onResume() {
		super.onResume();
		// prevent fragment being transparent
		// note, setting color here only seems to affect the "main" preference fragment screen, and not sub-screens
		// note, on Galaxy Nexus Android 4.3 this sets to black rather than the dark grey that the background theme should be (and what the sub-screens use); works okay on Nexus 7 Android 5
		// we used to use a light theme for the PreferenceFragment, but mixing themes in same activity seems to cause problems (e.g., for EditTextPreference colors)
		TypedArray array = getActivity().getTheme().obtainStyledAttributes(new int[] {  
			    android.R.attr.colorBackground
		});
		int backgroundColor = array.getColor(0, Color.BLACK);
		/*if( MyDebug.LOG ) {
			int r = (backgroundColor >> 16) & 0xFF;
			int g = (backgroundColor >> 8) & 0xFF;
			int b = (backgroundColor >> 0) & 0xFF;
			Log.d(TAG, "backgroundColor: " + r + " , " + g + " , " + b);
		}*/
		getView().setBackgroundColor(backgroundColor);
		array.recycle();
	}
}
