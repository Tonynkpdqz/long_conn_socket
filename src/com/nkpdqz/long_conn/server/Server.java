package com.nkpdqz.long_conn.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    /**
     * server端处理客户端发来的对象，并返回一个对象
     */
    public interface ObjectAction{
        Object doAction(Object o,Server server);
    }

    public static final class DefaultObjectAction implements ObjectAction{

        @Override
        public Object doAction(Object o, Server server) {
            System.out.println("echo");
            return o;
        }
    }

    public static void main(String[] args) {
        int port = 65432;
        Server server = new Server(port);
        server.start();
    }

    private void start() {
        if (running)return;
        running = true;
        new Thread(new ConnWatchDao()).start();
    }

    public Server(int port) {
        this.port = port;
    }

    public Server() {
    }

    private int port;
    private volatile boolean running = false;
    private long receiveTimeDelay = 3000;
    private ConcurrentHashMap<Class,ObjectAction> actionMapping = new ConcurrentHashMap<>();
    private Thread connWatchDog;

    @SuppressWarnings("deprecation")
    public void stop(){
        if (running)
            running = false;
        if (connWatchDog!=null)
            connWatchDog.stop();
    }

    public void addActionMap(Class<Object> clz,ObjectAction action){
        actionMapping.put(clz,action);
    }

    class ConnWatchDao implements Runnable {
        @Override
        public void run() {
            try {
                ServerSocket ss = new ServerSocket(port,5);
                while (running){
                    Socket s = ss.accept();
                    new Thread(new SocketAction(s)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Server.this.stop();
            }
        }
    }

    class SocketAction implements Runnable {

        private Socket socket;
        boolean run = true;
        long lastReceiveTime = System.currentTimeMillis();
        public SocketAction(Socket s) {
            socket = s;
        }

        @Override
        public void run() {
            while (run && running){
                if (System.currentTimeMillis() - lastReceiveTime>receiveTimeDelay){
                    overThis();
                }else {
                    try {
                        InputStream inputStream = socket.getInputStream();
                        if (inputStream.available()>0){
                            ObjectInputStream ois = new ObjectInputStream(inputStream);
                            Object o = ois.readObject();
                            lastReceiveTime = System.currentTimeMillis();
                            System.out.println("接收到："+o);
                            ObjectAction objectAction = actionMapping.get(o);
                            objectAction = objectAction==null?new DefaultObjectAction():objectAction;
                            Object response = objectAction.doAction(o, Server.this);
                            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                            oos.writeObject(response);
                            oos.flush();
                        }else {
                            Thread.sleep(10);
                        }
                    } catch (IOException | ClassNotFoundException | InterruptedException e) {
                        e.printStackTrace();
                        overThis();
                    }
                }
            }
        }

        private void overThis() {
            if (run)run=false;
            if (socket!=null){
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
