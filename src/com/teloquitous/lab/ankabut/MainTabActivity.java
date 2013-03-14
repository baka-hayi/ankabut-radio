package com.teloquitous.lab.ankabut;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import com.teloquitous.lab.ankabut.fragment.ArtikelRssFragment;
import com.teloquitous.lab.ankabut.fragment.AudioRssFragment;
import com.teloquitous.lab.ankabut.fragment.RadioListFragment;
import com.teloquitous.lab.ankabut.fragment.TabPageIndicator;


public class MainTabActivity extends FragmentActivity implements AnkabutKeyStrings {
	private static final String[] CONTENT = new String[] { "Radio", "Kajian",  "Artikel" }; //"Mp3Qur'an",
	private int tab = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_tab);
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
		if(!p.getBoolean(_KEY_PREF_ON_RADIO, true)) {
			tab = 1;
		} else {
			tab = 0;
		}
		
		
		FragmentPagerAdapter adapter = new MyTabFragmentAdapter(getApplicationContext(), getSupportFragmentManager());
		ViewPager pager = (ViewPager)findViewById(R.id.pager);
        pager.setAdapter(adapter);
        
        TabPageIndicator indicator = (TabPageIndicator)findViewById(R.id.indicator);
        indicator.setViewPager(pager);
        indicator.setCurrentItem(tab);
        
        
	}

	
	class MyTabFragmentAdapter extends FragmentPagerAdapter {
		Context _context;

		public MyTabFragmentAdapter(Context c, FragmentManager fm) {
			super(fm);
			_context = c;
		}

		@Override
		public Fragment getItem(int pos) {
			Fragment f = new Fragment();
			switch (pos) {
			case 0:
				f = RadioListFragment.newInstance(_context);
				break;
			case 1:
				f = AudioRssFragment.newInstance(_context);
				break;
			case 2: 
				f = ArtikelRssFragment.newInstance(_context);
				break;
//			case 3: 
//				f = LayoutTwo.newInstance(_context);
//				break;
			}
			return f;
		}
		
		@Override
		public CharSequence getPageTitle(int position) {
			 return CONTENT[position % CONTENT.length];
		}

		@Override
		public int getCount() {
			return CONTENT.length;
		}
		
	}

}
