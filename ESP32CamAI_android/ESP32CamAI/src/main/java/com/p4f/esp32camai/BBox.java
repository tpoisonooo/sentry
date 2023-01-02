package com.p4f.esp32camai;

import com.zerokol.views.joystickView.JoystickView;

public class BBox {
    float x, y, width, height;
    int label;
    float score;

    public BBox() {
        this.score = -1.f;
    }

    @Override
    public String toString() {
        return "BBox{" +
                "x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                ", label=" + label +
                ", score=" + score +
                '}';
    }

    public BBox(float data[]) {
        this.x = data[0];
        this.y = data[1];
        this.width = data[2];
        this.height = data[3];
        this.label = Math.round(data[4]);
        this.score = data[5];
    }

    public BBox(float x, float y, float width, float height, float label, float score) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = Math.round(label);
        this.score = score;
    }

    public int direction(int maxw, int maxh) {
        float centerx = x + (width / 2);
        float centery = y + (height / 2);

        float halfw = maxw / 2.f;
        float halfh = maxh / 2.f;
        float fdirx = (centerx - halfw) / halfw;
        float fdiry = (centery - halfh) / halfh;

        int dirx = 0, diry = 0;
        if (fdirx > 0.3f) {
            dirx = 1;
        } else if (fdirx < -0.3f) {
            dirx = -1;
        }
        if (fdiry > 0.3f) {
            diry = 1;
        } else if (diry < -0.3f) {
            diry = -1;
        }
        int map[][] = {
                // dirx = -1
                // diry = -1,0,1
                {JoystickView.LEFT_FRONT, JoystickView.LEFT, JoystickView.BOTTOM_LEFT},
                // dirx = 0
                // diry = -1, 0, 1
                {JoystickView.FRONT, -1, JoystickView.BOTTOM},
                // dirx = 1
                // diry = -1,0,1
                {JoystickView.FRONT_RIGHT, JoystickView.RIGHT ,JoystickView.RIGHT_BOTTOM},
        };
        int dir = map[dirx + 1][diry + 1];
        return dir;
    }
}
