package com.woohoo.androidaudiostreamer;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.spoledge.aacdecoder.MultiPlayer;
import com.spoledge.aacdecoder.PlayerCallback;

@Kroll.module(name = "Androidaudiostreamer", id = "com.woohoo.androidaudiostreamer")
public class AndroidaudiostreamerModule extends KrollModule {
	protected static final String LOG = "AAS";
	String metaTitle;
	String metaGenre;
	int audioSessionId = 0;
	String metaUrl;
	static String currentUrl;
	static String currentCharset;
	int status;
	boolean allowBackground = false;
	boolean streamHandlerSet = false;
	boolean playerStarted;
	static boolean isCurrentlyPlaying = false;
	static boolean classInit = true;
	@Kroll.constant
	public static final int STATE_STOPPED = 0;
	@Kroll.constant
	public static final int STATE_BUFFERING = 1;
	@Kroll.constant
	public static final int STATE_PLAYING = 2;
	@Kroll.constant
	public static final int STATE_STREAMERROR = 3;

	public static final String LCAT = "AAC";

	PlayerCallback clb = new PlayerCallback() {
		public void playerStarted() {
			status = STATE_BUFFERING;
			playerStarted = true;
			if (hasListeners("change")) {
				KrollDict statusProps = new KrollDict();
				statusProps.put("status", status);
				fireEvent("change", statusProps);
			}
		}

		public void playerPCMFeedBuffer(boolean isPlaying, int bufSizeMs,
				int bufCapacityMs) {
			status = STATE_BUFFERING;
			if (isPlaying) {
				status = STATE_PLAYING;
			}
			if (hasListeners("change")) {
				KrollDict statusProps = new KrollDict();
				statusProps.put("status", status);
				statusProps.put("bufSizeMs", bufSizeMs);
				statusProps.put("bufCapacityMs", bufCapacityMs);
				fireEvent("change", statusProps);
			}
		}

		public void playerStopped(int perf) {
			playerStarted = false;
			status = STATE_STOPPED;
			if (hasListeners("change")) {
				KrollDict statusProps = new KrollDict();
				statusProps.put("status", status);
				fireEvent("change", statusProps);
			}
		}

		public void playerException(Throwable t) {
			status = STATE_STREAMERROR;
			if (playerStarted) {
				playerStopped(0);
			}
			if (hasListeners("change")) {
				KrollDict statusProps = new KrollDict();
				statusProps.put("status", status);
				fireEvent("change", statusProps);
			}
		}

		public void playerMetadata(final String key, final String value) {
			KrollDict metaProps = new KrollDict();
			if ("StreamTitle".equals(key) || "icy-name".equals(key)
					|| "icy-description".equals(key)) {
				metaTitle = value;
				metaProps.put("title", value);
			} else if ("StreamUrl".equals(key) || "icy-url".equals(key)) {
				metaUrl = value;
				metaProps.put("url", value);
			} else if ("StreamGenre".equals(key) || "icy-genre".equals(key)) {
				metaGenre = value;
				metaProps.put("genre", value);
			} else {
				return;
			}

			if (hasListeners("metadata") && !metaProps.isEmpty()) {
				metaProps.put("title", metaTitle);
				fireEvent("metadata", metaProps);
			}
		}

		@Override
		public void playerAudioTrackCreated(AudioTrack audiotrack) {
			audioSessionId = audiotrack.getAudioSessionId();
			if (hasListeners("ready")) {
				KrollDict props = new KrollDict();
				props.put("audioSessionId", audioSessionId);
				fireEvent("ready", props);
			}
		}
	};

	private static MultiPlayer aacPlayer = null;
	static AudioManager audioManager = (AudioManager) TiApplication
			.getInstance().getSystemService(Context.AUDIO_SERVICE);

	public AndroidaudiostreamerModule() {
		super();
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app) {
		try {
			java.net.URL
					.setURLStreamHandlerFactory(new java.net.URLStreamHandlerFactory() {
						public java.net.URLStreamHandler createURLStreamHandler(
								String protocol) {
							if ("icy".equals(protocol))
								return new com.spoledge.aacdecoder.IcyURLStreamHandler();
							return null;
						}
					});
		} catch (Throwable t) {
			Log.w(LOG,
					"Cannot set the ICY URLStreamHandler - maybe already set ? - "
							+ t);
		}
	}

