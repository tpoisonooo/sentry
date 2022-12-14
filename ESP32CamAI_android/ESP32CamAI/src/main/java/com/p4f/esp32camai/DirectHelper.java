package com.p4f.esp32camai;

import android.util.Log;

import com.zerokol.views.joystickView.JoystickView;

import java.util.ArrayList;
import com.p4f.esp32camai.PWM;

public class DirectHelper {
    private static int FAST = 2;
    private static int SLOW = 1;

    // front back controller
    private static PWM pwm_fb = new PWM((byte) '0', 16, 24);
    // left right controller
    private static PWM pwm_lr = new PWM((byte) '1', 8, 28);
    // camera up down controller
    private static PWM pwm_ud = new PWM((byte)'2', 10, 20);

    public static String debug_joystck(){
        return "pwm_fb " + String.valueOf(pwm_fb.value()) + ", pwm_lr " + String.valueOf(pwm_lr.value());
    }

    public static String debug_camera_pos(){
        return "pwm_ud " + String.valueOf(pwm_ud.value());
    }

    public static ArrayList<byte[]> camera_up() {
        return pwm_ud.command(SLOW);
    }

    public  static ArrayList<byte[]> camera_down() {
        return pwm_ud.command(-SLOW);
    }

    public static ArrayList<byte[]> joystick(int direction, int power) {
        ArrayList<byte[]> ret = new ArrayList<>();
        int delta = SLOW;
        if (power > 50) {
            delta = FAST;
        }

        switch (direction){
            case JoystickView.FRONT:
                ret.addAll(pwm_fb.command(delta));
//                pwm0 += delta;
                break;
            case JoystickView.FRONT_RIGHT:
                ret.addAll(pwm_fb.command(delta));
                ret.addAll(pwm_lr.command(delta));
//                pwm0 += delta;
//                pwm1 += delta;
                break;
            case JoystickView.RIGHT:
                ret.addAll(pwm_lr.command(delta));
//                pwm1 += delta;
                break;
            case JoystickView.RIGHT_BOTTOM:
                ret.addAll(pwm_fb.command(-delta));
                ret.addAll(pwm_lr.command(delta));
//                pwm1 += delta;
//                pwm0 -= delta;
                break;
            case JoystickView.BOTTOM:
                ret.addAll(pwm_fb.command(-delta));
//                pwm0 -= delta;
                break;
            case JoystickView.BOTTOM_LEFT:
                ret.addAll(pwm_fb.command(-delta));
                ret.addAll(pwm_lr.command(-delta));
//                pwm0 -= delta;
//                pwm1 -= delta;
                break;
            case JoystickView.LEFT:
                ret.addAll(pwm_lr.command(-delta));
//                pwm1 -= delta;
                break;
            case JoystickView.LEFT_FRONT:
                ret.addAll(pwm_fb.command(delta));
                ret.addAll(pwm_lr.command(-delta));
//                pwm0 += delta;
//                pwm1 -= delta;
                break;
            default:
                Log.e(Esp32CameraFragment.class.toString(), "unkown direction");
        }
        return ret;
    }
}
