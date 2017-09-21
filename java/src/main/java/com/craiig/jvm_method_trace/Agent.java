package com.craiig.jvm_method_trace;

public class Agent
{
    public static native boolean start();

    public static native void stop();

    public static native boolean isRunning();

    public static native String getFilePath();

    public static native void setFilePath(String filePath);
}
