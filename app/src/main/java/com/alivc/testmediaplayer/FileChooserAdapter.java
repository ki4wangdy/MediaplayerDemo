package com.alivc.testmediaplayer;

import java.util.ArrayList;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;


public class FileChooserAdapter extends BaseAdapter {

	private ArrayList<FileInfo> mFileLists;
	private LayoutInflater mLayoutInflater = null;

	private static ArrayList<String> PPT_SUFFIX = new ArrayList<String>();

	static { 
		PPT_SUFFIX.add(".mp4");
		PPT_SUFFIX.add(".mkv");
		PPT_SUFFIX.add(".flv");
        PPT_SUFFIX.add(".wmv");
        PPT_SUFFIX.add(".ts");
        PPT_SUFFIX.add(".rm");
        PPT_SUFFIX.add(".rmvb");
        PPT_SUFFIX.add(".webm");
        PPT_SUFFIX.add(".mov");
        PPT_SUFFIX.add(".vstream");
        PPT_SUFFIX.add(".mpeg");
        PPT_SUFFIX.add(".f4v");
        PPT_SUFFIX.add(".avi");
        PPT_SUFFIX.add(".mkv");
        PPT_SUFFIX.add(".ogv");
        PPT_SUFFIX.add(".dv");
        PPT_SUFFIX.add(".divx");
        PPT_SUFFIX.add(".vob");
        PPT_SUFFIX.add(".asf");
        PPT_SUFFIX.add(".3gp");
        PPT_SUFFIX.add(".h264");
        PPT_SUFFIX.add(".hevc");
        PPT_SUFFIX.add(".h261");
        PPT_SUFFIX.add(".h263");
        PPT_SUFFIX.add(".m3u8");
        PPT_SUFFIX.add(".avs");
        PPT_SUFFIX.add(".swf");
        PPT_SUFFIX.add(".m4v");
        PPT_SUFFIX.add(".mpg");
	}

	public FileChooserAdapter(Context context, ArrayList<FileInfo> fileLists) {
		super();
		mFileLists = fileLists;
		mLayoutInflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return mFileLists.size();
	}

	@Override
	public FileInfo getItem(int position) {
		// TODO Auto-generated method stub
		return mFileLists.get(position);
	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub
		View view = null;
		ViewHolder holder = null;
		if (convertView == null || convertView.getTag() == null) {
			view = mLayoutInflater.inflate(R.layout.filechooser_gridview_item,
					null);
			holder = new ViewHolder(view);
			view.setTag(holder);
		} else {
			view = convertView;
			holder = (ViewHolder) convertView.getTag();
		}

		FileInfo fileInfo = getItem(position);
        //TODO 
		
		holder.tvFileName.setText(fileInfo.getFileName());
		
		if(fileInfo.isDirectory()){      //��欢澶�
			holder.imgFileIcon.setImageResource(R.drawable.ic_folder);
			holder.tvFileName.setTextColor(Color.GRAY);
		}
		else if(fileInfo.isPPTFile()){   //PPT��欢
			holder.imgFileIcon.setImageResource(R.drawable.ic_ppt);
			holder.tvFileName.setTextColor(Color.RED);
		}
		else {                           //�����欢
			holder.imgFileIcon.setImageResource(R.drawable.ic_file_unknown);
			holder.tvFileName.setTextColor(Color.GRAY);
		}
		return view;
	}

	static class ViewHolder {
		ImageView imgFileIcon;
		TextView tvFileName;

		public ViewHolder(View view) {
			imgFileIcon = (ImageView) view.findViewById(R.id.imgFileIcon);
			tvFileName = (TextView) view.findViewById(R.id.tvFileName);
		}
	}

	
	enum FileType {
		FILE , DIRECTORY;
	}

	// =========================
	// Model
	// =========================
	static class FileInfo {
		private FileType fileType;
		private String fileName;
		private String filePath;

		public FileInfo(String filePath, String fileName, boolean isDirectory) {
			this.filePath = filePath;
			this.fileName = fileName;
			fileType = isDirectory ? FileType.DIRECTORY : FileType.FILE;
		}

		public boolean isPPTFile(){
			if(fileName.lastIndexOf(".") < 0)  //Don't have the suffix 
				return false ;
			String fileSuffix = fileName.substring(fileName.lastIndexOf("."));
			if(!isDirectory() && PPT_SUFFIX.contains(fileSuffix))
				return true ;
			else
				return false ;
		}
  
		public boolean isDirectory(){
			if(fileType == FileType.DIRECTORY)
				return true ;
			else
				return false ;
		}
		
		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		@Override
		public String toString() {
			return "FileInfo [fileType=" + fileType + ", fileName=" + fileName
					+ ", filePath=" + filePath + "]";
		}
	}

}
