package com.p4f.esp32camai;

import android.util.Log;

import com.zerokol.views.joystickView.JoystickView;

import java.util.ArrayList;

public class DirectHelper {
    private static int pwm0 = 0;
    private static int pwm1 = 0;
    private static int FAST = 2;
    private static int SLOW = 1;

    private static int MIN = 6;
    private static int MAX = 32;

    public static ArrayList<byte[]> cmd(int direction, int power) {
        ArrayList<byte[]> ret = new ArrayList<>();
        int delta = SLOW;
        if (power > 50) {
            delta = FAST;
        }

        int last_pwm0 = pwm0;
        int last_pwm1 = pwm1;
        switch (direction){
            case JoystickView.FRONT:
                pwm0 += delta;
                break;
            case JoystickView.FRONT_RIGHT:
                pwm0 += delta;
                pwm1 += delta;
                break;
            case JoystickView.RIGHT:
                pwm1 += delta;
                break;
            case JoystickView.RIGHT_BOTTOM:
                pwm1 += delta;
                pwm0 -= delta;
                break;
            case JoystickView.BOTTOM:
                pwm0 -= delta;
                break;
            case JoystickView.BOTTOM_LEFT:
                pwm0 -= delta;
                pwm1 -= delta;
                break;
            case JoystickView.LEFT:
                pwm1 -= delta;
                break;
            case JoystickView.LEFT_FRONT:
                pwm0 += delta;
                pwm1 -= delta;
                break;
            default:
                Log.e(Esp32CameraFragment.class.toString(), "unkown direction");
        }
        pwm0 = Math.max(MIN, Math.min(pwm0, MAX));
        pwm1 = Math.max(MIN, Math.min(pwm1, MAX));

        if (pwm0 != last_pwm0) {
            byte[] cmd = new byte[] {'p', 'w', 'm', '0', '\0', '\0', '\0', '\0', '\0'};
            byte[] value = int2bytes(pwm0);
            cmd[4] = value[0];
            cmd[5] = value[1];
            cmd[6] = value[2];
            cmd[7] = value[3];
            ret.add(cmd);
        }
        if (pwm1 != last_pwm1) {
            byte[] cmd = new byte[] {'p', 'w', 'm', '1', '\0', '\0', '\0', '\0', '\0'};
            byte[] value = int2bytes(pwm1);
            cmd[4] = value[0];
            cmd[5] = value[1];
            cmd[6] = value[2];
            cmd[7] = value[3];
            ret.add(cmd);
        }
        return ret;
    }

    public static byte[] int2bytes(int value) {
        byte[] src = new byte[4];
        src[0] =  (byte) ((value>>24) & 0xFF);
        src[1] =  (byte) ((value>>16) & 0xFF);
        src[2] =  (byte) ((value>>8) & 0xFF);
        src[3] =  (byte) (value & 0xFF);
        return src;
    }
}
