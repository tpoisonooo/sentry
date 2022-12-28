/*
BSD 2-Clause License

Copyright (c) 2020, ANM-P4F
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <WebSocketsServer.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include "camera_wrap.h"
#include "SSD1306.h"

// #define DEBUG
// #define SAVE_IMG

// const char* ssid = "linglong";
// const char* password = "12345678";

const char* ssid = "CU_4myU";
const char* password = "4r3jgfwr";

// const char* ssid = "haliluqiuqiuhayaya";
// const char* password = "77552100";
//holds the current upload
int cameraInitState = -1;
uint8_t* jpgBuff = new uint8_t[68123];
size_t   jpgLength = 0;
uint8_t camNo=0;
bool clientConnected = false;

//Creating UDP Listener Object. 
WiFiUDP UDPServer;
IPAddress addrRemote;
unsigned int portRemote;
unsigned int UDPPort = 6868;
const int RECVLENGTH = 16;
byte packetBuffer[RECVLENGTH];

WebSocketsServer webSocket = WebSocketsServer(86);
String html_home;

const int LED_BUILT_IN        = 4;
// const int PIN_SERVO_YAW       = 2;
const int SERVO_RESOLUTION    = 16;
unsigned long previousMillisServo = 0;

// top fps = 25
const unsigned long intervalServo = 40;

// I2C display
// IO15 --- SDA
// IO14 --- SCL
SSD1306 display(0x3c, 15, 14);

// pwm
#define PWM0_CHANNEL (0)
#define PWM1_CHANNEL (2)
#define PWM2_CHANNEL (4)

// GPIO 0 is camera clock, not available
#define LAZER_GPIO  1
#define FIRE_GPIO 3


void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {

  // 消息类型
  switch(type) {
      case WStype_DISCONNECTED:
          camNo = num;
          clientConnected = false;
          break;
      case WStype_CONNECTED:
          clientConnected = true;
          break;
      case WStype_TEXT:
      case WStype_BIN:
      case WStype_ERROR:
      case WStype_FRAGMENT_TEXT_START:
      case WStype_FRAGMENT_BIN_START:
      case WStype_FRAGMENT:
      case WStype_FRAGMENT_FIN:
          // Serial.println(type);
          break;
  }
}

int parse_int32(byte* pdata) {
  int ret = 0;
  ret += (int)(pdata[0]) << 24;
  ret += (int)(pdata[1]) << 16;
  ret += (int)(pdata[2]) << 8;
  ret += (int)(pdata[3]);
  return ret;
}

/**
    final byte[] mRequestConnect      = new byte[]{'w','h','o','a','m','i'};
    final byte[] mFire = new byte[]{'f','i','r','e'};
    final byte[] mLaserOn = new byte[]{'l','a','z','e', 'r', 'o', 'n'};
    final byte[] mLaserOff = new byte[]{'l','a','z','e', 'r', 'o', 'f', 'f'};
 */
void processUDPData(){
  // int life = 4;
  int len = 0;
  // while(life > 0) {
  // life--;
  len = UDPServer.parsePacket();

  if (len > 0) {
      UDPServer.read(packetBuffer, RECVLENGTH);

      String strPackage = String((const char*)packetBuffer);

      // display.drawString(0, 16, strPackage);
      // display.display();
      display.clear();
      display.drawString(0, 32, String(packetBuffer, 4));

      if(strPackage.equals("whoami")){
        display.drawString(0,32,"response");
        addrRemote = UDPServer.remoteIP();
        portRemote = UDPServer.remotePort();
        UDPServer.beginPacket(addrRemote, portRemote-1);
        String res = "ESP32-CAM";
        UDPServer.write((const uint8_t*)res.c_str(),res.length());
        UDPServer.endPacket();
        // Serial.println("response");

      }else if(strPackage.equals("fire")){
        digitalWrite(FIRE_GPIO, HIGH);
        delay(40);
        digitalWrite(FIRE_GPIO, LOW);
        // life-=3;

      }else if(strPackage.equals("lazeron")){
        digitalWrite(LAZER_GPIO, HIGH);
        delay(10);

      }else if(strPackage.equals("lazeroff")){
        digitalWrite(LAZER_GPIO, LOW);
        delay(10);
      
      } else if(packetBuffer[0] == 'p' && packetBuffer[1]=='w' && packetBuffer[2]=='m') {
        // control

        int duty = parse_int32(packetBuffer + 4);
        int channel = PWM0_CHANNEL;
        if (packetBuffer[3] == '0') {
          channel = PWM0_CHANNEL;
        } else if (packetBuffer[3] == '1') {
          channel = PWM1_CHANNEL;
        } else {
          channel = PWM2_CHANNEL;
        }

        display.drawString(16, 48, String(duty));

        // duty = max(8, min(28, duty));
        ledcWrite(channel, duty);
        delay(10);
      }
      display.display();

      memset(packetBuffer, 0, RECVLENGTH);
  }
  //  else {
  //   break;
  // }
  // }
}


