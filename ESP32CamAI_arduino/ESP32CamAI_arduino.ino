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

// const char* ssid = "ziroom105";
// const char* password = "ziroomer002";

const char* ssid = "linglong";
const char* password = "12345678";

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
const unsigned long intervalServo = 10;

// I2C display
// IO15 --- SDA
// IO14 --- SCL
SSD1306 display(0x3c, 15, 14);

// pwm
#define PWM0_CHANNEL (0)
#define PWM1_CHANNEL (1)
#define PWM2_CHANNEL (2)
#define PWM3_CHANNEL (3)
#define PWM4_CHANNEL (4)
#define PWM5_CHANNEL (5)


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
          Serial.println(type);
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

void processUDPData(){
  int cb = UDPServer.parsePacket();

  if (cb) {
      UDPServer.read(packetBuffer, RECVLENGTH);

      String strPackage = String((const char*)packetBuffer);

      // display.drawString(0, 16, strPackage);
      // display.display();

      display.clear();
      if(strPackage.equals("whoami")){
        display.drawString(0,32,"response");
        addrRemote = UDPServer.remoteIP();
        portRemote = UDPServer.remotePort();
        UDPServer.beginPacket(addrRemote, portRemote-1);
        String res = "ESP32-CAM";
        UDPServer.write((const uint8_t*)res.c_str(),res.length());
        UDPServer.endPacket();
        Serial.println("response");
      }
      // internal LED
      else if(strPackage.equals("ledon")){
        display.drawString(0,48, "ledon");
        digitalWrite(LED_BUILT_IN, HIGH);
      }else if(strPackage.equals("ledoff")){
        display.drawString(0,48, "ledoff");
        digitalWrite(LED_BUILT_IN, LOW);
      }
      // control
      // pwmX占空比
      // pwm095
      // pwm188
      else if(packetBuffer[0] == 'p' && packetBuffer[1]=='w' && packetBuffer[2]=='m') {
        int duty = parse_int32(packetBuffer + 4);
        int channel = PWM0_CHANNEL;

        if (packetBuffer[3] == '1') {
          channel = PWM1_CHANNEL;
          display.drawString(0, 48, String(packetBuffer, 4)+String(duty));
        } else{
          display.drawString(0, 32, String(packetBuffer, 4)+String(duty));
        }
        duty = max(0, min(255, duty));
        ledcWrite(channel, duty);
        delay(10);
      }
      display.display();

      memset(packetBuffer, 0, RECVLENGTH);
  }

}

// one function one job
// 一个函数，只做一件事
// 一个函数，不要做一堆事
// 设置 PWM 
void setup_pwm() {
  display.drawString(0,0,"pwm self-testing..");
  // 对象，表示 显示器
  // .display() 函数，
  display.display();

  #define LED_GPIO0   13
  #define LED_GPIO1   12
  #define LED_GPIO2   2
  #define LED_GPIO4   16
  #define LED_GPIO5   0


  // pin 引脚
  // Mode 
  pinMode(LED_GPIO0, OUTPUT);
  pinMode(LED_GPIO1, OUTPUT);
  pinMode(LED_GPIO2, OUTPUT);
  pinMode(LED_GPIO4, OUTPUT);
  pinMode(LED_GPIO5, OUTPUT);

  // PWM 不是硬件实现的 PWM
  // 要软件控制器
  // 15
  // ledc   LED controller
  // 输出的控制器， attach 附加， pin 管脚
  // 
  ledcAttachPin(LED_GPIO0, PWM0_CHANNEL);
  ledcAttachPin(LED_GPIO1, PWM1_CHANNEL);
  ledcAttachPin(LED_GPIO2, PWM2_CHANNEL);
  ledcAttachPin(LED_GPIO4, PWM4_CHANNEL);
  ledcAttachPin(LED_GPIO5, PWM5_CHANNEL);

  //1s /  50 Hz   =  PWM 20ms , PWM 一个周期
  // 8 精度， 用 8 个比特表达 PWM 控制精度
  // 0~255 个数值
  // 255 ==  100% 占空比
  // 0 == 0% 占空比
  // 1 == 1/255 % 占空比
  // 能控制多么细

  ledcSetup(PWM0_CHANNEL, 50, 8);
  ledcSetup(PWM1_CHANNEL, 50, 8);
  ledcSetup(PWM2_CHANNEL, 50, 8);
  ledcSetup(PWM4_CHANNEL, 50, 8);
  ledcSetup(PWM5_CHANNEL, 50, 8);


  int duty=0;
  while(duty < 256)
  {

    ledcWrite(PWM0_CHANNEL, duty);
    delay(10);
    ledcWrite(PWM1_CHANNEL, duty);
    delay(10);
    ledcWrite(PWM2_CHANNEL, duty);
    delay(10);
    ledcWrite(PWM4_CHANNEL, duty);
    delay(10);
    ledcWrite(PWM5_CHANNEL, duty);
    delay(10);
    duty++;
  }
}

void setup(void) {

  Serial.begin(115200);
  Serial.print("\n");
  Serial.setDebugOutput(true);

  // init display
  display.init();

  // init pwm
  setup_pwm();

  // pinMode(LED_BUILT_IN, OUTPUT);
  // digitalWrite(LED_BUILT_IN, LOW);

  // init camera
  cameraInitState = initCamera();

  Serial.printf("camera init state %d\n", cameraInitState);

  if(cameraInitState != 0){
    display.clear();
    display.drawString(0,0,"camera init failed!");
    display.display();
    return;
  }

  //WIFI INIT
  Serial.printf("Connecting to %s\n", ssid);
  WiFi.begin(ssid, password);
  WiFi.setSleep(false);
  display.clear();
  display.drawString(0,0,"connecting wifi..");
  display.display();

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.print("Connected! IP address: ");

  // display IPv4 addr to OLED
  display.clear();
  display.drawString(0,0,WiFi.localIP().toString());
  display.display();

  Serial.println(WiFi.localIP().toString());

  // start websocket to send Image
  // http/https --> 不会数据丢失，代价是速度慢、发热
  // 
  // tcp/udp  -->  速度快，代码不好写
  // websocket: 一种 tcp 协议的服务后端
  webSocket.begin();
  webSocket.onEvent(webSocketEvent); // 返回摄像头数据

  UDPServer.begin(UDPPort);  // 服务处理控制数据， PWM + 开关
}

void loop(void) {
  webSocket.loop();
  if(clientConnected == true){
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

