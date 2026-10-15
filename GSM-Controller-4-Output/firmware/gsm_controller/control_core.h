#pragma once
#include <stdint.h>
#include <string.h>
#include <stdlib.h>

// Shared pure C++ parser and timer engine; also exercised by host tests.
struct Command {
  char id[9] = {};
  int channel = -1; // -1 STATUS, 0 ALL, 1 timed, 2–4 manual
  bool on = false;
  uint16_t seconds = 0;
};
inline bool parseCommand(const char* text, Command& out) {
  if (!text || strlen(text) >= 80) return false;
  char buf[80]; strcpy(buf,text); char* ctx=nullptr;
  char* t[6]={}; int n=0;
  for(char* p=strtok_r(buf," \t",&ctx);p;p=strtok_r(nullptr," \t",&ctx)) {
    if(n==6) return false; t[n++]=p;
  }
  if(n<2 || strlen(t[0])!=8) return false;
  Command c;
  for(int i=0;i<8;i++) if(!((t[0][i]>='0'&&t[0][i]<='9')||(t[0][i]>='A'&&t[0][i]<='F')))return false;
  strcpy(c.id,t[0]);
  if(!strcmp(t[1],"STATUS")){ if(n!=2)return false; out=c; return true; }
  if(n<3) return false;
  if(!strcmp(t[1],"ALL"))c.channel=0;
  else if(!strcmp(t[1],"R1"))c.channel=1;
  else if(strlen(t[1])==2&&t[1][0]=='R'&&t[1][1]>='2'&&t[1][1]<='4')c.channel=t[1][1]-'0';
  else return false;
  if(!strcmp(t[2],"OFF")){ if(n!=3)return false; out=c; return true; }
  if(strcmp(t[2],"ON"))return false;
  c.on=true;
  if(c.channel>=2){if(n!=3)return false;out=c;return true;}
  if(n!=4 || t[3][0]<'1'||t[3][0]>'9' || strlen(t[3])>3)return false;
  for(char* p=t[3];*p;p++)if(*p<'0'||*p>'9')return false;
  unsigned long s=strtoul(t[3],nullptr,10); if(s<1||s>900)return false;
  c.seconds=(uint16_t)s;out=c;return true;
}
inline bool sameCommand(const Command& a,const Command& b){return a.channel==b.channel&&a.on==b.on&&a.seconds==b.seconds;}
class Controller {
 public:
  bool r1=false,r2=false,r3=false,r4=false;
  uint32_t started=0; uint16_t duration=0;
  Command seen[32]; uint8_t count=0,next=0;
  // Returns 0 new, 1 duplicate (no action), -1 ID conflict.
  int apply(const Command& c,uint32_t now) {
    tick(now);
    for(int i=0;i<count;i++)if(!strcmp(seen[i].id,c.id))return sameCommand(seen[i],c)?1:-1;
    seen[next]=c;next=(next+1)%32;if(count<32)count++;
    if(c.channel<0)return 0;
    if(c.channel==0||c.channel==1){r1=c.on;if(r1){started=now;duration=c.seconds;}else duration=0;}
    if(c.channel==0||c.channel==2)r2=c.on;
    if(c.channel==0||c.channel==3)r3=c.on;
    if(c.channel==0||c.channel==4)r4=c.on;
    return 0;
  }
  void tick(uint32_t now){if(r1&&(uint32_t)(now-started)>=(uint32_t)duration*1000){r1=false;duration=0;}}
  uint32_t left(uint32_t now)const {if(!r1)return 0;uint32_t elapsed=(now-started)/1000;return elapsed>=duration?0:duration-elapsed;}
};
