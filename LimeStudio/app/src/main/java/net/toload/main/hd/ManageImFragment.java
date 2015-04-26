package net.toload.main.hd;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.ToggleButton;

import net.toload.main.hd.data.Word;
import net.toload.main.hd.ui.ManageImAdapter;
import net.toload.main.hd.ui.ManageImHandler;
import net.toload.main.hd.ui.ManageImRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */

/**
 * A placeholder fragment containing a simple view.
 */
public class ManageImFragment extends Fragment {


    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String ARG_SECTION_CODE = "section_code";


    private GridView gridManageIm;

    private ToggleButton toggleManageIm;

    private Button btnManageImAdd;
    private Button btnManageImKeyboard;
    private Button btnManageImSearch;
    private Button btnManageImPrevious;
    private Button btnManageImNext;

    private EditText edtManageImSearch;
    private TextView txtNavigationInfo;

    private List<Word> wordlist;

    private int page = 0;
    private boolean searchroot = true;

    private String prequery = "";

    private String code;
    private Activity activity;
    private ManageImHandler handler;
    private ManageImAdapter adapter;

    private Thread manageimthread;

    private ProgressDialog progress;

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static ManageImFragment newInstance(int sectionNumber, String code) {
        ManageImFragment fragment = new ManageImFragment();
        Bundle args = new Bundle();
                args.putInt(ARG_SECTION_NUMBER, sectionNumber);
                args.putString(ARG_SECTION_CODE, code);
        fragment.setArguments(args);
        return fragment;
    }

    public ManageImFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_manage_im, container, false);

        this.handler = new ManageImHandler(this);
        this.activity = this.getActivity();

        this.progress = new ProgressDialog(this.activity);
        this.progress.setCancelable(false);
        this.progress.setMessage(getResources().getString(R.string.manage_im_loading));

        this.gridManageIm = (GridView) root.findViewById(R.id.gridManageIm);
        this.btnManageImAdd = (Button) root.findViewById(R.id.btnManageImAdd);
        this.btnManageImKeyboard = (Button) root.findViewById(R.id.btnManageImKeyboard);

        this.toggleManageIm = (ToggleButton) root.findViewById(R.id.toggleManageIm);
        this.toggleManageIm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked){
                    searchroot = false;
                }else{
                    searchroot = true;
                }
                edtManageImSearch.setText("");
            }
        });

        this.btnManageImNext = (Button) root.findViewById(R.id.btnManageImNext);
        this.btnManageImNext.setEnabled(false);
        this.btnManageImNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page+1);
                if(checkrecord < wordlist.size()){
                    page++;
                }
                updateGridView(wordlist);
            }
        });
        this.btnManageImPrevious = (Button) root.findViewById(R.id.btnManageImPrevious);
        this.btnManageImPrevious.setEnabled(false);
        this.btnManageImPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(page > 0){
                    page--;
                }
                updateGridView(wordlist);
            }
        });

        this.edtManageImSearch = (EditText) root.findViewById(R.id.edtManageImSearch);

        this.btnManageImSearch = (Button) root.findViewById(R.id.btnManageImSearch);
        this.btnManageImSearch.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                String query = edtManageImSearch.getText().toString();
                if(query != null && query.length() > 0){
                    query = query.trim();
                    manageimthread = new Thread(new ManageImRunnable(handler, activity, code, query, searchroot));
                    manageimthread.start();
                }
            }
        });

        this.txtNavigationInfo = (TextView) root.findViewById(R.id.txtNavigationInfo);

        this.manageimthread = new Thread(new ManageImRunnable(this.handler, this.activity, this.code, null, searchroot));
        this.manageimthread.start();

        /*
        private EditText edtManageImSearch;
        private TextView txtNavigationInfo;
        */
        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));

        this.code = getArguments().getString(ARG_SECTION_CODE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(this.manageimthread != null){
            this.handler.removeCallbacks(manageimthread);
        }
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
    }

    public void showProgress(){
        if(!this.progress.isShowing()){
            this.progress.show();
        }
    }

    public void cancelProgress(){
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
    }

    public void updateGridView(List<Word> wordlist){

        this.wordlist = wordlist;
        List<Word> templist = new ArrayList<Word>();

        int startrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);

        if(page > 0){
            this.btnManageImPrevious.setEnabled(true);
        }else{
            this.btnManageImPrevious.setEnabled(false);
        }

        if(endrecord <= this.wordlist.size()){
            this.btnManageImNext.setEnabled(true);
        }else{
            this.btnManageImNext.setEnabled(false);
        }

        if(this.wordlist.size() > 0){

            for(int i = startrecord; i < endrecord ; i++){
                if(i >= this.wordlist.size()){
                    endrecord = this.wordlist.size();
                    break;
                }
                Word w = this.wordlist.get(i);
                templist.add(w);
            }
        }

        if(this.adapter == null){
            this.adapter = new ManageImAdapter(this.activity, templist);
            this.gridManageIm.setAdapter(this.adapter);
        }else{
            this.adapter.setList(templist);
            this.adapter.notifyDataSetChanged();
        }

        String nav = "0";

        if(this.wordlist.size() > 0){
            nav = (startrecord+1) + "-" + endrecord;
            nav += " of " + this.wordlist.size();
        }

        this.txtNavigationInfo.setText(nav);
        cancelProgress();

    }
}