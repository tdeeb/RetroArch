package org.retroarch.browser.preferences;

import java.io.File;
import java.io.IOException;

import org.retroarch.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

/**
 * Class retrieving, saving, or loading preferences.
 */
public final class UserPreferences
{
	// Logging tag.
	private static final String TAG = "UserPreferences";

	public static String getDefaultConfigPath(Context ctx)
	{
		// Internal/External storage dirs.
		final String internal = System.getenv("INTERNAL_STORAGE");
		final String external = System.getenv("EXTERNAL_STORAGE");

		// Native library directory and data directory for this front-end.
		final String nativeLibraryDir = ctx.getApplicationInfo().nativeLibraryDir;
		final String dataDir = ctx.getApplicationInfo().dataDir;
		
		// Get libretro name and path
		final SharedPreferences prefs = getPreferences(ctx);
		final String libretro_path = prefs.getString("libretro_path", nativeLibraryDir);
		final String libretro_name = prefs.getString("libretro_name", ctx.getString(R.string.no_core));

		// Check if global config is being used. Return true upon failure.
		final boolean globalConfigEnabled = prefs.getBoolean("global_config_enable", true);

		String append_path;
		// If we aren't using the global config.
		if (!globalConfigEnabled && !libretro_path.equals(nativeLibraryDir))
		{
			String sanitized_name = sanitizeLibretroPath(libretro_path);
			append_path = File.separator + sanitized_name + ".cfg";
		}
		else // Using global config.
		{
			append_path = File.separator + "retroarch.cfg";
		}

		if (external != null)
		{
			String confPath = external + append_path;
			if (new File(confPath).exists())
				return confPath;
		}
		else if (internal != null)
		{
			String confPath = internal + append_path;
			if (new File(confPath).exists())
				return confPath;
		}
		else
		{
			String confPath = "/mnt/extsd" + append_path;
			if (new File(confPath).exists())
				return confPath;
		}

		if (internal != null && new File(internal + append_path).canWrite())
			return internal + append_path;
		else if (external != null && new File(internal + append_path).canWrite())
			return external + append_path;
		else if (dataDir != null)
			return dataDir + append_path;
		else
			// emergency fallback, all else failed
			return "/mnt/sd" + append_path;
	}

	public static void readbackConfigFile(Context ctx)
	{
		String path = getDefaultConfigPath(ctx);
		ConfigFile config;
		try
		{
			config = new ConfigFile(new File(path));
		}
		catch (IOException e)
		{
			return;
		}

		Log.i(TAG, "Config readback from: " + path);
		
		SharedPreferences prefs = getPreferences(ctx);
		SharedPreferences.Editor edit = prefs.edit();

		readbackString(config, edit, "rgui_browser_directory");
		readbackString(config, edit, "savefile_directory");
		readbackString(config, edit, "savestate_directory");
		readbackBool(config, edit, "savefile_directory_enable"); // Ignored by RetroArch
		readbackBool(config, edit, "savestate_directory_enable"); // Ignored by RetroArch

		readbackString(config, edit, "input_overlay");
		readbackBool(config, edit, "input_overlay_enable");
		readbackBool(config, edit, "video_scale_integer");
		readbackBool(config, edit, "video_smooth");
		readbackBool(config, edit, "video_threaded");
		readbackBool(config, edit, "rewind_enable");
		readbackBool(config, edit, "savestate_auto_load");
		readbackBool(config, edit, "savestate_auto_save");
		//readbackDouble(config, edit, "video_refresh_rate");

		readbackBool(config, edit, "audio_rate_control");
		readbackBool(config, edit, "audio_enable");
		// TODO: other audio settings

		readbackDouble(config, edit, "input_overlay_opacity");
		readbackBool(config, edit, "input_autodetect_enable");
		//readbackInt(config, edit, "input_back_behavior");

		readbackBool(config, edit, "video_allow_rotate");
		readbackBool(config, edit, "video_font_enable");

		readbackBool(config, edit, "video_vsync");

		edit.commit();
	}
	
