package com.p4f.esp32camai;

import java.util.ArrayList;

public class PWM {
    private int min = 0;
    private int max = 0;
    private int cur = min;
    private byte id = 0x0;

    public PWM(byte _id, int _min, int _max){
        id = _id;
        min = _min;
        max = _max;
        cur = (this.min + this.max) / 2;
    }

    public int value() {
        return cur;
    }

    public static byte[] int2bytes(int value) {
        byte[] src = new byte[4];
        src[0] =  (byte) ((value>>24) & 0xFF);
        src[1] =  (byte) ((value>>16) & 0xFF);
        src[2] =  (byte) ((value>>8) & 0xFF);
        src[3] =  (byte) (value & 0xFF);
        return src;
    }

    public ArrayList<byte[]> command(int delta) {
        ArrayList<byte[]> ret = new ArrayList<>();

        int last = cur;
        cur += delta;
        cur = Math.max(min, Math.min(cur, max));

        if (cur != last) {
            byte[] cmd = new byte[] {'p', 'w', 'm', this.id, '\0', '\0', '\0', '\0', '\0'};
            byte[] value = int2bytes(cur);
            cmd[4] = value[0];
            cmd[5] = value[1];
            cmd[6] = value[2];
            cmd[7] = value[3];
            ret.add(cmd);
        }
        return ret;
    }
}
