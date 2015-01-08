package br.com.rads.jankenpon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothJankenService {

	private static final String TAG = "JANKEN_SERVICE";

	private static final String NAME = "Jan-ken-pon";

	// identificar unico da aplica�‹o
	public static final UUID JANKEN_UUDID = UUID
			.fromString("8ae3485b-0ed6-4a10-82fa-4f04a488fe8d");

	private final BluetoothAdapter bluetooth;
	private final Handler handler;
	private AcceptThread acceptThread;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;

	private int currentState;
	public static final int STATE_NONE = 0;
	public static final int STATE_LISTEN = 1;
	public static final int STATE_CONNECTED = 2;
	public static final int STATE_CONNECTING = 3;

	public BluetoothJankenService(Context context, Handler mhandler) {
		bluetooth = BluetoothAdapter.getDefaultAdapter();
		handler = mhandler;
		currentState = STATE_NONE;
	}

	public synchronized int getState() {
		return currentState;
	}

	private synchronized void setState(int state) {

		currentState = state;

		// Notifica o handler sobre mudanca de estado
		handler.obtainMessage(JankenGame.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();

	}

	/**
	 * Inicia o dispositivo como server
	 * Vai ficar paradao enquanto nenhum client se conectar
	 * 
	 */
	public synchronized void start() {
		Log.d(TAG, "start()");

		// Cancela threads que estejam rodando
		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}

		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}

		if (acceptThread == null) {
			acceptThread = new AcceptThread();
			acceptThread.start();
		}

		setState(STATE_LISTEN);
	}

	/**
	 * Cria a thread de conexão 
	 */
	public synchronized void connect(BluetoothDevice device) {

		Log.d(TAG, "connect()");
		
		if (currentState == STATE_CONNECTING) {
			if (connectThread != null) {
				connectThread.cancel();
				connectThread = null;
			}
		}

		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}

		connectThread = new ConnectThread(device);
		connectThread.start();

		setState(STATE_CONNECTING);
	}

	/**
	 * Cria a thread de comunicacao
	 */
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice remoteDevice) {
		Log.d(TAG, "connected()");

		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}

		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}

		if (acceptThread != null) {
			acceptThread.cancel();
			acceptThread = null;
		}

		//cria nova thread de comunicacao
		connectedThread = new ConnectedThread(socket);
		connectedThread.start();

		Message message = handler
				.obtainMessage(JankenGame.MESSAGE_DEVICE_NAME);

		Bundle bundle = new Bundle();
		bundle.putString(JankenGame.DEVICE_NAME, remoteDevice.getName());

		message.setData(bundle);
		handler.sendMessage(message);
		setState(STATE_CONNECTED);

	}

	/**
	 * Para todas as threads
	 */
	public synchronized void stop() {
		Log.d(TAG, "stop()");

		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}

		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}

		if (acceptThread != null) {
			acceptThread.cancel();
			acceptThread = null;
		}

		setState(STATE_NONE);

	}

	/**
	 * Envia mensagem para thread responsavel 
	 */
	public void write(byte[] out){
		
		ConnectedThread tempThread;
		
		synchronized (this) {
			if (currentState != STATE_CONNECTED) {
				return;
			}
			tempThread = connectedThread;
		}
		
		tempThread.write(out);
	}

	/**
	 * Trata falha na conexao
	 */
	public void connectionFailed() {
		setState(STATE_LISTEN);
		Message msg = handler.obtainMessage(JankenGame.MESSAGE_TOAST);

		Bundle bundle = new Bundle();
		bundle.putString(JankenGame.TOAST,
				"Nao foi possivel conectar os devices");

		msg.setData(bundle);

		handler.sendMessage(msg);
	}

	/**
	 * Trata perda da conexao
	 */
	public void connectionLost() {
		setState(STATE_LISTEN);
		Message msg = handler.obtainMessage(JankenGame.MESSAGE_TOAST);

		Bundle bundle = new Bundle();
		bundle.putString(JankenGame.TOAST,
				"Conexao morreu!");

		msg.setData(bundle);

		handler.sendMessage(msg);
	}
	
	/**
	 * Thread que inicia o device como server 
	 */
	private class AcceptThread extends Thread {

		// Server socket local
		private final BluetoothServerSocket serverSocket;

		public AcceptThread() {

			BluetoothServerSocket tempSocket = null;

			try {
				tempSocket = bluetooth.listenUsingRfcommWithServiceRecord(NAME,
						JANKEN_UUDID);
			} catch (IOException e) {
				Log.e(TAG,
						"listenUsingRfcommWithServiceRecord failed: "
								+ e.toString());
			}

			serverSocket = tempSocket;
		}

		@Override
		public void run() {

			setName("AcceptThread");
			BluetoothSocket socket = null;

			while (currentState != STATE_CONNECTED) {

				try {
					Log.d(TAG, "AcceptThread RUN");
					socket = serverSocket.accept();
				} catch (IOException e) {
					Log.e(TAG, "AcceptThreadErro: " + e.toString() + "Por causa do cancelamento, ja esta conectado");
					break;
				}

				if (socket != null) {
					synchronized (BluetoothJankenService.this) {
						switch (currentState) {
						
						case STATE_NONE:
						case STATE_CONNECTED:
							try {
								socket.close();
							} catch (IOException e) {
								Log.e(TAG, "Socket nao fechou");
							}
							break;

						case STATE_LISTEN:
						case STATE_CONNECTING:
							connected(socket, socket.getRemoteDevice());
							break;
						}
					}
				}
			}

		}

		public void cancel() {
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Thread que faz conexao entre os devices
	 */
	private class ConnectThread extends Thread {

		private final BluetoothSocket socket;
		private final BluetoothDevice device;

		public ConnectThread(BluetoothDevice device) {

			this.device = device;
			BluetoothSocket tempSocket = null;

			try {
				tempSocket = device
						.createRfcommSocketToServiceRecord(JANKEN_UUDID);
			} catch (IOException e) {
				e.printStackTrace();
			}

			socket = tempSocket;

		}

		@Override
		public void run() {

			if (bluetooth.isDiscovering())
				bluetooth.cancelDiscovery();

			try {
				socket.connect();
			} catch (IOException e) {

				connectionFailed();

				try {
					socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				BluetoothJankenService.this.start();
				return;
			}

			// Ja fez a conexao, entao mata a thread nao e mais necessaria
			synchronized (BluetoothJankenService.this) {
				connectThread = null;
			}

			connected(socket, device);
		}

		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Thread que trata comunicacao 
	 */
	private class ConnectedThread extends Thread {

		private final BluetoothSocket socket;
		private final InputStream input;
		private final OutputStream output;

		public ConnectedThread(BluetoothSocket socket) {
			this.socket = socket;

			InputStream tempInput = null;
			OutputStream tempOutput = null;

			try {
				tempInput = socket.getInputStream();
				tempOutput = socket.getOutputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}

			input = tempInput;
			output = tempOutput;
		}

		@Override
		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;

			while (true) {
				try {
					bytes = input.read(buffer);
					handler.obtainMessage(JankenGame.MESSAGE_READ, bytes,-1, buffer).sendToTarget();
				} catch (IOException e) {
					connectionLost();
					break;
				}
			}

		}
		
		//Notifica o handler que uma mensagem foi enviada
		public void write(byte[] buffer){
			try {
				output.write(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
			handler.obtainMessage(JankenGame.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
		}

		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
