package com.shahid.gsmcontrol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java wire protocol, shared with the phone receiver simulator and host tests. */
public final class Protocol {
    private Protocol() {}
    public static boolean phone(String p) { return p != null && p.matches("\\+[1-9][0-9]{7,14}"); }
    public static boolean pin(String p) { return p != null && p.matches("[0-9]{6,12}"); }
    public static String normalize(String p) {
        if (p == null) return "";
        p = p.trim().replace(" ", "").replace("-", "");
        if (p.startsWith("00")) p = "+" + p.substring(2);
        // Pakistan SMS sender numbers sometimes use domestic format.
        if (p.matches("03[0-9]{9}")) p = "+92" + p.substring(1);
        return p;
    }
    public static final class Command {
        public final String id, action;
        public final int channel, seconds;
        public final boolean on;
        private Command(String id, String action, int channel, boolean on, int seconds) {
            this.id=id; this.action=action; this.channel=channel; this.on=on; this.seconds=seconds;
        }
    }
    public static Command parse(String id, String action) {
        if (id == null || !id.matches("[A-F0-9]{8}") || action == null) return null;
        if (action.equals("STATUS")) return new Command(id,action,-1,false,0);
        Matcher m=Pattern.compile("(R1|R2|ALL) (ON|OFF)(?: ([1-9][0-9]{0,2}))?").matcher(action);
        if (!m.matches()) return null;
        int channel=m.group(1).equals("ALL")?0:m.group(1).equals("R1")?1:2;
        boolean on=m.group(2).equals("ON");
        int seconds=m.group(3)==null?0:Integer.parseInt(m.group(3));
        if (on && channel!=2) { if(seconds<1||seconds>900) return null; }
        else if(m.group(3)!=null) return null;
        return new Command(id,action,channel,on,seconds);
    }
    public static Command authorized(String sender, String allowed, String body, String pin) {
        if (!phone(allowed) || !pin(pin) || !normalize(sender).equals(allowed) || body==null || body.length()>96) return null;
        String prefix=pin+" "; if(!body.startsWith(prefix))return null;
        String rest=body.substring(prefix.length());int split=rest.indexOf(' ');
        return split<0?null:parse(rest.substring(0,split),rest.substring(split+1));
    }
    public static final class Report {
        public final String id; public final boolean r1,r2; public final long uptime,left;
        private Report(String id,boolean r1,boolean r2,long up,long left){this.id=id;this.r1=r1;this.r2=r2;this.uptime=up;this.left=left;}
    }
    public static Report report(String body) {
        if(body==null||body.length()>140)return null;
        Matcher m=Pattern.compile("GSM1 ID=([A-F0-9]{8}) R1=(ON|OFF) R2=(ON|OFF) UP=([0-9]{1,12}) LEFT=([0-9]{1,3})").matcher(body.trim());
        if(!m.matches())return null;
        long left=Long.parseLong(m.group(5));if(left>900)return null;
        return new Report(m.group(1),m.group(2).equals("ON"),m.group(3).equals("ON"),Long.parseLong(m.group(4)),left);
    }
    public static final class Engine {
        public boolean r1=false,r2=false; public long deadline=0;
        public final LinkedHashMap<String,String> seen=new LinkedHashMap<>();
        public void tick(long now){if(r1 && now>=deadline){r1=false;deadline=0;}}
        public int apply(Command c,long now){
            tick(now);if(seen.containsKey(c.id))return seen.get(c.id).equals(c.action)?1:-1;
            seen.put(c.id,c.action);while(seen.size()>32){seen.remove(seen.keySet().iterator().next());}
            if(c.channel==0||c.channel==1){r1=c.on;deadline=r1?now+c.seconds*1000L:0;}
            if(c.channel==0||c.channel==2)r2=c.on;
            return 0;
        }
        public long left(long now){tick(now);return r1?Math.max(0,(deadline-now+999)/1000):0;}
        public String reply(String id,long now){tick(now);return "GSM1 ID="+id+" R1="+(r1?"ON":"OFF")+" R2="+(r2?"ON":"OFF")+" UP="+(now/1000)+" LEFT="+left(now);}
    }
}
