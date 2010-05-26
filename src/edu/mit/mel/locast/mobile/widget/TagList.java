package edu.mit.mel.locast.mobile.widget;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.mit.mel.locast.mobile.MelAndroid;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

/**
 * A user-modifiable set of free tags. The user can add a new tag to a set of available tags
 * or choose from a set of recommended tags.
 * 
 * @author stevep
 *
 */
public class TagList extends TagListView implements OnEditorActionListener, OnClickListener {
	ViewGroup recommendedTagView;
	TextView recommendedTagLabel;
	
	List<String> recommendedTags = new Vector<String>();
	List<String> shownRecs = new Vector<String>();
	
	AndroidNetworkClient nc;
	
	final static long cacheAge = 60 * 1000;
	//static Map<String, Integer> autocomplete = new HashMap<String, Integer>();
	static List<String> acList;
	static Date lastUpdated;
	
	private static int STYLE_FULL = 1,
					STYLE_SELECTOR = 2;
	private int style = STYLE_FULL;
	
	static ArrayAdapter<String> acAdapter;
	
	public TagList(Context context) {
		this(context, null);
	}
	
    public TagList(Context context, AttributeSet attrs){
    	super(context, attrs);
    	
		recommendedTagView = (ViewGroup)findViewById(R.id.tag_recommended_tags);
		recommendedTagLabel = (TextView)findViewById(R.id.tag_recommended_label);
		
		((ImageButton)findViewById(R.id.tag_add_button)).setOnClickListener(this);
		this.nc = AndroidNetworkClient.getInstance(context);

		if (lastUpdated == null || (new Date().getTime() - lastUpdated.getTime() > cacheAge)){
			new TagLoaderTask().execute(new Object());	
		}
		setOnTagClickListener(this);
		acAdapter = new ArrayAdapter<String>(context, R.id.tag_add_text);
		((EditText)findViewById(R.id.tag_add_text)).setOnEditorActionListener(this);

    }

    @Override
    protected void inflateLayout(Context context, AttributeSet attrs) {
    	LayoutInflater.from(context).inflate(R.layout.taglist, this);
		if ("selector".equals(attrs.getAttributeValue(MelAndroid.NS, "taglist_style"))){
			style = STYLE_SELECTOR;
		}
    	if (style == STYLE_SELECTOR){
    		findViewById(R.id.tag_manual_entry).setVisibility(View.GONE);
    		findViewById(R.id.tag_recommended_label).setVisibility(View.GONE);
    		
    	}
    }
    
    @Override
    public boolean addTag(String tag) {
    	if (! super.addTag(tag)) {
			return false;
		}
    	
		if (recommendedTags.contains(tag)){
			recommendedTagView.removeViewAt(shownRecs.indexOf(tag));
			shownRecs.remove(tag);
			if (shownRecs.size() == 0){
				recommendedTagLabel.setVisibility(TextView.GONE);
			}
		}
		return true;
    }
    
    @Override
    public boolean removeTag(String tag) {
    	if(! super.removeTag(tag)) {
			return false;
		}
    		
		if (recommendedTags.contains(tag) && !shownRecs.contains(tag)){
			shownRecs.add(tag);
			Collections.sort(shownRecs);
			recommendedTagLabel.setVisibility(TextView.VISIBLE);
			recommendedTagView.addView(getTagView(tag, false), shownRecs.indexOf(tag));
		}

    	return true;
    }
    
    /**
	 * A list of tags that are recommended for the given item.
	 * 
	 * @param tag
	 */
	public void addRecommendedTag(String tag){
		if (tag.length() == 0) {
			throw new IllegalArgumentException("cannot add empty tag");
		}
		if (! recommendedTags.contains(tag)){
			recommendedTags.add(tag);	
			
			if (! getTags().contains(tag)){
				shownRecs.add(tag);
				Collections.sort(shownRecs);
				recommendedTagLabel.setVisibility(TextView.VISIBLE);
				recommendedTagView.addView(getTagView(tag, false), shownRecs.indexOf(tag));
			}
		}
	}
	
