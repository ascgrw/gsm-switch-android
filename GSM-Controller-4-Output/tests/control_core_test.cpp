#include "../firmware/gsm_controller/control_core.h"
#include <assert.h>
#include <stdio.h>
int main(){Command c;Controller engine;
 assert(parseCommand("ABC12345 R1 ON 3",c));assert(engine.apply(c,100)==0);assert(engine.r1&&!engine.r2);assert(engine.left(1100)==2);
 assert(engine.apply(c,2000)==1);engine.tick(3100);assert(!engine.r1);
 assert(parseCommand("ABC12345 R1 ON 9",c));assert(engine.apply(c,4000)==-1);assert(!engine.r1);
 assert(parseCommand("AAA00001 ALL ON 900",c));engine.apply(c,5000);assert(engine.r1&&engine.r2&&engine.r3&&engine.r4);
 assert(parseCommand("AAA00002 ALL OFF",c));engine.apply(c,6000);assert(!engine.r1&&!engine.r2&&!engine.r3&&!engine.r4);
 assert(parseCommand("AAA00003 R2 ON",c));engine.apply(c,6000);assert(!engine.r1&&engine.r2&&!engine.r3&&!engine.r4);
 assert(parseCommand("AAA00004 STATUS",c));engine.apply(c,7000);assert(engine.r2);
 assert(parseCommand("AAA00006 R3 ON",c));engine.apply(c,8000);assert(engine.r3&&!engine.r4);
 assert(parseCommand("AAA00007 R4 ON",c));engine.apply(c,9000);assert(engine.r4);engine.tick(100000);assert(engine.r3&&engine.r4&&!engine.r1);
 const char* bad[]={"", "foo", "ABC12345 R1 ON", "ABC12345 R1 ON 0","ABC12345 R1 ON 901","ABC12345 R1 ON 1x","ABC12345 R2 ON 3","ABC12345 ALL OFF 0","abc12345 STATUS","ABC12345 R3 ON 3","ABC12345 R4 ON 9","ABC12345 R5 ON","ABC12345 R1 ON -3","ABC12345 STATUS extra","ABC12345 R1 ON 1\nR2 ON"};
 for(auto x:bad)assert(!parseCommand(x,c));
 Controller wrap;assert(parseCommand("AAA00005 R1 ON 2",c));wrap.apply(c,0xffffff00);wrap.tick(0x000002e8);assert(wrap.r1);wrap.tick(0x000006d0);assert(!wrap.r1);
 puts("PASS: parser, timing, OFF, status, duplicate IDs, ID conflicts, millis wraparound");
}
