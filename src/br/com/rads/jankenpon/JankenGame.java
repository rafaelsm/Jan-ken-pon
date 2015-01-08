package br.com.rads.jankenpon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import br.com.rads.jan_ken_pon.R;

@SuppressLint("HandlerLeak")
public class JankenGame extends Activity implements OnClickListener {

	private static final String TAG = "JAN KEN PON";

	private static final int REQUEST_ENABLE_BLUETOOTH = 1;
	private static final int REQUEST_CONNECT_DEVICE = 2;

	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	private BluetoothAdapter bluetooth = null;
	private BluetoothJankenService jankenService = null;

	private TextView title;

	private RadioGroup radioGroup;
	private Button readyButton;
	private ImageView playerImage;
	private ImageView enemyImage;

	private Player player;
	private Player enemy;
	
	private boolean hasConnection;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		bluetooth = BluetoothAdapter.getDefaultAdapter();

		// garante que o dispositivo possua bluetooth
		if (bluetooth == null) {
			Toast.makeText(this, "Bluetooth indisponivel", Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}

		title = (TextView) findViewById(R.id.enemy_name);
		radioGroup = (RadioGroup) findViewById(R.id.radio_group);
		readyButton = (Button) findViewById(R.id.button_ready);
		playerImage = (ImageView) findViewById(R.id.player_image);
		enemyImage = (ImageView) findViewById(R.id.enemy_image);

		readyButton.setOnClickListener(this);
		radioGroup.check(R.id.opt_rock);
	}
	

