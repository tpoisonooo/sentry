package com.p4f.esp32camai;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.makeramen.roundedimageview.RoundedImageView;
import com.zerokol.views.joystickView.JoystickView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class Esp32CameraFragment extends Fragment{
    final String TAG = "ExCameraFragment";

    private UDPSocket mUdpClient;
    private String mServerAddressBroadCast = "255.255.255.255";
    InetAddress mServerAddr;
    int mServerPort = 6868;
    boolean mAuto = false;

    // start preview camera
    final byte[] mRequestConnect      = new byte[]{'w','h','o','a','m','i'};
    final byte[] mFire = new byte[]{'f','i','r','e'};
    final byte[] mLaserOn = new byte[]{'l','a','z','e', 'r', 'o', 'n'};
    final byte[] mLaserOff = new byte[]{'l','a','z','e', 'r', 'o', 'f', 'f'};

    ImageView mServerImageView;
    TextView tvDetect;
    TextView stick_state;

    private WebSocketClient mWebSocketClient;
    private String mServerExactAddress;
    private boolean mStream = false;
    private boolean mLaser = false;

    private NanoDetNcnn nanodetncnn = new NanoDetNcnn();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // init ncnn handle
        int current_cpugpu = 0;
        boolean ret_init = nanodetncnn.loadModel(getActivity().getAssets(), current_cpugpu);
        if (!ret_init)
        {
            Log.e(Esp32CameraFragment.class.getName(), "nanodetncnn loadModel failed");
        }

        // init UDP
        mUdpClient = new UDPSocket(12345);
        mUdpClient.runUdpServer();

        try {
            mServerAddr = InetAddress.getByName(mServerAddressBroadCast);
        }catch (Exception e){
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_camera, parent, false);

        mServerImageView = (ImageView)rootView.findViewById(R.id.imageView);
        tvDetect = (TextView) rootView.findViewById(R.id.tv_detect);
        Button lazerBtn = (Button) rootView.findViewById(R.id.btn_laser);
        TextView tvBarrel = (TextView)rootView.findViewById(R.id.tv_barrel);
        JoystickView barrel = rootView.findViewById(R.id.barrel);
        Button streamBtn = (Button) rootView.findViewById(R.id.btn_stream);
        RoundedImageView rivFire = (RoundedImageView) rootView.findViewById(R.id.riv_fire);
        stick_state = rootView.findViewById(R.id.stick_state);
        JoystickView joystickView = rootView.findViewById(R.id.joystick);
        Button btnTrack = (Button) rootView.findViewById(R.id.btn_track);

        btnTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!SingleClick.available()) {
                    return;
                }

                if (!mAuto) {
                    btnTrack.setBackgroundResource(R.drawable.my_button_bg_2);
                    btnTrack.setTextColor(Color.rgb(0,0,255));
                    mAuto = true;
                } else{
                    btnTrack.setBackgroundResource(R.drawable.my_button_bg);
                    btnTrack.setTextColor(Color.rgb(255,255,255));
                    mAuto = false;
                }
            }
        });

        lazerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                if (!SingleClick.available()) {
                    return;
                }

                if (!mLaser) {
                    ((Button) getActivity().findViewById(R.id.btn_laser)).setBackgroundResource(R.drawable.my_button_bg_2);
                    ((Button) getActivity().findViewById(R.id.btn_laser)).setTextColor(Color.rgb(0,0,255));
                    mLaser = true;
                    mUdpClient.sendBytes(mServerAddr, mServerPort, mLaserOn);
                } else{
                    ((Button) getActivity().findViewById(R.id.btn_laser)).setBackgroundResource(R.drawable.my_button_bg);
                    ((Button) getActivity().findViewById(R.id.btn_laser)).setTextColor(Color.rgb(255,255,255));
                    mLaser = false;
                    mUdpClient.sendBytes(mServerAddr, mServerPort, mLaserOff);
                }
            }
        });

        barrel.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                if (!SingleClick.available()) {
                    return;
                }
                ArrayList<byte[]> cmds = new ArrayList<>();
                switch (direction){
                    case JoystickView.FRONT:
                        cmds = DirectHelper.camera_up();
                        break;
                    case JoystickView.BOTTOM:
                        cmds = DirectHelper.camera_down();
                        break;
                }
                for (byte[] x : cmds){
                    Log.e("barrel_up", x.toString());
                    mUdpClient.sendBytes(mServerAddr, mServerPort, x);
                }
                tvBarrel.setText(DirectHelper.debug_camera_pos());
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL);

        streamBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (!mStream) {
                    try {
                        mServerAddr = InetAddress.getByName(mServerAddressBroadCast);
                    }catch (Exception e){

                    }
                    mUdpClient.sendBytes(mServerAddr, mServerPort, mRequestConnect);
                    Pair<SocketAddress, String> res = mUdpClient.getResponse();
                    int cnt = 8;
                    while (res.first == null && cnt > 0) {
                        res = mUdpClient.getResponse();
                        cnt--;
                    }
                    if (res.first != null) {
                        Log.d(TAG, res.first.toString() + ":" + res.second);
                        mServerExactAddress = res.first.toString().split(":")[0].replace("/","");
                        mStream = true;
                        connectWebSocket();
                        ((Button) getActivity().findViewById(R.id.btn_stream)).setBackgroundResource(R.drawable.my_button_bg_2);
                        ((Button) getActivity().findViewById(R.id.btn_stream)).setTextColor(Color.rgb(0,0,255));
                        try {
                            mServerAddr = InetAddress.getByName(mServerExactAddress);
                        }catch (Exception e){

                        }
                    }else{
                        Toast toast =
                                Toast.makeText(
                                        getActivity(), "Cannot connect to ESP32 Camera", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }
                } else {
                    mStream = false;
                    mWebSocketClient.close();
                    ((Button) getActivity().findViewById(R.id.btn_stream)).setBackgroundResource(R.drawable.my_button_bg);
                    ((Button) getActivity().findViewById(R.id.btn_stream)).setTextColor(Color.rgb(255,255,255));
                }
            }
        });


        rivFire.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (!SingleClick.available()) {
                    return;
                }
                mUdpClient.sendBytes(mServerAddr, mServerPort, mFire);
            }
        });

        stick_state.setText(DirectHelper.debug_joystck());

        joystickView.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                if (!SingleClick.available()) {
                    return;
                }
                ArrayList<byte[]> cmds = DirectHelper.joystick(direction, power);
                for (byte[] x : cmds){
                    Log.e("udpsend", x.toString());
                    mUdpClient.sendBytes(mServerAddr, mServerPort, x);
                }
                stick_state.setText(DirectHelper.debug_joystck());
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL);

        return rootView;
    }

    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI("ws://"+mServerExactAddress+":86/");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.d("Websocket", "Open");
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.d("Websocket", "Closed " + s);
            }

            @Override
            public void onMessage(String message){
                Log.d("Websocket", "Receive");
            }

            @Override
            public void onMessage(ByteBuffer message){
                byte[] imageBytes= new byte[message.remaining()];
                message.get(imageBytes);
                final Bitmap bmp=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
                if (bmp == null)
                {
                    return;
                }


                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        int viewWidth = mServerImageView.getWidth();
                        Matrix matrix = new Matrix();
//                        matrix.postRotate(90);
                        final Bitmap bmp_traspose = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true );
                        float imagRatio = (float)bmp_traspose.getHeight()/(float)bmp_traspose.getWidth();
                        int dispViewH = (int)(viewWidth*imagRatio);
                        mServerImageView.setImageBitmap(Bitmap.createScaledBitmap(bmp_traspose, viewWidth, dispViewH, false));
                    }
                });

                if (mAuto){
                    nanodetncnn.append(bmp);

                    float[] last = nanodetncnn.fetch();
                    final BBox bbox = parse_target(last);
                    if (bbox.score > 0.0f) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvDetect.setText(String.valueOf(bbox));
                            }
                        });
                    }

                    if (bbox.score > 0.3f) {
                        int dir = bbox.direction(bmp.getWidth(), bmp.getHeight());
                        if (dir != 0) {
                            ArrayList<byte[]> cmds = DirectHelper.joystick(dir, 100);
                            for (byte[] x : cmds){
                                Log.e("udpsend", x.toString());
                                mUdpClient.sendBytes(mServerAddr, mServerPort, x);
                            }
                            stick_state.setText(DirectHelper.debug_joystck());
                        }
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.d("Websocket", "Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }

    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        mWebSocketClient.close();
        super.onDestroy();
    }

    public BBox parse_target(float[] arr) {
        float score = -1.f;
        BBox ret = new BBox();
        if (arr.length < 6) {
            return ret;
        }

        for (int i = 0; i < arr.length; i+=6) {
            if (arr[i + 5] > score) {
                score = arr[i+5];

                ret.x = arr[i];
                ret.y = arr[i+1];
                ret.width = arr[i+2];
                ret.height = arr[i+3];
                ret.label = Math.round(arr[i+4]);
                ret.score = Math.round(arr[i+5]);
            }
        }

        Log.e(Esp32CameraFragment.class.getName(), String.valueOf(ret));
        return ret;
    }
}

