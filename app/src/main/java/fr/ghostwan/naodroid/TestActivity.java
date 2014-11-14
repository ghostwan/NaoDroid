package fr.ghostwan.naodroid;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


public class TestActivity extends Activity implements SensorEventListener {

	TextView textView;
	StringBuilder builder = new StringBuilder();

	float [] history = new float[2];
	String [] direction = {"NONE","NONE"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    textView = new TextView(this);
	    setContentView(textView);

	    SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	    Sensor accelerometer = manager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
	    manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.test, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

	@Override
	public void onSensorChanged(SensorEvent event) {

		float xChange = history[0] - event.values[0];
		float yChange = history[1] - event.values[1];

		history[0] = event.values[0];
		history[1] = event.values[1];

		if (xChange > 2){
			direction[0] = "LEFT";
		}
		else if (xChange < -2){
			direction[0] = "RIGHT";
		}

		if (yChange > 2){
			direction[1] = "DOWN";
		}
		else if (yChange < -2){
			direction[1] = "UP";
		}

		builder.setLength(0);
		builder.append("x: ");
		builder.append(direction[0]);
		builder.append(" y: ");
		builder.append(direction[1]);

		textView.setText(builder.toString());
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

}