	@Kroll.method
	public void stop() {
		if (isCurrentlyPlaying) {
			aacPlayer.stop();
			isCurrentlyPlaying = false;
		}
	}

	@SuppressWarnings("unchecked")
	@Kroll.method
	public void play(Object args) {
		String url = null, charset = "UTF-8";
		int decodeBufferCapacityMs = 700;
		int audioBufferCapacityMs = 1500;
		int expectedKBitSecRate = 0; // auto
		if (args != null) {
			if (args instanceof HashMap) {
				KrollDict dict = null;
				try {
					dict = new KrollDict((HashMap<String, Object>) args);
				} catch (Exception e) {
					Log.e(LCAT, "Unable to parse args" + args.toString());
					return;
				}
				if (dict.containsKeyAndNotNull("url")) {
					url = dict.getString("url");
				}
				if (dict.containsKeyAndNotNull("charset")) {
					charset = dict.getString("charset");
				}
				if (dict.containsKeyAndNotNull("expectedKBitSecRate")) {
					expectedKBitSecRate = dict.getInt("expectedKBitSecRate");
				}
				if (dict.containsKeyAndNotNull("decodeBufferCapacityMs")) {
					expectedKBitSecRate = dict.getInt("decodeBufferCapacityMs");
				}
				if (dict.containsKeyAndNotNull("audioBufferCapacityMs")) {
					expectedKBitSecRate = dict.getInt("audioBufferCapacityMs");
				}

			} else if (args instanceof String) {
				url = (String) args;
			} else {
				Log.e(LCAT, "args either dict or string");
			}
		}
		if (!isCurrentlyPlaying) {
			try {
				if (aacPlayer == null) {
					aacPlayer = new MultiPlayer(clb);
				}
				currentUrl = url;
				currentCharset = charset;
				aacPlayer.setDecodeBufferCapacityMs(decodeBufferCapacityMs);
				aacPlayer.setAudioBufferCapacityMs(audioBufferCapacityMs);
				if (expectedKBitSecRate == 0) {
					aacPlayer.playAsync(url);
				} else {
					aacPlayer.playAsync(url, expectedKBitSecRate);
				}
				if (charset != null) {
					aacPlayer.setMetadataCharEnc(charset);
				}
				isCurrentlyPlaying = true;
			} catch (Throwable t) {
				Log.e(LOG, "Error starting stream: " + t);
			}
		} else {
			Log.e(LOG, "Player was currently playing");
		}
	}

	@Kroll.method
	public void volume(int vol) {
		int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		float volume = (maxVol / 10) * vol;
		vol = Math.round(volume);
		if (vol <= maxVol) {
			audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
		}
	}

	@Kroll.method
	public int getMaxVolume() {
		return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
	}

	@Kroll.method
	public int getCurrentVolume() {
		return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
	}

	@Kroll.method
	public String getMetaTitle() {
		return metaTitle;
	}

	@Kroll.method
	public String getMetaGenre() {
		return metaGenre;
	}

	@Kroll.method
	public String getMetaUrl() {
		return metaUrl;
	}

	@Kroll.method
	public String getStatus() {
		return String.valueOf(status);
	}

	@Kroll.method
	public void setAllowBackground(boolean allow) {
		allowBackground = allow;
	}

	@Kroll.method
	public int getAudioSessionId() {
		return audioSessionId;
	}

	@Kroll.method
	public int getDeclaredBitRate() {
		return aacPlayer.getDeclaredBitRate();
	}

	@Kroll.method
	public void setDecodeBufferCapacityMs(int rate) {
		aacPlayer.setDecodeBufferCapacityMs(rate);
	}

	@Kroll.method
	public void setAudioBufferCapacityMs(int rate) {
		aacPlayer.setAudioBufferCapacityMs(rate);
	}

	@Override
	public void onPause(Activity activity) {
		super.onPause(activity);
		if (aacPlayer != null && !allowBackground) {
			Log.e(LOG, "on Pause with aac player and background disallowed: ");
			aacPlayer.stop();
		}
	}

	@Override
	public void onResume(Activity activity) {
		super.onResume(activity);
		if (aacPlayer != null && !allowBackground) {
			Log.e(LOG, "on Pause with aac player and background allowed: ");
			aacPlayer.stop();
		}
	}
}