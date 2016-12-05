
package com.alivc.testmediaplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoListActivity extends Activity {

    private ListView mListView;
    private VideoListAdapter mVideoListAdapter;
    private Context mContext = VideoListActivity.this;
    private AtomicBoolean isFirst = new AtomicBoolean(false);
    private ArrayList<Video> videoList = new ArrayList<Video>();
    private List<Video> mRemoteList = new ArrayList<Video>();

    private final String mRootDir = "/mnt/sdcard/aliyun";
    private String mPrefixTitle = "";

    private static final int REQUEST_CODE = 1;   //request code
    public static final String EXTRA_FILE_CHOOSER = "file_chooser";
    private int mSelectedPosition = -1;
    // private final int MSG_SHOW_SELECTION = 1;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                videoList.addAll(mRemoteList);
                mVideoListAdapter.notifyDataSetChanged();
            }
            if (msg.what == 1) {
                mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                if (mSelectedPosition < 0) mSelectedPosition = 0;
                mListView.setSelection(mSelectedPosition);
                mListView.setItemChecked(mSelectedPosition, true);
//                post(new Runnable() {
//                    public void run() {
//                        mListView.setSelection(-1);
//                        if(mSelectedPosition < 0){
//                            mSelectedPosition = 0;
//                        }
//                        mListView.setSelection(mSelectedPosition);
//                    }
//                });
//
            }
            super.handleMessage(msg);
        }

    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //int cpu_count = Runtime.getRuntime().availableProcessors(); 

        acquireVersion();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_videolist);

        findViews();
        new FilelistRefreshThread().start();

        fileChooserIntent = new Intent(this,
                FileChooserActivity.class);

        // PingExecutor.ex
    }

    public void acquireVersion() {
        try {
            PackageManager pm = getPackageManager();
            //       String pn = getPackageName();
            PackageInfo pinfo = pm.getPackageInfo(getPackageName(), 0);
            String versionName = pinfo.versionName;
            mPrefixTitle = versionName;

        } catch (PackageManager.NameNotFoundException e) {

        }

        return;
    }

    protected void onResume() {
        super.onResume();
        //mListView.setItemsCanFocus(true);
        mListView.setVisibility(View.VISIBLE);

        return;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_CANCELED) {
            //toast(getText(R.string.open_file_none));
            return;
        }
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE) {
            //aquire path
            String videoPath = data.getStringExtra(EXTRA_FILE_CHOOSER);
            //Log.v(TAG, "onActivityResult # pptPath : "+ pptPath );
            if (videoPath != null) {
                Video video = new Video();
                int pos = videoPath.lastIndexOf("/");
                String name = videoPath.substring(pos + 1);
                video.setName(name);
                video.setUri(videoPath);
                startPlayer(video, false);
            }
        }
    }

    private Intent fileChooserIntent;

    private void startPlayer(Video video, boolean hardwareCodec) {

        if (video.getName() == "aquire path") {
            startActivityForResult(fileChooserIntent, REQUEST_CODE);
            return;
        }

        Intent intent = new Intent();
        Bundle bundle = new Bundle();

        bundle.putString("TITLE", video.getName());
        bundle.putString("URI", video.getUri());
        bundle.putInt("decode_type", video.useHwDecoder ? 0 : 1);

        if (video.inLoopPlay()) {
            int selectedIndex = 0;
            Bundle loopBundle = new Bundle();
            bundle.putBundle("loopList", loopBundle);
            int k = 0;

            for (Video v : videoList) {
                if (v.inLoopPlay()) {
                    loopBundle.putString("TITLE" + k, v.getName());
                    loopBundle.putString("URI" + k, v.getUri());

                    if (v.getUri() == video.getUri()) {
                        selectedIndex = k;
                    }

                    k++;
                }
            }
            loopBundle.putInt("ItemCount", k);

            loopBundle.putInt("SelectedIndex", selectedIndex);
        }

        intent.putExtras(bundle);
        intent.setClass(mContext, PlayerActivity.class);
        startActivity(intent);
        mListView.setVisibility(View.INVISIBLE);
    }

    private View mLastSeletedItemView = null;

    private void decorateItem(View view, int position) {
        if (mLastSeletedItemView != null) {
            TextView pre = (TextView) mLastSeletedItemView.findViewById(R.id.video_title);
            pre.setTextColor(Color.BLACK);
            mLastSeletedItemView.setBackgroundColor(Color.WHITE);
        }
        TextView t = (TextView) view.findViewById(R.id.video_title);
        t.setTextColor(Color.WHITE);
        view.setBackgroundColor(Color.BLUE);


        //   t.setBackgroundColor(Color.DKGRAY);
        mLastSeletedItemView = view;
    }

    private void updateTitle(int selected, int total_item_count) {
        TextView titleView = (TextView) findViewById(R.id.listViewTitle);
        titleView.setText("videolist [ " + selected + "/" + total_item_count + " ] - (v" + mPrefixTitle + ")");

    }

    private void findViews() {
        mVideoListAdapter = new VideoListAdapter(videoList, mContext);

        mListView = (ListView) findViewById(R.id.fileListView);
        mListView.setAdapter(mVideoListAdapter);
        mListView.setOnItemClickListener(itemClickListener);


        // mListView.setOn

        mListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                mSelectedPosition = position;
                decorateItem(view, position);

                updateTitle(position + 1, videoList.size());
                //Toast.makeText(VideoListActivity.this, "item :" + t.getText(), Toast.LENGTH_SHORT).show();
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                if (videoList.size() > 0) {
                    updateTitle(0, videoList.size());
                } else {
                    updateTitle(0, 0);
                }
                mSelectedPosition = -1;
            }
        });
    }

    OnItemClickListener itemClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> arg0, View view,
                                int position, long arg3) {
            if (position > 0) {
                Video v = videoList.get(position - 1);
                CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkBox1);
                boolean isChecked = checkBox.isChecked();
                startPlayer(v, isChecked);
            } else {
                // TODO show dialog
                dialog();
            }
            return;
        }
    };

    protected void dialog() {
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(VideoListActivity.this);
        builder.setIcon(android.R.drawable.ic_dialog_info).setView(inputServer);
        builder.setMessage("确认继续播放吗？");

        builder.setTitle("提示");

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent();
                Bundle bundle = new Bundle();

                bundle.putString("TITLE", "自定义视频");
                bundle.putString("URI", inputServer.getText().toString());
                bundle.putInt("decode_type", 1);

                intent.putExtras(bundle);
                intent.setClass(mContext, PlayerActivity.class);
                startActivity(intent);
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.create().show();
    }

    private List<Video> getLocationVideoList(String rootPath) {
        List<Video> videoList = new ArrayList<Video>();

        String videoRootPath = mRootDir;
        File dir = new File(videoRootPath);
        if (!dir.isDirectory()) {
            return videoList;
        }

        File[] files = dir.listFiles();
        if (null == files) {
            return videoList;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            } else {
                String fullPath = file.getAbsolutePath().toLowerCase();
                String prefix = getExtension(fullPath);
                if (getVideoFilter().contains(prefix)) {
                    File f = new File(fullPath);
                    String fileName = f.getName();
                    long size = f.length();
                    Video video = new Video(fileName, fullPath, "",
                            (int) size, Boolean.TRUE);
                    videoList.add(video);
                }
            }
        }

        return videoList;
    }

    private ArrayList<Video> getRemoteVideoList() {

        ArrayList<Video> videoList = new ArrayList<Video>();
//
//        Video firstVideo = new Video();
//        firstVideo.setName("播放自定义视频");
//        firstVideo.setLocation(Boolean.FALSE);
//        videoList.add(firstVideo);

        String listFile = mRootDir + File.separator + "videoList.txt";
        String SPACE_CHAR = "\\s+";

        File file = new File(listFile);
        if (!file.exists()) {
            return videoList;
        }

        InputStreamReader reader;
        Closeable resource = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(reader);
            resource = bufferedReader;
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                String[] content = line.split(SPACE_CHAR);
                Log.d("VideoList", "Line = " + line);
                Log.d("VideoList", "content length = " + content.length);
                if (content != null && content.length >= 4) {
                    String title = content[0];
                    String id = content[1];
                    String definition = content[2];
                    String url = content[3];

                    Video video = new Video();
                    if (content.length > 4) {
                        String hw = content[4];
                        int t = Integer.parseInt(hw);
                        if (t == 1) {
                            video.setUseHwDecoder(false);
//                            video.setInLoopPlay(true);
                        }
                    }

                    video.setName(title);
                    video.setVideoId(Long.valueOf(id));
                    video.setDefinition(definition);
                    video.setUri(url);
                    video.setLocation(Boolean.FALSE);
                    videoList.add(video);
                }
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                resource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return videoList;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    class FilelistRefreshThread extends Thread {

        public void run() {
            if (isFirst.get()) {
                return;
            }

            init();
            mRemoteList = getRemoteVideoList();
            List<Video> localList = getLocationVideoList(mRootDir);
            mRemoteList.addAll(localList);
            mHandler.sendMessage(mHandler.obtainMessage(0));

            mHandler.sendMessageDelayed(mHandler.obtainMessage(1), 100);

            isFirst.set(Boolean.TRUE);

        }

        // TODO 为方便测试不同的手机平台的下对h264 ＋ aac的硬解的支持率添加,之后需要删除
        private void init() {
            File rootPath = new File(mRootDir);
            if (!rootPath.exists()) {
                rootPath.mkdir();
            }

            File videoListFile = new File(mRootDir, "videolist.txt");
            if (!videoListFile.exists()) {
                try {
                    FileWriter fileWriter = new FileWriter(videoListFile);
                    fileWriter.write("rtmp[标清] 3 hd rtmp://tan.cdnpe.com/app-test/video-test_sd");
                    fileWriter.flush();
                    fileWriter.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    class VideoListAdapter extends BaseAdapter {

        private Context mContext;
        ArrayList<Video> mDataFilelist;
        private TextView mTitle;
        private TextView mIsLocation;

        public VideoListAdapter(ArrayList<Video> videoList, Context context) {
            mDataFilelist = videoList;
            mContext = context;
        }

        public int getCount() {
            return mDataFilelist.size() + 1;
        }

        public Object getItem(int position) {
            return mDataFilelist.get(position - 1);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.videoitem, null);
                viewHolder = new ViewHolder();
                viewHolder.checkBox = (CheckBox) convertView.findViewById(R.id.checkBox1);
                viewHolder.titleTV = (TextView) convertView.findViewById(R.id.video_title);
                viewHolder.isLocationTV = (TextView) convertView.findViewById(R.id.video_source);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            mTitle = viewHolder.titleTV;
            mIsLocation = viewHolder.isLocationTV;
            CheckBox checkBox = viewHolder.checkBox;

            if (position > 0) {
                Video v = mDataFilelist.get(position - 1);

                checkBox.setChecked(v.isUseHwDecoder());

                boolean inLoop = v.inLoopPlay();
                String sInloop = inLoop ? "looplist" : "";

                if (v.isLocation()) {
                    mTitle.setText(v.getName());

                    mIsLocation.setText("local" + sInloop);
                } else {
                    mTitle.setText(v.getName() + "_" + v.getVideoId() + "_" + v.getDefinition());
                    mIsLocation.setText("network" + sInloop);
//                checkBox.setEnabled(false);
                }

            } else {
                mTitle.setText("自定义网络视频");
                mIsLocation.setText("");

            }
            return convertView;
        }

    }

    static class ViewHolder {
        public CheckBox checkBox;
        public TextView titleTV;
        public TextView isLocationTV;
    }


    ////// util function
    private static String getExtension(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    ///////util function
    private static HashSet<String> getVideoFilter() {
        HashSet<String> fileFilter = new HashSet<String>();
        fileFilter.add("mp4");
        fileFilter.add("mkv");
        fileFilter.add("flv");
        fileFilter.add("wmv");
        fileFilter.add("ts");
        fileFilter.add("rm");
        fileFilter.add("rmvb");
        fileFilter.add("webm");
        fileFilter.add("mov");
        fileFilter.add("vstream");
        fileFilter.add("mpeg");
        fileFilter.add("f4v");
        fileFilter.add("avi");
        fileFilter.add("mkv");
        fileFilter.add("ogv");
        fileFilter.add("dv");
        fileFilter.add("divx");
        fileFilter.add("vob");
        fileFilter.add("asf");
        fileFilter.add("3gp");
        fileFilter.add("h264");
        fileFilter.add("hevc");
        fileFilter.add("h261");
        fileFilter.add("h263");
        fileFilter.add("m3u8");
        fileFilter.add("avs");
        fileFilter.add("swf");
        fileFilter.add("m4v");
        fileFilter.add("mpg");
        return fileFilter;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //Video element
    public class Video {
        /**
         * video ID
         */
        private long videoId;
        /**
         * video name
         */
        private String name;
        /**
         * video address
         */
        private String uri;
        /**
         * video scale picture
         */
        private String pic;
        /**
         * video file
         */
        private int size;
        /**
         * video format
         */
        private String format;
        /**
         * video duration
         */
        private int druration;
        /**
         * video definition
         */
        private String definition;
        /**
         * video location
         */
        private boolean isLocation;

        private boolean useHwDecoder = true;

        private boolean inLoopPlay = false;

        public Video() {

        }

        public Video(String name, String uri, String pic, int size, boolean isLocation) {
            this.name = name;
            this.uri = uri;
            this.pic = pic;
            this.size = size;
            this.isLocation = isLocation;
        }

        public void setInLoopPlay(boolean b) {
            this.inLoopPlay = b;
        }

        ;

        public boolean inLoopPlay() {
            return this.inLoopPlay;
        }

        ;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPic() {
            return pic;
        }

        public void setPic(String pic) {
            this.pic = pic;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public int getDruration() {
            return druration;
        }

        public void setDruration(int druration) {
            this.druration = druration;
        }

        public boolean isLocation() {
            return isLocation;
        }

        public void setLocation(boolean isLocation) {
            this.isLocation = isLocation;
        }

        public long getVideoId() {
            return videoId;
        }

        public void setVideoId(long videoId) {
            this.videoId = videoId;
        }

        public String getDefinition() {
            return definition;
        }

        public void setDefinition(String definition) {
            this.definition = definition;
        }

        public boolean isUseHwDecoder() {
            return useHwDecoder;
        }

        public void setUseHwDecoder(boolean useHwDecoder) {
            this.useHwDecoder = useHwDecoder;
        }
    }
}
