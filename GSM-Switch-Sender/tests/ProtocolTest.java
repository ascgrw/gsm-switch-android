package com.shahid.gsmcontrol;
public final class ProtocolTest {
    private static void check(boolean yes){if(!yes)throw new AssertionError();}
    public static void main(String[] args){
        Protocol.Engine e=new Protocol.Engine();Protocol.Command c=Protocol.parse("ABC12345","R1 ON 3");check(c!=null);check(e.apply(c,100)==0);check(e.r1&&!e.r2);check(e.left(1100)==2);
        check(e.apply(c,2000)==1);e.tick(3100);check(!e.r1);
        check(e.apply(Protocol.parse("ABC12345","R1 ON 9"),4000)==-1);
        e.apply(Protocol.parse("AAA00001","ALL ON 900"),5000);check(e.r1&&e.r2);
        e.apply(Protocol.parse("AAA00002","ALL OFF"),6000);check(!e.r1&&!e.r2);
        String[] bad={"R1 ON","R1 ON 0","R1 ON 901","R1 ON 1x","R2 ON 3","ALL OFF 0","R3 ON","STATUS extra","R1 ON 1\nR2 ON"};
        for(String s:bad)check(Protocol.parse("ABC12345",s)==null);
        check(Protocol.authorized("03001234567","+923001234567","654321 AAA00003 R2 ON","654321")!=null);
        check(Protocol.authorized("+923111234567","+923001234567","654321 AAA00003 R2 ON","654321")==null);
        check(Protocol.authorized("+923001234567","+923001234567","000000 AAA00003 R2 ON","654321")==null);
        check(Protocol.report("GSM1 ID=AAA00003 R1=OFF R2=ON UP=120 LEFT=0")!=null);
        check(Protocol.report("GSM1 ID=AAA00003 R1=OFF R2=ON UP=120 LEFT=901")==null);
        check(Protocol.report("GSM1 ID=AAA00003 R1=OFF R2=ON UP=120 LEFT=0 extra")==null);
        System.out.println("PASS: Java command protocol, sender/PIN checks, timers, duplicate IDs, ID conflict, reply parser.");
    }
}
