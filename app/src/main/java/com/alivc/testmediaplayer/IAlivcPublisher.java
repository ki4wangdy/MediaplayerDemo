package com.alivc.testmediaplayer;

import android.view.Surface;

import java.util.Map;

/**
 * 阿里云推流模块接口
 */
public interface IAlivcPublisher {

    /**
     * 初始化推流器
     *
     * @param params 初始化参数
     * @return 错误码
     */
    public int init(Map<String, String> params);

    /**
     * 准备推流器
     *
     * @param surface   Surface
     * @param extParams 扩展参数
     * @return 错误码
     */
    public int prepare(Surface surface, Map<String, String> extParams);

    /**
     * 启动推流器
     *
     * @param url 推流地址
     * @return
     */
    public int start(String url);

    /**
     * 停止推流器
     *
     * @return 错误码
     */
    public int stop();

    /**
     * 销毁推流器
     *
     * @return 错误码
     */
    public int destroy();
}