	public static void updateConfigFile(Context ctx)
	{
		String path = getDefaultConfigPath(ctx);
		ConfigFile config;
		try
		{
			config = new ConfigFile(new File(path));
		}
		catch (IOException e)
		{
			config = new ConfigFile();
		}

		Log.i(TAG, "Writing config to: " + path);

		// Native library and data directories.
		final String dataDir = ctx.getApplicationInfo().dataDir;
		final String nativeLibraryDir = ctx.getApplicationInfo().nativeLibraryDir;

		final SharedPreferences prefs = getPreferences(ctx);
		final String libretro_path = prefs.getString("libretro_path", nativeLibraryDir);
		

		config.setString("libretro_path", libretro_path);
		config.setString("rgui_browser_directory", prefs.getString("rgui_browser_directory", ""));
		config.setBoolean("audio_rate_control", prefs.getBoolean("audio_rate_control", true));

		int optimalRate = getOptimalSamplingRate(ctx);
		config.setInt("audio_out_rate", optimalRate);

		// Refactor this entire mess and make this usable for per-core config
		if (Build.VERSION.SDK_INT >= 17 && prefs.getBoolean("audio_latency_auto", true))
		{
			int buffersize = getLowLatencyBufferSize(ctx);

			boolean lowLatency = hasLowLatencyAudio(ctx);
			Log.i(TAG, "Audio is low latency: " + (lowLatency ? "yes" : "no"));

			config.setInt("audio_latency", 64);
			if (lowLatency)
			{
				config.setInt("audio_block_frames", buffersize);
			}
			else
			{
				config.setInt("audio_block_frames", 0);
			}
		}
		else
		{
			String latency_audio = prefs.getString("audio_latency", "64");
			config.setInt("audio_latency", Integer.parseInt(latency_audio));
		}

		config.setBoolean("audio_enable", prefs.getBoolean("audio_enable", true));
		config.setBoolean("video_smooth", prefs.getBoolean("video_smooth", true));
		config.setBoolean("video_allow_rotate", prefs.getBoolean("video_allow_rotate", true));
		config.setBoolean("savestate_auto_load", prefs.getBoolean("savestate_auto_load", true));
		config.setBoolean("savestate_auto_save", prefs.getBoolean("savestate_auto_save", false));
		config.setBoolean("rewind_enable", prefs.getBoolean("rewind_enable", false));
		config.setBoolean("video_vsync", prefs.getBoolean("video_vsync", true));
		config.setBoolean("input_autodetect_enable", prefs.getBoolean("input_autodetect_enable", true));
		config.setBoolean("input_debug_enable", prefs.getBoolean("input_debug_enable", false));
		config.setInt("input_back_behavior", Integer.parseInt(prefs.getString("input_back_behavior", "0")));

		// Set the iCade profiles
		config.setInt("input_autodetect_icade_profile_pad1", Integer.parseInt(prefs.getString("", "0")));
		config.setInt("input_autodetect_icade_profile_pad2", Integer.parseInt(prefs.getString("input_autodetect_icade_profile_pad2", "0")));
		config.setInt("input_autodetect_icade_profile_pad3", Integer.parseInt(prefs.getString("input_autodetect_icade_profile_pad3", "0")));
		config.setInt("input_autodetect_icade_profile_pad4", Integer.parseInt(prefs.getString("input_autodetect_icade_profile_pad4", "0")));

		// Set the video refresh rate.
		config.setDouble("video_refresh_rate", getRefreshRate(ctx));

		// Set whether or not we're using threaded video.
		config.setBoolean("video_threaded", prefs.getBoolean("video_threaded", true));

		// Refactor these weird values - 'full', 'auto', 'square', whatever -
		// go by what we have in RGUI - makes maintaining state easier too
		String aspect = prefs.getString("video_aspect_ratio", "auto");
		if (aspect.equals("full"))
		{
			config.setBoolean("video_force_aspect", false);
		}
		else if (aspect.equals("auto"))
		{
			config.setBoolean("video_force_aspect", true);
			config.setBoolean("video_force_aspect_auto", true);
			config.setDouble("video_aspect_ratio", -1.0);
		}
		else if (aspect.equals("square"))
		{
			config.setBoolean("video_force_aspect", true);
			config.setBoolean("video_force_aspect_auto", false);
			config.setDouble("video_aspect_ratio", -1.0);
		}
		else
		{
			double aspect_ratio = Double.parseDouble(aspect);
			config.setBoolean("video_force_aspect", true);
			config.setDouble("video_aspect_ratio", aspect_ratio);
		}

		// Set whether or not integer scaling is enabled.
		config.setBoolean("video_scale_integer", prefs.getBoolean("video_scale_integer", false));

		// Set whether or not shaders are being used.
		String shaderPath = prefs.getString("video_shader", "");
		config.setString("video_shader", shaderPath);
		config.setBoolean("video_shader_enable", prefs.getBoolean("video_shader_enable", false) && new File(shaderPath).exists());

		// Set whether or not custom overlays are being used.
		final boolean useOverlay = prefs.getBoolean("input_overlay_enable", true);
		config.setBoolean("input_overlay_enable", useOverlay); // Not used by RetroArch directly.
		if (useOverlay)
		{
			String overlayPath = prefs.getString("input_overlay", dataDir + "/overlays/snes-landscape.cfg");
			config.setString("input_overlay", overlayPath);
			config.setDouble("input_overlay_opacity", prefs.getFloat("input_overlay_opacity", 1.0f));
		}
		else
		{
			config.setString("input_overlay", "");
		}

		// Set whether or not custom directories are being used.
		final boolean usingCustomSaveFileDir = prefs.getBoolean("savefile_directory_enable", false);
		final boolean usingCustomSaveStateDir = prefs.getBoolean("savestate_directory_enable", false);
		final boolean usingCustomSystemDir = prefs.getBoolean("system_directory_enable", false);
		config.setString("savefile_directory", usingCustomSaveFileDir ? prefs.getString("savefile_directory", "") : "");
		config.setString("savestate_directory", usingCustomSaveStateDir ? prefs.getString("savestate_directory", "") : "");
		config.setString("system_directory", usingCustomSystemDir ? prefs.getString("system_directory", "") : "");

		config.setBoolean("video_font_enable", prefs.getBoolean("video_font_enable", true));
		config.setString("game_history_path", dataDir + "/retroarch-history.txt");

		for (int i = 1; i <= 4; i++)
		{
			final String[] btns =
			{ 
				"up", "down", "left", "right",
				"a", "b", "x", "y", "start", "select",
				"l", "r", "l2", "r2", "l3", "r3"
			};

			for (String b : btns)
			{
				String p = "input_player" + i + "_" + b + "_btn";
				config.setInt(p, prefs.getInt(p, 0));
			}
		}

		try
		{
			config.write(new File(path));
		}
		catch (IOException e)
		{
			Log.e(TAG, "Failed to save config file to: " + path);
		}
	}

