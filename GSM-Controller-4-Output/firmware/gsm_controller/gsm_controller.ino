/* GSM Switch Controller — ESP8266 Arduino core 3.1.2, NodeMCU 1.0.
 * Four active-HIGH MOSFET relay drivers. R1 max 900 seconds, reset defaults OFF.
 * Nonblocking AT state machine: timer remains active while the modem is busy.
 * SMS: PIN ABC12345 R1 ON 300 | R1 OFF | R2/R3/R4 ON | ALL ON 300 | ALL OFF | STATUS.
 * Only configured sender is authorized. IDs deduplicated for 32 commands until reset.
 */
#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <SoftwareSerial.h>
#include "config.h"
#include "control_core.h"
#include "web_ui.h"

ESP8266WebServer server(80);
EspSoftwareSerial::UART gsm;
Controller controller;
String serialLine, smsSender, smsBody;
bool configured=false,modemReady=false,bodyExpected=false,smsBodyReceived=false;
int networkRegistration=0,signalQuality=99,currentIndex=-1,initStep=0;
bool simReady=false,modemOnline=false;
uint32_t operationStart=0,lastPoll=0,lastScan=0,nextInit=0,lastModemReply=0;
int scanQueue[24];uint8_t scanCount=0;
String outNumber,outText;
struct Reply { String number,text; };
Reply replyQueue[4];uint8_t replyCount=0;
int deleteIndex=-1;
enum Operation { IDLE,INITIALIZE,SCAN,READ_SMS,DELETE_SMS,SEND_PROMPT,SEND_RESULT,POLL_REG,POLL_SIGNAL,POLL_SIM };
Operation operation=IDLE;
const char* const initCommands[]={"AT","ATE0","AT+IPR=9600","AT+CMGF=1","AT+CSCS=\"GSM\"","AT+CPMS=\"SM\",\"SM\",\"SM\"","AT+CMGD=1,4","AT+CNMI=2,1,0,0,0"};
void writeOutputs(){digitalWrite(RELAY1_PIN,controller.r1?HIGH:LOW);digitalWrite(RELAY2_PIN,controller.r2?HIGH:LOW);digitalWrite(RELAY3_PIN,controller.r3?HIGH:LOW);digitalWrite(RELAY4_PIN,controller.r4?HIGH:LOW);}
bool digitPin(){size_t n=strlen(SMS_PIN);if(n<6||n>12)return false;for(size_t i=0;i<n;i++)if(SMS_PIN[i]<'0'||SMS_PIN[i]>'9')return false;return true;}
bool validPhone(const char* p){if(!p||p[0]!='+'||p[1]<'1'||p[1]>'9')return false;size_t n=strlen(p);if(n<9||n>16)return false;for(size_t i=1;i<n;i++)if(p[i]<'0'||p[i]>'9')return false;return true;}
String normalizePhone(String p){p.trim();if(p.startsWith("00"))p="+"+p.substring(2);return p;}
String quoted(const String& s,int item){int start=-1;for(int i=0;i<=item;i++){start=s.indexOf('"',start+1);if(start<0)return "";int end=s.indexOf('"',start+1);if(end<0)return "";if(i==item)return s.substring(start+1,end);start=end;}return "";}
String statusText(const char* id){return String("GSM1 ID=")+id+" R1="+(controller.r1?"ON":"OFF")+" R2="+(controller.r2?"ON":"OFF")+" R3="+(controller.r3?"ON":"OFF")+" R4="+(controller.r4?"ON":"OFF")+" UP="+String(millis()/1000)+" LEFT="+String(controller.left(millis()));}
String statusJson(){return String("{\"r1\":")+(controller.r1?"true":"false")+",\"r2\":"+(controller.r2?"true":"false")+",\"r3\":"+(controller.r3?"true":"false")+",\"r4\":"+(controller.r4?"true":"false")+",\"left\":"+String(controller.left(millis()))+",\"uptime\":"+String(millis()/1000)+",\"modemReady\":"+(modemReady?"true":"false")+",\"modemOnline\":"+(modemOnline?"true":"false")+",\"simReady\":"+(simReady?"true":"false")+",\"registration\":"+String(networkRegistration)+",\"csq\":"+String(signalQuality)+"}";}
void queueReply(const String& phone,const String& body){if(replyCount<4){replyQueue[replyCount++]={phone,body};}else Serial.println("Reply queue full; no automatic retry");}
void processSMS(){
  // No reply at all to unauthorized senders or wrong PINs.
  if(normalizePhone(smsSender)!=String(AUTHORIZED_PHONE))return;
  if(smsBody.indexOf('\n')>=0||smsBody.length()>96)return;
  int gap=smsBody.indexOf(' ');if(gap<0||smsBody.substring(0,gap)!=SMS_PIN)return;
  Command c;String command=smsBody.substring(gap+1);
  if(!parseCommand(command.c_str(),c)){queueReply(AUTHORIZED_PHONE,"GSM1 ERROR=INVALID_COMMAND");return;}
  int result=controller.apply(c,millis());writeOutputs();
  if(result<0){queueReply(AUTHORIZED_PHONE,String("GSM1 ID=")+c.id+" ERROR=ID_CONFLICT");return;}
  queueReply(AUTHORIZED_PHONE,statusText(c.id));
}
void queueIndex(int index){if(index<0||index>999)return;for(int i=0;i<scanCount;i++)if(scanQueue[i]==index)return;if(scanCount<24)scanQueue[scanCount++]=index;}
void beginAT(Operation op,const String& command){operation=op;operationStart=millis();gsm.print(command);gsm.print('\r');}
void finishOperation(bool ok){
  Operation previous=operation;operation=IDLE;bodyExpected=false;
  if(previous==INITIALIZE){if(ok){initStep++;if(initStep==(int)(sizeof(initCommands)/sizeof(initCommands[0]))){modemReady=true;lastScan=millis()-60000;Serial.println("Modem initialized");}}else{initStep=0;modemReady=false;nextInit=millis()+5000;}}
  if(previous==READ_SMS){if(ok&&smsBodyReceived){processSMS();deleteIndex=currentIndex;}currentIndex=-1;smsBodyReceived=false;}
  if(previous==SEND_RESULT||previous==SEND_PROMPT){Serial.println(ok?"SMS accepted by modem (not a delivery receipt)":"SMS send unconfirmed; not retried");outNumber="";outText="";}
}
void consumeLine(String line){
  line.trim();if(!line.length())return;
  // Read body before terminal response classification, so a body saying OK cannot end CMGR.
  if(bodyExpected){smsBody=line;smsBodyReceived=true;bodyExpected=false;return;}
  if(line.startsWith("+CMTI:")){queueIndex(line.substring(line.lastIndexOf(',')+1).toInt());return;}
  if(operation==READ_SMS&&line.startsWith("+CMGR:")){smsSender=quoted(line,1);bodyExpected=true;return;}
  if(operation==SCAN&&line.startsWith("+CMGL:")){queueIndex(line.substring(6,line.indexOf(',')).toInt());return;}
  if(line.startsWith("+CREG:")){int comma=line.indexOf(',');networkRegistration=(comma>=0?line.substring(comma+1):line.substring(6)).toInt();}
  if(line.startsWith("+CSQ:"))signalQuality=line.substring(5,line.indexOf(',')).toInt();
  if(line.startsWith("+CPIN:"))simReady=line.indexOf("READY")>=0;
  if(line=="OK"){lastModemReply=millis();modemOnline=true;finishOperation(true);return;}
  if(line=="ERROR"||line.startsWith("+CME ERROR")||line.startsWith("+CMS ERROR")){finishOperation(false);return;}
  if(line=="RDY"||line=="SMS Ready"){modemReady=false;initStep=0;nextInit=millis()+2000;}
}
void readModem(){while(gsm.available()){
  char c=(char)gsm.read();
  if(c=='>'&&operation==SEND_PROMPT){gsm.print(outText);gsm.write((uint8_t)26);operation=SEND_RESULT;operationStart=millis();serialLine="";continue;}
  if(c=='\n'){consumeLine(serialLine);serialLine="";}else if(c!='\r'){if(serialLine.length()<256)serialLine+=c;}
}}
void serviceModem(){
  uint32_t now=millis();readModem();if(modemOnline&&(uint32_t)(now-lastModemReply)>90000){modemOnline=false;networkRegistration=0;simReady=false;signalQuality=99;}
  if(operation!=IDLE){uint32_t limit=operation==SEND_RESULT?60000:5000;if((uint32_t)(now-operationStart)>limit){if(operation==SEND_PROMPT)gsm.write((uint8_t)27);finishOperation(false);}return;}
  if(!modemReady){if((int32_t)(now-nextInit)>=0)beginAT(INITIALIZE,initCommands[initStep]);return;}
  if(deleteIndex>=0){int index=deleteIndex;deleteIndex=-1;beginAT(DELETE_SMS,"AT+CMGD="+String(index));return;}
  if(replyCount){outNumber=replyQueue[0].number;outText=replyQueue[0].text;for(int i=1;i<replyCount;i++)replyQueue[i-1]=replyQueue[i];replyCount--;beginAT(SEND_PROMPT,"AT+CMGS=\""+outNumber+"\"");return;}
  if(scanCount){currentIndex=scanQueue[0];for(int i=1;i<scanCount;i++)scanQueue[i-1]=scanQueue[i];scanCount--;smsSender="";smsBody="";smsBodyReceived=false;beginAT(READ_SMS,"AT+CMGR="+String(currentIndex));return;}
  if((uint32_t)(now-lastScan)>60000){lastScan=now;beginAT(SCAN,"AT+CMGL=\"ALL\"");return;}
  if((uint32_t)(now-lastPoll)>10000){lastPoll=now;static uint8_t poll=0;poll=(poll+1)%3;if(poll==0)beginAT(POLL_REG,"AT+CREG?");else if(poll==1)beginAT(POLL_SIGNAL,"AT+CSQ");else beginAT(POLL_SIM,"AT+CPIN?");}
}
bool authorizedHTTP(){if(server.header("Authorization")==String("Bearer ")+API_TOKEN)return true;server.send(401,"text/plain","Incorrect API token");return false;}
void setup(){
  // Set latch LOW before pin mode; external gate pulldowns also keep coils off during boot.
  for(uint8_t pin:{RELAY1_PIN,RELAY2_PIN,RELAY3_PIN,RELAY4_PIN}){digitalWrite(pin,LOW);pinMode(pin,OUTPUT);}
  Serial.begin(115200);
  configured=digitPin()&&validPhone(AUTHORIZED_PHONE)&&strlen(AP_PASSWORD)>=12&&strlen(AP_PASSWORD)<=63&&strlen(API_TOKEN)>=24;
  if(!configured){Serial.println("Fill AUTHORIZED_PHONE, SMS_PIN, AP_PASSWORD and API_TOKEN in config.h. Outputs remain OFF.");return;}
  gsm.begin(9600,SWSERIAL_8N1,GSM_RX_PIN,GSM_TX_PIN,false,512);
  WiFi.mode(WIFI_AP);WiFi.softAPConfig(IPAddress(192,168,4,1),IPAddress(192,168,4,1),IPAddress(255,255,255,0));WiFi.softAP(AP_NAME,AP_PASSWORD);
  server.collectHeaders("Authorization");
  server.on("/",HTTP_GET,[]{server.sendHeader("Cache-Control","no-store");server.send_P(200,"text/html",WEB_UI);});
  server.on("/api/status",HTTP_GET,[]{if(!authorizedHTTP())return;controller.tick(millis());writeOutputs();server.sendHeader("Cache-Control","no-store");server.send(200,"application/json",statusJson());});
  server.on("/api/command",HTTP_POST,[]{if(!authorizedHTTP())return;if(server.arg("plain").length()>80){server.send(400,"text/plain","Command too long");return;}Command c;if(!parseCommand(server.arg("plain").c_str(),c)){server.send(400,"text/plain","Invalid command");return;}int result=controller.apply(c,millis());writeOutputs();if(result<0){server.send(409,"text/plain","ID already used with a different command");return;}server.sendHeader("Cache-Control","no-store");server.send(200,"application/json",statusJson());});
  server.onNotFound([]{server.send(404,"text/plain","Not found");});server.begin();nextInit=millis()+5000;
  Serial.println("Join configured GSM-Switch Wi-Fi and open http://192.168.4.1");
}
void loop(){controller.tick(millis());writeOutputs();if(configured){server.handleClient();serviceModem();}yield();}
