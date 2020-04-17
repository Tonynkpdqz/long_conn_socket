package com.nkpdqz.long_conn;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
/*
 * 维持连接的消息对象（心跳包）
 */
public class KeepAlive implements Serializable {

    private static final long serialVersionUID = -2813120366138988481L;

    @Override
    public String toString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "维持连接包";
    }
}