	private static void readbackString(ConfigFile cfg, SharedPreferences.Editor edit, String key)
	{
		if (cfg.keyExists(key))
			edit.putString(key, cfg.getString(key));
		else
			edit.remove(key);
	}

	private static void readbackBool(ConfigFile cfg, SharedPreferences.Editor edit, String key)
	{
		if (cfg.keyExists(key))
			edit.putBoolean(key, cfg.getBoolean(key));
		else
			edit.remove(key);
	}

	private static void readbackDouble(ConfigFile cfg, SharedPreferences.Editor edit, String key)
	{
		if (cfg.keyExists(key))
			edit.putFloat(key, (float)cfg.getDouble(key));
		else
			edit.remove(key);
	}

	private static void readbackFloat(ConfigFile cfg, SharedPreferences.Editor edit, String key)
	{
		if (cfg.keyExists(key))
			edit.putFloat(key, cfg.getFloat(key));
		else
			edit.remove(key);
	}

	private static void readbackInt(ConfigFile cfg, SharedPreferences.Editor edit, String key)
	{
		if (cfg.keyExists(key))
			edit.putInt(key, cfg.getInt(key));
		else
			edit.remove(key);
	}

	public static SharedPreferences getPreferences(Context ctx)
	{
		return PreferenceManager.getDefaultSharedPreferences(ctx);
	}

	private static String sanitizeLibretroPath(String path)
	{
		String sanitized_name = path.substring(
				path.lastIndexOf("/") + 1,
				path.lastIndexOf("."));
		sanitized_name = sanitized_name.replace("neon", "");
		sanitized_name = sanitized_name.replace("libretro_", "");
		return sanitized_name;
	}

	public static double getRefreshRate(Context ctx)
	{
		double rate = 0;
		SharedPreferences prefs = getPreferences(ctx);
		String refresh_rate = prefs.getString("video_refresh_rate", "");
		if (!refresh_rate.isEmpty())
		{
			try
			{
				rate = Double.parseDouble(refresh_rate);
			}
			catch (NumberFormatException e)
			{
				Log.e(TAG, "Cannot parse: " + refresh_rate + " as a double!");
				rate = getDisplayRefreshRate(ctx);
			}
		}
		else
		{
			rate = getDisplayRefreshRate(ctx);
		}

		Log.i(TAG, "Using refresh rate: " + rate + " Hz.");
		return rate;
	}

	private static double getDisplayRefreshRate(Context ctx)
	{
		// Android is *very* likely to screw this up.
		// It is rarely a good value to use, so make sure it's not
		// completely wrong. Some phones return refresh rates that are
		// completely bogus
		// (like 0.3 Hz, etc), so try to be very conservative here.
		final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
		final Display display = wm.getDefaultDisplay();
		double rate = display.getRefreshRate();
		if (rate > 61.0 || rate < 58.0)
			rate = 59.95;
		return rate;
	}

	@TargetApi(17)
	public static int getLowLatencyOptimalSamplingRate(Context ctx)
	{
		AudioManager manager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

		return Integer.parseInt(manager
				.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
	}

	@TargetApi(17)
	public static int getLowLatencyBufferSize(Context ctx)
	{
		AudioManager manager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
		int buffersize = Integer.parseInt(manager
				.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
		Log.i(TAG, "Queried ideal buffer size (frames): " + buffersize);
		return buffersize;
	}

	@TargetApi(17)
	public static boolean hasLowLatencyAudio(Context ctx)
	{
		PackageManager pm = ctx.getPackageManager();
		return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
	}

	public static int getOptimalSamplingRate(Context ctx)
	{
		int ret;
		if (Build.VERSION.SDK_INT >= 17)
			ret = getLowLatencyOptimalSamplingRate(ctx);
		else
			ret = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);

		Log.i(TAG, "Using sampling rate: " + ret + " Hz");
		return ret;
	}
}