	public void addedRecommendedTags(List <String> tags){
		for (final String tag: tags){
			addRecommendedTag(tag);
		}
	}
	
	@Override
	public void clearAllTags(){
		super.clearAllTags();
		clearRecommendedTags();
	}
	
	public void clearRecommendedTags(){
		recommendedTags.clear();
		shownRecs.clear();
		recommendedTagView.removeAllViews();
		recommendedTagLabel.setVisibility(TextView.GONE);
	}
	
	@Override
	TagButton getTagView(String tag, boolean added){
		final TagButton b = new TagButton(getContext(), tag, added, true);
		b.setOnClickListener(this);
		return b;
	}

	/**
	 * Listener to handle the act of clicking on a tag button.
	 */
	public void onClick(View v) {
		boolean changed = false;
		
		switch (v.getId()){
		case R.id.tag_add_button:
			changed = addEditTextTag();
			break;
			
		default:
			if (v instanceof TagButton){
				final TagButton tagButton = (TagButton)v;
				if (tagButton.isAdded()){
					changed = removeTag((String)tagButton.getTag());
				}else{
					changed = addTag((String)tagButton.getTag());
				}
			}
		}
		
		if (changed && listener != null){
			listener.onTagListChange(this);
		}
	}
	
	private boolean addEditTextTag(){
		boolean changed = false;
		final EditText t = (EditText) findViewById(R.id.tag_add_text);
		String tag = t.getText().toString();
		tag = tag.trim();
		tag = tag.toLowerCase();
		
		if (tag.length() > 0){
			changed = addTag(tag);
			t.setText("");
		}
		return changed;
	}
	
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		switch (v.getId()){
		case R.id.tag_add_text:
			if (addEditTextTag() && listener != null){
				listener.onTagListChange(this);
			}
			return true;
			
		}
		return false;
	}
	
	public class TagLoaderTask extends AsyncTask<Object, Integer, Object>{

		@Override
		protected Object doInBackground(Object... params) {
			try {
				acList = nc.getTagsList();
				lastUpdated = new Date();
			} catch (final Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Object result) {
			super.onPostExecute(result);
			acAdapter = new ArrayAdapter<String>(getContext(),android.R.layout.simple_dropdown_item_1line , acList);
			((AutoCompleteTextView)findViewById(R.id.tag_add_text)).setAdapter(acAdapter);
		}
	}
	

	static class SavedState extends BaseSavedState{
		private final List<String> addedTags;
		private final List<String> recTags;
		
		SavedState(Parcelable superState, List<String>addedTags, List<String>recTags) {
			super(superState);
			this.addedTags = addedTags;
			this.recTags = recTags;
		}
		
		@SuppressWarnings("unchecked")
		private SavedState(Parcel in) {
			super(in);
			addedTags = in.readArrayList(String.class.getClassLoader());
			recTags = in.readArrayList(String.class.getClassLoader());
		}
		
		public List<String> getAddedTags() {
			return addedTags;
		}
		
		public List<String> getRecTags() {
			return recTags;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeList(addedTags);
			dest.writeList(recTags);
		}

		public static final Parcelable.Creator<SavedState> CREATOR
		= new Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		
		return new SavedState(superState, getTags(), recommendedTags);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		final SavedState ss = (SavedState)state;
		super.onRestoreInstanceState(ss.getSuperState());
		clearAllTags();
		addTags(ss.getAddedTags());
		addedRecommendedTags(ss.getRecTags());
	}
	
	private OnTagListChangeListener listener = null;
	public void setOnTagListChangeListener(OnTagListChangeListener listener){
		this.listener = listener;
	}
	
	public interface OnTagListChangeListener {
		public void onTagListChange(TagList v);
	}
}