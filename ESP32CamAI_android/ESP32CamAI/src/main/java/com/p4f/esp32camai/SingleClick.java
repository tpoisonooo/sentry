package com.p4f.esp32camai;

public class SingleClick {
    private static final int DEFAULT_TIME = 10;
    private static long lastTime;

    public static boolean available(){
        boolean isSingle ;
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastTime >= DEFAULT_TIME){
            isSingle = true;
        }else{
            isSingle = false;
        }
        lastTime = currentTime;

        return isSingle;
    }
}

