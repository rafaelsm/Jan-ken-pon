package br.com.rads.jankenpon;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import br.com.rads.jan_ken_pon.R;

public class DeviceListActivity extends Activity implements OnItemClickListener {

//	private static final String TAG = "DeviceListActivity";
	public static final String EXTRA_DEVICE_ADDRESS = "device_address";
	
	private BluetoothAdapter bluetooth;
	private ArrayAdapter<String> pairedDevicesAdapter;
	private ArrayAdapter<String> newDevicesAdapter;
	
	private ListView pairedList;
	private ListView newDevicesList;
	private Button searchButton;
	private TextView titlePairedDevices;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_device_list);

		// Pega views no xml
		searchButton = (Button) findViewById(R.id.button_search);
		pairedList = (ListView) findViewById(R.id.paired_list);
		newDevicesList = (ListView) findViewById(R.id.new_devices_list);
		titlePairedDevices = (TextView) findViewById(R.id.paired_text);

		// inicia adapters
		pairedDevicesAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);
		newDevicesAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);

		// seta adapters
		pairedList.setAdapter(pairedDevicesAdapter);
		newDevicesList.setAdapter(newDevicesAdapter);

		// registra listener
		pairedList.setOnItemClickListener(this);
		newDevicesList.setOnItemClickListener(this);
		searchButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				searchDevices();
				searchButton.setVisibility(View.GONE);
				newDevicesList.setVisibility(View.VISIBLE);
			}
		});

		// Registra o filtro para descoberta de devices
		IntentFilter broadcastFilter = new IntentFilter(
				BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(broadcastReceiver, broadcastFilter);

		// Registra o filtro para o fim da descoberta
		broadcastFilter = new IntentFilter(
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(broadcastReceiver, broadcastFilter);

		// Aqui nao precisa verificar se o bluetooth existe, pois se a aplicação
		// nâo fechou é porque tem
		bluetooth = BluetoothAdapter.getDefaultAdapter();

		// pega lista de dispositivos pareados
		Set<BluetoothDevice> devices = bluetooth.getBondedDevices();

		if (devices.size() > 0) {
			titlePairedDevices.setVisibility(View.VISIBLE);
			for (BluetoothDevice device : devices) {
				pairedDevicesAdapter.add(device.getName() + "\n"
						+ device.getAddress());
			}
		} else {
			pairedDevicesAdapter.add("Nenhum dispositivo");
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (bluetooth != null) {
			bluetooth.cancelDiscovery();
		}

		this.unregisterReceiver(broadcastReceiver);
	}

	private void searchDevices() {

		setProgressBarIndeterminateVisibility(true);
		setTitle("Procurando...");

		findViewById(R.id.new_devices_text).setVisibility(View.VISIBLE);
		

		if (bluetooth.isDiscovering()) {
			bluetooth.cancelDiscovery();
		}

		bluetooth.startDiscovery();
	}

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();

			// Quando encontra um device
			if (action.equals(BluetoothDevice.ACTION_FOUND)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					newDevicesAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
			}
			// Quando a descoberta de dipositivos termina
			else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {

				setProgressBarIndeterminateVisibility(false);
				setTitle("Escolha um dispositivo");

				if (newDevicesAdapter.getCount() == 0) {
					newDevicesAdapter.add("Nenhum dispositivo");
				}
			}
		}
	};

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position,
			long id) {

		bluetooth.cancelDiscovery();

		// pega o endereço mac
		String info = ((TextView) view).getText().toString();
		String macAdress = info.substring(info.length() - 17);

		Intent intent = new Intent();
		intent.putExtra(EXTRA_DEVICE_ADDRESS, macAdress);

		setResult(RESULT_OK, intent);
		finish();

	}
}
