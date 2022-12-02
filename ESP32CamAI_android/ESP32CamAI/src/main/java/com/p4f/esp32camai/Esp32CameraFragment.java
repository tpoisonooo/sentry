package com.p4f.esp32camai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

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
import org.opencv.android.OpenCVLoader;

public class Esp32CameraFragment extends Fragment{
    final String TAG = "ExCameraFragment";

    private UDPSocket mUdpClient;
    private String mServerAddressBroadCast = "255.255.255.255";
    InetAddress mServerAddr;
    int mServerPort = 6868;

    // start preview camera
    final byte[] mRequestConnect      = new byte[]{'w','h','o','a','m','i'};
    final byte[] mFire = new byte[]{'f','i','r','e'};

    ImageView mServerImageView;

    private WebSocketClient mWebSocketClient;
    private String mServerExactAddress;
    private boolean mStream = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUdpClient = new UDPSocket(12345);
        mUdpClient.runUdpServer();

        try {
            mServerAddr = InetAddress.getByName(mServerAddressBroadCast);
        }catch (Exception e){
        }

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, getActivity(), null);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_camera, parent, false);

        mServerImageView = (ImageView)rootView.findViewById(R.id.imageView);
        Button streamBtn = (Button) rootView.findViewById(R.id.streamBtn);
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
                        ((Button) getActivity().findViewById(R.id.streamBtn)).setBackgroundResource(R.drawable.my_button_bg_2);
                        ((Button) getActivity().findViewById(R.id.streamBtn)).setTextColor(Color.rgb(0,0,255));
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
                    ((Button) getActivity().findViewById(R.id.streamBtn)).setBackgroundResource(R.drawable.my_button_bg);
                    ((Button) getActivity().findViewById(R.id.streamBtn)).setTextColor(Color.rgb(255,255,255));
                }
            }
        });

        RoundedImageView rivFire = (RoundedImageView) rootView.findViewById(R.id.riv_fire);
        rivFire.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mUdpClient.sendBytes(mServerAddr, mServerPort, mFire);
            }
        });

        TextView stick_state = rootView.findViewById(R.id.stick_state);
        JoystickView joystickView = rootView.findViewById(R.id.joystick);
        joystickView.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                if (!SingleClick.available()) {
                    return;
                }
                stick_state.setText("力度:"+String.valueOf(power) +", 方向:" +String.valueOf(direction));
                ArrayList<byte[]> cmds = DirectHelper.cmd(direction, power);
                for (byte[] x : cmds){
                    Log.e("udpsend", x.toString());
                    mUdpClient.sendBytes(mServerAddr, mServerPort, x);
                }
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
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        byte[] imageBytes= new byte[message.remaining()];
                        message.get(imageBytes);
                        final Bitmap bmp=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
                        if (bmp == null)
                        {
                            return;
                        }
                        int viewWidth = mServerImageView.getWidth();
                        Matrix matrix = new Matrix();
//                        matrix.postRotate(90);
                        final Bitmap bmp_traspose = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true );
                        float imagRatio = (float)bmp_traspose.getHeight()/(float)bmp_traspose.getWidth();
                        int dispViewH = (int)(viewWidth*imagRatio);
                        mServerImageView.setImageBitmap(Bitmap.createScaledBitmap(bmp_traspose, viewWidth, dispViewH, false));
                    }
                });
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
}

