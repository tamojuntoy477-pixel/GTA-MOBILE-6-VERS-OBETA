package com.nox.gtamobile6;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(new CityView());
    }

    static class CityView extends View {
        Paint p = new Paint(3);
        float px = 0, py = 0, targetX = 0, targetY = 0;
        long start = System.currentTimeMillis();

        CityView(Activity a) { super(a); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        void rect(Canvas c, int color, float l, float t, float r, float b) {
            p.setColor(color); c.drawRect(l,t,r,b,p);
        }
        @Override protected void onDraw(Canvas raw) {
            super.onDraw(raw);
            float w=getWidth(), h=getHeight();
            if (px==0 && py==0) { px=w/2; py=h/2; targetX=px; targetY=py; }
            Canvas c=raw;
            rect(c, Color.rgb(43,105,65),0,0,w,h);
            // City blocks and roads: original placeholder map.
            for (int y=-1; y<5; y++) for (int x=-1; x<8; x++) {
                float bx=x*190+((w/2-px)*0.18f), by=y*150+((h/2-py)*0.18f);
                rect(c, Color.rgb(53,59,62), bx+55,by+12,bx+165,by+115);
                rect(c, Color.rgb(91,94,86), bx+65,by+22,bx+155,by+105);
                rect(c, Color.rgb(65,125,74), bx+72,by+29,bx+148,by+98);
            }
            // Roads
            rect(c, Color.rgb(48,49,53),0,h*.42f,w,h*.58f);
            rect(c, Color.rgb(48,49,53),w*.43f,0,w*.57f,h);
            p.setColor(Color.rgb(232,199,91)); p.setStrokeWidth(3);
            p.setPathEffect(new DashPathEffect(new float[]{18,14},0));
            c.drawLine(0,h*.5f,w,h*.5f,p); c.drawLine(w*.5f,0,w*.5f,h,p);
            p.setPathEffect(null);
            // Player
            p.setColor(Color.rgb(235,70,65)); c.drawRoundRect(px-13,py-19,px+13,py+19,7,7,p);
            p.setColor(Color.WHITE); p.setTextSize(22); c.drawText("GTA MOBILE 6  •  PROTÓTIPO BETA",24,34,p);
            p.setTextSize(14); c.drawText("Toque na tela para caminhar pela cidade",24,58,p);
            p.setTextSize(12); c.drawText("MAPA ORIGINAL EM DESENVOLVIMENTO  |  BUILD 0.1.0",24,h-18,p);
            float dx=targetX-px, dy=targetY-py, d=(float)Math.sqrt(dx*dx+dy*dy);
            if(d>2){ px+=dx/d*Math.min(5,d); py+=dy/d*Math.min(5,d); invalidate(); }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_MOVE) {
                targetX=e.getX(); targetY=e.getY(); invalidate(); return true;
            }
            return true;
        }
    }
}
