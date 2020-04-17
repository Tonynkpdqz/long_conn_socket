package com.nkpdqz.long_conn.client;

import com.nkpdqz.long_conn.KeepAlive;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Client {

    /**
     * 处理服务端发回的对象，可实现这个接口
     */
    public static interface ObjectAction{
        void doAction(Object obj,Client client);
    }

    public static final class DefaultObjectAction implements ObjectAction{

        @Override
        public void doAction(Object obj, Client client) {
            System.out.println("处理： "+obj.toString());
        }
    }

    public static void main(String[] args) throws IOException {
        String serverIp = "127.0.0.1";
        int port = 65432;
        Client client = new Client(serverIp,port);
        client.start();
    }

    private String serverIp;
    private int port;
    private Socket socket;
    private boolean running = false;    //连接状态
    private long lastSendTime;    //最后一次发送数据的时间
    //保存接收消息对象类型和该消息类型处理的对象。
    private ConcurrentHashMap<Class,ObjectAction> actionMapping = new ConcurrentHashMap<>();

    public Client() {
    }

    public Client(String serverIp, int port) {
        this.serverIp = serverIp;
        this.port = port;
    }

    private void start() throws IOException {
        if (running) return;
        socket = new Socket(serverIp,port);
        System.out.println("本地端口："+socket.getLocalPort());
        lastSendTime = System.currentTimeMillis();
        running = true;
        new Thread(new KeepAliveWatchDog()).start();    //保持长连接的线程,每隔2s发送一个心跳包
        new Thread(new ReceiveWatchDog()).start();    //处理消息的线程
    }

    public void addActionMap(Class<Object> clazz,ObjectAction action){
        actionMapping.put(clazz,action);
    }

    public void sendObject(Object o) throws IOException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectOutputStream.writeObject(o);
        objectOutputStream.flush();
        System.out.println("发送:"+ objectOutputStream.toString());
        objectOutputStream.close();
    }

    public void stop(){
        if (running)
            running = false;
    }

    class KeepAliveWatchDog implements Runnable {

        long checkDelay = 10;
        long keepAliveDelay = 2000;

        /**
         * 若间隔过两秒，则发送一个心跳包，若不到两秒，则每隔checkDelay时间检查一次
         */
        @Override
        public void run() {
            while (running){
                if (System.currentTimeMillis() - lastSendTime<keepAliveDelay){
                    try {
                        Client.this.sendObject(new KeepAlive());
                    } catch (IOException e) {
                        e.printStackTrace();
                        Client.this.stop();
                    }
                }else {
                    try {
                        Thread.sleep(checkDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Client.this.stop();
                    }
                }
            }
        }
    }

    class ReceiveWatchDog implements Runnable {
        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                if (in.available()>0) {
                    ObjectInputStream objectInputStream = new ObjectInputStream(in);
                    Object o = objectInputStream.readObject();
                    System.out.println("接受："+o);
                    ObjectAction oa = actionMapping.get(o.getClass());
                    oa = oa==null?new DefaultObjectAction():oa;
                    oa.doAction(o,Client.this);
                }else {
                    Thread.sleep(10);
                }
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
                Client.this.stop();
            }
        }
    }
}