	@Override
	protected void onStart() {
		super.onStart();

		// Caso o bluetooth nao esteja ativado, pede para o jogador ativar;
		if (!bluetooth.isEnabled()) {
			Intent enableBluetoothIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetoothIntent,
					REQUEST_ENABLE_BLUETOOTH);
		}
		// Senao, ja estiva ativado...
		else {
			if (jankenService == null) {
				// inicia um novo jogo
				setupJanken();
			}
		}

	}

	@Override
	protected synchronized void onResume() {
		super.onResume();

		if (jankenService != null) {

			// Se nada foi iniciado ainda
			if (jankenService.getState() == BluetoothJankenService.STATE_NONE) {
				jankenService.start();
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (jankenService != null) {
			jankenService.stop();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch (requestCode) {
		case REQUEST_ENABLE_BLUETOOTH:

			if (resultCode == RESULT_OK) {
				setupJanken();
			} else {
				Toast.makeText(this, "Bluetooth desabilitado",
						Toast.LENGTH_SHORT).show();
				finish();
			}

			break;

		case REQUEST_CONNECT_DEVICE:

			if (resultCode == RESULT_OK) {

				String macAddress = data
						.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

				BluetoothDevice device = bluetooth.getRemoteDevice(macAddress);

				jankenService.connect(device);
			}

			break;
		}

	}

	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.menu_search:

			Intent intent = new Intent(JankenGame.this,
					DeviceListActivity.class);
			startActivityForResult(intent, REQUEST_CONNECT_DEVICE);

			break;

		case R.id.menu_discovery:

			enableDiscovery();
			break;
		}

		return true;
	}

	private void enableDiscovery() {

		if (bluetooth.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {

			Intent discoveryIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoveryIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoveryIntent);

		}

	}
	
	private void setupJanken() {
		player = new Player(bluetooth.getName());
		jankenService = new BluetoothJankenService(this, handler);
	}

	/**
	 * O cara
	 * 
	 * Esse handler e quem vai manipular as mensagens recebidas, tanto de
	 * estados de conexao quanto do proprio jogo.
	 * 
	 */
	private final Handler handler = new Handler() {

		public void handleMessage(Message msg) {

			switch (msg.what) {

			// Mudancas de conexao
			case MESSAGE_STATE_CHANGE:

				// que beleza, switch dentro de switch
				switch (msg.arg1) {
				case BluetoothJankenService.STATE_CONNECTED:
					Log.d(TAG, "Conectado!!!!");
					hasConnection = true;
					break;

				case BluetoothJankenService.STATE_CONNECTING:
					Log.d(TAG, "Conectando...");
					title.setText("Conectando...");
					break;

				case BluetoothJankenService.STATE_LISTEN:
				case BluetoothJankenService.STATE_NONE:
					Log.d(TAG, "Desconectado");
					title.setText("Desconectado");
					hasConnection = false;
					break;
				}
				break;

			// Apenas loga que algo foi enviado
			case MESSAGE_WRITE:
				Log.d(TAG, "Jogador pronto");
				break;

			// Faz algo com a mensagem que chegou
			case MESSAGE_READ:

				byte[] readBuff = (byte[]) msg.obj;

				String readBuffer = new String(readBuff, 0, msg.arg1);

				enemy.setMove(readBuffer);

				if (!player.getMove().isEmpty()) {
					startJankenGame();
				}

				break;

			// Recebe o nome do dispositivo conectado
			case MESSAGE_DEVICE_NAME:

				String connectedDeviceName = msg.getData().getString(
						DEVICE_NAME);
				title.setText(connectedDeviceName);
				title.invalidate();

				enemy = new Player(connectedDeviceName);

				break;

			// Mensagens triviais
			case MESSAGE_TOAST:

				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();

				break;
			}

		};
	};

	// Envia uma mensagem
	private void sendMessage(String message) {

		byte[] send = message.getBytes();
		jankenService.write(send);

		blockOptions();
		player.setMove(message);

		if (enemy != null && !enemy.getMove().isEmpty()) {
			startJankenGame();
		}

	}

	private void blockOptions() {
		readyButton.setEnabled(false);
		
		for (int i = 0; i < radioGroup.getChildCount(); i++) {
			RadioButton rButton = (RadioButton) radioGroup.getChildAt(i);
			rButton.setEnabled(false);
		}
	}

	private void releaseOptions() {
		readyButton.setEnabled(true);
		
		for (int i = 0; i < radioGroup.getChildCount(); i++) {
			RadioButton rButton = (RadioButton) radioGroup.getChildAt(i);
			rButton.setEnabled(true);
		}
	}

	private void startJankenGame() {
		
		playerImage.setBackgroundResource(R.drawable.alex_prepare_1);
		enemyImage.setBackgroundResource(R.drawable.enemy_prepare_1);
		
		startAnimation();
		
		final Player winner = Jankenpon.fight(player, enemy);
		MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.jankenmusic);
		mediaPlayer.start();
		
		handler.postDelayed( new Runnable() {
			@Override
			public void run() {
				String text = "";

				if (winner != null) {

					boolean playerWin = winner.getName().equalsIgnoreCase(player.getName());

					if (playerWin) {
						text = "Your " + player.getMove() + " is super effective!!"
								+ " YOU WIN!!!!";
					} else {
						text = "Haha you lose...";
					}

				} else {
					text = "Draw...";
				}
				
				Toast.makeText(JankenGame.this, text, Toast.LENGTH_LONG).show();
				resetGame();
				
			}
		}, 8000);
		
	}

	private void startAnimation() {
		
		if (player.getMove().equals(Jankenpon.ROCK)) {
			playerImage.setBackgroundResource(R.drawable.anim_alex_rock);
		}else if (player.getMove().equals(Jankenpon.PAPER)) {
			playerImage.setBackgroundResource(R.drawable.anim_alex_paper);
		}else if (player.getMove().equals(Jankenpon.SCISSOR)) {
			playerImage.setBackgroundResource(R.drawable.anim_alex_scissor);
		}
		
		if (enemy.getMove().equals(Jankenpon.ROCK)) {
			enemyImage.setBackgroundResource(R.drawable.anim_enemy_rock);
		}else if (enemy.getMove().equals(Jankenpon.PAPER)) {
			enemyImage.setBackgroundResource(R.drawable.anim_enemy_paper);
		}else if (enemy.getMove().equals(Jankenpon.SCISSOR)) {
			enemyImage.setBackgroundResource(R.drawable.anim_enemy_scissor);
		}
		
		AnimationDrawable animPlayer = (AnimationDrawable) playerImage.getBackground();
		AnimationDrawable animEnemy = (AnimationDrawable) enemyImage.getBackground();
		
		animPlayer.start();		
		animEnemy.start();
		
	}


	private void resetGame() {
		player.setMove("");
		enemy.setMove("");
		
		releaseOptions();
	}

	@Override
	public void onClick(View v) {

		if (v.getId() == R.id.button_ready) {

			String move = "";

			switch (radioGroup.getCheckedRadioButtonId()) {
			case R.id.opt_rock:
				move = Jankenpon.ROCK;
				break;

			case R.id.opt_paper:
				move = Jankenpon.PAPER;
				break;

			case R.id.opt_scissor:
				move = Jankenpon.SCISSOR;
				break;
			}

			if (hasConnection)
				sendMessage(move);

		}

	}

}
