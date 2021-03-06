package com.teloquitous.lab.ankabut.fragment;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.teloquitous.lab.ankabut.AnkabutKeyStrings;
import com.teloquitous.lab.ankabut.R;
import com.teloquitous.lab.ankabut.mediaplayer.TeloPlayerService;
import com.teloquitous.lab.ankabut.mediaplayer.TeloPlayerService.MediaPlayerBinder;
import com.teloquitous.lab.ankabut.mediaplayer.TeloPlayerServiceClient;
import com.teloquitous.lab.ankabut.rss.Kajian;
import com.teloquitous.lab.ankabut.rss.KajianRSSListAdapter;
import com.teloquitous.lab.ankabut.rss.RssParserHandler;

public class AudioRssFragment extends Fragment implements
		TeloPlayerServiceClient, AnkabutKeyStrings {
	private static final String _KAJIAN_NET_RSS = "http://kajian.net/kajian-audio/added/all/song/stats.xml";
	private ListView listView;
	private KajianRSSListAdapter adapter;
	private static List<Kajian> data = new ArrayList<Kajian>();
	private TextView textEmpty;
	private Animation anim;
	private Kajian curKajian;
	private TeloPlayerService tPlayer;
	private MediaPlayer mPlayer;
	private boolean mBound;
	private static int selectedPos;
	private static boolean dataInitiated = false;
	private int lastSelectPos;
	private boolean playedOnLastRun = false;
	private boolean serviceBerjalan = false;
	private ViewGroup root;

	private SharedPreferences preferenceManager;
	private DownloadManager downloadManager;

	/**
	 * pakai model last update, jadi data XML yang berisi list kajian, disimpan
	 * ke dalam SDCARD, kemudian dijadikan database, nah setiap kali dijalankan,
	 * yang dibuka adalah file ini, bukan nge-load dari internet. Pada interval
	 * waktu tertentu, (misal sehari) data lama digantikan dengan data yang
	 * terbaru (ambil dari internet).
	 * 
	 * @param context
	 * @return
	 */

	public static Fragment newInstance(Context context) {
		AudioRssFragment f = new AudioRssFragment();
		selectedPos = -1;
		return f;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		root = (ViewGroup) inflater.inflate(R.layout.activity_audio_rss, null);
		listView = (ListView) root.findViewById(R.id.list);
		listView.setItemsCanFocus(false);
		textEmpty = (TextView) root.findViewById(R.id.textViewEmpty);

		anim = new AlphaAnimation(0.0f, 1.0f);
		anim.setDuration(300);
		anim.setStartOffset(200);
		anim.setRepeatMode(Animation.REVERSE);
		anim.setRepeatCount(Animation.INFINITE);
		textEmpty.startAnimation(anim);

		bindToService();
		initPreferences();
		iniListViewHeader();

		if (!dataInitiated) {
			new MyTask().execute();
		} else {
			fillListView();
		}

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
					long arg3) {
				curKajian = data.get(pos);
				lastSelectPos = selectedPos;
				if (mBound) {
					mPlayer = tPlayer.getMediaPlayer();
					if (mPlayer == null) {
						selectedPos = pos;
						tPlayer.initMediaPlayer(curKajian);
					} else {
						if (selectedPos == pos) {
							tPlayer.stopMediaPlayer();
							lastSelectPos = pos;
						} 
						else {
							if (selectedPos == -1)
								selectedPos = pos; // prevent on rerun error
													// caused by array index out
													// of bound.
							tPlayer.stopMediaPlayer();
							selectedPos = pos;
							tPlayer.initMediaPlayer(curKajian);
						}
					}
				}
				listView.invalidateViews();
			}
		});

		listView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View v,
					int arg2, long arg3) {
				Log.d("Long pressed", "pressed");
				return false;
			}
		});

		return root;
	}

	private void fillListView() {
		if (data != null && data.size() > 0) {
			try {
				adapter = new KajianRSSListAdapter(AudioRssFragment.this, data,
						serviceBerjalan);

				listView.setAdapter(adapter);
				dataInitiated = true;
//				registerForContextMenu(listView);
				int i = 0;
				if (playedOnLastRun) {
					for (Kajian k : data) {
						if (k.isPlayedAtm()) {
							selectedPos = i;
							playedOnLastRun = false;
							break;
						}
						i++;
					}
				}
			} catch (Exception e) {
				dataInitiated = false;
				e.printStackTrace();
				textEmpty.setText("Gagal mengambil data.\n "
						+ "Silahkan tutup dan jalankan kembali aplikasi.");
				textEmpty.setTextColor(Color.parseColor("#FF9999"));
				textEmpty.clearAnimation();

			}
		} else {
			String text;
			if (isInternetConnected()) {
				text = "Gagal mengambil data";
			} else {
				text = "Tidak ada koneksi Internet";
			}
			textEmpty.clearAnimation();
			textEmpty.setText(text);
			textEmpty.clearAnimation();
			textEmpty.setTextColor(Color.parseColor("#DD5500"));
		}

	}

	private boolean isInternetConnected() {
		try {
			ConnectivityManager conMgr = (ConnectivityManager) getActivity()
					.getSystemService(Context.CONNECTIVITY_SERVICE);

			if (conMgr.getNetworkInfo(0).getState() == NetworkInfo.State.CONNECTED
					|| conMgr.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTING) {
				return true;
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	private void iniListViewHeader() {

		TextView tvTitle = (TextView) root.findViewById(R.id.textViewKajianNet);
		TextView tvSubTitle = (TextView) root
				.findViewById(R.id.textViewKajianNetDetail);
		// set gradient
		int color[] = { Color.parseColor("#F98264"),
				Color.parseColor("#ED4015") };
		float position[] = { 0, 1 };
		TileMode md = TileMode.REPEAT;
		LinearGradient lg = new LinearGradient(0, 0, 0, 35, color, position, md);
		Shader grad = lg;
		tvTitle.getPaint().setShader(grad);
		tvSubTitle.getPaint().setShader(grad);

		final LinearLayout l = (LinearLayout) root
				.findViewById(R.id.head_layout);
		ShapeDrawable.ShaderFactory sf = new ShapeDrawable.ShaderFactory() {
			@Override
			public Shader resize(int width, int height) {
				LinearGradient lg = new LinearGradient(0, 0, 0,
						l.getHeight(),
						new int[] {
								Color.parseColor("#00000000"), // atas
								Color.parseColor("#00D4FFD4"), // atas
								Color.parseColor("#22D4FFD4"),
								Color.parseColor("#FF33B5E5"),
								Color.parseColor("#FF33B5E5") }, // bawah
						new float[] { 0, 0.65f, 0.99f, 0.99f, 1 },
						Shader.TileMode.REPEAT);
				return lg;
			}
		};
		PaintDrawable p = new PaintDrawable();
		p.setShape(new RectShape());
		p.setShaderFactory(sf);
		l.setBackgroundDrawable(p.getCurrent());

		try {
			if (getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
				l.setVisibility(View.GONE);
			} else {
				l.setVisibility(View.VISIBLE);
			}
		} catch (Exception e) {
			l.setVisibility(View.VISIBLE);
		}

	}

	private void initPreferences() {
		try {
			preferenceManager = PreferenceManager
					.getDefaultSharedPreferences(getActivity());
			playedOnLastRun = preferenceManager.getBoolean(_KEY_PREF_ON_PLAY,
					false);
		} catch (Exception e) {
		}

	}

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
		}

		@Override
		public void onServiceConnected(ComponentName className,
				IBinder serviceBinder) {
			MediaPlayerBinder binder = (MediaPlayerBinder) serviceBinder;
			tPlayer = binder.getService();
			tPlayer.setClient(AudioRssFragment.this);
			mBound = true;

			// set current ipem played is selected kajian.
		}
	};

	private class MyTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... arg0) {
			try {
				URL rssUrl = new URL(_KAJIAN_NET_RSS);
				SAXParserFactory mySAXParserFactory = SAXParserFactory
						.newInstance();
				SAXParser mySAXParser = mySAXParserFactory.newSAXParser();
				XMLReader myXMLReader = mySAXParser.getXMLReader();
				RssParserHandler myRSSHandler = new RssParserHandler();
				myXMLReader.setContentHandler(myRSSHandler);
				InputSource myInputSource = new InputSource(rssUrl.openStream());
				myXMLReader.parse(myInputSource);

				data = myRSSHandler.getItems();
				// bindToService();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			fillListView();

			super.onPostExecute(result);
		}

	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		switch (v.getId()) {
		case R.id.list:
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			selectedPos = info.position;
			menu.setHeaderTitle(data.get(info.position).getTitle());
			getActivity().getMenuInflater().inflate(
					R.menu.contex_menu_audio_rss, menu);
			Log.d("Context menu", "created");
			break;

		default:
			break;
		}
	}
	

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		Kajian menuSelectedKajian = data.get((int) info.id);
		Log.d("Context menu", "Selected");

		switch (item.getItemId()) {
		case R.id.menu_download:
			Log.d("Context menu", "Selected");
			startDownloadManager(menuSelectedKajian);
			return true;
		default:
			break;
		}
		return super.onContextItemSelected(item);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		Log.d("Option menu", "Selected");
		return super.onOptionsItemSelected(item);
	}

	public void bindToService() {
		Intent intent = new Intent(getActivity(), TeloPlayerService.class);
		serviceBerjalan = teloPlayerRunning();
		if (serviceBerjalan) {
			getActivity().bindService(intent, mConnection,
					Context.BIND_AUTO_CREATE);
		} else {
			getActivity().startService(intent);
			getActivity().bindService(intent, mConnection,
					Context.BIND_AUTO_CREATE);
		}

	}

	public boolean teloPlayerRunning() {
		ActivityManager manager = (ActivityManager) getActivity()
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (TeloPlayerService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	public void shutdownAct() {
		if (mBound) {
			tPlayer.stopMediaPlayer();
			getActivity().unbindService(mConnection);
			mBound = false;
		}

		Intent i = new Intent(getActivity(), TeloPlayerService.class);
		getActivity().stopService(i);
		getActivity().finish();
	}

	@Override
	public void onInitializePlayerStart(String message) {
		data.get(selectedPos).setStatusMessage(message);
		data.get(selectedPos).setPlayedAtm(true);
		listView.invalidateViews();

		if (preferenceManager != null) {
			Editor e = preferenceManager.edit();
			e.putBoolean(_KEY_PREF_ON_RADIO, true);
			e.putBoolean(_KEY_PREF_ON_PLAY, true);
			e.putString(_KEY_PREF_PLAY_URL, curKajian.getLink());
			e.putInt(_KEY_PREF_PLAY_LIST_POS, selectedPos);
			e.commit();
		}
	}

	@Override
	public void onInitializeComplete() {
		data.get(selectedPos).setStatusMessage("Menjalankan");
		data.get(selectedPos).setPlayedAtm(true);
		listView.invalidateViews();
	}

	@Override
	public void onError() {
		data.get(selectedPos).setStatusMessage("Gagal");
		data.get(selectedPos).setPlayedAtm(false);
		listView.invalidateViews();
		selectedPos = -1;
		savePreferences();
	}

	@Override
	public void onCompleted() {
		data.get(selectedPos).setStatusMessage("Selesai.");
		data.get(selectedPos).setPlayedAtm(false);
		listView.invalidateViews();
		selectedPos = -1;
		savePreferences();
	}

	@Override
	public void onStopped(boolean stat) {
		if (lastSelectPos != selectedPos && lastSelectPos != -1) {
			data.get(lastSelectPos).setStatusMessage("Selesai.");
			data.get(lastSelectPos).setPlayedAtm(false);
		} else {
			data.get(selectedPos).setStatusMessage("Berhenti");
			data.get(selectedPos).setPlayedAtm(false);
		}

		listView.invalidateViews();
		selectedPos = -1;
		savePreferences();
	}

	private void savePreferences() {
		if (preferenceManager != null) {
			Editor e = preferenceManager.edit();
			e.clear();
			e.commit();
		}
	}

	/**
	 * DownloadManager Controller
	 * 
	 * @param menuSelectedKajian
	 */

	private void startDownloadManager(Kajian menuSelectedKajian) {
		Log.d("start download", "start download");
		File f = new File(Environment.getExternalStorageDirectory()
				+ "/Ankabut");
		if (!f.exists()) {
			f.mkdir();
		}
		downloadManager = (DownloadManager) getActivity().getSystemService(
				Context.DOWNLOAD_SERVICE);
		Request r = new Request(Uri.parse(menuSelectedKajian.getLink()));
		r.setDestinationInExternalPublicDir("Ankabut",
				menuSelectedKajian.getTitle() + ".mp3");
		r.setAllowedNetworkTypes(Request.NETWORK_WIFI | Request.NETWORK_MOBILE)
				.setTitle("Ankabut")
				.setDescription(menuSelectedKajian.getTitle());
		Toast.makeText(getActivity(), "Mendownload: " + menuSelectedKajian.getTitle(), Toast.LENGTH_LONG).show();

		downloadManager.enqueue(r);
	}

	@Override
	public void onDestroy() {
		try {
			if(!mPlayer.isPlaying()) {
				getActivity().stopService(new Intent(getActivity(), TeloPlayerService.class));
			} else {
				if (mBound)
					getActivity().unbindService(mConnection);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		super.onDestroy();
	}
	
	@Override
	public void onPause() {
		if (mBound)
			getActivity().unbindService(mConnection);
		super.onPause();
	}
	
	 @Override
	public void onResume() {
		super.onResume();
	}

}
