package com.nox.gtamobile6;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.view.View;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
             View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        game = new GameView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(game, new FrameLayout.LayoutParams(-1, -1));
        root.addView(new JoystickOverlay(this, game.renderer), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }
    @Override protected void onPause() { super.onPause(); if (game != null) game.onPause(); }
    @Override protected void onResume() { super.onResume(); if (game != null) game.onResume(); }

    static class GameView extends GLSurfaceView {
        final CityRenderer renderer;
        GameView(Activity a) {
            super(a);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8,8,8,8,16,0);
            renderer = new CityRenderer();
            setRenderer(renderer);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
        private float lastX, lastY;
        private boolean draggingCamera;
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX=e.getX(); lastY=e.getY(); draggingCamera=false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getX()-lastX, dy=e.getY()-lastY;
                    if (Math.abs(dx)+Math.abs(dy)>2f) draggingCamera=true;
                    if (draggingCamera) renderer.rotateCamera(dx,dy);
                    lastX=e.getX(); lastY=e.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!draggingCamera) {
                        final float nx=(e.getX()/Math.max(1f,getWidth())-.5f)*34f;
                        final float nz=(e.getY()/Math.max(1f,getHeight())-.5f)*34f;
                        queueEvent(() -> renderer.setTarget(nx,-nz));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL: return true;
            }
            return true;
        }
    }

    static class JoystickOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final CityRenderer renderer;
        private float cx, cy, knobX, knobY, radius;
        private boolean active = false;
        JoystickOverlay(Activity a, CityRenderer renderer) {
            super(a);
            this.renderer = renderer;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        private void layoutStick() {
            float d = getResources().getDisplayMetrics().density;
            radius = 65f*d;
            cx = 24f*d + radius;
            cy = getHeight() - (24f*d + radius);
            if (!active) { knobX = cx; knobY = cy; }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            layoutStick();
            paint.setColor(Color.argb(65, 15, 20, 28));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.argb(155, 235, 240, 245));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(165, 55, 105, 175));
            canvas.drawCircle(knobX, knobY, radius*0.43f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.argb(210, 235, 240, 250));
            canvas.drawCircle(knobX, knobY, radius*0.43f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                layoutStick();
                if (event.getX() > cx + radius*1.35f || event.getY() < cy - radius*1.35f) return false;
                active = true;
            }
            if (active && (event.getAction()==MotionEvent.ACTION_MOVE || event.getAction()==MotionEvent.ACTION_DOWN)) {
                float dx=event.getX()-cx, dy=event.getY()-cy;
                float len=(float)Math.sqrt(dx*dx+dy*dy);
                float limit=radius*0.78f;
                if(len>limit) { dx=dx/len*limit; dy=dy/len*limit; }
                knobX=cx+dx; knobY=cy+dy;
                renderer.setMoveInput(dx/limit, -dy/limit);
                invalidate();
                return true;
            }
            if (active && (event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL)) {
                active=false; knobX=cx; knobY=cy;
                renderer.setMoveInput(0f,0f);
                invalidate();
                return true;
            }
            return active;
        }
    }

    static class CityRenderer implements GLSurfaceView.Renderer {
        private static final String VS =
            "uniform mat4 uMVP; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal; varying vec3 vNormal; varying float vDepth; void main(){ vec4 p=uMVP*vec4(aPosition,1.0); gl_Position=p; vNormal=normalize(aNormal); vDepth=abs(p.z/p.w); }";
        private static final String FS =
            "precision mediump float; uniform vec4 uColor; varying vec3 vNormal; varying float vDepth; void main(){ vec3 n=normalize(vNormal); vec3 light=normalize(vec3(-0.42,0.82,0.36)); float diffuse=max(dot(n,light),0.0); float hemi=0.30+0.22*max(n.y,0.0); float shade=clamp(hemi+diffuse*0.62,0.28,1.0); vec3 lit=uColor.rgb*shade; float fog= smoothstep(0.72,0.995,vDepth); vec3 sky=vec3(0.53,0.70,0.82); gl_FragColor=vec4(mix(lit,sky,fog*0.58),uColor.a); }";
        private final float[] cube = {
            -1,-1,1, 1,-1,1, 1,1,1, -1,-1,1, 1,1,1, -1,1,1,
            1,-1,-1, -1,-1,-1, -1,1,-1, 1,-1,-1, -1,1,-1, 1,1,-1,
            -1,-1,-1, -1,-1,1, -1,1,1, -1,-1,-1, -1,1,1, -1,1,-1,
            1,-1,1, 1,-1,-1, 1,1,-1, 1,-1,1, 1,1,-1, 1,1,1,
            -1,1,1, 1,1,1, 1,1,-1, -1,1,1, 1,1,-1, -1,1,-1,
            -1,-1,-1, 1,-1,-1, 1,-1,1, -1,-1,-1, 1,-1,1, -1,-1,1
        };
        private FloatBuffer vertices, sphereVertices, cubeNormals;
        private int sphereVertexCount;
        private int program, positionHandle, normalHandle, mvpHandle, modelHandle, colorHandle;
        private final float[] projection = new float[16], view = new float[16];
        private final float[] model = new float[16], mvp = new float[16], temp = new float[16];
        private volatile float playerX = 0, playerZ = 0, targetX = 0, targetZ = 0;
        private volatile float moveX = 0f, moveZ = 0f;
        private volatile float cameraYaw = 0f, cameraPitch = 5.4f;
        private int width = 1, height = 1;

        void setMoveInput(float x, float z) { moveX = Math.max(-1f,Math.min(1f,x)); moveZ = Math.max(-1f,Math.min(1f,z)); }
        void rotateCamera(float dx,float dy) {
            cameraYaw += dx*0.006f;
            cameraPitch = Math.max(3.0f,Math.min(9.0f,cameraPitch+dy*0.012f));
        }
        void setTarget(float x, float z) {
            targetX = Math.max(-18, Math.min(18, x));
            targetZ = Math.max(-18, Math.min(18, z));
        }
        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.48f, 0.70f, 0.88f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            ByteBuffer bb = ByteBuffer.allocateDirect(cube.length * 4).order(ByteOrder.nativeOrder());
            vertices = bb.asFloatBuffer(); vertices.put(cube).position(0);
            float[] faceNormals = new float[108];
            float[][] dirs = {{0,0,1},{0,0,-1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
            for (int face=0; face<6; face++) for (int vertex=0; vertex<6; vertex++) {
                int k=(face*6+vertex)*3;
                faceNormals[k]=dirs[face][0]; faceNormals[k+1]=dirs[face][1]; faceNormals[k+2]=dirs[face][2];
            }
            ByteBuffer nb=ByteBuffer.allocateDirect(faceNormals.length*4).order(ByteOrder.nativeOrder());
            cubeNormals=nb.asFloatBuffer(); cubeNormals.put(faceNormals).position(0);
            sphereVertices = buildSphere(24, 32);
            sphereVertexCount = sphereVertices.limit() / 3;
            int v = shader(GLES20.GL_VERTEX_SHADER, VS);
            int f = shader(GLES20.GL_FRAGMENT_SHADER, FS);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, v); GLES20.glAttachShader(program, f);
            GLES20.glLinkProgram(program);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVP");
            modelHandle = GLES20.glGetUniformLocation(program, "uModel");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");
        }
        private FloatBuffer buildSphere(int stacks, int slices) {
            java.util.ArrayList<Float> data = new java.util.ArrayList<>();
            for (int i=0;i<stacks;i++) {
                double a0=Math.PI*i/stacks, a1=Math.PI*(i+1)/stacks;
                for (int j=0;j<slices;j++) {
                    double b0=2*Math.PI*j/slices, b1=2*Math.PI*(j+1)/slices;
                    addTriangle(data,a0,b0,a1,b0,a1,b1);
                    addTriangle(data,a0,b0,a1,b1,a0,b1);
                }
            }
            float[] out=new float[data.size()];
            for(int i=0;i<out.length;i++) out[i]=data.get(i);
            ByteBuffer bytes=ByteBuffer.allocateDirect(out.length*4).order(ByteOrder.nativeOrder());
            FloatBuffer result=bytes.asFloatBuffer(); result.put(out).position(0); return result;
        }
        private void addTriangle(java.util.ArrayList<Float> out,double a,double b,double c,double d,double e,double f) {
            addPoint(out,a,b); addPoint(out,c,d); addPoint(out,e,f);
        }
        private void addPoint(java.util.ArrayList<Float> out,double a,double b) {
            out.add((float)(Math.sin(a)*Math.cos(b)));
            out.add((float)Math.cos(a));
            out.add((float)(Math.sin(a)*Math.sin(b)));
        }
        private void sphere(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(temp,0,view,0,model,0);
            Matrix.multiplyMM(mvp,0,projection,0,temp,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1f);
            sphereVertices.position(0);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,sphereVertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,sphereVertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sphereVertexCount);
        }
        private int shader(int type, String source) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, source); GLES20.glCompileShader(s); return s;
        }
        @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
            width = Math.max(1,w); height = Math.max(1,h);
            GLES20.glViewport(0,0,width,height);
            Matrix.perspectiveM(projection,0,60f,(float)width/height,0.1f,120f);
        }
        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (Math.abs(moveX)+Math.abs(moveZ)>0.05f) {
                playerX += moveX*0.075f; playerZ += moveZ*0.075f;
                targetX=playerX; targetZ=playerZ;
            } else { playerX += (targetX-playerX)*0.045f; playerZ += (targetZ-playerZ)*0.045f; }
            float cameraDistance=9.0f;
            float cameraX=playerX+(float)Math.sin(cameraYaw)*cameraDistance;
            float cameraZ=playerZ+(float)Math.cos(cameraYaw)*cameraDistance;
            Matrix.setLookAtM(view,0,cameraX,cameraPitch,cameraZ,playerX,0.9f,playerZ,0f,1f,0f);
            GLES20.glUseProgram(program);
            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,vertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,cubeNormals);

            box(0,-0.28f,0,190f,0.25f,190f,0.22f,0.42f,0.25f); // larger continuous city terrain
            // Asphalt avenues, raised sidewalks and curb edges.
            box(0,-0.12f,0,5.2f,0.04f,48f,0.16f,0.17f,0.19f);
            box(-3.05f,-0.075f,0,0.72f,0.075f,48f,0.48f,0.48f,0.45f);
            box(3.05f,-0.075f,0,0.72f,0.075f,48f,0.48f,0.48f,0.45f);
            box(0,-0.075f,-3.05f,48f,0.075f,0.72f,0.48f,0.48f,0.45f);
            box(0,-0.075f,3.05f,48f,0.075f,0.72f,0.48f,0.48f,0.45f);
            box(0,-0.10f,0,48f,0.04f,5.2f,0.16f,0.17f,0.19f);
            box(0,-0.07f,0,0.18f,0.025f,40f,0.85f,0.72f,0.35f);
            box(0,-0.07f,0,40f,0.025f,0.18f,0.85f,0.72f,0.35f);
            // Lane markings and crosswalks
            for (int i=-6;i<=6;i++) {
                box(i*4.2f,-0.045f,0,0.055f,0.018f,2.0f,0.92f,0.88f,0.66f);
                box(0,-0.045f,i*4.2f,2.0f,0.018f,0.055f,0.92f,0.88f,0.66f);
            }
            // Expanded urban grid: varied towers, apartments and commercial blocks
            for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++) {
                if (Math.abs(x)<=1 || Math.abs(z)<=1) continue;
                float bx=x*8.4f, bz=z*8.4f;
                float bh=3.0f + ((x*x+z*z+x*3+z*5)&3)*1.18f;
                float bw=1.85f+((x+z+12)&1)*0.55f;
                int style=(x*7+z*11+40)&3;
                building(bx,bz,bw,bh,style);
            }
            // Secondary boulevards create distinct city districts
            for (int road=-4;road<=4;road++) {
                if (road==0) continue;
                float lane=road*14.5f;
                box(lane,-0.115f,0,2.5f,0.045f,180f,0.18f,0.19f,0.21f);
                box(0,-0.115f,lane,180f,0.045f,2.5f,0.18f,0.19f,0.21f);
                for(int mark=-16;mark<=16;mark++) {
                    box(lane,-0.055f,mark*5.0f,0.045f,0.012f,1.6f,0.88f,0.82f,0.64f);
                    box(mark*5.0f,-0.055f,lane,1.6f,0.012f,0.045f,0.88f,0.82f,0.64f);
                }
            }
            // Trees around the blocks
            for (int i=-6;i<=6;i++) {
                tree(i*8.4f+2.1f, -5.2f);
                tree(i*8.4f-2.1f, 5.2f);
            }
            // Street furniture: lamps and bus-stop shelters
            for (int i=-3;i<=3;i++) {
                streetLamp(i*8.4f+3.0f,-6.0f);
                streetLamp(i*8.4f-3.0f,6.0f);
            }
            // Small park plaza with paths and benches
            box(10f,-0.015f,10f,3.2f,0.035f,3.2f,0.28f,0.48f,0.25f);
            box(10f,0.03f,10f,0.18f,0.025f,2.7f,0.72f,0.68f,0.55f);
            box(10f,0.03f,10f,2.7f,0.025f,0.18f,0.72f,0.68f,0.55f);
            bench(8.2f,9.0f);
            bench(11.8f,11.0f);
            // Moving traffic on the main avenue.
            float trafficTime=(System.nanoTime()%60000000000L)*0.000000001f;
            float carA=(float)Math.sin(trafficTime*0.42f)*10.5f;
            float carB=(float)Math.sin(trafficTime*0.42f+Math.PI)*10.5f;
            trafficCar(-3.9f, 0.28f, carA, 0.92f, 0.22f, 0.42f);
            trafficCar(3.9f, 0.28f, carB, 0.18f, 0.38f, 0.82f);
            trafficCar(-8f, 0.28f, 3.9f, 0.82f, 0.68f, 0.12f);
            trafficCar(8f, 0.28f, -3.9f, 0.20f, 0.68f, 0.38f);
            // Detailed original player avatar: jacket, shirt, trousers, shoes, hair and face
            drawPlayer(playerX,playerZ);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(normalHandle);
        }
        private void trafficCar(float x,float y,float z,float r,float g,float b) {
            // Original compact car with layered bodywork, windows, lights and animated rolling wheels.
            box(x,y,z,0.68f,0.20f,1.16f,r,g,b);
            box(x,y+0.15f,z+0.02f,0.63f,0.10f,0.82f,r*0.78f,g*0.78f,b*0.78f);
            box(x,y+0.28f,z-0.10f,0.49f,0.22f,0.62f,0.10f,0.16f,0.21f);
            box(x,y+0.29f,z-0.10f,0.42f,0.16f,0.54f,0.24f,0.43f,0.49f);
            box(x,y+0.28f,z+0.43f,0.49f,0.20f,0.27f,0.10f,0.16f,0.21f);
            // Front/rear bumpers and lamps.
            box(x,y-0.02f,z-1.17f,0.62f,0.075f,0.09f,0.13f,0.14f,0.15f);
            box(x,y-0.02f,z+1.17f,0.62f,0.075f,0.09f,0.13f,0.14f,0.15f);
            box(x-0.48f,y+0.015f,z-1.19f,0.13f,0.075f,0.035f,1.0f,0.88f,0.65f);
            box(x+0.48f,y+0.015f,z-1.19f,0.13f,0.075f,0.035f,1.0f,0.88f,0.65f);
            box(x-0.48f,y+0.015f,z+1.19f,0.13f,0.075f,0.035f,0.88f,0.08f,0.06f);
            box(x+0.48f,y+0.015f,z+1.19f,0.13f,0.075f,0.035f,0.88f,0.08f,0.06f);
            // Side windows, door seams and mirrors.
            for(int side=-1;side<=1;side+=2) {
                box(x+side*0.665f,y+0.27f,z-0.10f,0.025f,0.15f,0.31f,0.20f,0.36f,0.43f);
                box(x+side*0.69f,y+0.22f,z+0.35f,0.045f,0.065f,0.12f,0.12f,0.14f,0.16f);
                box(x+side*0.69f,y+0.04f,z+0.02f,0.018f,0.11f,0.012f,0.10f,0.11f,0.12f);
                // Rubber tires and metallic hubs at each axle.
                for(int axle=-1;axle<=1;axle+=2) {
                    float wz=z+axle*0.72f;
                    box(x+side*0.61f,y-0.105f,wz,0.105f,0.17f,0.19f,0.035f,0.038f,0.042f);
                    sphere(x+side*0.72f,y-0.105f,wz,0.055f,0.085f,0.085f,0.48f,0.50f,0.52f);
                    sphere(x+side*0.755f,y-0.105f,wz,0.022f,0.035f,0.035f,0.16f,0.17f,0.18f);
                }
            }
            box(x,y-0.005f,z-0.02f,0.08f,0.012f,0.84f,0.78f,0.78f,0.72f);
        }
        private void building(float x,float z,float w,float h,int style) {
            float d=2.35f;
            float[][] palettes={{0.48f,0.46f,0.42f},{0.36f,0.40f,0.43f},{0.55f,0.49f,0.39f},{0.39f,0.43f,0.39f}};
            float[] c=palettes[style];
            // Main facade, darker ground-floor storefront and roof parapet.
            box(x,h*0.5f,z,w,h*0.5f,d,c[0],c[1],c[2]);
            box(x,0.48f,z-d*0.505f,w*0.98f,0.45f,0.035f,0.19f,0.22f,0.24f);
            box(x,h+0.09f,z,w+0.10f,0.12f,d+0.10f,c[0]*0.72f,c[1]*0.72f,c[2]*0.72f);
            box(x,h+0.24f,z,0.62f,0.08f,0.62f,0.20f,0.22f,0.24f);
            int floors=Math.max(2,(int)(h/0.92f));
            for(int floor=0;floor<floors;floor++) {
                float wy=0.88f+floor*0.92f;
                if(wy>h-0.18f) continue;
                // Front and rear windows, with deep frames and sill lines.
                for(int col=-1;col<=1;col++) {
                    float wx=x+col*w*0.29f;
                    box(wx,wy,z-d*0.512f,0.36f,0.25f,0.035f,0.10f,0.22f,0.29f);
                    box(wx,wy,z-d*0.535f,0.29f,0.19f,0.018f,0.34f,0.61f,0.69f);
                    box(wx,wy,z+d*0.512f,0.36f,0.25f,0.035f,0.10f,0.22f,0.29f);
                    box(wx,wy,z+d*0.535f,0.29f,0.19f,0.018f,0.34f,0.61f,0.69f);
                }
                // Side windows and continuous floor slab details.
                for(int side=-1;side<=1;side+=2) {
                    for(int col=-1;col<=1;col++) {
                        float wz=z+col*d*0.29f;
                        box(x+side*(w*0.505f),wy,wz,0.035f,0.25f,0.34f,0.10f,0.22f,0.29f);
                        box(x+side*(w*0.528f),wy,wz,0.018f,0.19f,0.27f,0.34f,0.61f,0.69f);
                    }
                }
                box(x,wy-0.34f,z,w*0.99f,0.025f,d*1.01f,c[0]*0.82f,c[1]*0.82f,c[2]*0.82f);
            }
            // Awnings and entrance make the ground floor read as a real shop.
            box(x,0.92f,z-d*0.57f,w*0.54f,0.07f,0.22f,0.72f,0.25f,0.12f);
            box(x,0.50f,z-d*0.56f,0.36f,0.34f,0.035f,0.16f,0.35f,0.39f);
        }
        private void drawPlayer(float x,float z) {
            // Procedural humanoid with a smoother walk cycle, separate joints and idle breathing.
            float phase=(float)(System.nanoTime()*0.0000000048);
            float stride=(float)Math.sin(phase);
            float opposite=-stride;
            float travel=(float)Math.hypot(targetX-playerX,targetZ-playerZ);
            float moving=Math.max(Math.min(1f,travel*0.48f),Math.min(1f,(Math.abs(moveX)+Math.abs(moveZ))*0.65f));
            float idleBreath=(float)Math.sin(phase*0.48f)*0.012f;
            float bob=Math.abs(stride)*0.055f*moving+idleBreath;
            float armSwing=stride*0.27f*moving;
            float legSwing=stride*0.22f*moving;
            float skinR=0.58f,skinG=0.34f,skinB=0.22f;
            float jacketR=0.075f,jacketG=0.19f,jacketB=0.52f;
            float pantsR=0.105f,pantsG=0.13f,pantsB=0.19f;

            // Shoes follow the alternating stride instead of sliding in place.
            sphere(x-0.145f,0.105f+bob,z-0.11f+legSwing*0.72f,0.15f,0.09f,0.25f,0.035f,0.04f,0.055f);
            sphere(x+0.145f,0.105f+bob,z-0.11f-legSwing*0.72f,0.15f,0.09f,0.25f,0.035f,0.04f,0.055f);
            // Tapered, articulated trouser legs with rounded knee and calf transitions.
            segment(x-0.145f,0.83f+bob,z,x-0.145f,0.48f+bob+legSwing,z+legSwing*0.36f,0.125f,pantsR,pantsG,pantsB);
            segment(x-0.145f,0.48f+bob+legSwing,z+legSwing*0.36f,x-0.145f,0.18f+bob,z+legSwing*0.72f,0.092f,0.16f,0.18f,0.22f);
            segment(x+0.145f,0.83f+bob,z,x+0.145f,0.48f+bob-legSwing,z-legSwing*0.36f,0.125f,pantsR,pantsG,pantsB);
            segment(x+0.145f,0.48f+bob-legSwing,z-legSwing*0.36f,x+0.145f,0.18f+bob,z-legSwing*0.72f,0.092f,0.16f,0.18f,0.22f);
            sphere(x,0.91f+bob,z,0.255f,0.19f,0.16f,0.09f,0.15f,0.36f);

            // Jacket, chest panels and collar.
            sphere(x,1.16f+bob,z,0.315f,0.33f+idleBreath,0.19f,jacketR,jacketG,jacketB);
            sphere(x,1.36f+bob,z+0.005f,0.335f,0.155f,0.19f,0.10f,0.25f,0.62f);
            sphere(x,1.13f+bob,z-0.195f,0.055f,0.065f,0.018f,0.78f,0.79f,0.82f);
            sphere(x,0.94f+bob,z-0.165f,0.25f,0.045f,0.025f,0.045f,0.055f,0.075f);
            sphere(x-0.105f,1.19f+bob,z-0.181f,0.018f,0.24f,0.018f,0.20f,0.30f,0.58f);
            sphere(x+0.105f,1.19f+bob,z-0.181f,0.018f,0.24f,0.018f,0.20f,0.30f,0.58f);
            sphere(x,1.46f+bob,z-0.005f,0.115f,0.105f,0.12f,0.82f,0.80f,0.72f);
            sphere(x,1.49f+bob,z+0.012f,0.105f,0.07f,0.105f,skinR,skinG,skinB);

            // Arms swing in opposition to the legs, with elbows and hands.
            sphere(x-0.335f,1.34f+bob,z,0.155f,0.17f,0.155f,jacketR,jacketG,jacketB);
            sphere(x+0.335f,1.34f+bob,z,0.155f,0.17f,0.155f,jacketR,jacketG,jacketB);
            float leftShoulderX=x-0.34f, rightShoulderX=x+0.34f;
            float leftElbowX=x-0.42f-armSwing*0.35f, rightElbowX=x+0.42f+armSwing*0.35f;
            float leftHandX=x-0.435f-armSwing, rightHandX=x+0.435f+armSwing;
            float leftElbowY=1.13f+bob, rightElbowY=1.13f+bob;
            float handY=0.88f+bob;
            segment(leftShoulderX,1.34f+bob,z,leftElbowX,leftElbowY,z-armSwing*0.45f,0.125f,0.085f,0.21f,0.56f);
            segment(rightShoulderX,1.34f+bob,z,rightElbowX,rightElbowY,z+armSwing*0.45f,0.125f,0.085f,0.21f,0.56f);
            segment(leftElbowX,leftElbowY,z-armSwing*0.45f,leftHandX,handY,z-armSwing,0.092f,0.075f,0.18f,0.48f);
            segment(rightElbowX,rightElbowY,z+armSwing*0.45f,rightHandX,handY,z+armSwing,0.092f,0.075f,0.18f,0.48f);
            sphere(leftHandX,handY-0.04f,z-armSwing,0.075f,0.09f,0.075f,skinR,skinG,skinB);
            sphere(rightHandX,handY-0.04f,z+armSwing,0.075f,0.09f,0.075f,skinR,skinG,skinB);
            sphere(x-0.445f-armSwing,0.82f+bob,z-armSwing,0.085f,0.085f,0.085f,skinR,skinG,skinB);
            sphere(x+0.445f+armSwing,0.82f+bob,z+armSwing,0.085f,0.085f,0.085f,skinR,skinG,skinB);

            // Neck, head, ears, hair and facial details.
            sphere(x,1.55f+bob,z,0.09f,0.13f,0.09f,skinR,skinG,skinB);
            sphere(x,1.78f+bob,z,0.17f,0.225f,0.165f,skinR,skinG,skinB);
            sphere(x-0.184f,1.77f+bob,z,0.045f,0.07f,0.045f,skinR,skinG,skinB);
            sphere(x+0.184f,1.77f+bob,z,0.045f,0.07f,0.045f,skinR,skinG,skinB);
            sphere(x,1.955f+bob,z+0.005f,0.18f,0.075f,0.17f,0.035f,0.025f,0.02f);
            sphere(x,1.91f+bob,z-0.09f,0.13f,0.065f,0.095f,0.035f,0.025f,0.02f);
            sphere(x-0.15f,1.87f+bob,z+0.015f,0.055f,0.13f,0.15f,0.035f,0.025f,0.02f);
            sphere(x+0.15f,1.87f+bob,z+0.015f,0.055f,0.13f,0.15f,0.035f,0.025f,0.02f);
            sphere(x-0.075f,1.805f+bob,z-0.157f,0.022f,0.018f,0.014f,0.025f,0.02f,0.018f);
            sphere(x+0.075f,1.805f+bob,z-0.157f,0.022f,0.018f,0.014f,0.025f,0.02f,0.018f);
            sphere(x-0.075f,1.845f+bob,z-0.155f,0.045f,0.012f,0.018f,0.045f,0.03f,0.025f);
            sphere(x+0.075f,1.845f+bob,z-0.155f,0.045f,0.012f,0.018f,0.045f,0.03f,0.025f);
            sphere(x,1.735f+bob,z-0.181f,0.027f,0.045f,0.028f,0.48f,0.25f,0.16f);
            sphere(x,1.675f+bob,z-0.165f,0.043f,0.009f,0.012f,0.20f,0.045f,0.04f);

            // Backpack and shoulder straps.
            sphere(x,1.13f+bob,z+0.205f,0.19f,0.25f,0.075f,0.045f,0.055f,0.075f);
            sphere(x-0.24f,1.12f+bob,z+0.08f,0.035f,0.19f,0.045f,0.055f,0.10f,0.28f);
            sphere(x+0.24f,1.12f+bob,z+0.08f,0.035f,0.19f,0.045f,0.055f,0.10f,0.28f);
        }
        // Rounded limb mesh: an ellipsoid aligned between two joints instead of detached balls.
        private void segment(float ax,float ay,float az,float bx,float by,float bz,float radius,float r,float g,float b) {
            float dx=bx-ax, dy=by-ay, dz=bz-az;
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(length<0.001f) return;
            float mx=(ax+bx)*0.5f, my=(ay+by)*0.5f, mz=(az+bz)*0.5f;
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,mx,my,mz);
            float dot=Math.max(-1f,Math.min(1f,dy/length));
            float angle=(float)Math.toDegrees(Math.acos(dot));
            float rx=dz, rz=-dx;
            if(Math.abs(angle)>0.01f && (Math.abs(rx)+Math.abs(rz))>0.0001f) Matrix.rotateM(model,0,angle,rx,0f,rz);
            Matrix.scaleM(model,0,radius,length*0.62f,radius);
            Matrix.multiplyMM(temp,0,view,0,model,0);
            Matrix.multiplyMM(mvp,0,projection,0,temp,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1f);
            sphereVertices.position(0);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,sphereVertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,sphereVertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sphereVertexCount);
        }
        private void streetLamp(float x,float z) {
            box(x,1.45f,z,0.055f,1.45f,0.055f,0.20f,0.21f,0.23f);
            box(x,2.92f,z,0.22f,0.10f,0.22f,0.95f,0.84f,0.48f);
            box(x+0.28f,2.88f,z,0.32f,0.045f,0.045f,0.20f,0.21f,0.23f);
        }
        private void bench(float x,float z) {
            box(x,0.35f,z,0.72f,0.08f,0.22f,0.45f,0.25f,0.12f);
            box(x,0.58f,z+0.17f,0.72f,0.28f,0.08f,0.45f,0.25f,0.12f);
            box(x-0.5f,0.16f,z,0.06f,0.20f,0.07f,0.18f,0.18f,0.18f);
            box(x+0.5f,0.16f,z,0.06f,0.20f,0.07f,0.18f,0.18f,0.18f);
        }
        private void tree(float x,float z) {
            box(x,0.72f,z,0.12f,0.72f,0.12f,0.30f,0.18f,0.09f);
            sphere(x,1.62f,z,0.66f,0.78f,0.66f,0.075f,0.29f,0.12f);
            sphere(x-0.34f,1.48f,z+0.08f,0.42f,0.50f,0.43f,0.09f,0.34f,0.14f);
            sphere(x+0.34f,1.50f,z-0.06f,0.43f,0.52f,0.42f,0.065f,0.25f,0.10f);
        }
        private void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(temp,0,view,0,model,0);
            Matrix.multiplyMM(mvp,0,projection,0,temp,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1f);
            vertices.position(0); cubeNormals.position(0);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,vertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,cubeNormals);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