void setup_pwm() {
  display.drawString(0,0,"pwm init..");
  // 对象，表示 显示器
  // .display() 函数，
  display.display();

#define LED_GPIO0   13
#define LED_GPIO1   12
#define LED_GPIO2   2
  // pin 引脚
  // Mode 
  pinMode(LED_GPIO0, OUTPUT);
  pinMode(LED_GPIO1, OUTPUT);
  pinMode(LED_GPIO2, OUTPUT);
  pinMode(LAZER_GPIO, OUTPUT);
  pinMode(FIRE_GPIO, OUTPUT);

  ledcAttachPin(LED_GPIO0, PWM0_CHANNEL);
  ledcAttachPin(LED_GPIO1, PWM1_CHANNEL);
  ledcAttachPin(LED_GPIO2, PWM2_CHANNEL);

  ledcSetup(PWM0_CHANNEL, 50, 9);
  ledcSetup(PWM1_CHANNEL, 50, 9);
  ledcSetup(PWM2_CHANNEL, 50, 9);

  // never rotate whole circle !
  // fontend
  for (int duty = 32; duty < 48; ++duty)
  {
    ledcWrite(PWM0_CHANNEL, duty);
    delay(10);
  }
  delay(1000);
  
  // leftright
  for (int duty = 16; duty < 56; ++duty)
  {
    ledcWrite(PWM1_CHANNEL, duty);
    delay(10);
  }
  delay(1000);
  // cam updown
  for (int duty = 20; duty < 40; ++duty)
  {
    ledcWrite(PWM2_CHANNEL, duty);
    delay(10);
  }
  delay(1000);
}

void setup(void) {
  // Serial.begin(115200);
  // Serial.print("\n");
  // Serial.setDebugOutput(true);

  // init display
  display.init();

  // init pwm
  setup_pwm();

  // pinMode(LED_BUILT_IN, OUTPUT);
  // digitalWrite(LED_BUILT_IN, LOW);

  // init camera
  cameraInitState = initCamera();

  // Serial.printf("camera init state %d\n", cameraInitState);

  if(cameraInitState != 0){
    display.clear();
    display.drawString(0,0,"camera init failed!");
    display.display();
    return;
  }

  //WIFI INIT
  // Serial.printf("Connecting to %s\n", ssid);
  WiFi.begin(ssid, password);
  WiFi.setSleep(false);
  display.clear();
  display.drawString(0,0,"connecting wifi..");
  display.display();

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    // Serial.print(".");
  }
  // Serial.println("");
  // Serial.print("Connected! IP address: ");

  // display IPv4 addr to OLED
  display.clear();
  display.drawString(0,0,WiFi.localIP().toString());
  display.display();

  // Serial.println(WiFi.localIP().toString());

  // start websocket to send Image
  webSocket.begin();
  webSocket.onEvent(webSocketEvent); // 返回摄像头数据

  UDPServer.begin(UDPPort);  // 服务处理控制数据， PWM + 开关
}

void loop(void) {
  webSocket.loop();
  if(clientConnected == true) {
    grabImage(jpgLength, jpgBuff);
    webSocket.sendBIN(camNo, jpgBuff, jpgLength);
    // Serial.print("send img: ");
    // Serial.println(jpgLength);
  }

  unsigned long currentMillis = millis();
  if (currentMillis - previousMillisServo >= intervalServo) {
    previousMillisServo = currentMillis;
    processUDPData();
  }
}
