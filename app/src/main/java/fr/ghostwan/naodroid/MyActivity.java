package fr.ghostwan.naodroid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.aldebaran.qimessaging.*;
import com.aldebaran.qimessaging.helpers.ALInterface;
import com.aldebaran.qimessaging.helpers.ALModule;
import com.aldebaran.qimessaging.helpers.al.*;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class MyActivity extends Activity implements ALInterface {

	private static final String EXTRA_EVENT_ID = "test";
	private static final String TAG = "NaoDroid";
	private static final String ACTION_RUN_BEHAVIOR = "fr.ghostwan.naodroid.ACTION_RUN_BEHAVIOR";
	private static final String ACTION_DISMISS_NOTIFICATION = "fr.ghostwan.naodroid.ACTION_DISMISS_NOTIFICATION";
	private static final String ACTION_STOP_BEHAVIOR = "fr.ghostwan.naodroid.ACTION_STOP_BEHAVIOR";
	private EditText ipText;
	private Context context;
	private Button connectButton;
	private String ipAddress;
	private BroadcastReceiver broadcastReceiver;
	private int requestCode;
	private Map<String, String> behaviors;


	private Session session;
	private ALTextToSpeech alSpeech;
	private ALBehaviorManager alBehaviorManager;
	private ALAutonomousLife alAutonomousLife;
	private TextView logText;
	private int currentVolume;
	private ALAudioDevice alaudioDevice;
	private NotificationManagerCompat notificationManager;
	private ALMotion almotion;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestCode = 0;
		setContentView(R.layout.activity_my);
		context = this;
		ipText = (EditText) findViewById(R.id.robotip_edit);
		connectButton = (Button) findViewById(R.id.connect_button);
		logText = (TextView) findViewById(R.id.log_text);
		logText.setMovementMethod(new ScrollingMovementMethod());
		ALModule.alInterface = this;
		notificationManager = NotificationManagerCompat.from(context);

		EmbeddedTools ebt = new EmbeddedTools();
		File cacheDir = getApplicationContext().getCacheDir();
		ebt.overrideTempDirectory(cacheDir);
		ebt.loadEmbeddedLibraries();

		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {

				try {
					String action = intent.getAction();
					String state = alAutonomousLife.getState();

					writeLog("Intent Action :"+action);
					writeLog("Life state:"+state);

					if (action.equals(ACTION_RUN_BEHAVIOR)) {
						String name = intent.getExtras().getString("name");
						alSpeech.say("je lance le behavior " + name);

						String lastBehavior = behaviors.get(name);
						if (state.equals("disabled")) {
							alBehaviorManager.runBehavior(lastBehavior);
						}
						else {
							alAutonomousLife.switchFocus(lastBehavior + "/.");
						}
						requestCode++;
						NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context)
								.setSmallIcon(R.drawable.nao)
								.setContentTitle("NaoDroid")
								.setContentText(name + "running")
								.setDeleteIntent(PendingIntent.getBroadcast(context, requestCode,
										new Intent(ACTION_DISMISS_NOTIFICATION), PendingIntent.FLAG_CANCEL_CURRENT));


						notificationManager.notify(002, notificationBuilder.build());
					}
					else if (action.equals(ACTION_DISMISS_NOTIFICATION)) {
						onDisconnected();
					}
					else if (action.equals(ACTION_STOP_BEHAVIOR)) {
						if (state.equals("disabled")) {
							alBehaviorManager.stopAllBehaviors();
							almotion.rest();
						}
						else
							alAutonomousLife.stopFocus();

						notificationManager.cancel(002);
					}
				}
				catch (CallError callError) {
					callError.printStackTrace();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}


			}
		};
		IntentFilter filter = new IntentFilter(ACTION_RUN_BEHAVIOR);
		filter.addAction(ACTION_DISMISS_NOTIFICATION);
		filter.addAction(ACTION_STOP_BEHAVIOR);
		registerReceiver(broadcastReceiver, filter);
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(broadcastReceiver);
		super.onDestroy();
	}

	private void writeLog(final String text) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				appendColoredText(logText, text, Color.BLACK);
				Log.i(TAG, text);
			}
		});

	}

	public static void appendColoredText(TextView tv, String text, int color) {
		int start = tv.getText().length();
		tv.append(text + "\n");
		int end = tv.getText().length();

		Spannable spannableText = (Spannable) tv.getText();
		spannableText.setSpan(new ForegroundColorSpan(color), start, end, 0);

		final int scrollAmount = tv.getLayout().getLineTop(tv.getLineCount()) - tv.getHeight();
		if (scrollAmount > 0)
			tv.scrollTo(0, scrollAmount);
		else
			tv.scrollTo(0, 0);
	}

	private void writeError(final String text, final Exception e) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				appendColoredText(logText, "ERROR: " + text, Color.RED);
				Log.e(TAG, text, e);
			}
		});
	}

	private void startServiceRoutine() {

		Thread routine = new Thread(new Runnable() {
			@Override
			public void run() {
				Looper.prepare();
				if (ipText.getText() != null && !ipText.getText().toString().equals("")) {
					session = new Session();
					try {
						ipAddress = ipText.getText().toString();
						if (!ipAddress.contains(".")) {
							InetAddress[] inets = InetAddress.getAllByName(ipAddress);
							if (inets != null && inets.length > 0)
								ipAddress = inets[0].getHostAddress();
						}
						writeLog("Ip address : " + ipAddress);
						session.connect("tcp://" + ipAddress + ":9559").sync(500, TimeUnit.MILLISECONDS);
						if (session.isConnected())
							connectServices();
						else
							Toast.makeText(context, "Cannot connect to " + ipAddress, Toast.LENGTH_SHORT).show();
					}
					catch (Exception e) {
						e.printStackTrace();
						writeError("connection problem", e);
					}


				}
				else {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(context, "Please enter a robot ipText or a robot name", Toast.LENGTH_SHORT).show();
						}
					});
				}
			}
		});

		routine.start();
	}

	private void connectServices() throws InterruptedException, CallError {
		alSpeech = new ALTextToSpeech(session);
		alSpeech.setAsynchronous(true);
		alBehaviorManager = new ALBehaviorManager(session);
		alAutonomousLife = new ALAutonomousLife(session);
		alaudioDevice = new ALAudioDevice(session);
		almotion = new ALMotion(session);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				connectButton.setText("Disconnect");
			}
		});

		alSpeech.say(getString(R.string.connect_say));
		behaviors = getBehaviors();
		currentVolume = alaudioDevice.getOutputVolume();
		createNotification(behaviors);
	}

	private Map<String, String> getBehaviors() throws InterruptedException, CallError {
		HashMap<String, String> behaviorMap = new HashMap<String, String>();

		List<String> list = alBehaviorManager.getInstalledBehaviors();
		for (String name : list) {
			if (!name.contains("animations")
					&& !name.contains("dialog")
					&& !name.contains("presentation")
					&& alBehaviorManager.getBehaviorNature(name).equals("interactive")) {
				writeLog(name);
				behaviorMap.put(name, name);
			}
		}

		return behaviorMap;
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	public void createNotification(Map<String, String> behaviorList) {
		int notificationId = 001;
		// Build intent for notification content
		Intent viewIntent = new Intent(this, MyActivity.class);
		int eventId = 1;
		viewIntent.putExtra(EXTRA_EVENT_ID, eventId);
		PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0, viewIntent, 0);

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.nao)
				.setContentTitle("NaoDroid")
				.setContentText("connected to robot :" + ipAddress)
				.setDeleteIntent(PendingIntent.getBroadcast(this,
						requestCode, new Intent(ACTION_DISMISS_NOTIFICATION), PendingIntent.FLAG_CANCEL_CURRENT))
				.setContentIntent(viewPendingIntent);


		for (String name : behaviorList.keySet()) {
			addAction(notificationBuilder, name);
		}
		Intent intent = new Intent(ACTION_STOP_BEHAVIOR);
		PendingIntent behaviorIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		notificationBuilder.addAction(R.drawable.stop, "stop behavior", behaviorIntent);
		requestCode++;

		notificationManager.notify(notificationId, notificationBuilder.build());
	}

	private void addAction(NotificationCompat.Builder notificationBuilder, String name) {
		Intent intent = new Intent(ACTION_RUN_BEHAVIOR);
		intent.putExtra("name", name);
		PendingIntent behaviorIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		notificationBuilder.addAction(R.drawable.nao, name, behaviorIntent);
		requestCode++;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.my, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void onConnectButton(View view) throws InterruptedException, CallError {
		if (connectButton.getText().equals("Connect")) {
			connectButton.setText("Connecting...");
			ipText.setEnabled(false);
			startServiceRoutine();
		}
		else {
			ipText.setEnabled(true);
			onDisconnected();
		}
	}

	public void onDisconnected() throws InterruptedException, CallError {

		if (session.isConnected()) {
			alSpeech.say(getString(R.string.disconnect_say));
			session.close();
		}
		connectButton.setText("Connect");
	}

	@Override
	public void onALModuleReady() {
		writeLog("Session connected ");
	}

	@Override
	public void onALModuleException(Exception e) {
		Toast.makeText(context, "Cannot connect to " + ipAddress, Toast.LENGTH_SHORT).show();
		writeError("error connection", e);
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		try {

			if (session.isConnected()) {
				if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
					if ((currentVolume - 5) >= 0) {
						currentVolume -= 5;
						alaudioDevice.setOutputVolume(currentVolume);
						alSpeech.say("" + currentVolume);
					}
					return true;
				}
				else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
					if ((currentVolume + 5) <= 100) {
						currentVolume += 5;
						alaudioDevice.setOutputVolume(currentVolume);
						alSpeech.say("" + currentVolume);
					}
					return true;
				}
			}
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		catch (CallError callError) {
			callError.printStackTrace();
		}

		return super.onKeyDown(keyCode, event);
	}


}
