package com.alivc.testmediaplayer;

import android.app.Application;
import android.widget.Toast;

import com.alivc.player.AccessKey;
import com.alivc.player.AccessKeyCallback;
import com.alivc.player.AliVcMediaPlayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VideoApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();

        // 检查/mnt/sdcard/TAOBAOPLAYER 是否存在,不存在创建
        File rootPath = new File("/mnt/sdcard/aliyun");
        if (!rootPath.exists()) {
            rootPath.mkdir();
        }

        // videolist.txt是否存在,不存在复制
        File videolistFile = new File(rootPath, "videolist.txt");
        if (!videolistFile.exists()) {
            try {
                copyAssetsToSD("videolist.txt", videolistFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // accesstoken.txt 是否存在,不存在复制
        File assessKeyFile = new File(rootPath, "accesstoken.txt");
        if (!assessKeyFile.exists()) {
            try {
                copyAssetsToSD("accesstoken.txt", assessKeyFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        File file = new File("/mnt/sdcard/aliyun/accesstoken.txt");
        if (file.exists()) {
            try {
                BufferedReader fileReader = new BufferedReader(new FileReader(file));
                final String accessKeyId = fileReader.readLine();
                final String accessKeySecret = fileReader.readLine();
                final String businessId = "video_live";

                AliVcMediaPlayer.init(getApplicationContext(), businessId, new AccessKeyCallback() {
                    public AccessKey getAccessToken() {
                        return new AccessKey(accessKeyId, accessKeySecret);
                    }
                });

            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            Toast.makeText(getApplicationContext(), "accesstoken.txt not exists.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyAssetsToSD(String assetsFile, String sdFile) throws IOException {
        InputStream myInput;
        OutputStream myOutput = new FileOutputStream(sdFile);
        myInput = this.getAssets().open(assetsFile);
        byte[] buffer = new byte[1024];
        int length = myInput.read(buffer);
        while (length > 0) {
            myOutput.write(buffer, 0, length);
            length = myInput.read(buffer);
        }

        myOutput.flush();
        myInput.close();
        myOutput.close();
    }
}
